// ---------- API 基础路径 ----------
const API_BASE = (window.apiUrl ? window.apiUrl('/3d/api') : '/3d/api');

// ---------- DOM 引用 ----------
const moduleContainer = document.getElementById('module-container');
const barChartContainer = document.getElementById('bar-chart-container');
const tooltipEl = document.getElementById('moduleTooltip');

let barChart = null;

// ---------- 1. 模块 3D 展示（Three.js） ----------
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { CSS2DRenderer, CSS2DObject } from 'three/addons/renderers/CSS2DRenderer.js';

let scene, camera, renderer, labelRenderer, controls;
let moduleMeshes = [];
let raycaster = new THREE.Raycaster();
let mouse = new THREE.Vector2();

async function fetchModules() {
    const url = API_BASE + '/modules';
    try {
        const resp = await fetch(url);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        return await resp.json();
    } catch (err) {
        console.warn('获取模块失败，使用默认数据', err);
        return [
            { name: '轻量 Web 框架', icon: '⚙️', description: '自研 IoC/MVC，嵌入式 Tomcat' },
            { name: '数据库管理', icon: '🗄️', description: 'JDBC 元数据，树形导航' },
            { name: '代码分析', icon: '📊', description: '行数统计，语言识别' },
            { name: '目录管理', icon: '📁', description: '虚拟文件系统，安全校验' },
            { name: '智能 Agent', icon: '🤖', description: 'AI 助手，知识库问答' },
            { name: 'API 调试', icon: '🔌', description: 'Swagger 文档，在线调试' }
        ];
    }
}

function initModuleScene() {
    const width = moduleContainer.clientWidth;
    const height = moduleContainer.clientHeight;

    // 场景
    scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0a0f1a);

    // 相机
    camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);
    camera.position.set(8, 6, 12);
    camera.lookAt(0, 0, 0);

    // WebGL渲染器
    renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(window.devicePixelRatio);
    moduleContainer.appendChild(renderer.domElement);

    // CSS2D渲染器（标签）
    labelRenderer = new CSS2DRenderer();
    labelRenderer.setSize(width, height);
    labelRenderer.domElement.style.position = 'absolute';
    labelRenderer.domElement.style.top = '0';
    labelRenderer.domElement.style.left = '0';
    labelRenderer.domElement.style.pointerEvents = 'none'; // 让点击穿透
    moduleContainer.appendChild(labelRenderer.domElement);

    // 控制器
    controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.05;
    controls.autoRotate = true;
    controls.autoRotateSpeed = 1.5;
    controls.target.set(0, 0, 0);
    controls.maxPolarAngle = Math.PI / 2;

    // 灯光
    const ambientLight = new THREE.AmbientLight(0x404060);
    scene.add(ambientLight);
    const dirLight = new THREE.DirectionalLight(0xffffff, 1.5);
    dirLight.position.set(5, 10, 7);
    scene.add(dirLight);
    const backLight = new THREE.DirectionalLight(0x4da6ff, 0.5);
    backLight.position.set(-5, 0, -5);
    scene.add(backLight);

    // 辅助网格（透明）
    const gridHelper = new THREE.GridHelper(20, 20, 0x4da6ff, 0x4da6ff);
    gridHelper.position.y = -2.5;
    scene.add(gridHelper);

    // 响应窗口变化
    window.addEventListener('resize', onModuleResize);
}

function onModuleResize() {
    const width = moduleContainer.clientWidth;
    const height = moduleContainer.clientHeight;
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    renderer.setSize(width, height);
    labelRenderer.setSize(width, height);
}

