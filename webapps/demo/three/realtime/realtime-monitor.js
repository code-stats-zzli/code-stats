// ----- 导入 Three.js 核心库 -----
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { CSS2DRenderer, CSS2DObject } from 'three/addons/renderers/CSS2DRenderer.js';

// ----- 工具：获取 Context Path -----
function getContextPath() {
    const path = window.location.pathname;
    const parts = path.split('/');
    if (parts.length > 1 && parts[1]) return '/' + parts[1];
    return '';
}
const CONTEXT_PATH = getContextPath();
function apiUrl(path) {
    if (path.startsWith('/')) return CONTEXT_PATH + path;
    return CONTEXT_PATH + '/' + path;
}

// ----- DOM 引用 -----
const container = document.getElementById('canvas-container');
const loadingOverlay = document.getElementById('loading-overlay');
const loadingText = document.getElementById('loading-text');
const statusSpan = document.getElementById('status');
const toggleBtn = document.getElementById('toggleAutoRotate');

// ----- Three.js 全局变量 -----
let scene, camera, renderer, labelRenderer, controls;
let threadGroup, memoryGroup, poolGroup, requestStatsGroup;
let animationId = null;
let autoRotate = true;
let dataInterval = null;

let currentData = null;

// ----- 初始化场景 -----
function initScene() {
    const width = container.clientWidth;
    const height = container.clientHeight;

    scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0a0f1a);

    camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);
    camera.position.set(18, 12, 22);
    camera.lookAt(0, 0, 0);

    renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    container.appendChild(renderer.domElement);

    labelRenderer = new CSS2DRenderer();
    labelRenderer.setSize(width, height);
    labelRenderer.domElement.style.position = 'absolute';
    labelRenderer.domElement.style.top = '0';
    labelRenderer.domElement.style.left = '0';
    labelRenderer.domElement.style.pointerEvents = 'none';
    container.appendChild(labelRenderer.domElement);

    controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
    controls.autoRotate = true;
    controls.autoRotateSpeed = 1.2;
    controls.target.set(0, 0, 0);
    controls.update();

    const ambient = new THREE.AmbientLight(0x404060);
    scene.add(ambient);
    const dirLight = new THREE.DirectionalLight(0xffffff, 1.2);
    dirLight.position.set(5, 10, 7);
    scene.add(dirLight);
    const backLight = new THREE.DirectionalLight(0x4da6ff, 0.5);
    backLight.position.set(-5, 0, -5);
    scene.add(backLight);

    const gridHelper = new THREE.GridHelper(30, 20, 0x4da6ff, 0x2a4a7a);
    gridHelper.position.y = -3;
    scene.add(gridHelper);

    threadGroup = new THREE.Group();
    memoryGroup = new THREE.Group();
    poolGroup = new THREE.Group();
    requestStatsGroup = new THREE.Group();
    scene.add(threadGroup);
    scene.add(memoryGroup);
    scene.add(poolGroup);
    scene.add(requestStatsGroup);

    window.addEventListener('resize', onResize);
    loadingOverlay.classList.add('hidden');
}

function onResize() {
    const width = container.clientWidth;
    const height = container.clientHeight;
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    renderer.setSize(width, height);
    labelRenderer.setSize(width, height);
}

function clearGroup(group) {
    while (group.children.length) {
        const child = group.children[0];
        if (child.geometry) child.geometry.dispose();
        if (child.material) child.material.dispose();
        group.remove(child);
    }
}

