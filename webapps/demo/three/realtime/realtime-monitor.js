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
let threadGroup, memoryGroup, poolGroup, requestStatsGroup, memoryRegionGroup, threadFlowGroup;
let animationId = null;
let autoRotate = true;
let dataInterval = null;
let currentData = null;
let flowParticles = []; // 存储每个线程流的粒子系统

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

    // 分组
    threadGroup = new THREE.Group();
    memoryGroup = new THREE.Group();
    poolGroup = new THREE.Group();
    requestStatsGroup = new THREE.Group();
    memoryRegionGroup = new THREE.Group();
    threadFlowGroup = new THREE.Group();
    scene.add(threadGroup);
    scene.add(memoryGroup);
    scene.add(poolGroup);
    scene.add(requestStatsGroup);
    scene.add(memoryRegionGroup);
    scene.add(threadFlowGroup);

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

// ----- 渲染线程柱状体（原有）-----
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

// ----- 渲染内存（环形图 + 细分区域）-----
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

    // 细分内存池（环形小立方体）
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

// ----- 渲染内存分区（新生代/老年代 3D 模型）-----
function renderMemoryRegions(pools) {
    clearGroup(memoryRegionGroup);
    if (!pools) return;

    // 从 pools 中提取各区域大小（单位 MB）
    const edenUsed = (pools['Eden Space.used'] || 0) / (1024 * 1024);
    const edenMax = (pools['Eden Space.max'] || 1) / (1024 * 1024);
    const s0Used = (pools['Survivor Space.used'] || 0) / (1024 * 1024);
    const s0Max = (pools['Survivor Space.max'] || 1) / (1024 * 1024);
    const s1Used = (pools['Survivor Space.used'] || 0) / (1024 * 1024); // 通常 S0 和 S1 大小相同
    const s1Max = (pools['Survivor Space.max'] || 1) / (1024 * 1024);
    const oldUsed = (pools['Tenured Gen.used'] || 0) / (1024 * 1024);
    const oldMax = (pools['Tenured Gen.max'] || 1) / (1024 * 1024);

    // 比例因子（缩放至合适大小）
    const scale = 0.8 / Math.max(edenMax, s0Max, s1Max, oldMax, 1);

    // 创建区域
    const regionData = [
        { name: 'Eden', used: edenUsed, max: edenMax, color: 0x4fc3f7, y: 2 },
        { name: 'S0', used: s0Used, max: s0Max, color: 0x4dd0e1, y: 0 },
        { name: 'S1', used: s1Used, max: s1Max, color: 0x4dd0e1, y: -2 },
        { name: 'Old', used: oldUsed, max: oldMax, color: 0x1a237e, y: -4 }
    ];

    const width = 2.0;
    const depth = 1.5;
    const maxHeight = 3.0;

    regionData.forEach((r, idx) => {
        const ratio = Math.min(1, r.used / r.max);
        const height = Math.max(0.2, ratio * maxHeight);
        const geo = new THREE.BoxGeometry(width, height, depth);
        const mat = new THREE.MeshPhongMaterial({
            color: r.color,
            transparent: true,
            opacity: 0.8,
            emissive: new THREE.Color(r.color).multiplyScalar(0.2)
        });
        const mesh = new THREE.Mesh(geo, mat);
        const x = (idx - 1.5) * 3.0;
        mesh.position.set(x, height / 2 - 2, 0);
        memoryRegionGroup.add(mesh);

        // 边框
        const edges = new THREE.EdgesGeometry(geo);
        const line = new THREE.LineSegments(edges, new THREE.LineBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.3 }));
        line.position.copy(mesh.position);
        memoryRegionGroup.add(line);

        // 标签
        const labelDiv = document.createElement('div');
        labelDiv.textContent = `${r.name}\n${(r.used).toFixed(1)}MB / ${(r.max).toFixed(1)}MB`;
        labelDiv.style.color = '#fff';
        labelDiv.style.fontSize = '11px';
        labelDiv.style.textAlign = 'center';
        labelDiv.style.background = 'rgba(0,0,0,0.6)';
        labelDiv.style.padding = '2px 6px';
        labelDiv.style.borderRadius = '8px';
        labelDiv.style.border = '1px solid rgba(255,255,255,0.2)';
        labelDiv.style.whiteSpace = 'pre-line';
        const label = new CSS2DObject(labelDiv);
        label.position.set(x, height + 0.3 - 2, 0);
        memoryRegionGroup.add(label);
    });

    // 添加算法说明
    const algoDiv = document.createElement('div');
    algoDiv.textContent = '新生代: 复制算法  |  老年代: 标记-整理';
    algoDiv.style.color = '#94a3b8';
    algoDiv.style.fontSize = '12px';
    algoDiv.style.background = 'rgba(0,0,0,0.5)';
    algoDiv.style.padding = '4px 12px';
    algoDiv.style.borderRadius = '20px';
    algoDiv.style.border = '1px solid rgba(255,255,255,0.1)';
    const algoLabel = new CSS2DObject(algoDiv);
    algoLabel.position.set(0, -5, 0);
    memoryRegionGroup.add(algoLabel);
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

