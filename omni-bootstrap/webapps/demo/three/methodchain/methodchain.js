import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { CSS2DRenderer, CSS2DObject } from 'three/addons/renderers/CSS2DRenderer.js';

// ============ Context Path ============
function getContextPath() {
    const parts = window.location.pathname.split('/');
    return (parts.length > 1 && parts[1]) ? '/' + parts[1] : '';
}
function apiUrl(p) {
    const ctx = getContextPath();
    return ctx + (p.startsWith('/') ? p : '/' + p);
}
const API = apiUrl('/code/api/method');

// ============ DOM ============
const graphArea = document.getElementById('graph-area');
const rootDirInput = document.getElementById('rootDirInput');
const scanBtn = document.getElementById('scanBtn');
const statInfo = document.getElementById('statInfo');
const entrySelect = document.getElementById('entrySelect');
const depthInput = document.getElementById('depthInput');
const loadBtn = document.getElementById('loadBtn');
const reloadBtn = document.getElementById('reloadBtn');
const methodInfo = document.getElementById('methodInfo');
const umlText = document.getElementById('umlText');
const neighbors = document.getElementById('neighbors');
const copyUml = document.getElementById('copyUml');
const previewUml = document.getElementById('previewUml');

// ============ Three.js ============
let scene, camera, renderer, labelRenderer, controls;
let treeGroup;

function initScene() {
    const w = graphArea.clientWidth;
    const h = graphArea.clientHeight;

    scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0a0f1a);

    camera = new THREE.PerspectiveCamera(45, w / h, 0.1, 2000);
    camera.position.set(0, 0, 25);

    renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(w, h);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    graphArea.appendChild(renderer.domElement);

    labelRenderer = new CSS2DRenderer();
    labelRenderer.setSize(w, h);
    labelRenderer.domElement.style.position = 'absolute';
    labelRenderer.domElement.style.top = '0';
    labelRenderer.domElement.style.left = '0';
    labelRenderer.domElement.style.pointerEvents = 'none';
    graphArea.appendChild(labelRenderer.domElement);

    controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
    controls.target.set(0, 0, 0);

    scene.add(new THREE.AmbientLight(0x8080a0));
    const dir = new THREE.DirectionalLight(0xffffff, 1.2);
    dir.position.set(5, 10, 7);
    scene.add(dir);
    const back = new THREE.DirectionalLight(0x4da6ff, 0.5);
    back.position.set(-5, 0, -5);
    scene.add(back);

    treeGroup = new THREE.Group();
    scene.add(treeGroup);

    window.addEventListener('resize', onResize);
}

function onResize() {
    const w = graphArea.clientWidth;
    const h = graphArea.clientHeight;
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    renderer.setSize(w, h);
    labelRenderer.setSize(w, h);
}

function clearTree() {
    while (treeGroup.children.length) {
        const c = treeGroup.children[0];
        if (c.geometry) c.geometry.dispose();
        if (c.material) c.material.dispose();
        treeGroup.remove(c);
    }
}

