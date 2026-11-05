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
const sendBtn = document.getElementById('sendBtn');

// ----- 全局变量 -----
let scene, camera, renderer, labelRenderer, controls;
let nodesGroup, edgesGroup, particlesGroup;
let animationId = null;
let traceData = null;
let particleSystem = null; // 粒子系统对象
let isAnimating = false;

// ----- 初始化场景 -----
function initScene() {
    const width = container.clientWidth;
    const height = container.clientHeight;

    scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0a0f1a);

    // 相机
    camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);
    camera.position.set(10, 6, 15);
    camera.lookAt(0, 0, 0);

    // 渲染器
    renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    container.appendChild(renderer.domElement);

    // CSS2D 渲染器（标签）
    labelRenderer = new CSS2DRenderer();
    labelRenderer.setSize(width, height);
    labelRenderer.domElement.style.position = 'absolute';
    labelRenderer.domElement.style.top = '0';
    labelRenderer.domElement.style.left = '0';
    labelRenderer.domElement.style.pointerEvents = 'none';
    container.appendChild(labelRenderer.domElement);

    // 控制器
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
    const gridHelper = new THREE.GridHelper(30, 20, 0x4da6ff, 0x2a4a7a);
    gridHelper.position.y = -5;
    scene.add(gridHelper);

    // 分组容器
    nodesGroup = new THREE.Group();
    edgesGroup = new THREE.Group();
    particlesGroup = new THREE.Group();
    scene.add(nodesGroup);
    scene.add(edgesGroup);
    scene.add(particlesGroup);

    // 窗口自适应
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
    // 清除节点
    while (nodesGroup.children.length) {
        const child = nodesGroup.children[0];
        if (child.geometry) child.geometry.dispose();
        if (child.material) child.material.dispose();
        nodesGroup.remove(child);
    }
    // 清除边
    while (edgesGroup.children.length) {
        const child = edgesGroup.children[0];
        if (child.geometry) child.geometry.dispose();
        if (child.material) child.material.dispose();
        edgesGroup.remove(child);
    }
    // 清除粒子
    if (particleSystem) {
        particlesGroup.remove(particleSystem);
        if (particleSystem.geometry) particleSystem.geometry.dispose();
        if (particleSystem.material) particleSystem.material.dispose();
        particleSystem = null;
    }
    // 清除旧标签
    const toRemove = [];
    scene.children.forEach(child => {
        if (child.isCSS2DObject) {
            toRemove.push(child);
        }
    });
    toRemove.forEach(child => scene.remove(child));
}

// ----- 根据数据渲染场景 -----
function renderTrace(data) {
    clearGraph();
    traceData = data;

    const nodes = data.nodes || [];
    const links = data.links || [];
    const tracePath = data.tracePath || [];

    if (nodes.length === 0) {
        loadingText.textContent = '⚠️ 无节点数据';
        return;
    }

    // 1. 创建节点（球体 + 标签）
    const sphereGeo = new THREE.SphereGeometry(0.6, 32, 32);
    nodes.forEach(node => {
        const color = new THREE.Color(node.color || '#4da6ff');
        const material = new THREE.MeshPhongMaterial({
            color: color,
            emissive: color.clone().multiplyScalar(0.2),
            shininess: 40
        });
        const sphere = new THREE.Mesh(sphereGeo, material);
        sphere.position.set(node.x, node.y, node.z);
        sphere.userData = { id: node.id };
        nodesGroup.add(sphere);

        // 标签
        const labelDiv = document.createElement('div');
        labelDiv.textContent = node.label || node.id;
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
        label.position.set(node.x, node.y - 1.2, node.z);
        scene.add(label);
    });

    // 2. 创建边（线条）
    const lineMat = new THREE.LineBasicMaterial({ color: 0x4da6ff, transparent: true, opacity: 0.3 });
    links.forEach(link => {
        const source = nodes.find(n => n.id === link.source);
        const target = nodes.find(n => n.id === link.target);
        if (!source || !target) return;
        const points = [
            new THREE.Vector3(source.x, source.y, source.z),
            new THREE.Vector3(target.x, target.y, target.z)
        ];
        const lineGeo = new THREE.BufferGeometry().setFromPoints(points);
        const line = new THREE.Line(lineGeo, lineMat);
        edgesGroup.add(line);
    });

    // 3. 创建粒子路径（基于 tracePath 顺序，构建 CatmullRomCurve3）
    const pathPoints = tracePath.map(id => {
        const node = nodes.find(n => n.id === id);
        if (!node) return null;
        return new THREE.Vector3(node.x, node.y, node.z);
    }).filter(p => p !== null);

    if (pathPoints.length < 2) {
        console.warn('路径点不足，无法生成粒子曲线');
        return;
    }

    // 生成平滑曲线（通过 CatmullRomCurve3）
    const curve = new THREE.CatmullRomCurve3(pathPoints);

    // 创建粒子系统（Points）
    const particleCount = 50; // 粒子数量
    const positions = new Float32Array(particleCount * 3);
    const colors = new Float32Array(particleCount * 3);
    const sizes = new Float32Array(particleCount);

    // 每个粒子的进度（0~1）均匀分布
    const progresses = new Float32Array(particleCount);
    for (let i = 0; i < particleCount; i++) {
        progresses[i] = i / particleCount;
    }

    const particleGeo = new THREE.BufferGeometry();
    particleGeo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    particleGeo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    particleGeo.setAttribute('size', new THREE.BufferAttribute(sizes, 1));

    // 定义粒子材质（使用 PointsMaterial，颜色从红到橙渐变）
    const particleMat = new THREE.PointsMaterial({
        size: 0.3,
        vertexColors: true,
        transparent: true,
        opacity: 0.8,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
        sizeAttenuation: true
    });

    particleSystem = new THREE.Points(particleGeo, particleMat);
    particleSystem.userData = {
        curve: curve,
        progresses: progresses,
        speed: 0.005, // 每帧进度增量
        isRunning: false,
        durationMs: data.durationMs || 300
    };
    // 设置初始位置
    updateParticles(0);
    particlesGroup.add(particleSystem);

    // 隐藏加载遮罩
    loadingOverlay.classList.add('hidden');
    statusSpan.textContent = '已加载，点击按钮触发';
}