// ----- 渲染请求统计（科技感面板）-----
function renderRequestStats(data) {
    clearGroup(requestStatsGroup);
    if (!data) return;

    const { total, running, errors, avgResponseTime, recent } = data;
    const errorRate = total > 0 ? (errors / total * 100).toFixed(1) : 0;

    const div = document.createElement('div');
    div.style.cssText = `
        color: #e2e8f0;
        font-size: 13px;
        background: rgba(0,0,0,0.7);
        backdrop-filter: blur(8px);
        padding: 16px 20px;
        border-radius: 16px;
        border: 1px solid rgba(0,102,255,0.6);
        box-shadow: 0 0 20px rgba(0,102,255,0.2);
        min-width: 220px;
        pointer-events: none;
    `;
    div.innerHTML = `
        <div style="font-weight:600;color:#4da6ff;margin-bottom:10px;font-size:14px;">📊 请求统计</div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:4px 16px;">
            <span>总请求</span><span style="color:#4da6ff;font-weight:600;">${total}</span>
            <span>运行中</span><span style="color:#22c55e;font-weight:600;">${running}</span>
            <span>错误</span><span style="color:#ef4444;font-weight:600;">${errors}</span>
            <span>平均响应</span><span style="color:#fbbf24;font-weight:600;">${avgResponseTime}ms</span>
        </div>
        <div style="margin-top:8px;height:4px;background:#1e293b;border-radius:4px;overflow:hidden;">
            <div style="width:${errorRate}%;height:100%;background:linear-gradient(90deg,#ef4444,#f59e0b);"></div>
        </div>
        <div style="font-size:11px;color:#94a3b8;margin-top:4px;text-align:right;">错误率 ${errorRate}%</div>
        ${recent && recent.length ? `<div style="margin-top:8px;font-size:11px;color:#94a3b8;border-top:1px solid rgba(255,255,255,0.1);padding-top:8px;">最近: ${recent.slice(0,2).map(r => `${r.uri} (${r.duration}ms)`).join(' | ')}</div>` : ''}
    `;
    const label = new CSS2DObject(div);
    label.position.set(8, 2, 0);
    requestStatsGroup.add(label);
}

// ===================== 线程详情面板 =====================
let threadPanel = null;

