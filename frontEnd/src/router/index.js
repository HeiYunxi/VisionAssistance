import { createRouter, createWebHashHistory } from "vue-router";

const routes = [
    {
        path: '/',
        meta: {
            title: '首页'
        },
        // component: () => import('../views/index.vue'),
        component: () => import('../views/yoloView.vue'),

    }
];

const router = createRouter({
    history: createWebHashHistory(),
    routes
});

export default router;