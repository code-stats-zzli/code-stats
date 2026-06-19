// ---------- API 基础路径 ----------
const API_BASE = (window.apiUrl ? window.apiUrl('/3d/api') : '/3d/api');

// ---------- DOM 引用 ----------
const container = document.getElementById('graph-container');
const loadingOverlay = document.getElementById('loading-overlay');
const loadingText = document.getElementById('loading-text');
const loadingError = document.getElementById('loading-error');
const nodeCountSpan = document.getElementById('node-count');
const edgeCountSpan = document.getElementById('edge-count');
const classSelector = document.getElementById('classSelector');
const refreshBtn = document.getElementById('refreshGraphBtn');

let forceGraph = null;

// ---------- 获取类名列表 ----------
async function fetchClassNames() {
    const params = new URLSearchParams();
    params.append('rootDir', '../../../');
    params.append('extensions', 'java');
    params.append('excludePaths', '');
    params.append('extraPaths', '');
    params.append('threadCount', '4');
    params.append('excludeComments', 'true');

    const url = API_BASE + '/classes?' + params.toString();
    try {
        const resp = await fetch(url);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const data = await resp.json();
        return data;
    } catch (err) {
        console.warn('获取类名列表失败，使用模拟列表', err);
        // 降级：使用硬编码列表（仅用于演示）
        return [
            'com.omni.Bootstrap',
            'com.omni.framework.spring.boot.SpringApplication',
            'com.omni.framework.tomcat.Catalina',
            'com.omni.framework.spring.mvc.DispatcherServlet'
        ];
    }
}

// ---------- 填充下拉框 ----------
async function populateClassSelector() {
    const classes = await fetchClassNames();
    classSelector.innerHTML = '<option value="">-- 全部 --</option>';
    for (const cls of classes) {
        const option = document.createElement('option');
        option.value = cls;
        option.textContent = cls;
        classSelector.appendChild(option);
    }
}

// ---------- 获取依赖数据 ----------
async function fetchDependencyData(className = '') {
    const params = new URLSearchParams();
    params.append('rootDir', '../../../');
    params.append('extensions', 'java');
    params.append('excludePaths', '');
    params.append('extraPaths', '');
    params.append('threadCount', '4');
    params.append('excludeComments', 'true');
    if (className) {
        params.append('className', className);
    }

    const url = API_BASE + '/graph?' + params.toString();
    console.log('请求 URL:', url);
    try {
        const resp = await fetch(url);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const data = await resp.json();
        if (data.error) throw new Error(data.error);
        return data;
    } catch (err) {
        console.warn('后端数据获取失败，使用模拟数据', err);
        return generateMockData(className);
    }
}

// ---------- 模拟数据（可保留，用于降级） ----------
function generateMockData(className = '') {
    // 保持原有模拟逻辑，但可以根据 className 过滤（这里简单返回全部）
    const classes = [
        'com.omni.Bootstrap',
        'com.omni.framework.spring.boot.SpringApplication',
        'com.omni.framework.spring.context.support.AnnotationConfigApplicationContext',
        'com.omni.framework.tomcat.Catalina',
        'com.omni.framework.tomcat.connector.Connector',
        'com.omni.framework.spring.mvc.DispatcherServlet',
        'com.omni.framework.jdbc.JdbcTemplate',
        'com.omni.framework.pool.SimpleDataSource',
        'com.omni.business.assistance.agent.controller.AgentController',
        'com.omni.business.assistance.agent.service.AIChatService',
        'com.omni.business.assistance.codestat.controller.CodeAnalysisController',
        'com.omni.business.assistance.codestat.service.CodeAnalysisService'
    ];
    const edges = [
        ['com.omni.Bootstrap', 'com.omni.framework.spring.boot.SpringApplication'],
        ['com.omni.framework.spring.boot.SpringApplication', 'com.omni.framework.spring.context.support.AnnotationConfigApplicationContext'],
        ['com.omni.framework.spring.boot.SpringApplication', 'com.omni.framework.tomcat.Catalina'],
        ['com.omni.framework.tomcat.Catalina', 'com.omni.framework.tomcat.connector.Connector'],
        ['com.omni.framework.tomcat.Catalina', 'com.omni.framework.spring.mvc.DispatcherServlet'],
        ['com.omni.framework.spring.mvc.DispatcherServlet', 'com.omni.framework.jdbc.JdbcTemplate'],
        ['com.omni.framework.jdbc.JdbcTemplate', 'com.omni.framework.pool.SimpleDataSource'],
        ['com.omni.business.assistance.agent.controller.AgentController', 'com.omni.business.assistance.agent.service.AIChatService'],
        ['com.omni.business.assistance.codestat.controller.CodeAnalysisController', 'com.omni.business.assistance.codestat.service.CodeAnalysisService'],
        ['com.omni.framework.spring.boot.SpringApplication', 'com.omni.business.assistance.agent.controller.AgentController'],
        ['com.omni.framework.spring.boot.SpringApplication', 'com.omni.business.assistance.codestat.controller.CodeAnalysisController']
    ];
    // 如果指定了 className，过滤相关的边（简单实现，仅演示）
    let filteredEdges = edges;
    if (className) {
        filteredEdges = edges.filter(([src, tgt]) => src === className || tgt === className);
    }
    const nodeSet = new Set();
    filteredEdges.forEach(([src, tgt]) => { nodeSet.add(src); nodeSet.add(tgt); });
    const nodes = Array.from(nodeSet).map(id => ({ id, type: 'class' }));
    return { nodes, links: filteredEdges.map(([src, tgt]) => ({ source: src, target: tgt })) };
}

