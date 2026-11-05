// ----- 导入 Three.js 核心库和扩展 -----
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { CSS2DRenderer, CSS2DObject } from 'three/addons/renderers/CSS2DRenderer.js';

// ----- 工具函数：获取 context path -----
function getContextPath() {
    const path = window.location.pathname;
    const parts = path.split('/');
    if (parts.length > 1 && parts[1]) {
        return '/' + parts[1];
    }
    return '';
}
const CONTEXT_PATH = getContextPath();
function apiUrl(path) {
    if (path.startsWith('/')) {
        return CONTEXT_PATH + path;
    }
    return CONTEXT_PATH + '/' + path;
}

// ----- DOM 引用 -----
const container = document.getElementById('canvas-container');
const loadingOverlay = document.getElementById('loading-overlay');
const loadingText = document.getElementById('loading-text');
const errorMsg = document.getElementById('error-msg');
const statusSpan = document.getElementById('status');
const refreshBtn = document.getElementById('refreshBtn');
const switchSystemBtn = document.getElementById('switchSystemBtn');
const switchProjectBtn = document.getElementById('switchProjectBtn');

// ----- 全局变量 -----
let scene, camera, renderer, labelRenderer, controls;
let nodesGroup, edgesGroup;
let animationId = null;
let currentMode = 'system'; // 'system' | 'project'

// ----- 初始化场景 -----
function initScene() {
    const width = container.clientWidth;
    const height = container.clientHeight;

    scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0a0f1a);

    camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);
    camera.position.set(15, 10, 20);
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
    controls.autoRotate = false;
    controls.target.set(0, 0, 0);
    controls.update();

    // 灯光
    const ambient = new THREE.AmbientLight(0x404060);
    scene.add(ambient);
    const dirLight = new THREE.DirectionalLight(0xffffff, 1.2);
    dirLight.position.set(5, 10, 7);
    scene.add(dirLight);
    const backLight = new THREE.DirectionalLight(0x4da6ff, 0.5);
    backLight.position.set(-5, 0, -5);
    scene.add(backLight);

    // 网格辅助
    const gridHelper = new THREE.GridHelper(40, 20, 0x4da6ff, 0x2a4a7a);
    gridHelper.position.y = -5.5;
    scene.add(gridHelper);

    nodesGroup = new THREE.Group();
    edgesGroup = new THREE.Group();
    scene.add(nodesGroup);
    scene.add(edgesGroup);

    window.addEventListener('resize', onResize);
}

function onResize() {
    const width = container.clientWidth;
    const height = container.clientHeight;
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    renderer.setSize(width, height);
    labelRenderer.setSize(width, height);
}

// ----- 清除旧图形 -----
function clearGraph() {
    if (nodesGroup) {
        while (nodesGroup.children.length) {
            const child = nodesGroup.children[0];
            if (child.geometry) child.geometry.dispose();
            if (child.material) child.material.dispose();
            nodesGroup.remove(child);
        }
    }
    if (edgesGroup) {
        while (edgesGroup.children.length) {
            const child = edgesGroup.children[0];
            if (child.geometry) child.geometry.dispose();
            if (child.material) child.material.dispose();
            edgesGroup.remove(child);
        }
    }
    const toRemove = [];
    scene.children.forEach(child => {
        if (child.isCSS2DObject) {
            toRemove.push(child);
        }
    });
    toRemove.forEach(child => scene.remove(child));
}

// ==================== 核心布局优化 ====================
/**
 * 计算每个节点的位置，采用分层分组布局：
 * - Controller 放在最上层 (y=5)
 * - Service 放在中间层 (y=0)
 * - Mapper 放在下层 (y=-5)
 * - 其他类 (class) 放在 Service 同一层 (y=0) 但水平偏移以避免重叠
 *
 * 每组内的节点水平均匀分布，并且每组占据独立的水平区间，组间留有空隙。
 */
function computeNodePositions(nodes) {
    // 按类型分组
    const groups = { controller: [], service: [], mapper: [], class: [] };
    nodes.forEach(n => {
        const type = n.type || 'class';
        if (!groups[type]) groups[type] = [];
        groups[type].push(n);
    });

    // 各组的 y 坐标
    const layerY = {
        controller: 5,
        service: 0,
        mapper: -5,
        class: 0
    };

    // 每组水平间距和组间额外间距
    const spacingX = 3.5;          // 同组内节点间距
    const groupGap = 4;            // 组间额外间距
    const totalGroups = Object.keys(groups).filter(k => groups[k].length > 0).length;

    // 先计算每组的总宽度，分配水平区间
    const groupRanges = {};
    let currentX = 0;
    // 为了居中，先计算总宽度
    let totalWidth = 0;
    const groupWidths = {};
    for (const [type, list] of Object.entries(groups)) {
        if (list.length === 0) continue;
        const width = (list.length - 1) * spacingX;
        groupWidths[type] = width;
        totalWidth += width + groupGap;
    }
    totalWidth -= groupGap; // 减去最后一个多余的间隙
    const startX = -totalWidth / 2;

    let offsetX = startX;
    for (const [type, list] of Object.entries(groups)) {
        if (list.length === 0) continue;
        const count = list.length;
        const width = (count - 1) * spacingX;
        const startPos = offsetX;
        // 记录该组的起始位置和间距
        groupRanges[type] = { start: startPos, spacing: spacingX, count: count };
        offsetX += width + groupGap;
    }

    // 为每个节点分配坐标
    const posMap = new Map();
    for (const [type, list] of Object.entries(groups)) {
        const range = groupRanges[type];
        if (!range) continue;
        const { start, spacing, count } = range;
        const y = layerY[type] || 0;
        list.forEach((node, idx) => {
            const x = start + idx * spacing;
            // 对 class 类型，如果与 service 同层，可以在 z 轴稍微偏移以区分，这里保持 z=0
            const z = 0;
            posMap.set(node.id, { x, y, z, node });
        });
    }

    return posMap;
}