// ----- 渲染线程柱状体 -----
function renderThreads(data) {
    clearGroup(threadGroup);
    const states = data.states || {};
    const counts = ['RUNNABLE', 'WAITING', 'TIMED_WAITING', 'BLOCKED', 'NEW', 'TERMINATED'];
    const colors = [0x22c55e, 0xf59e0b, 0xf59e0b, 0xef4444, 0x94a3b8, 0x94a3b8];
    const labels = ['运行', '等待', '定时等待', '阻塞', '新建', '终止'];

    const barWidth = 2.0;
    const maxHeight = 8;
    const maxCount = 50;

    let totalBars = 0;
    const validCounts = counts.map(s => states[s] || 0);
    validCounts.forEach(c => totalBars += c);
    if (totalBars === 0) return;

    const startX = -(totalBars * barWidth) / 2 + barWidth / 2;
    let x = startX;

    validCounts.forEach((count, idx) => {
        if (count === 0) return;
        const height = Math.max(0.5, (count / maxCount) * maxHeight);
        const geometry = new THREE.BoxGeometry(barWidth * 0.9, height, barWidth * 0.9);
        const material = new THREE.MeshPhongMaterial({
            color: colors[idx],
            emissive: new THREE.Color(colors[idx]).multiplyScalar(0.2),
            transparent: true,
            opacity: 0.85
        });
        const box = new THREE.Mesh(geometry, material);
        box.position.set(x, height / 2 - 2.5, 0);
        threadGroup.add(box);

        const div = document.createElement('div');
        div.textContent = `${labels[idx]}: ${count}`;
        div.style.color = '#e2e8f0';
        div.style.fontSize = '12px';
        div.style.fontWeight = '500';
        div.style.textShadow = '0 0 8px rgba(0,0,0,0.9)';
        div.style.background = 'rgba(0,0,0,0.6)';
        div.style.padding = '2px 10px';
        div.style.borderRadius = '12px';
        div.style.border = '1px solid rgba(255,255,255,0.15)';
        const label = new CSS2DObject(div);
        label.position.set(x, height - 2, 0);
        threadGroup.add(label);

        x += barWidth;
    });
}

// ----- 渲染内存（环形图 + 细分区域） -----
function renderMemory(data) {
    clearGroup(memoryGroup);
    const { heapUsed, heapMax, nonHeapUsed, pools } = data;

    const heapRatio = Math.min(1, heapUsed / (heapMax || 1));
    const nonHeapRatio = Math.min(1, nonHeapUsed / (heapMax || 1) * 0.5);

    const segments = 60;
    const radius = 3.5;
    const heightScale = 3.0;

    // 堆内存（蓝色）
    const heapCount = Math.floor(segments * heapRatio);
    for (let i = 0; i < heapCount; i++) {
        const angle = (i / segments) * Math.PI * 2;
        const x = radius * Math.cos(angle);
        const z = radius * Math.sin(angle);
        const h = 0.3 + (i / segments) * heightScale;
        const geo = new THREE.BoxGeometry(0.25, h, 0.25);
        const mat = new THREE.MeshPhongMaterial({
            color: 0x4da6ff,
            emissive: new THREE.Color(0x4da6ff).multiplyScalar(0.2)
        });
        const cube = new THREE.Mesh(geo, mat);
        cube.position.set(x, h / 2 - 1.5, z);
        memoryGroup.add(cube);
    }

    // 非堆（紫色）
    const nonHeapCount = Math.floor(segments * nonHeapRatio);
    for (let i = 0; i < nonHeapCount; i++) {
        const angle = (i / segments) * Math.PI * 2 + 0.2;
        const x = (radius + 1.2) * Math.cos(angle);
        const z = (radius + 1.2) * Math.sin(angle);
        const h = 0.3 + (i / segments) * heightScale * 0.6;
        const geo = new THREE.BoxGeometry(0.25, h, 0.25);
        const mat = new THREE.MeshPhongMaterial({
            color: 0xa78bfa,
            emissive: new THREE.Color(0xa78bfa).multiplyScalar(0.2)
        });
        const cube = new THREE.Mesh(geo, mat);
        cube.position.set(x, h / 2 - 1.5, z);
        memoryGroup.add(cube);
    }

    // 细分内存池（使用小立方体堆叠成环形）
    if (pools) {
        const poolNames = Object.keys(pools).filter(k => k.endsWith('.used'));
        const poolColors = [0x22d3ee, 0x60a5fa, 0x34d399, 0xfbbf24, 0xf472b6];
        let idx = 0;
        const poolRadius = radius + 2.5;
        poolNames.forEach(key => {
            const used = pools[key] || 0;
            const maxKey = key.replace('.used', '.max');
            const maxVal = pools[maxKey] || used;
            const ratio = maxVal > 0 ? Math.min(1, used / maxVal) : 0;
            if (ratio === 0) return;
            const count = Math.max(1, Math.floor(segments * ratio * 0.3));
            const color = poolColors[idx % poolColors.length];
            const angleOffset = idx * 0.5;
            for (let i = 0; i < count; i++) {
                const angle = (i / segments) * Math.PI * 2 + angleOffset;
                const x = poolRadius * Math.cos(angle);
                const z = poolRadius * Math.sin(angle);
                const h = 0.2 + ratio * 1.5;
                const geo = new THREE.BoxGeometry(0.2, h, 0.2);
                const mat = new THREE.MeshPhongMaterial({ color: color, emissive: new THREE.Color(color).multiplyScalar(0.2) });
                const cube = new THREE.Mesh(geo, mat);
                cube.position.set(x, h / 2 - 1.5, z);
                memoryGroup.add(cube);
            }
            idx++;
        });
    }

    const labelDiv = document.createElement('div');
    labelDiv.textContent = `堆 ${(heapRatio*100).toFixed(0)}% | 非堆 ${(nonHeapRatio*100).toFixed(0)}%`;
    labelDiv.style.color = '#e2e8f0';
    labelDiv.style.fontSize = '13px';
    labelDiv.style.fontWeight = '500';
    labelDiv.style.textShadow = '0 0 8px rgba(0,0,0,0.9)';
    labelDiv.style.background = 'rgba(0,0,0,0.6)';
    labelDiv.style.padding = '4px 14px';
    labelDiv.style.borderRadius = '20px';
    labelDiv.style.border = '1px solid rgba(255,255,255,0.15)';
    const label = new CSS2DObject(labelDiv);
    label.position.set(0, 0.5, 0);
    memoryGroup.add(label);
}