function createModuleNode(module, index, total) {
    const radius = 4.5;
    const angle = (index / total) * Math.PI * 2;
    const x = radius * Math.cos(angle);
    const z = radius * Math.sin(angle);
    const y = 0;

    // 模块主体（球体）
    const geometry = new THREE.SphereGeometry(0.8, 32, 32);
    const material = new THREE.MeshPhongMaterial({
        color: 0x4da6ff,
        emissive: 0x1a3a6a,
        shininess: 60,
        transparent: true,
        opacity: 0.9
    });
    const mesh = new THREE.Mesh(geometry, material);
    mesh.position.set(x, y, z);
    mesh.userData = { module, index };
    scene.add(mesh);
    moduleMeshes.push(mesh);

    // 装饰光环（圆环）
    const ringGeo = new THREE.TorusGeometry(0.9, 0.05, 16, 32);
    const ringMat = new THREE.MeshPhongMaterial({ color: 0x4da6ff, emissive: 0x2a6aff });
    const ring = new THREE.Mesh(ringGeo, ringMat);
    ring.position.copy(mesh.position);
    ring.rotation.x = Math.PI / 2;
    ring.userData.parentMesh = mesh;
    scene.add(ring);

    // 标签（CSS2D）
    const labelDiv = document.createElement('div');
    labelDiv.textContent = module.icon + ' ' + module.name;
    labelDiv.style.color = '#e2e8f0';
    labelDiv.style.fontSize = '16px';
    labelDiv.style.fontWeight = '600';
    labelDiv.style.textShadow = '0 0 10px rgba(0,102,255,0.5)';
    labelDiv.style.background = 'rgba(0,0,0,0.5)';
    labelDiv.style.padding = '4px 12px';
    labelDiv.style.borderRadius = '20px';
    labelDiv.style.border = '1px solid rgba(0,102,255,0.3)';
    labelDiv.style.backdropFilter = 'blur(4px)';
    labelDiv.style.whiteSpace = 'nowrap';
    labelDiv.style.pointerEvents = 'none';
    const label = new CSS2DObject(labelDiv);
    label.position.set(x, y - 1.5, z);
    scene.add(label);

    // 连接线（从中心到节点）
    const points = [
        new THREE.Vector3(0, 0, 0),
        new THREE.Vector3(x * 0.8, y, z * 0.8)
    ];
    const lineGeo = new THREE.BufferGeometry().setFromPoints(points);
    const lineMat = new THREE.LineBasicMaterial({ color: 0x4da6ff, transparent: true, opacity: 0.3 });
    const line = new THREE.Line(lineGeo, lineMat);
    scene.add(line);
}

async function loadModules() {
    const modules = await fetchModules();
    const total = modules.length;
    modules.forEach((mod, i) => createModuleNode(mod, i, total));

    // 添加中心发光球体
    const centerGeo = new THREE.SphereGeometry(0.4, 16, 16);
    const centerMat = new THREE.MeshBasicMaterial({ color: 0x4da6ff });
    const centerMesh = new THREE.Mesh(centerGeo, centerMat);
    scene.add(centerMesh);

    // 动画循环
    animateModules();
}

function animateModules() {
    requestAnimationFrame(animateModules);
    controls.update(); // 自动旋转启用
    // 让光环自转
    scene.children.forEach(child => {
        if (child.isMesh && child.geometry.type === 'TorusGeometry') {
            child.rotation.z += 0.01;
        }
    });
    renderer.render(scene, camera);
    labelRenderer.render(scene, camera);
}

// ---------- 鼠标交互（点击显示详情） ----------
function setupModuleInteraction() {
    moduleContainer.addEventListener('click', (event) => {
        const rect = moduleContainer.getBoundingClientRect();
        mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
        mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

        raycaster.setFromCamera(mouse, camera);
        const intersects = raycaster.intersectObjects(moduleMeshes);
        if (intersects.length > 0) {
            const hit = intersects[0].object;
            const moduleData = hit.userData.module;
            if (moduleData) {
                showTooltip(event.clientX, event.clientY, moduleData);
            }
        } else {
            hideTooltip();
        }
    });

    // 鼠标移出容器隐藏提示
    moduleContainer.addEventListener('mouseleave', hideTooltip);
}

function showTooltip(x, y, module) {
    tooltipEl.innerHTML = `
        <span class="icon">${module.icon}</span>
        <span class="name">${module.name}</span>
        <span class="desc">${module.description || '暂无描述'}</span>
    `;
    tooltipEl.style.left = (x + 16) + 'px';
    tooltipEl.style.top = (y - 20) + 'px';
    tooltipEl.classList.add('visible');
}

function hideTooltip() {
    tooltipEl.classList.remove('visible');
}