// ----- 渲染调用链数据（使用优化布局） -----
function renderCallChain(data) {
    clearGraph();

    const nodes = data.nodes || [];
    const links = data.links || [];

    if (nodes.length === 0) {
        loadingText.textContent = '⚠️ 未识别到任何节点';
        return;
    }

    // 颜色映射
    const typeColor = {
        controller: 0x4da6ff,
        service: 0x22c55e,
        mapper: 0xf59e0b,
        class: 0x94a3b8
    };

    // 计算优化后的位置
    const posMap = computeNodePositions(nodes);

    // 创建节点球体
    const sphereGeo = new THREE.SphereGeometry(0.8, 32, 32);
    nodes.forEach(n => {
        const pos = posMap.get(n.id);
        if (!pos) return;
        const color = typeColor[n.type] || 0x94a3b8;
        const material = new THREE.MeshPhongMaterial({
            color: color,
            emissive: new THREE.Color(color).multiplyScalar(0.2),
            shininess: 40
        });
        const sphere = new THREE.Mesh(sphereGeo, material);
        const size = Math.max(0.6, Math.min(1.8, (n.size || 1) * 0.25 + 0.5));
        sphere.scale.set(size, size, size);
        sphere.position.set(pos.x, pos.y, pos.z);
        sphere.userData = { nodeId: n.id, label: n.label || n.id };
        nodesGroup.add(sphere);

        // 标签
        const labelDiv = document.createElement('div');
        labelDiv.textContent = n.label || n.id;
        labelDiv.style.color = '#e2e8f0';
        labelDiv.style.fontSize = '12px';
        labelDiv.style.fontWeight = '500';
        labelDiv.style.textShadow = '0 0 8px rgba(0,0,0,0.9)';
        labelDiv.style.background = 'rgba(0,0,0,0.6)';
        labelDiv.style.padding = '2px 10px';
        labelDiv.style.borderRadius = '12px';
        labelDiv.style.border = '1px solid rgba(255,255,255,0.15)';
        labelDiv.style.whiteSpace = 'nowrap';
        labelDiv.style.pointerEvents = 'none';
        const label = new CSS2DObject(labelDiv);
        label.position.set(pos.x, pos.y - 1.4, pos.z);
        scene.add(label);
    });

    // 创建连线
    const arrowColor = 0xff6b6b;
    links.forEach(link => {
        const sourcePos = posMap.get(link.source);
        const targetPos = posMap.get(link.target);
        if (!sourcePos || !targetPos) return;

        const start = new THREE.Vector3(sourcePos.x, sourcePos.y, sourcePos.z);
        const end = new THREE.Vector3(targetPos.x, targetPos.y, targetPos.z);

        // 主线条
        const points = [start, end];
        const lineGeo = new THREE.BufferGeometry().setFromPoints(points);
        const lineMat = new THREE.LineBasicMaterial({ color: arrowColor, transparent: true, opacity: 0.5 });
        const line = new THREE.Line(lineGeo, lineMat);
        edgesGroup.add(line);

        // 箭头
        const direction = new THREE.Vector3().copy(end).sub(start).normalize();
        const arrowLength = 0.8;
        const arrowPos = new THREE.Vector3().copy(end).add(direction.clone().multiplyScalar(-arrowLength - 0.3));
        const arrowHelper = new THREE.ArrowHelper(direction, arrowPos, arrowLength, arrowColor, 0.5, 0.3);
        edgesGroup.add(arrowHelper);
    });

    loadingOverlay.classList.add('hidden');
    statusSpan.textContent = `✅ 已加载 ${nodes.length} 个节点，${links.length} 条调用关系`;
}

// ----- 加载数据 -----
async function loadCallChain() {
    loadingOverlay.classList.remove('hidden');
    errorMsg.style.display = 'none';
    loadingText.textContent = currentMode === 'system' ? '正在加载系统全链路...' : '正在分析项目调用链...';
    statusSpan.textContent = '⏳ 加载中...';

    try {
        let url;
        if (currentMode === 'system') {
            url = apiUrl('/3d/api/system-call-chain');
        } else {
            const params = new URLSearchParams();
            params.append('rootDir', '../../../');
            params.append('extensions', 'java');
            params.append('excludeComments', 'true');
            params.append('threadCount', '4');
            url = apiUrl('/3d/api/call-chain?' + params.toString());
        }
        const resp = await fetch(url);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const data = await resp.json();
        if (data.error) throw new Error(data.error);

        if (!scene) initScene();
        renderCallChain(data);
    } catch (err) {
        console.error('加载调用链失败:', err);
        errorMsg.textContent = '❌ 加载失败: ' + err.message;
        errorMsg.style.display = 'block';
        loadingText.textContent = '加载失败，请重试';
        statusSpan.textContent = '❌ 加载失败';
        if (!scene) initScene();
        loadingOverlay.classList.add('hidden');
    }
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
    loadCallChain();
}

// 切换按钮事件
switchSystemBtn.addEventListener('click', () => {
    if (currentMode === 'system') return;
    currentMode = 'system';
    switchSystemBtn.classList.add('active');
    switchProjectBtn.classList.remove('active');
    loadCallChain();
});

switchProjectBtn.addEventListener('click', () => {
    if (currentMode === 'project') return;
    currentMode = 'project';
    switchProjectBtn.classList.add('active');
    switchSystemBtn.classList.remove('active');
    loadCallChain();
});

refreshBtn.addEventListener('click', loadCallChain);

if (document.readyState === 'complete') {
    start();
} else {
    window.addEventListener('load', start);
}