function renderThreadDetails(threads) {
    if (!threadPanel) {
        const div = document.createElement('div');
        div.id = 'threadPanel';
        div.style.cssText = `
            position: absolute;
            top: 20px;
            right: 20px;
            width: 460px;
            max-height: 60vh;
            overflow-y: auto;
            background: rgba(0,0,0,0.8);
            backdrop-filter: blur(8px);
            border-radius: 16px;
            border: 1px solid rgba(0,102,255,0.4);
            padding: 12px 16px;
            color: #e2e8f0;
            font-size: 12px;
            pointer-events: auto;
            z-index: 10;
            box-shadow: 0 8px 20px rgba(0,0,0,0.5);
        `;
        const header = document.createElement('div');
        header.style.cssText = 'display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;';
        header.innerHTML = `
            <span style="font-weight:600;color:#4da6ff;">🧵 线程实时详情</span>
            <button id="testCallChainBtn" style="background:#0066ff;color:#fff;border:none;border-radius:40px;padding:4px 16px;cursor:pointer;font-size:12px;">🚀 测试调用链</button>
        `;
        div.appendChild(header);
        container.appendChild(div);
        threadPanel = div;

        document.getElementById('testCallChainBtn').addEventListener('click', async () => {
            try {
                const resp = await fetch(apiUrl('/3d/api/test-call-chain'));
                const data = await resp.json();
                statusSpan.textContent = data.message || '测试已触发';
            } catch (err) {
                statusSpan.textContent = '测试失败: ' + err.message;
            }
        });
    }

    // 按状态排序：BLOCKED > RUNNING > COMPLETED > 其他
    const sorted = [...threads].sort((a, b) => {
        const order = { 'BLOCKED': 0, 'RUNNING': 1, 'COMPLETED': 2 };
        const sa = order[a.status] ?? 3;
        const sb = order[b.status] ?? 3;
        return sa - sb;
    });
    // 只显示前10条
    const display = sorted.slice(0, 10);

    let html = '';
    display.forEach(t => {
        const statusColor = t.status === 'RUNNING' ? '#22c55e' :
            (t.status === 'COMPLETED' ? '#3b82f6' : '#94a3b8');
        const chain = t.callChain || '—';
        const nodes = t.nodes || [];
        let chainHtml = '';
        if (nodes.length > 0) {
            chainHtml = `<div style="display:flex;align-items:center;gap:4px;flex-wrap:wrap;margin-top:4px;">
                ${nodes.map(n => `<span style="background:rgba(0,102,255,0.2);padding:0 6px;border-radius:4px;">${n}</span>`).join(' → ')}
                <span class="flow-particle" data-thread="${t.threadId}" style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#ff6b6b;animation:flowPulse 0.8s infinite;"></span>
             </div>`;
        } else if (chain !== '—') {
            chainHtml = `<div style="margin-top:4px;font-size:11px;color:#94a3b8;">${chain}</div>`;
        } else {
            chainHtml = `<div style="margin-top:4px;font-size:11px;color:#64748b;">（空闲）</div>`;
        }
        html += `
            <div style="padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.08);">
                <div style="display:flex;justify-content:space-between;">
                    <span><strong>${t.threadName}</strong> (ID:${t.threadId})</span>
                    <span style="color:${statusColor};font-weight:500;">${t.status}</span>
                </div>
                ${chainHtml}
            </div>
        `;
    });
    if (threads.length > 10) {
        html += `<div style="text-align:center;color:#94a3b8;padding:4px;">... 还有 ${threads.length - 10} 条</div>`;
    }

    const contentDiv = document.createElement('div');
    contentDiv.innerHTML = html;
    while (threadPanel.children.length > 1) {
        threadPanel.removeChild(threadPanel.lastChild);
    }
    threadPanel.appendChild(contentDiv);
}

// ===================== 线程 3D 流图（运行中/阻塞）=====================
let flowParticlesList = [];
let flowCurves = [];

