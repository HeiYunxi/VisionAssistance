"""
Vue Dedicated Server - Provides API and MJPEG stream of YOLO detection data for Vue frontend.
"""

from flask import Flask, Response, jsonify, request
from flask_cors import CORS
import threading
import time
import cv2
import main_app  # Import modified main_app

# Global variable to store detection data
detection_data = {
    "detections": [],
    "vehicle_count": 0,
    "alert_triggered": False,
    "fps": 0,
    "processing_time": 0,
    "last_update": 0
}

app = Flask(__name__)
CORS(app)  # Allow cross-origin requests
data_lock = threading.Lock()

def update_detection_data(detections, alert_triggered, processing_time):
    """Update detection data - used by main_app callback."""
    global detection_data
    with data_lock:
        detection_data.update({
            "detections": detections,
            "vehicle_count": len(detections),
            "alert_triggered": alert_triggered,
            "processing_time": processing_time,
            "last_update": time.time()
        })

@app.route('/api/detections')
def get_detections():
    """Get current detection results."""
    with data_lock:
        data = {
            "detections": detection_data["detections"],
            "vehicle_count": detection_data["vehicle_count"],
            "alert_triggered": detection_data["alert_triggered"],
            "processing_time": detection_data["processing_time"],
            "last_update": detection_data["last_update"]
        }
        return jsonify(data)

@app.route('/stream')
def stream():
    """MJPEG video stream."""
    def gen_frames():
        while True:
            frame = main_app.get_latest_frame()
            if frame is None:
                time.sleep(0.05)
                continue
            try:
                ret, jpg = cv2.imencode('.jpg', frame)
                if ret:
                    yield (b'--frame\r\n'
                           b'Content-Type: image/jpeg\r\n\r\n' + jpg.tobytes() + b'\r\n')
            except Exception:
                pass
            time.sleep(0.01)  # Limit stream frame rate to save bandwidth

    return Response(gen_frames(), mimetype='multipart/x-mixed-replace; boundary=frame')
@app.route('/stream/raw')
def raw_stream():
    """原始摄像头流，不带检测框"""
    def gen_frames():
        while True:
            frame = main_app.get_latest_frame()
            if frame is None:
                time.sleep(0.05)
                continue
            try:
                ret, jpg = cv2.imencode('.jpg', frame)
                if ret:
                    yield (b'--frame\r\n'
                           b'Content-Type: image/jpeg\r\n\r\n' + jpg.tobytes() + b'\r\n')
            except Exception:
                pass
            time.sleep(0.033)  # ~30 FPS
    return Response(gen_frames(), mimetype='multipart/x-mixed-replace; boundary=frame')

@app.route('/api/config', methods=['GET', 'POST'])
def config():
    """Get or update configuration."""
    if request.method == 'POST':
        data = request.json
        if 'confidence' in data:
            main_app.CONF_THRESHOLD = float(data['confidence'])
        if 'area_threshold' in data:
            main_app.AREA_FRAC_THRESHOLD = float(data['area_threshold'])
        return jsonify({"status": "success"})

    return jsonify({
        "confidence": main_app.CONF_THRESHOLD,
        "area_threshold": main_app.AREA_FRAC_THRESHOLD,
        "target_width": main_app.TARGET_WIDTH,
        "target_height": main_app.TARGET_HEIGHT
    })

@app.route('/api/status')
def status():
    """Get system status."""
    return jsonify({
        "camera_connected": True,
        "model_loaded": True,
        "processing": True,
        "uptime": time.time() - getattr(main_app, '_start_time', time.time())
    })

def _run_flask():
    app.run(host='0.0.0.0', port=5000, threaded=True, use_reloader=False)

if __name__ == '__main__':
    # Record start time
    main_app._start_time = time.time()

    print("=" * 60)
    print("Vue server starting...")

    # --- Key modification: Establish data connection ---
    # Define a bridge function to pass main_app data to Flask storage variables
    def bridge_callback(detections, alert, proc_time):
        update_detection_data(detections, alert, proc_time)
        # Simple logging, print once every 30 frames to prevent flooding
        if int(time.time() * 10) % 30 == 0:
            print(f"Data sync: {len(detections)} vehicles, {proc_time:.1f}ms")

    # Register callback
    main_app.set_data_callback(bridge_callback)
    print("Connected detection engine with Web server")

    # Start Flask thread
    t = threading.Thread(target=_run_flask, daemon=True)
    t.start()

    print('- API: http://127.0.0.1:5000/api/detections')
    print('- Stream: http://127.0.0.1:5000/stream')
    print("=" * 60)

    # Start main application (this will block the main thread)
    try:
        main_app.main()
    except KeyboardInterrupt:
        print("Stopping service...")