// ---------- 2. 3D 柱状图（原有功能，保留不变） ----------
async function fetchBarData() {
    const params = new URLSearchParams();
    params.append('rootDir', '../../../');
    params.append('extensions', 'java');
    params.append('excludeComments', 'true');
    params.append('threadCount', '4');
    const url = API_BASE + '/bar-data?' + params.toString();
    try {
        const resp = await fetch(url);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const data = await resp.json();
        if (data.error) throw new Error(data.error);
        return data;
    } catch (err) {
        console.warn('获取柱状图数据失败，使用模拟数据', err);
        return {
            categories: ['Java', 'Python', 'JavaScript', 'Go', 'C++'],
            series: [15234, 8921, 6543, 3210, 1876]
        };
    }
}

async function renderBarChart() {
    if (!barChartContainer) return;
    if (!barChart) {
        barChart = echarts.init(barChartContainer);
    }
    barChart.showLoading();
    try {
        const data = await fetchBarData();
        const categories = data.categories || [];
        const seriesData = data.series || [];
        const option = {
            tooltip: { trigger: 'item', formatter: params => `${params.name}<br/>代码行数: ${params.value}` },
            grid3D: {
                viewControl: { autoRotate: true, autoRotateSpeed: 5, distance: 200 },
                boxWidth: 100, boxHeight: 60, boxDepth: 80,
                axisLine: { lineStyle: { color: '#4da6ff' } },
                axisLabel: { color: '#e2e8f0', fontSize: 12 },
                splitLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }
            },
            xAxis3D: { type: 'category', data: categories, axisLabel: { rotate: 30, interval: 0 } },
            yAxis3D: { type: 'value', name: '代码行数', nameTextStyle: { color: '#e2e8f0' } },
            zAxis3D: { type: 'category', data: [''], axisLabel: { show: false } },
            series: [{
                type: 'bar3D',
                data: seriesData.map((value, index) => ({ name: categories[index] || `项${index+1}`, value })),
                shading: 'lambert',
                label: { show: true, formatter: '{c}', textStyle: { color: '#fff', fontSize: 12 } },
                itemStyle: { color: '#4da6ff', opacity: 0.9 },
                emphasis: { itemStyle: { color: '#ff6b6b' } }
            }]
        };
        barChart.hideLoading();
        barChart.setOption(option, true);
        barChart.resize();
    } catch (err) {
        console.error('渲染柱状图失败', err);
        barChart.hideLoading();
        barChart.setOption({
            title: { text: '加载数据失败，请检查后端服务', left: 'center', top: 'center', textStyle: { color: '#ef4444' } }
        });
    }
}

// ---------- Tab 切换逻辑 ----------
function setupTabSwitching() {
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        item.addEventListener('click', () => {
            const tab = item.dataset.tab;
            navItems.forEach(n => n.classList.remove('active'));
            item.classList.add('active');
            document.querySelectorAll('.tab-pane').forEach(pane => pane.classList.remove('active'));
            const targetPane = document.getElementById(`tab-${tab}`);
            if (targetPane) targetPane.classList.add('active');

            if (tab === 'barchart') {
                setTimeout(() => renderBarChart(), 100);
            } else if (tab === 'modules') {
                // 重新调整尺寸（防止布局变化）
                setTimeout(() => {
                    onModuleResize();
                    controls.update();
                }, 100);
            }
        });
    });
}

// ---------- 初始化 ----------
async function init() {
    // 初始化模块场景
    initModuleScene();
    await loadModules();
    setupModuleInteraction();

    // 初始化柱状图（默认不加载，切换到柱状图时加载）
    setupTabSwitching();

    // 处理初始激活的 tab（默认是 modules）
    // 如果 URL 有 hash，可以跳转
    const hash = window.location.hash;
    if (hash === '#barchart') {
        const barchartItem = document.querySelector('.nav-item[data-tab="barchart"]');
        if (barchartItem) barchartItem.click();
    }

    // 窗口 resize
    window.addEventListener('resize', () => {
        onModuleResize();
        if (barChart) barChart.resize();
    });
}

if (document.readyState === 'complete') {
    init();
} else {
    window.addEventListener('load', init);
}

// 暴露重新加载函数（便于调试）
window.reloadModules = loadModules;
window.reloadBarChart = renderBarChart;