function renderThreadFlows(threads) {
    // 清除旧的粒子流
    clearGroup(threadFlowGroup);
    flowParticlesList = [];
    flowCurves = [];

    const targetThreads = threads.filter(t => t.status === 'RUNNING' || t.status === 'BLOCKED');
    if (targetThreads.length === 0) return;

    // 为每个线程生成一条路径
    targetThreads.forEach((t, idx) => {
        const nodes = t.nodes || [];
        if (nodes.length < 2) return;

        // 构建三维坐标点（随机分布，避免重叠）
        const baseX = (idx - targetThreads.length/2) * 4;
        const baseY = 2 + Math.sin(idx) * 1.5;
        const baseZ = 0;

        const points = nodes.map((n, i) => {
            const x = baseX + i * 2.5;
            const y = baseY + Math.sin(i * 0.8) * 1.2;
            const z = baseZ + Math.cos(i * 0.5) * 1.0;
            return new THREE.Vector3(x, y, z);
        });

        // 创建平滑曲线
        const curve = new THREE.CatmullRomCurve3(points);
        flowCurves.push(curve);

        // 创建流动粒子
        const particleCount = 30;
        const positions = new Float32Array(particleCount * 3);
        const colors = new Float32Array(particleCount * 3);
        const sizes = new Float32Array(particleCount);
        const progresses = new Float32Array(particleCount);

        const color = new THREE.Color().setHSL(idx / targetThreads.length, 0.8, 0.6);
        for (let i = 0; i < particleCount; i++) {
            progresses[i] = i / particleCount;
            sizes[i] = 0.2 + Math.random() * 0.3;
            colors[i*3] = color.r;
            colors[i*3+1] = color.g;
            colors[i*3+2] = color.b;
        }

        const geo = new THREE.BufferGeometry();
        geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
        geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
        geo.setAttribute('size', new THREE.BufferAttribute(sizes, 1));

        const mat = new THREE.PointsMaterial({
            size: 0.3,
            vertexColors: true,
            transparent: true,
            opacity: 0.8,
            blending: THREE.AdditiveBlending,
            depthWrite: false,
            sizeAttenuation: true
        });
        const particleSystem = new THREE.Points(geo, mat);
        particleSystem.userData = {
            curve: curve,
            progresses: progresses,
            speed: 0.005 + Math.random() * 0.005,
            offset: 0
        };
        threadFlowGroup.add(particleSystem);
        flowParticlesList.push(particleSystem);
    });
}

// 更新粒子位置（每帧调用）
function updateFlowParticles() {
    flowParticlesList.forEach(ps => {
        const data = ps.userData;
        if (!data) return;
        data.offset += data.speed;
        if (data.offset > 1) data.offset -= 1;

        const positions = ps.geometry.attributes.position.array;
        const count = data.progresses.length;
        for (let i = 0; i < count; i++) {
            let t = (data.progresses[i] + data.offset) % 1;
            const point = data.curve.getPoint(t);
            positions[i*3] = point.x;
            positions[i*3+1] = point.y;
            positions[i*3+2] = point.z;
        }
        ps.geometry.attributes.position.needsUpdate = true;
    });
}

// ----- 更新所有场景 -----
function updateScene(data) {
    if (!data) return;
    currentData = data;
    renderThreads(data.threads);
    renderMemory(data.memory);
    renderPool(data.pool);
    renderRequestStats(data.requests);
    renderMemoryRegions(data.memory.pools);
    if (data.threadDetails) {
        renderThreadDetails(data.threadDetails);
        renderThreadFlows(data.threadDetails);
    }
}

// ----- 从后端获取数据 -----
async function fetchMonitorData() {
    try {
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

// ----- 模拟数据（仅演示）-----
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
        },
        threadDetails: [
            { threadId: 1, threadName: 'main', state: 'RUNNABLE', status: 'RUNNING', callChain: 'TestController.execute → TestService.step1 → TestService.step1_1', nodes: ['TestController.execute', 'TestService.step1', 'TestService.step1_1'] },
            { threadId: 2, threadName: 'http-nio-8080-exec-1', state: 'WAITING', status: 'IDLE', callChain: '' },
            { threadId: 3, threadName: 'pool-1-thread-1', state: 'RUNNABLE', status: 'COMPLETED', callChain: 'TestMapper.query → JdbcTemplate.execute', nodes: [] },
            { threadId: 4, threadName: 'blocked-thread', state: 'BLOCKED', status: 'BLOCKED', callChain: 'synchronized-block', nodes: ['Lock.wait', 'synchronized'] },
        ]
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
    updateFlowParticles();
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