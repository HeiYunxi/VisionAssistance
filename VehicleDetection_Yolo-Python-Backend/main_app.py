"""
Main application that composes detection and depth modules.
This file provides a `main()` function and can be run as a script.
"""

import time
import sys
import cv2
import numpy as np
import threading
import os

# --- Configuration ---
WEIGHTS = "yolov10m.pt"
CAMERA_INDEX = 0
CONF_THRESHOLD = 0.25
IOU_THRESHOLD = 0.45
AREA_FRAC_THRESHOLD = 0.3
DISPLAY_MAX_WIDTH = 1280
TARGET_WIDTH = 544
TARGET_HEIGHT = 960

# Import detection module (assumes detection.py exists in current directory)
import detection

# Thread-safe shared frame for external consumers
_frame_lock = threading.Lock()
_latest_frame = None
_frame_callback = None

# Thread-safe metadata
_meta_lock = threading.Lock()
_vehicle_count = 0
_last_inference_time = 0.0

# --- Added: Detailed data callback ---
_data_callback = None

def set_data_callback(cb):
    """
    Set detailed data callback.
    cb(detections, alert_level, inference_time_ms)
    """
    global _data_callback
    _data_callback = cb

def get_latest_frame():
    with _frame_lock:
        if _latest_frame is None: return None
        return _latest_frame.copy()

def set_frame_callback(cb):
    global _frame_callback
    _frame_callback = cb

def _publish_frame(frame):
    global _latest_frame
    try:
        fcpy = frame.copy()
    except Exception:
        fcpy = frame
    with _frame_lock:
        _latest_frame = fcpy
    if _frame_callback is not None:
        try:
            _frame_callback(fcpy)
        except Exception:
            pass

def get_vehicle_count():
    with _meta_lock: return int(_vehicle_count)

def get_last_inference_time():
    with _meta_lock: return float(_last_inference_time)

def _set_stats(vehicle_count: int, inference_time: float):
    global _vehicle_count, _last_inference_time
    with _meta_lock:
        _vehicle_count = int(vehicle_count)
        _last_inference_time = float(inference_time)

def init_model(weights_path: str):
    try:
        from ultralytics import YOLO
    except Exception:
        print("Error: ultralytics YOLO not available.")
        return None
    if not os.path.exists(weights_path):
        print(f"Warning: weights '{weights_path}' not found.")
    print(f"Loading YOLO model from {weights_path} ...")
    try:
        model = YOLO(weights_path)
        return model
    except Exception as e:
        print("Failed to load YOLO model:", e)
        return None

def init_camera(index: int, target_w: int, target_h: int):
    cap = cv2.VideoCapture(index)
    if not cap.isOpened():
        print(f"Cannot open camera {index}")
        sys.exit(2)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, float(target_w))
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, float(target_h))

    # Calculate display dimensions
    cam_w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or target_w)
    cam_h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or target_h)
    display_scale = 1.0
    if cam_w > DISPLAY_MAX_WIDTH and DISPLAY_MAX_WIDTH > 0:
        display_scale = DISPLAY_MAX_WIDTH / float(cam_w)
    disp_w = max(1, int(cam_w * display_scale))
    disp_h = max(1, int(cam_h * display_scale))
    return cap, disp_w, disp_h

def transform_frame_to_target(frame, target_w: int, target_h: int):
    h, w = frame.shape[:2]
    if (w, h) == (target_w, target_h): return frame
    scale = max(target_w / w, target_h / h)
    new_w = int(round(w * scale))
    new_h = int(round(h * scale))
    resized = cv2.resize(frame, (new_w, new_h), interpolation=cv2.INTER_LINEAR)
    x0 = max(0, (new_w - target_w) // 2)
    y0 = max(0, (new_h - target_h) // 2)
    cropped = resized[y0:y0 + target_h, x0:x0 + target_w]
    return cropped

def main():
    global _data_callback

    model = init_model(WEIGHTS)
    if not model: sys.exit(1)

    cap, disp_w, disp_h = init_camera(CAMERA_INDEX, TARGET_WIDTH, TARGET_HEIGHT)

    # Warmup
    try:
        test_img = (255 * np.zeros((TARGET_HEIGHT, TARGET_WIDTH, 3), dtype=np.uint8))
        _ = model.predict(source=test_img, conf=CONF_THRESHOLD, iou=IOU_THRESHOLD, verbose=False)
    except Exception: pass

    print("Main app started. Press q to quit.")

    while True:
        ret, frame = cap.read()
        if not ret:
            print("Failed to read frame")
            break

        frame_t = transform_frame_to_target(frame, TARGET_WIDTH, TARGET_HEIGHT)

        # Detection
        try:
            t0 = time.time()
            # Assume detection.detect_frame returns a list
            dets = detection.detect_frame(model, frame_t, conf=CONF_THRESHOLD, iou=IOU_THRESHOLD)
            inference_time = time.time() - t0
        except Exception as e:
            print(f"Detection error: {e}")
            dets = []
            inference_time = 0.0

        # Still call the original drawing function, but the boolean 'alert' returned here is no longer the main logic
        vis, _ = detection.draw_detections(frame_t, dets, area_frac_threshold=AREA_FRAC_THRESHOLD)

        # 1. Publish video frame
        try:
            _publish_frame(vis)
        except Exception: pass

        # 2. Update simple statistics
        try:
            _set_stats(len(dets), inference_time)
        except Exception: pass

        # 3. [Modified] Calculate alert level and push
        if _data_callback:
            try:
                # Calculate alert level
                count = len(dets)
                if count < 2:
                    alert_level = 0  # No alert
                elif 2 <= count <= 7:
                    alert_level = 1  # Low alert
                else:
                    alert_level = 2  # High alert

                # Serialize detection results
                serializable_dets = []
                for d in dets:
                    # Compatible with dictionary or object attributes
                    bbox = d.get('bbox') if isinstance(d, dict) else getattr(d, 'bbox', [])
                    conf = d.get('confidence') if isinstance(d, dict) else getattr(d, 'confidence', 0)
                    cls_name = d.get('class') if isinstance(d, dict) else getattr(d, 'class', 'unknown')

                    serializable_dets.append({
                        "bbox": [float(x) for x in bbox] if bbox else [],
                        "confidence": float(conf),
                        "class": str(cls_name)
                    })

                # Pass alert_level (int) instead of the original bool
                _data_callback(serializable_dets, alert_level, inference_time * 1000)
            except Exception as e:
                print(f"Callback error: {e}")

        # Display
        cv2.imshow("CarAlert", cv2.resize(vis, (disp_w, disp_h), interpolation=cv2.INTER_AREA))

        key = cv2.waitKey(1) & 0xFF
        if key in (ord('q'), ord('Q')):
            break

    cap.release()
    cv2.destroyAllWindows()

if __name__ == '__main__':
    main()