// ============ 渲染调用树 ============
function renderTree(root) {
    clearTree();
    if (!root) return;

    const layout = {};
    const edges = [];
    let maxDepth = 0;

    const SPACING_X = 3.5;
    const SPACING_Y = 1.6;
    let nextY = 0;

    function layoutNode(node, depth) {
        maxDepth = Math.max(maxDepth, depth);
        let y;
        if (!node.children || node.children.length === 0) {
            y = nextY;
            nextY -= SPACING_Y;
        } else {
            const childrenY = [];
            node.children.forEach(c => {
                const cy = layoutNode(c, depth + 1);
                childrenY.push(cy);
                edges.push({ from: node, to: c });
            });
            y = (Math.min(...childrenY) + Math.max(...childrenY)) / 2;
        }
        layout[node.id] = { x: depth * SPACING_X - 8, y, depth };
        return y;
    }
    layoutNode(root, 0);

    // Y 居中
    const allY = Object.values(layout).map(p => p.y);
    if (allY.length) {
        const yMid = (Math.min(...allY) + Math.max(...allY)) / 2;
        Object.values(layout).forEach(p => p.y -= yMid);
    }

    // 绘制节点
    const sphereGeo = new THREE.SphereGeometry(0.35, 20, 20);

    function drawNode(node) {
        const pos = layout[node.id];
        if (!pos) return;

        let color = 0x4da6ff;
        if (node.cycleCut) color = 0xef4444;
        else if (node.depthCut) color = 0xf59e0b;
        else if (pos.depth === 0) color = 0x22c55e;

        const mat = new THREE.MeshPhongMaterial({
            color,
            emissive: new THREE.Color(color).multiplyScalar(0.25),
            shininess: 40
        });
        const mesh = new THREE.Mesh(sphereGeo, mat);
        mesh.position.set(pos.x, pos.y, 0);
        treeGroup.add(mesh);

        const div = document.createElement('div');
        div.textContent = node.displayName || node.methodName || node.id;
        div.style.cssText = `
            color:#e2e8f0; font-size:10px;
            padding:2px 8px; background:rgba(0,0,0,0.7);
            border-radius:10px; white-space:nowrap;
            border:1px solid rgba(255,255,255,0.15);
            pointer-events:none;
        `;
        const label = new CSS2DObject(div);
        label.position.set(pos.x, pos.y + 0.65, 0);
        treeGroup.add(label);

        (node.children || []).forEach(drawNode);
    }
    drawNode(root);

    // 绘制边
    const lineMat = new THREE.LineBasicMaterial({
        color: 0x4da6ff, transparent: true, opacity: 0.4
    });
    edges.forEach(({ from, to }) => {
        const p1 = layout[from.id], p2 = layout[to.id];
        if (!p1 || !p2) return;
        const geo = new THREE.BufferGeometry().setFromPoints([
            new THREE.Vector3(p1.x, p1.y, 0),
            new THREE.Vector3(p2.x, p2.y, 0)
        ]);
        treeGroup.add(new THREE.Line(geo, lineMat));
    });

    // 相机自适应
    camera.position.set(0, 0, Math.max(20, (maxDepth + 2) * 5));
    controls.target.set(0, 0, 0);
    controls.update();
}

// ============ 交互逻辑 ============
async function scanProject() {
    const rootDir = rootDirInput.value.trim() || '.';
    statInfo.textContent = '⏳ 扫描中...';
    try {
        const resp = await fetch(API + '/entries?rootDir=' + encodeURIComponent(rootDir));
        const data = await resp.json();
        if (data.error) {
            statInfo.textContent = '❌ ' + data.error;
            return;
        }
        entrySelect.innerHTML = data.length === 0
            ? '<option>未找到入口方法</option>'
            : data.map(e =>
                `<option value="${escapeAttr(e.id)}">${escapeHtml(e.displayName)}</option>`
            ).join('');
        statInfo.textContent = '✅ 找到 ' + data.length + ' 个入口方法';
    } catch (e) {
        statInfo.textContent = '❌ ' + e.message;
    }
}

async function loadCallChain() {
    const rootDir = rootDirInput.value.trim() || '.';
    const entry = entrySelect.value;
    const maxDepth = parseInt(depthInput.value) || 5;
    if (!entry) {
        alert('请先扫描并选择入口方法');
        return;
    }
    statInfo.textContent = '⏳ 构建调用链...';
    try {
        const params = new URLSearchParams({ rootDir, entry, maxDepth });
        const resp = await fetch(API + '/calltree?' + params);
        const data = await resp.json();
        if (data.error) {
            alert(data.error);
            return;
        }
        renderTree(data);
        renderMethodInfo(data);
        statInfo.textContent = '✅ 已加载';

        // 并发生成 PlantUML
        const seqResp = await fetch(API + '/sequence?' + params);
        const uml = await seqResp.text();
        umlText.textContent = uml;

        // 邻居
        const nbResp = await fetch(API + '/neighbors?rootDir='
            + encodeURIComponent(rootDir) + '&methodId=' + encodeURIComponent(entry));
        const nb = await nbResp.json();
        renderNeighbors(nb);
    } catch (e) {
        statInfo.textContent = '❌ ' + e.message;
    }
}

function renderMethodInfo(node) {
    methodInfo.innerHTML = `
        <div>ID: <code>${escapeHtml(node.id)}</code></div>
        <div>类: ${escapeHtml(node.className || '-')}</div>
        <div>方法: ${escapeHtml(node.methodName || '-')}</div>
        <div>行号: ${node.lineNumber || '-'}</div>
        ${node.cycleCut ? '<div style="color:#ef4444;">⚠️ 循环调用已截断</div>' : ''}
        ${node.depthCut ? '<div style="color:#f59e0b;">⚠️ 深度超限已截断</div>' : ''}
    `;
}

