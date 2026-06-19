import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { CSS2DRenderer, CSS2DObject } from 'three/addons/renderers/CSS2DRenderer.js';

const container = document.getElementById('container');
const tooltip = document.getElementById('tooltip');

// API 路径
const API_BASE = (window.apiUrl ? window.apiUrl('/3d/api') : '/3d/api');

// 场景初始化
function initScene() {
    const width = container.clientWidth || 800;
    const height = container.clientHeight || 600;

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0a0f1a);

    const camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);
    camera.position.set(8, 6, 12);
    camera.lookAt(0, 0, 0);

    const renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(window.devicePixelRatio);
    container.appendChild(renderer.domElement);

    const labelRenderer = new CSS2DRenderer();
    labelRenderer.setSize(width, height);
    labelRenderer.domElement.style.position = 'absolute';
    labelRenderer.domElement.style.top = '0';
    labelRenderer.domElement.style.left = '0';
    labelRenderer.domElement.style.pointerEvents = 'none';
    container.appendChild(labelRenderer.domElement);

    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.05;
    controls.autoRotate = true;
    controls.autoRotateSpeed = 1.5;
    controls.target.set(0, 0, 0);
    controls.maxPolarAngle = Math.PI / 2;

    // 灯光
    const ambient = new THREE.AmbientLight(0x404060);
    scene.add(ambient);
    const dirLight = new THREE.DirectionalLight(0xffffff, 1.5);
    dirLight.position.set(5, 10, 7);
    scene.add(dirLight);
    const backLight = new THREE.DirectionalLight(0x4da6ff, 0.5);
    backLight.position.set(-5, 0, -5);
    scene.add(backLight);

    const grid = new THREE.GridHelper(20, 20, 0x4da6ff, 0x4da6ff);
    grid.position.y = -2.5;
    scene.add(grid);

    // Resize
    window.addEventListener('resize', () => {
        const w = container.clientWidth || 800;
        const h = container.clientHeight || 600;
        camera.aspect = w / h;
        camera.updateProjectionMatrix();
        renderer.setSize(w, h);
        labelRenderer.setSize(w, h);
    });

    return { scene, camera, renderer, labelRenderer, controls };
}

// 加载模块数据
async function fetchModules() {
    try {
        const resp = await fetch(API_BASE + '/modules');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        return await resp.json();
    } catch (err) {
        console.warn('使用默认模块数据', err);
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

// 创建节点
function createNodes(modules, scene, labelRenderer) {
    const meshes = [];
    const radius = 4.5;
    const total = modules.length;

    modules.forEach((mod, index) => {
        const angle = (index / total) * Math.PI * 2;
        const x = radius * Math.cos(angle);
        const z = radius * Math.sin(angle);
        const y = 0;

        // 球体
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
        mesh.userData = { module: mod };
        scene.add(mesh);
        meshes.push(mesh);

        // 光环
        const ringGeo = new THREE.TorusGeometry(0.9, 0.05, 16, 32);
        const ringMat = new THREE.MeshPhongMaterial({ color: 0x4da6ff, emissive: 0x2a6aff });
        const ring = new THREE.Mesh(ringGeo, ringMat);
        ring.position.copy(mesh.position);
        ring.rotation.x = Math.PI / 2;
        ring.userData.parentMesh = mesh;
        scene.add(ring);

        // CSS2D标签
        const labelDiv = document.createElement('div');
        labelDiv.textContent = mod.icon + ' ' + mod.name;
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

        // 连接线
        const points = [
            new THREE.Vector3(0, 0, 0),
            new THREE.Vector3(x * 0.8, y, z * 0.8)
        ];
        const lineGeo = new THREE.BufferGeometry().setFromPoints(points);
        const lineMat = new THREE.LineBasicMaterial({ color: 0x4da6ff, transparent: true, opacity: 0.3 });
        const line = new THREE.Line(lineGeo, lineMat);
        scene.add(line);
    });

    // 中心发光球
    const centerGeo = new THREE.SphereGeometry(0.4, 16, 16);
    const centerMat = new THREE.MeshBasicMaterial({ color: 0x4da6ff });
    const center = new THREE.Mesh(centerGeo, centerMat);
    scene.add(center);

    return meshes;
}

// 交互
function setupInteraction(meshes, scene, camera, renderer, labelRenderer, controls) {
    const raycaster = new THREE.Raycaster();
    const mouse = new THREE.Vector2();
    const tooltipEl = document.getElementById('tooltip');

    container.addEventListener('click', (event) => {
        const rect = container.getBoundingClientRect();
        mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
        mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

        raycaster.setFromCamera(mouse, camera);
        const intersects = raycaster.intersectObjects(meshes);
        if (intersects.length > 0) {
            const hit = intersects[0].object;
            const mod = hit.userData.module;
            if (mod) {
                tooltipEl.innerHTML = `
                    <span class="icon">${mod.icon}</span>
                    <span class="name">${mod.name}</span>
                    <span class="desc">${mod.description || '暂无描述'}</span>
                `;
                tooltipEl.style.left = (event.clientX + 16) + 'px';
                tooltipEl.style.top = (event.clientY - 20) + 'px';
                tooltipEl.classList.add('visible');
            }
        } else {
            tooltipEl.classList.remove('visible');
        }
    });

    container.addEventListener('mouseleave', () => {
        tooltipEl.classList.remove('visible');
    });
}

// 启动
async function start() {
    const { scene, camera, renderer, labelRenderer, controls } = initScene();
    const modules = await fetchModules();
    const meshes = createNodes(modules, scene, labelRenderer);
    setupInteraction(meshes, scene, camera, renderer, labelRenderer, controls);

    function animate() {
        requestAnimationFrame(animate);
        controls.update();
        // 光环自转
        scene.children.forEach(child => {
            if (child.isMesh && child.geometry.type === 'TorusGeometry') {
                child.rotation.z += 0.01;
            }
        });
        renderer.render(scene, camera);
        labelRenderer.render(scene, camera);
    }
    animate();

    // 窗口自适应
    window.addEventListener('resize', () => {
        const w = container.clientWidth || 800;
        const h = container.clientHeight || 600;
        camera.aspect = w / h;
        camera.updateProjectionMatrix();
        renderer.setSize(w, h);
        labelRenderer.setSize(w, h);
    });
}

start();