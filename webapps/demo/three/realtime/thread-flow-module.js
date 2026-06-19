import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

function getContextPath() {
    const path = window.location.pathname;
    const parts = path.split('/');
    if (parts.length > 1 && parts[1]) return '/' + parts[1];
    return '';
}
const CONTEXT_PATH = getContextPath();

// ----- 模块内部状态 -----
let currentUpdateFn = null;

export function initThreadFlow(container) {
    const width = container.clientWidth;
    const height = container.clientHeight;

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0a0f1a);

    const camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);
    camera.position.set(10, 6, 12);
    camera.lookAt(0, 0, 0);

    const renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    container.appendChild(renderer.domElement);

    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
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

    const gridHelper = new THREE.GridHelper(20, 15, 0x4da6ff, 0x2a4a7a);
    gridHelper.position.y = -3;
    scene.add(gridHelper);

    const linesGroup = new THREE.Group();
    const particlesGroup = new THREE.Group();
    scene.add(linesGroup);
    scene.add(particlesGroup);

    // 鼠标悬浮提示
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

    let hoveredLine = null;
    const raycaster = new THREE.Raycaster();
    const mouse = new THREE.Vector2();

    container.addEventListener('mousemove', (event) => {
        const rect = container.getBoundingClientRect();
        mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
        mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

        raycaster.setFromCamera(mouse, camera);
        const lines = linesGroup.children;
        const intersects = raycaster.intersectObjects(lines);

        if (hoveredLine && !intersects.includes(hoveredLine)) {
            hoveredLine.material.color.setHex(hoveredLine.userData.originalColor);
            hoveredLine = null;
            tooltip.style.opacity = '0';
        }

        if (intersects.length > 0) {
            const hit = intersects[0].object;
            if (hit.userData && hit.userData.thread) {
                if (hoveredLine !== hit) {
                    if (hoveredLine) hoveredLine.material.color.setHex(hoveredLine.userData.originalColor);
                    hoveredLine = hit;
                    hit.material.color.setHex(0xffffff);
                }
                const t = hit.userData.thread;
                const chain = t.callChain || '—';
                tooltip.innerHTML = `
                    <strong>${t.threadName}</strong> (ID: ${t.threadId})<br/>
                    状态: <span style="color:${t.status === 'RUNNING' ? '#22c55e' : '#ef4444'}">${t.status}</span><br/>
                    调用链: ${chain}
                `;
                tooltip.style.left = (event.clientX - rect.left + 16) + 'px';
                tooltip.style.top = (event.clientY - rect.top - 10) + 'px';
                tooltip.style.opacity = '1';
            }
        }
    });

    container.addEventListener('mouseleave', () => {
        tooltip.style.opacity = '0';
        if (hoveredLine) {
            hoveredLine.material.color.setHex(hoveredLine.userData.originalColor);
            hoveredLine = null;
        }
    });

    // ----- 核心渲染函数 -----
    function clearGraph() {
        while (linesGroup.children.length) {
            const child = linesGroup.children[0];
            if (child.geometry) child.geometry.dispose();
            if (child.material) child.material.dispose();
            linesGroup.remove(child);
        }
        while (particlesGroup.children.length) {
            const child = particlesGroup.children[0];
            if (child.geometry) child.geometry.dispose();
            if (child.material) child.material.dispose();
            particlesGroup.remove(child);
        }
    }

    function generatePathFromNodes(thread, index, total) {
        const nodes = thread.nodes || [];
        let points = [];
        if (nodes && nodes.length >= 2) {
            const count = nodes.length;
            const baseX = (index - total/2) * 4;
            const yBase = 0;
            for (let i = 0; i < count; i++) {
                const t = i / (count - 1);
                const x = baseX + (i - (count-1)/2) * 2.5;
                const y = yBase + Math.sin(t * Math.PI * 2) * 1.5;
                const z = (i % 3 - 1) * 1.2;
                points.push(new THREE.Vector3(x, y, z));
            }
        } else {
            // 降级：生成随机但固定的路径
            const numPoints = 5 + Math.floor(Math.random() * 3);
            const baseX = (index - total/2) * 4;
            for (let i = 0; i < numPoints; i++) {
                const t = i / (numPoints - 1);
                const x = baseX + (Math.random() - 0.5) * 3;
                const y = Math.sin(t * Math.PI * 2) * 1.5;
                const z = (Math.random() - 0.5) * 4;
                points.push(new THREE.Vector3(x, y, z));
            }
        }
        return points;
    }

    function renderThreadFlows(threads) {
        clearGraph();
        if (!threads || threads.length === 0) {
            const msg = document.createElement('div');
            msg.style.cssText = 'position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);color:#94a3b8;font-size:14px;';
            msg.textContent = '暂无线程数据';
            container.appendChild(msg);
            setTimeout(() => msg.remove(), 2000);
            return;
        }

        const filtered = threads
            .filter(t => t.status === 'RUNNING' || t.status === 'BLOCKED')
            .sort((a, b) => {
                if (a.status === 'BLOCKED' && b.status !== 'BLOCKED') return -1;
                if (a.status !== 'BLOCKED' && b.status === 'BLOCKED') return 1;
                return 0;
            });

        const total = filtered.length;
        if (total === 0) {
            const msg = document.createElement('div');
            msg.style.cssText = 'position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);color:#94a3b8;font-size:14px;';
            msg.textContent = '当前无活跃线程';
            container.appendChild(msg);
            setTimeout(() => msg.remove(), 2000);
            return;
        }

        filtered.forEach((t, idx) => {
            const color = t.status === 'RUNNING' ? 0x22c55e : 0xef4444;
            const points = generatePathFromNodes(t, idx, total);
            const curve = new THREE.CatmullRomCurve3(points);

            // 线条
            const curvePoints = curve.getPoints(40);
            const lineGeo = new THREE.BufferGeometry().setFromPoints(curvePoints);
            const lineMat = new THREE.LineBasicMaterial({
                color: color,
                transparent: true,
                opacity: 0.5,
            });
            const line = new THREE.Line(lineGeo, lineMat);
            line.userData = { thread: t, originalColor: color };
            linesGroup.add(line);

            // 粒子
            const particleCount = 25;
            const positions = new Float32Array(particleCount * 3);
            const progresses = new Float32Array(particleCount);
            for (let i = 0; i < particleCount; i++) {
                progresses[i] = i / particleCount;
            }
            const particleGeo = new THREE.BufferGeometry();
            particleGeo.setAttribute('position', new THREE.BufferAttribute(positions, 3));

            const particleMat = new THREE.PointsMaterial({
                color: color,
                size: 0.2,
                transparent: true,
                opacity: 0.9,
                blending: THREE.AdditiveBlending,
                depthWrite: false,
                sizeAttenuation: true,
            });
            const particleSystem = new THREE.Points(particleGeo, particleMat);
            particleSystem.userData = {
                curve: curve,
                progresses: progresses,
                speed: 0.004 + Math.random() * 0.004,
                offset: Math.random(),
            };
            particlesGroup.add(particleSystem);
        });
    }

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

    // 外部更新接口
    function updateData(threads) {
        renderThreadFlows(threads);
    }

    // 将更新函数暴露给外部
    currentUpdateFn = updateData;

    // 动画循环
    function animate() {
        requestAnimationFrame(animate);
        controls.update();
        updateParticles();
        renderer.render(scene, camera);
    }
    animate();

    // 窗口自适应
    const resizeObserver = new ResizeObserver(() => {
        const w = container.clientWidth;
        const h = container.clientHeight;
        camera.aspect = w / h;
        camera.updateProjectionMatrix();
        renderer.setSize(w, h);
    });
    resizeObserver.observe(container);

    // 返回清理函数
    return () => {
        resizeObserver.disconnect();
        renderer.dispose();
        if (tooltip.parentNode) tooltip.parentNode.removeChild(tooltip);
    };
}

// 导出更新函数
export function updateThreadFlowData(threads) {
    if (currentUpdateFn) {
        currentUpdateFn(threads);
    } else {
        console.warn('粒子流模块尚未初始化，无法更新数据');
    }
}