function renderNeighbors(nb) {
    if (!nb || nb.error) {
        neighbors.textContent = '';
        return;
    }
    let html = '';
    html += `<div class="neighbor-section">
        <div class="neighbor-section-title">📤 调用 (${nb.callees.length})</div>
        ${nb.callees.map(c =>
        `<div class="neighbor-item" data-id="${escapeAttr(c.id)}">${escapeHtml(c.displayName)}</div>`
    ).join('') || '<div style="color:#94a3b8;">无</div>'}
    </div>`;
    html += `<div class="neighbor-section">
        <div class="neighbor-section-title">📥 被调用 (${nb.callers.length})</div>
        ${nb.callers.map(c =>
        `<div class="neighbor-item" data-id="${escapeAttr(c.id)}">${escapeHtml(c.displayName)}</div>`
    ).join('') || '<div style="color:#94a3b8;">无</div>'}
    </div>`;
    neighbors.innerHTML = html;

    // 点击邻居 → 作为新入口
    neighbors.querySelectorAll('.neighbor-item').forEach(el => {
        el.addEventListener('click', () => {
            const id = el.dataset.id;
            entrySelect.innerHTML = `<option value="${escapeAttr(id)}">${escapeHtml(el.textContent)}</option>`;
            loadCallChain();
        });
    });
}

// ============ 辅助 ============
function escapeHtml(s) {
    if (s == null) return '';
    return String(s).replace(/[&<>"]/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
    }[m]));
}
function escapeAttr(s) { return escapeHtml(s); }

// ============ 事件绑定 ============
scanBtn.addEventListener('click', scanProject);
loadBtn.addEventListener('click', loadCallChain);
reloadBtn.addEventListener('click', async () => {
    await fetch(API + '/clearCache');
    await scanProject();
});
copyUml.addEventListener('click', () => {
    navigator.clipboard.writeText(umlText.textContent).then(() => {
        copyUml.textContent = '✅ 已复制';
        setTimeout(() => copyUml.textContent = '📋 复制', 1500);
    });
});
previewUml.addEventListener('click', () => {
    const text = umlText.textContent;
    if (!text || text.length < 20) {
        alert('无内容');
        return;
    }
    const url = 'https://www.plantuml.com/plantuml/svg/' + plantUmlEncode(text);
    window.open(url, '_blank');
});

// PlantUML hex 编码（官方支持 ~h 前缀 + hex）
function plantUmlEncode(text) {
    let hex = '';
    const bytes = new TextEncoder().encode(text);
    for (const b of bytes) hex += b.toString(16).padStart(2, '0');
    return '~h' + hex;
}

// ============ 启动 ============
initScene();
(function animate() {
    requestAnimationFrame(animate);
    controls.update();
    renderer.render(scene, camera);
    labelRenderer.render(scene, camera);
})();

/**
 * ★ 新增：加载默认配置
 * 优先级：URL 参数 > 后端配置 > 代码默认
 */
async function loadDefaultConfig() {
    // 1. URL 参数优先
    const urlParams = new URLSearchParams(window.location.search);
    const urlRoot = urlParams.get('rootDir');
    if (urlRoot) {
        rootDirInput.value = urlRoot;
        if (urlParams.get('maxDepth')) {
            depthInput.value = urlParams.get('maxDepth');
        }
        await scanProject();
        return;
    }

    // 2. 后端配置
    try {
        const resp = await fetch(API + '/config');
        const cfg = await resp.json();
        if (cfg.defaultRootDir) {
            rootDirInput.value = cfg.defaultRootDir;
        }
        if (cfg.defaultMaxDepth) {
            depthInput.value = cfg.defaultMaxDepth;
        }
        if (cfg.excludePaths && cfg.excludePaths.length) {
            const hint = '默认排除: ' + cfg.excludePaths.join(', ')
                + (cfg.excludeTest ? ' (含 test)' : '');
            rootDirInput.title = hint;
            statInfo.title = hint;
        }
        // 自动扫描一次
        if (cfg.defaultRootDir) {
            await scanProject();
        }
    } catch (e) {
        console.warn('加载默认配置失败', e);
        rootDirInput.value = '.';
        rootDirInput.placeholder = '如: ./src/main/java 或 D:/work/git/code-stats/src/main/java';
    }
}
loadDefaultConfig();