// ----- 渲染连接池蓄水池 -----
function renderPool(data) {
    clearGroup(poolGroup);
    const { active, idle, max } = data;
    const total = max || 1;
    const activeRatio = Math.min(1, active / total);
    const idleRatio = Math.min(1, idle / total);

    const poolRadius = 2.5;
    const poolHeight = 4;
    const waterHeight = poolHeight * (activeRatio + idleRatio * 0.3);
    const geo = new THREE.CylinderGeometry(poolRadius, poolRadius, poolHeight, 32);
    const mat = new THREE.MeshPhongMaterial({
        color: 0x1e293b,
        transparent: true,
        opacity: 0.4,
        wireframe: false,
        side: THREE.DoubleSide
    });
    const cylinder = new THREE.Mesh(geo, mat);
    cylinder.position.set(0, 0, 0);
    poolGroup.add(cylinder);

    if (waterHeight > 0.1) {
        const waterGeo = new THREE.CylinderGeometry(poolRadius * 0.9, poolRadius * 0.9, waterHeight, 32);
        const waterMat = new THREE.MeshPhongMaterial({
            color: 0x22c55e,
            transparent: true,
            opacity: 0.6,
            emissive: new THREE.Color(0x22c55e).multiplyScalar(0.2)
        });
        const water = new THREE.Mesh(waterGeo, waterMat);
        water.position.set(0, -poolHeight/2 + waterHeight/2, 0);
        poolGroup.add(water);
    }

    const idleCount = Math.min(idle, 20);
    for (let i = 0; i < idleCount; i++) {
        const angle = (i / idleCount) * Math.PI * 2;
        const r = poolRadius * 0.6;
        const x = r * Math.cos(angle);
        const z = r * Math.sin(angle);
        const dot = new THREE.Mesh(
            new THREE.SphereGeometry(0.15, 8, 8),
            new THREE.MeshPhongMaterial({ color: 0x94a3b8 })
        );
        dot.position.set(x, -poolHeight/2 + 0.2, z);
        poolGroup.add(dot);
    }

    const labelDiv = document.createElement('div');
    labelDiv.textContent = `活跃 ${active} / 空闲 ${idle} / 最大 ${max}`;
    labelDiv.style.color = '#e2e8f0';
    labelDiv.style.fontSize = '13px';
    labelDiv.style.fontWeight = '500';
    labelDiv.style.textShadow = '0 0 8px rgba(0,0,0,0.9)';
    labelDiv.style.background = 'rgba(0,0,0,0.6)';
    labelDiv.style.padding = '4px 14px';
    labelDiv.style.borderRadius = '20px';
    labelDiv.style.border = '1px solid rgba(255,255,255,0.15)';
    const label = new CSS2DObject(labelDiv);
    label.position.set(0, -poolHeight/2 - 1.2, 0);
    poolGroup.add(label);
}

