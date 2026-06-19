// ---------- API 基础路径（可修改为实际后端地址） ----------
const API_BASE = '/3d/api';   // 若需要前缀可改为 '/api/3d' 等

// ---------- DOM 引用 ----------
const container = document.getElementById('graph-container');
const loadingOverlay = document.getElementById('loading-overlay');
const loadingText = document.getElementById('loading-text');
const loadingError = document.getElementById('loading-error');
const nodeCountSpan = document.getElementById('node-count');
const edgeCountSpan = document.getElementById('edge-count');

// ---------- 核心变量 ----------
let forceGraph = null;

// ---------- 获取数据 ----------
async function fetchDependencyData() {
    const params = new URLSearchParams();
    // 根据实际项目根目录调整 rootDir
    params.append('rootDir', '../../../');
    params.append('extensions', 'java');
    params.append('excludePaths', '');
    params.append('extraPaths', '');
    params.append('threadCount', '4');
    params.append('excludeComments', 'true');

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
        return generateMockData();
    }
}

// ---------- 模拟数据（丰富结构，方便测试） ----------
function generateMockData() {
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
    const nodeSet = new Set();
    edges.forEach(([src, tgt]) => { nodeSet.add(src); nodeSet.add(tgt); });
    const nodes = Array.from(nodeSet).map(id => ({ id, type: 'class' }));
    return { nodes, links: edges.map(([src, tgt]) => ({ source: src, target: tgt })) };
}

// ---------- 数据转换 ----------
function transformData(raw) {
    console.log('原始数据 keys:', Object.keys(raw));
    // 标准格式
    if (raw.nodes && raw.links) {
        const nodes = raw.nodes.map(n => ({ id: String(n.id), type: n.type || 'class' }));
        const links = raw.links.map(l => ({
            source: String(l.source),
            target: String(l.target)
        }));
        return { nodes, links };
    }
    // 兼容旧结构（classInfoMap + graph）
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

    // 确保容器有尺寸
    const rect = container.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
        container.style.width = '100vw';
        container.style.height = '100vh';
        // 再次检查
        if (container.getBoundingClientRect().width === 0) {
            container.style.width = window.innerWidth + 'px';
            container.style.height = window.innerHeight + 'px';
        }
    }

    // 检查全局 ForceGraph3D
    if (typeof window.ForceGraph3D !== 'function') {
        console.error('ForceGraph3D 未定义，请检查 CDN 是否加载成功');
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

    // 配置图形
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

// ---------- 启动 ----------
async function init() {
    try {
        loadingText.textContent = '正在获取依赖数据...';
        const raw = await fetchDependencyData();
        console.log('📦 原始数据:', { nodes: raw.nodes?.length, links: raw.links?.length });
        const graphData = transformData(raw);
        renderGraph(graphData);
    } catch (err) {
        console.error('初始化失败', err);
        loadingText.textContent = '加载失败，请刷新重试';
        loadingError.style.display = 'block';
        loadingError.textContent = err.message || '未知错误';
        // 降级使用模拟数据
        try {
            const mock = generateMockData();
            renderGraph(mock);
        } catch (e) {
            loadingError.textContent += ' (模拟数据也失败)';
            console.error('模拟数据渲染失败', e);
        }
    }
}

// 页面加载完成时初始化
if (document.readyState === 'complete') {
    init();
} else {
    window.addEventListener('load', init);
}

// 暴露重新加载函数（便于调试）
window.reload3DGraph = init;