// ==================== 工具函数 ====================
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

// ==================== 全局状态 ====================
let autoRefresh = true;
let intervalId = null;
let historyData = { gc: [], heap: [], classLoad: [] };
const MAX_HISTORY = 60;

// ==================== ECharts 初始化 ====================
const gcChart = echarts.init(document.getElementById('gcChart'));
const heapChart = echarts.init(document.getElementById('heapChart'));
const classChart = echarts.init(document.getElementById('classChart'));
const regionChart = echarts.init(document.getElementById('regionChart'));

function createLineOption(title, unit = '') {
    return {
        grid: { top: 28, bottom: 20, left: 40, right: 10 },
        xAxis: { type: 'category', data: [], axisLabel: { fontSize: 10, color: '#94a3b8' } },
        yAxis: { type: 'value', name: unit, nameTextStyle: { color: '#94a3b8' }, splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } } },
        series: [{ type: 'line', smooth: true, symbol: 'none', lineStyle: { width: 2 } }],
        tooltip: { trigger: 'axis' },
        backgroundColor: 'transparent'
    };
}

const gcOption = createLineOption('GC', 'ms');
gcOption.series[0].data = [];
gcOption.series[0].name = 'GC 总耗时';
gcChart.setOption(gcOption);

const heapOption = createLineOption('Heap', 'MB');
heapOption.series[0].data = [];
heapOption.series[0].name = '堆使用量';
heapChart.setOption(heapOption);

const classOption = createLineOption('Class', '个');
classOption.series[0].data = [];
classOption.series[0].name = '已加载类数';
classChart.setOption(classOption);

const regionOption = {
    grid: { top: 28, bottom: 20, left: 50, right: 10 },
    xAxis: { type: 'category', data: [], axisLabel: { fontSize: 10, color: '#94a3b8' } },
    yAxis: { type: 'value', name: 'MB', nameTextStyle: { color: '#94a3b8' }, splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } } },
    series: [
        { name: '已用', type: 'bar', stack: 'total', color: '#4da6ff' },
        { name: '剩余', type: 'bar', stack: 'total', color: '#1e293b' }
    ],
    tooltip: { trigger: 'axis' },
    backgroundColor: 'transparent'
};
regionChart.setOption(regionOption);

// ==================== Three.js 3D 场景 ====================
let scene, camera, renderer, labelRenderer, controls;
let barGroup;
const regions = ['Eden', 'S0', 'S1', 'Old', 'Metaspace'];

function initThree() {
    const container = document.getElementById('three-container');
    const width = container.clientWidth;
    const height = container.clientHeight;

    scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0a0f1a);

    camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);
    camera.position.set(8, 6, 12);
    camera.lookAt(0, 0, 0);

    renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(window.devicePixelRatio);
    container.appendChild(renderer.domElement);

    labelRenderer = new THREE.CSS2DRenderer();
    labelRenderer.setSize(width, height);
    labelRenderer.domElement.style.position = 'absolute';
    labelRenderer.domElement.style.top = '0';
    labelRenderer.domElement.style.left = '0';
    labelRenderer.domElement.style.pointerEvents = 'none';
    container.appendChild(labelRenderer.domElement);

    controls = new THREE.OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
    controls.autoRotate = true;
    controls.autoRotateSpeed = 1.0;
    controls.target.set(0, 1, 0);
    controls.update();

    const ambient = new THREE.AmbientLight(0x404060);
    scene.add(ambient);
    const dirLight = new THREE.DirectionalLight(0xffffff, 1.2);
    dirLight.position.set(5, 10, 7);
    scene.add(dirLight);
    const backLight = new THREE.DirectionalLight(0x4da6ff, 0.5);
    backLight.position.set(-5, 0, -5);
    scene.add(backLight);

    const gridHelper = new THREE.GridHelper(20, 10, 0x4da6ff, 0x2a4a7a);
    gridHelper.position.y = -0.5;
    scene.add(gridHelper);

    barGroup = new THREE.Group();
    scene.add(barGroup);

    createBars([]);

    window.addEventListener('resize', onResize);
}