// ----- 更新粒子位置（根据进度）-----
function updateParticles(offset) {
    if (!particleSystem) return;
    const data = particleSystem.userData;
    const curve = data.curve;
    const progresses = data.progresses;
    const positions = particleSystem.geometry.attributes.position.array;
    const colors = particleSystem.geometry.attributes.color.array;
    const sizes = particleSystem.geometry.attributes.size.array;

    const count = progresses.length;
    for (let i = 0; i < count; i++) {
        let t = (progresses[i] + offset) % 1;
        const point = curve.getPoint(t);
        positions[i * 3] = point.x;
        positions[i * 3 + 1] = point.y;
        positions[i * 3 + 2] = point.z;

        // 颜色：根据位置渐变 (红->橙->黄)
        const color = new THREE.Color().setHSL(0.05 + t * 0.1, 1, 0.5);
        colors[i * 3] = color.r;
        colors[i * 3 + 1] = color.g;
        colors[i * 3 + 2] = color.b;

        // 大小：中间大两头小
        const sizeFactor = Math.sin(t * Math.PI);
        sizes[i] = 0.2 + sizeFactor * 0.4;
    }
    particleSystem.geometry.attributes.position.needsUpdate = true;
    particleSystem.geometry.attributes.color.needsUpdate = true;
    particleSystem.geometry.attributes.size.needsUpdate = true;
}

// ----- 动画循环 -----
let lastTime = 0;
let particleOffset = 0;
let isParticleRunning = false;
let animationStartTime = 0;

function animate(time) {
    requestAnimationFrame(animate);

    // 更新粒子动画（如果正在运行）
    if (isParticleRunning && particleSystem) {
        const delta = (time - lastTime) / 1000;
        const speed = particleSystem.userData.speed;
        particleOffset += speed * delta * 30; // 调整速度
        if (particleOffset > 1) {
            particleOffset = 0;
            // 一次循环结束
            isParticleRunning = false;
            statusSpan.textContent = '✅ 请求完成';
            // 可触发重置
        }
        updateParticles(particleOffset);
    }
    lastTime = time;

    controls.update();
    renderer.render(scene, camera);
    labelRenderer.render(scene, camera);
}

// ----- 启动粒子动画（由按钮触发）-----
function startParticleAnimation() {
    if (!particleSystem) {
        statusSpan.textContent = '❌ 未加载数据';
        return;
    }
    if (isParticleRunning) {
        statusSpan.textContent = '⏳ 动画进行中...';
        return;
    }
    // 重置粒子进度
    particleOffset = 0;
    isParticleRunning = true;
    // 随机速度（根据后端返回耗时模拟）
    const durationMs = particleSystem.userData.durationMs || 300;
    // 将耗时映射到速度：耗时越长，速度越慢
    const baseSpeed = 0.008;
    const speedFactor = 200 / durationMs; // 范围约0.5~2
    particleSystem.userData.speed = baseSpeed * Math.min(Math.max(speedFactor, 0.5), 2);
    statusSpan.textContent = '⏳ 请求处理中...';
}

// ----- 获取数据并渲染 -----
async function loadTraceData() {
    loadingOverlay.classList.remove('hidden');
    errorMsg.style.display = 'none';
    loadingText.textContent = '正在获取调用链数据...';
    statusSpan.textContent = '⏳ 加载中...';

    try {
        const url = apiUrl('/3d/api/trace');
        const resp = await fetch(url);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const data = await resp.json();
        if (data.error) throw new Error(data.error);

        if (!scene) initScene();
        renderTrace(data);
        // 自动触发一次演示动画
        setTimeout(() => startParticleAnimation(), 500);
    } catch (err) {
        console.error('加载失败:', err);
        errorMsg.textContent = '❌ 加载失败: ' + err.message;
        errorMsg.style.display = 'block';
        loadingText.textContent = '加载失败，请重试';
        statusSpan.textContent = '❌ 加载失败';
        if (!scene) initScene();
        loadingOverlay.classList.add('hidden');
    }
}

// ----- 启动 -----
function start() {
    if (!scene) {
        initScene();
        animate(0);
    }
    loadTraceData();
}

// 发送按钮事件
sendBtn.addEventListener('click', startParticleAnimation);

// 页面加载完成启动
if (document.readyState === 'complete') {
    start();
} else {
    window.addEventListener('load', start);
}