// ----- 渲染请求统计（CSS2D显示） -----
function renderRequestStats(data) {
    clearGroup(requestStatsGroup);
    if (!data) return;

    const { total, running, errors, avgResponseTime, recent } = data;
    const div = document.createElement('div');
    div.style.color = '#e2e8f0';
    div.style.fontSize = '13px';
    div.style.background = 'rgba(0,0,0,0.6)';
    div.style.padding = '12px 16px';
    div.style.borderRadius = '16px';
    div.style.border = '1px solid rgba(0,102,255,0.4)';
    div.style.backdropFilter = 'blur(4px)';
    div.style.maxWidth = '300px';
    div.style.maxHeight = '200px';
    div.style.overflowY = 'auto';
    div.innerHTML = `
        <div><strong>📊 请求统计</strong></div>
        <div>总请求: ${total}</div>
        <div>运行中: ${running}</div>
        <div>错误: ${errors}</div>
        <div>平均响应: ${avgResponseTime}ms</div>
        <div style="margin-top:8px;font-size:12px;">最近请求:</div>
        ${recent ? recent.map(r => `<div style="border-bottom:1px solid #333;padding:4px 0;">${r.uri} (${r.duration}ms)${r.error ? ' ❌' : ''}</div>`).join('') : ''}
    `;
    const label = new CSS2DObject(div);
    label.position.set(8, 2, 0);
    requestStatsGroup.add(label);
}

// ----- 更新所有场景 -----
function updateScene(data) {
    if (!data) return;
    currentData = data;
    renderThreads(data.threads);
    renderMemory(data.memory);
    renderPool(data.pool);
    renderRequestStats(data.requests);
}

// ----- 从后端获取数据（✅ 修正接口路径）-----
async function fetchMonitorData() {
    try {
        // ★★★ 修改点：路径从 /3d/api/jvm-data 改为 /3d/api/monitor-data ★★★
        const resp = await fetch(apiUrl('/3d/api/monitor-data'));
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const data = await resp.json();
        if (data.error) throw new Error(data.error);
        updateScene(data);
        statusSpan.textContent = '🟢 实时';
    } catch (err) {
        console.warn('获取数据失败，使用模拟数据', err);
        const mock = generateMockData();
        updateScene(mock);
        statusSpan.textContent = '🟡 模拟模式';
    }
}

// ----- 模拟数据（仅演示，正常情况下不会触发）-----
function generateMockData() {
    return {
        threads: {
            count: 30,
            states: { RUNNABLE: 18, WAITING: 5, TIMED_WAITING: 4, BLOCKED: 2, NEW: 1, TERMINATED: 0 }
        },
        memory: {
            heapUsed: 512 * 1024 * 1024,
            heapMax: 1024 * 1024 * 1024,
            nonHeapUsed: 120 * 1024 * 1024,
            pools: {
                "Eden Space.used": 200 * 1024 * 1024,
                "Eden Space.max": 300 * 1024 * 1024,
                "Survivor Space.used": 30 * 1024 * 1024,
                "Survivor Space.max": 50 * 1024 * 1024,
                "Tenured Gen.used": 280 * 1024 * 1024,
                "Tenured Gen.max": 700 * 1024 * 1024,
                "Metaspace.used": 100 * 1024 * 1024,
                "Metaspace.max": 200 * 1024 * 1024
            }
        },
        pool: { active: 8, idle: 5, max: 30 },
        requests: {
            total: 1250,
            running: 3,
            errors: 12,
            avgResponseTime: 45,
            recent: [
                { uri: '/agent/api/chat', duration: 120, error: false },
                { uri: '/code/api/analyze', duration: 350, error: false },
                { uri: '/dbclient/api/tables', duration: 80, error: true },
            ]
        }
    };
}

// ----- 启动定时刷新 -----
function startPolling() {
    if (dataInterval) clearInterval(dataInterval);
    fetchMonitorData();
    dataInterval = setInterval(fetchMonitorData, 1000);
}

// ----- 动画循环 -----
function animate() {
    animationId = requestAnimationFrame(animate);
    controls.update();
    renderer.render(scene, camera);
    labelRenderer.render(scene, camera);
}

// ----- 启动 -----
function start() {
    if (!scene) {
        initScene();
        animate();
    }
    startPolling();
}

toggleBtn.addEventListener('click', () => {
    autoRotate = !autoRotate;
    controls.autoRotate = autoRotate;
    toggleBtn.textContent = autoRotate ? '⏸️ 暂停旋转' : '▶️ 恢复旋转';
});

if (document.readyState === 'complete') {
    start();
} else {
    window.addEventListener('load', start);
}

window.reloadMonitor = start;