function onResize() {
    const container = document.getElementById('three-container');
    const width = container.clientWidth;
    const height = container.clientHeight;
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    renderer.setSize(width, height);
    labelRenderer.setSize(width, height);
}

function createBars(data) {
    while (barGroup.children.length) {
        const child = barGroup.children[0];
        if (child.geometry) child.geometry.dispose();
        if (child.material) child.material.dispose();
        barGroup.remove(child);
    }

    const hasData = data && data.length === regions.length;
    const defaultData = hasData ? data : regions.map(() => ({ used: 0, max: 100 }));

    const spacing = 2.2;
    const startX = -(regions.length - 1) * spacing / 2;

    regions.forEach((name, idx) => {
        const info = hasData ? data[idx] : { used: 0, max: 100 };
        const used = Math.min(info.used, info.max);
        const total = Math.max(info.max, 1);
        const usedRatio = used / total;

        const totalHeight = total / 100;
        const usedHeight = usedRatio * totalHeight;

        const geo = new THREE.BoxGeometry(1.2, totalHeight, 1.2);
        const mat = new THREE.MeshPhongMaterial({
            color: 0x1e293b,
            transparent: true,
            opacity: 0.3,
            wireframe: false
        });
        const box = new THREE.Mesh(geo, mat);
        box.position.set(startX + idx * spacing, totalHeight/2 - 0.5, 0);
        barGroup.add(box);

        const usedGeo = new THREE.BoxGeometry(1.0, usedHeight, 1.0);
        const color = usedRatio < 0.6 ? 0x22c55e : (usedRatio < 0.85 ? 0xf59e0b : 0xef4444);
        const usedMat = new THREE.MeshPhongMaterial({
            color: color,
            emissive: new THREE.Color(color).multiplyScalar(0.2)
        });
        const usedBox = new THREE.Mesh(usedGeo, usedMat);
        usedBox.position.set(startX + idx * spacing, usedHeight/2 - 0.5, 0);
        barGroup.add(usedBox);

        const labelDiv = document.createElement('div');
        labelDiv.textContent = `${name}\n${(used/1024/1024).toFixed(1)}MB / ${(total/1024/1024).toFixed(1)}MB`;
        labelDiv.style.color = '#e2e8f0';
        labelDiv.style.fontSize = '11px';
        labelDiv.style.textAlign = 'center';
        labelDiv.style.background = 'rgba(0,0,0,0.6)';
        labelDiv.style.padding = '2px 8px';
        labelDiv.style.borderRadius = '12px';
        labelDiv.style.border = '1px solid rgba(255,255,255,0.2)';
        labelDiv.style.whiteSpace = 'pre-line';
        const label = new THREE.CSS2DObject(labelDiv);
        label.position.set(startX + idx * spacing, totalHeight + 0.3, 0);
        barGroup.add(label);
    });
}

function updateThree(data) {
    if (!barGroup) return;
    createBars(data);
}

// ==================== 数据获取与更新 ====================
async function fetchData() {
    try {
        const resp = await fetch(apiUrl('/3d/api/jstats/monitor'));
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const data = await resp.json();
        updateUI(data);
        document.getElementById('status').textContent = '🟢 实时';
        document.getElementById('status').style.color = '#22c55e';
    } catch (err) {
        console.error('获取数据失败', err);
        document.getElementById('status').textContent = '❌ 连接失败';
        document.getElementById('status').style.color = '#ef4444';
    }
}