// ---------- 数据转换 ----------
function transformData(raw) {
    if (raw.nodes && raw.links) {
        const nodes = raw.nodes.map(n => ({ id: String(n.id), type: n.type || 'class' }));
        const links = raw.links.map(l => ({
            source: String(l.source),
            target: String(l.target)
        }));
        return { nodes, links };
    }
    // 兼容其他结构
    if (raw.classInfoMap && raw.graph) {
        const nodes = Object.keys(raw.classInfoMap).map(id => {
            const info = raw.classInfoMap[id];
            let type = 'class';
            if (info && info.type) {
                if (info.type === 'INTERFACE') type = 'interface';
                else if (info.type === 'ENUM') type = 'enum';
            }
            return { id, type };
        });
        const links = [];
        const adj = raw.graph.adjacencyMap || {};
        for (const [src, deps] of Object.entries(adj)) {
            if (Array.isArray(deps)) {
                deps.forEach(tgt => links.push({ source: src, target: tgt }));
            }
        }
        return { nodes, links };
    }
    console.warn('未知数据结构，使用模拟数据');
    return generateMockData();
}

// ---------- 渲染 3D 图 ----------
function renderGraph(data) {
    if (forceGraph) {
        try { forceGraph._destructor(); } catch (e) { /* ignore */ }
        forceGraph = null;
    }

    const rect = container.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
        container.style.width = '100vw';
        container.style.height = '100vh';
        if (container.getBoundingClientRect().width === 0) {
            container.style.width = window.innerWidth + 'px';
            container.style.height = window.innerHeight + 'px';
        }
    }

    if (typeof window.ForceGraph3D !== 'function') {
        console.error('ForceGraph3D 未定义');
        loadingText.textContent = '3D 库加载失败，请刷新重试';
        loadingError.style.display = 'block';
        loadingError.textContent = '请检查网络或刷新页面';
        return;
    }

    try {
        const Graph = window.ForceGraph3D;
        forceGraph = Graph()(container);
    } catch (e) {
        console.error('初始化 3D 图失败', e);
        loadingText.textContent = '初始化 3D 图失败，请刷新重试';
        loadingError.style.display = 'block';
        loadingError.textContent = e.message || '未知错误';
        return;
    }

    forceGraph
        .graphData(data)
        .nodeLabel('id')
        .nodeAutoColorBy('type')
        .nodeVal(6)
        .linkWidth(1.2)
        .linkDirectionalParticles(1)
        .linkDirectionalParticleSpeed(0.01)
        .backgroundColor(0x0a0f1a)
        .onNodeClick((node) => {
            if (!node) return;
            const gd = forceGraph.graphData();
            gd.nodes.forEach(n => n.color = undefined);
            const target = gd.nodes.find(n => n.id === node.id);
            if (target) target.color = '#ff6b6b';
            forceGraph.graphData(gd);
        });

    const cameraDist = Math.max(30, Math.log10(data.nodes.length) * 15);
    forceGraph.cameraPosition({ x: cameraDist, y: cameraDist * 0.8, z: cameraDist });

    nodeCountSpan.textContent = data.nodes.length;
    edgeCountSpan.textContent = data.links.length;

    loadingOverlay.classList.add('hidden');
    setTimeout(() => { loadingOverlay.style.display = 'none'; }, 500);

    console.log(`✅ 3D 图渲染成功，节点数: ${data.nodes.length}, 边数: ${data.links.length}`);
}

// ---------- 主加载函数 ----------
async function loadGraph(className = '') {
    loadingOverlay.style.display = 'flex';
    loadingOverlay.classList.remove('hidden');
    loadingText.textContent = '正在获取依赖数据...';
    loadingError.style.display = 'none';

    try {
        const raw = await fetchDependencyData(className);
        const graphData = transformData(raw);
        renderGraph(graphData);
    } catch (err) {
        console.error('加载失败', err);
        loadingText.textContent = '加载失败，请刷新重试';
        loadingError.style.display = 'block';
        loadingError.textContent = err.message || '未知错误';
    }
}

// ---------- 初始化 ----------
async function init() {
    await populateClassSelector();
    // 默认加载全部
    await loadGraph('');

    // 绑定刷新按钮
    refreshBtn.addEventListener('click', () => {
        const selected = classSelector.value;
        loadGraph(selected);
    });

    // 也可以监听选择变化自动刷新（可选）
    classSelector.addEventListener('change', () => {
        // 可以取消注释实现自动刷新
        // const selected = classSelector.value;
        // loadGraph(selected);
    });
}

// 页面加载完成后初始化
if (document.readyState === 'complete') {
    init();
} else {
    window.addEventListener('load', init);
}

// 暴露重新加载函数
window.reload3DGraph = loadGraph;