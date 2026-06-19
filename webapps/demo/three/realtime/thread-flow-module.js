// ======== thread-flow-module.js ========
// 功能：在右侧面板中显示调用链的节点（球体+标签），节点间连线，并带有流动粒子效果
// 每个节点显示类/方法名称，支持鼠标悬停查看详情
// 节点从左到右水平排列，优先显示最新的线程（按 lastActiveTime 降序），若无时间则保持原顺序

import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { CSS2DRenderer, CSS2DObject } from 'three/addons/renderers/CSS2DRenderer.js';

// ----- 模块内部状态 -----
let currentUpdateFn = null; // 供外部调用的更新函数

// ----- 导出初始化函数 -----
export function initThreadFlow(container) {
    const width = container.clientWidth;
    const height = container.clientHeight;

    // ---------- 场景 ----------
    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0a0f1a);

    // ---------- 相机 ----------
    const camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);
    camera.position.set(0, 2, 14);
    camera.lookAt(0, 0, 0);

    // ---------- WebGL渲染器 ----------
    const renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    container.appendChild(renderer.domElement);

    // ---------- CSS2D渲染器（用于标签） ----------
    const labelRenderer = new CSS2DRenderer();
    labelRenderer.setSize(width, height);
    labelRenderer.domElement.style.position = 'absolute';
    labelRenderer.domElement.style.top = '0';
    labelRenderer.domElement.style.left = '0';
    labelRenderer.domElement.style.pointerEvents = 'none';
    container.appendChild(labelRenderer.domElement);

    // ---------- 控制器 ----------
    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
    controls.target.set(0, 0, 0);
    controls.update();

    // ---------- 灯光 ----------
    const ambient = new THREE.AmbientLight(0x404060);
    scene.add(ambient);
    const dirLight = new THREE.DirectionalLight(0xffffff, 1.2);
    dirLight.position.set(5, 10, 7);
    scene.add(dirLight);
    const backLight = new THREE.DirectionalLight(0x4da6ff, 0.5);
    backLight.position.set(-5, 0, -5);
    scene.add(backLight);

    // ---------- 网格辅助 ----------
    const gridHelper = new THREE.GridHelper(20, 15, 0x4da6ff, 0x2a4a7a);
    gridHelper.position.y = -2.5;
    scene.add(gridHelper);

    // ---------- 分组 ----------
    const nodesGroup = new THREE.Group();
    const edgesGroup = new THREE.Group();
    const particlesGroup = new THREE.Group();
    scene.add(nodesGroup);
    scene.add(edgesGroup);
    scene.add(particlesGroup);

    // ---------- 鼠标悬浮提示 ----------
    const tooltip = document.createElement('div');
    tooltip.style.cssText = `
        position: absolute;
        background: rgba(0,0,0,0.85);
        backdrop-filter: blur(8px);
        color: #e2e8f0;
        padding: 10px 16px;
        border-radius: 12px;
        border: 1px solid rgba(0,102,255,0.6);
        pointer-events: none;
        opacity: 0;
        transition: opacity 0.2s;
        z-index: 100;
        max-width: 280px;
        font-size: 12px;
        line-height: 1.4;
        box-shadow: 0 4px 20px rgba(0,0,0,0.5);
    `;
    container.style.position = 'relative';
    container.appendChild(tooltip);

    let hoveredObject = null;
    const raycaster = new THREE.Raycaster();
    const mouse = new THREE.Vector2();

    container.addEventListener('mousemove', (event) => {
        const rect = container.getBoundingClientRect();
        mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
        mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

        raycaster.setFromCamera(mouse, camera);
        const meshes = nodesGroup.children.filter(child => child.isMesh);
        const intersects = raycaster.intersectObjects(meshes);

        if (hoveredObject && !intersects.includes(hoveredObject)) {
            if (hoveredObject.material) {
                hoveredObject.material.emissive.setHex(0x000000);
            }
            hoveredObject = null;
            tooltip.style.opacity = '0';
        }

        if (intersects.length > 0) {
            const hit = intersects[0].object;
            if (hit.userData && hit.userData.nodeName) {
                if (hoveredObject !== hit) {
                    if (hoveredObject) hoveredObject.material.emissive.setHex(0x000000);
                    hoveredObject = hit;
                    hit.material.emissive.setHex(0x4444ff);
                }
                const name = hit.userData.nodeName;
                const status = hit.userData.status || 'UNKNOWN';
                const color = status === 'RUNNING' ? '#22c55e' : (status === 'BLOCKED' ? '#ef4444' : '#f59e0b');
                tooltip.innerHTML = `
                    <strong>${name}</strong><br/>
                    状态: <span style="color:${color}">${status}</span>
                `;
                tooltip.style.left = (event.clientX - rect.left + 16) + 'px';
                tooltip.style.top = (event.clientY - rect.top - 10) + 'px';
                tooltip.style.opacity = '1';
            }
        }
    });

    container.addEventListener('mouseleave', () => {
        tooltip.style.opacity = '0';
        if (hoveredObject) {
            hoveredObject.material.emissive.setHex(0x000000);
            hoveredObject = null;
        }
    });

    // ---------- 核心渲染函数 ----------
    function clearGraph() {
        while (nodesGroup.children.length) {
            const child = nodesGroup.children[0];
            if (child.geometry) child.geometry.dispose();
            if (child.material) child.material.dispose();
            nodesGroup.remove(child);
        }
        while (edgesGroup.children.length) {
            const child = edgesGroup.children[0];
            if (child.geometry) child.geometry.dispose();
            if (child.material) child.material.dispose();
            edgesGroup.remove(child);
        }
        while (particlesGroup.children.length) {
            const child = particlesGroup.children[0];
            if (child.geometry) child.geometry.dispose();
            if (child.material) child.material.dispose();
            particlesGroup.remove(child);
        }
    }

    // 生成节点位置：水平从左到右，Y轴居中，Z轴微偏移
    function generateNodePositions(nodeCount, startX = -6, endX = 6, y = 0) {
        const positions = [];
        if (nodeCount === 0) return positions;
        if (nodeCount === 1) {
            positions.push(new THREE.Vector3(0, y, 0));
            return positions;
        }
        for (let i = 0; i < nodeCount; i++) {
            const t = i / (nodeCount - 1);
            const x = startX + t * (endX - startX);
            const z = Math.sin(i * 1.3) * 0.4;
            positions.push(new THREE.Vector3(x, y, z));
        }
        return positions;
    }

    // 渲染线程调用链：使用排序后的线程列表的第一条（最新）
    function renderThreadFlows(threads) {
        clearGraph();

        // 如果没有线程数据，显示提示
        if (!threads || threads.length === 0) {
            const msgDiv = document.createElement('div');
            msgDiv.textContent = '暂无线程数据';
            msgDiv.style.color = '#94a3b8';
            msgDiv.style.fontSize = '14px';
            msgDiv.style.background = 'rgba(0,0,0,0.4)';
            msgDiv.style.padding = '12px 24px';
            msgDiv.style.borderRadius = '20px';
            msgDiv.style.border = '1px solid rgba(255,255,255,0.1)';
            const label = new CSS2DObject(msgDiv);
            label.position.set(0, 0, 0);
            nodesGroup.add(label);
            return;
        }

        // 取第一条线程（已按时间排序）
        const firstThread = threads[0];
        let nodeNames = firstThread.nodes;

        // 如果 nodes 不存在或为空，尝试从 callChain 解析
        if (!nodeNames || nodeNames.length === 0) {
            if (firstThread.callChain && typeof firstThread.callChain === 'string') {
                const separator = firstThread.callChain.includes(' → ') ? ' → ' :
                    (firstThread.callChain.includes('->') ? '->' : null);
                if (separator) {
                    nodeNames = firstThread.callChain.split(separator).map(s => s.trim()).filter(s => s.length > 0);
                } else {
                    nodeNames = [firstThread.callChain.trim()];
                }
            } else {
                nodeNames = [];
            }
        }

        // ★ 关键修改：即使只有一个节点，也显示，不再要求至少2个
        if (!nodeNames || nodeNames.length === 0) {
            const msgDiv = document.createElement('div');
            msgDiv.textContent = '该线程无调用链信息';
            msgDiv.style.color = '#94a3b8';
            msgDiv.style.fontSize = '14px';
            msgDiv.style.background = 'rgba(0,0,0,0.4)';
            msgDiv.style.padding = '12px 24px';
            msgDiv.style.borderRadius = '20px';
            msgDiv.style.border = '1px solid rgba(255,255,255,0.1)';
            const label = new CSS2DObject(msgDiv);
            label.position.set(0, 0, 0);
            nodesGroup.add(label);
            return;
        }

        const status = firstThread.status || 'IDLE';
        const isRunning = status === 'RUNNING';
        const color = isRunning ? 0x22c55e : (status === 'BLOCKED' ? 0xef4444 : 0xf59e0b);

        // 水平布局
        const nodeCount = nodeNames.length;
        const positions = generateNodePositions(nodeCount, -6, 6, 0);

        // ----- 创建节点（球体 + 标签） -----
        const sphereGeo = new THREE.SphereGeometry(0.5, 24, 24);
        for (let i = 0; i < nodeCount; i++) {
            const pos = positions[i];
            const name = nodeNames[i];

            const material = new THREE.MeshPhongMaterial({
                color: color,
                emissive: new THREE.Color(color).multiplyScalar(0.1),
                shininess: 40,
                transparent: true,
                opacity: 0.9
            });
            const sphere = new THREE.Mesh(sphereGeo, material);
            sphere.position.copy(pos);
            sphere.userData = {
                nodeName: name,
                status: status,
                index: i
            };
            nodesGroup.add(sphere);

            // CSS2D 标签
            const labelDiv = document.createElement('div');
            labelDiv.textContent = name;
            labelDiv.style.color = '#e2e8f0';
            labelDiv.style.fontSize = '11px';
            labelDiv.style.fontWeight = '500';
            labelDiv.style.textShadow = '0 0 8px rgba(0,0,0,0.9)';
            labelDiv.style.background = 'rgba(0,0,0,0.6)';
            labelDiv.style.padding = '2px 8px';
            labelDiv.style.borderRadius = '12px';
            labelDiv.style.border = '1px solid rgba(255,255,255,0.15)';
            labelDiv.style.whiteSpace = 'nowrap';
            labelDiv.style.pointerEvents = 'none';
            const label = new CSS2DObject(labelDiv);
            label.position.set(pos.x, pos.y + 0.9, pos.z);
            nodesGroup.add(label);
        }

        // ----- 创建连线（只有多于1个节点时才绘制） -----
        if (nodeCount > 1) {
            const lineMat = new THREE.LineBasicMaterial({
                color: color,
                transparent: true,
                opacity: 0.4
            });
            for (let i = 0; i < nodeCount - 1; i++) {
                const p1 = positions[i];
                const p2 = positions[i + 1];
                const points = [p1, p2];
                const lineGeo = new THREE.BufferGeometry().setFromPoints(points);
                const line = new THREE.Line(lineGeo, lineMat);
                edgesGroup.add(line);
            }
        }

        // ----- 粒子流（只有多于1个节点时才生成路径） -----
        if (nodeCount > 1) {
            const pathPoints = positions.map(p => p.clone());
            const curve = new THREE.CatmullRomCurve3(pathPoints);

            const particleCount = 30;
            const particlePositions = new Float32Array(particleCount * 3);
            const progresses = new Float32Array(particleCount);
            for (let i = 0; i < particleCount; i++) {
                progresses[i] = i / particleCount;
            }

            const particleGeo = new THREE.BufferGeometry();
            particleGeo.setAttribute('position', new THREE.BufferAttribute(particlePositions, 3));
            const particleMat = new THREE.PointsMaterial({
                color: 0xff6b6b,
                size: 0.2,
                transparent: true,
                opacity: 0.7,
                blending: THREE.AdditiveBlending,
                depthWrite: false,
                sizeAttenuation: true
            });
            const particleSystem = new THREE.Points(particleGeo, particleMat);
            particleSystem.userData = {
                curve: curve,
                progresses: progresses,
                speed: 0.008,
                offset: Math.random()
            };
            particlesGroup.add(particleSystem);
        }
    }

    // 更新粒子位置
    function updateParticles() {
        particlesGroup.children.forEach(ps => {
            const data = ps.userData;
            if (!data) return;
            data.offset += data.speed;
            if (data.offset > 1) data.offset -= 1;

            const positions = ps.geometry.attributes.position.array;
            const count = data.progresses.length;
            for (let i = 0; i < count; i++) {
                let t = (data.progresses[i] + data.offset) % 1;
                const point = data.curve.getPoint(t);
                positions[i * 3] = point.x;
                positions[i * 3 + 1] = point.y;
                positions[i * 3 + 2] = point.z;
            }
            ps.geometry.attributes.position.needsUpdate = true;
        });
    }

    // ----- 外部更新接口（在此处对线程列表按时间排序） -----
    function updateData(threads) {
        // 如果线程列表存在，尝试按时间排序（降序，最新的在前）
        let sortedThreads = threads;
        if (threads && threads.length > 0) {
            // 检查是否有时间字段，如果有则按时间降序排序
            // 优先使用 lastActiveTime，其次 startTime，最后 timestamp
            const hasTime = threads.some(t => t.lastActiveTime !== undefined || t.startTime !== undefined || t.timestamp !== undefined);
            if (hasTime) {
                sortedThreads = [...threads].sort((a, b) => {
                    const timeA = a.lastActiveTime || a.startTime || a.timestamp || 0;
                    const timeB = b.lastActiveTime || b.startTime || b.timestamp || 0;
                    return timeB - timeA; // 降序，最新的在前
                });
            } else {
                // 如果没有时间字段，保持原顺序（外部可能已按活跃度排序）
                sortedThreads = threads;
            }
        }
        renderThreadFlows(sortedThreads);
    }

    currentUpdateFn = updateData;

    // 初始渲染空状态
    renderThreadFlows([]);

    // ----- 动画循环 -----
    function animate() {
        requestAnimationFrame(animate);
        controls.update();
        updateParticles();
        renderer.render(scene, camera);
        labelRenderer.render(scene, camera);
    }
    animate();

    // ----- 窗口自适应 -----
    const resizeObserver = new ResizeObserver(() => {
        const w = container.clientWidth;
        const h = container.clientHeight;
        camera.aspect = w / h;
        camera.updateProjectionMatrix();
        renderer.setSize(w, h);
        labelRenderer.setSize(w, h);
    });
    resizeObserver.observe(container);

    // 返回清理函数
    return () => {
        resizeObserver.disconnect();
        renderer.dispose();
        labelRenderer.domElement.remove();
        if (tooltip.parentNode) tooltip.parentNode.removeChild(tooltip);
    };
}

// ----- 导出更新函数供外部调用 -----
export function updateThreadFlowData(threads) {
    if (currentUpdateFn) {
        currentUpdateFn(threads);
    } else {
        console.warn('粒子流模块尚未初始化，无法更新数据');
    }
}