function updateUI(data) {
    // 线程列表
    const threads = data.threadInfos || [];
    const tbody = document.getElementById('threadTableBody');
    tbody.innerHTML = threads.slice(0, 20).map(t => `
        <tr>
            <td>${t.threadId}</td>
            <td>${escapeHtml(t.threadName)}</td>
            <td><span class="status-badge status-${t.state.toLowerCase()}">${t.state}</span></td>
        </tr>
    `).join('');

    // 类加载
    const cls = data.classLoading || {};
    document.getElementById('classLoadingDisplay').innerHTML = `
        <div><span class="label">已加载</span><span class="value">${cls.loadedCount || 0}</span></div>
        <div><span class="label">卸载</span><span class="value">${cls.unloadedCount || 0}</span></div>
        <div><span class="label">总加载</span><span class="value">${cls.totalLoadedCount || 0}</span></div>
        <div></div>
    `;

    // CPU / 内存
    const cpu = data.cpuMemory || {};
    document.getElementById('cpuMemDisplay').innerHTML = `
        <span>💻 CPU: ${cpu.cpuUsagePercent || 0}%</span>
        <span>📀 RSS: ${((cpu.memoryRssKb || 0) / 1024).toFixed(1)} MB</span>
    `;

    // JVM 参数
    const opts = data.jvmOptions || [];
    const optHtml = opts.filter(o => ['MaxHeapSize', 'MinHeapSize', 'UseG1GC'].includes(o.name))
        .map(o => `<div><strong>${o.name}</strong>: ${o.value}</div>`).join('');
    document.getElementById('jvmOptionsDisplay').innerHTML = optHtml || '无关键参数';

    // 图表更新
    const gc = data.gc || {};
    const now = Date.now();
    const timeLabel = new Date(now).toLocaleTimeString();

    const gct = gc.gct || 0;
    historyData.gc.push({ time: timeLabel, value: gct });
    if (historyData.gc.length > MAX_HISTORY) historyData.gc.shift();
    gcChart.setOption({
        xAxis: { data: historyData.gc.map(d => d.time) },
        series: [{ data: historyData.gc.map(d => d.value) }]
    });

    let heapUsed = 0;
    if (data.gc) {
        const gcData = data.gc;
        heapUsed = (gcData.ou || 0) + (gcData.eu || 0) + (gcData.s0u || 0) + (gcData.s1u || 0);
        heapUsed = heapUsed / (1024 * 1024);
    }
    historyData.heap.push({ time: timeLabel, value: heapUsed });
    if (historyData.heap.length > MAX_HISTORY) historyData.heap.shift();
    heapChart.setOption({
        xAxis: { data: historyData.heap.map(d => d.time) },
        series: [{ data: historyData.heap.map(d => d.value) }]
    });

    const loaded = cls.loadedCount || 0;
    historyData.classLoad.push({ time: timeLabel, value: loaded });
    if (historyData.classLoad.length > MAX_HISTORY) historyData.classLoad.shift();
    classChart.setOption({
        xAxis: { data: historyData.classLoad.map(d => d.time) },
        series: [{ data: historyData.classLoad.map(d => d.value) }]
    });

    const gcData = data.gc || {};
    const regionUsed = [
        gcData.eu || 0,
        gcData.s0u || 0,
        gcData.s1u || 0,
        gcData.ou || 0,
        gcData.mu || 0
    ];
    const regionMax = [
        gcData.ec || 1,
        gcData.s0c || 1,
        gcData.s1c || 1,
        gcData.oc || 1,
        gcData.mc || 1
    ];
    const regionData = regions.map((name, idx) => ({
        used: regionUsed[idx] / (1024 * 1024),
        max: regionMax[idx] / (1024 * 1024)
    }));
    regionChart.setOption({
        xAxis: { data: regions },
        series: [
            { data: regionData.map(d => d.used) },
            { data: regionData.map(d => Math.max(0, d.max - d.used)) }
        ]
    });

    updateThree(regionData);
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
}

// ==================== 定时刷新控制 ====================
function startAutoRefresh() {
    if (intervalId) clearInterval(intervalId);
    intervalId = setInterval(fetchData, 3000);
    document.getElementById('autoBtn').textContent = '⏸️ 暂停';
    autoRefresh = true;
}
function stopAutoRefresh() {
    if (intervalId) clearInterval(intervalId);
    intervalId = null;
    document.getElementById('autoBtn').textContent = '▶️ 继续';
    autoRefresh = false;
}

document.getElementById('refreshBtn').addEventListener('click', fetchData);
document.getElementById('autoBtn').addEventListener('click', () => {
    if (autoRefresh) stopAutoRefresh();
    else startAutoRefresh();
});

// ==================== 初始化 ====================
function init() {
    initThree();
    startAutoRefresh();
    fetchData();
    window.addEventListener('resize', onResize);
}

if (document.readyState === 'complete') {
    init();
} else {
    window.addEventListener('load', init);
}