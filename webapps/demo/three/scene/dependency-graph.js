// ---------- API 基础路径 ----------
// 使用新的 3D 专用接口
const API_BASE = window.apiUrl('/3d/api');

// ---------- DOM 引用 ----------
const container = document.getElementById('graph-container');
const loadingOverlay = document.getElementById('loading-overlay');
const loadingText = document.getElementById('loading-text');
const loadingError = document.getElementById('loading-error');
const nodeCountSpan = document.getElementById('node-count');
const edgeCountSpan = document.getElementById('edge-count');

// ---------- 核心变量 ----------
let forceGraph = null;

// ---------- 获取数据（调用新接口 /3d/api/graph） ----------
async function fetchDependencyData() {
    const params = new URLSearchParams();
    // 使用相对根目录（从 /three/scene/ 到项目根）
    const rootDir = '../../../'; // 相对路径到项目根
    params.append('rootDir', rootDir);
    params.append('extensions', 'java');
    params.append('excludePaths', '');
    params.append('extraPaths', '');
    params.append('threadCount', '4');
    params.append('excludeComments', 'true');

    try {
        const resp = await fetch(API_BASE + '/graph?' + params.toString());
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const data = await resp.json();
        if (data.error) throw new Error(data.error);
        return data; // 直接返回 { nodes, links, ... }
    } catch (err) {
        console.warn('后端数据获取失败，使用模拟数据', err);
        return generateMockData();
    }
}

// ---------- 模拟数据（确保始终有图可看） ----------
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
    // 构建节点集
    const nodeSet = new Set();
    edges.forEach(([src, tgt]) => { nodeSet.add(src); nodeSet.add(tgt); });
    const nodes = Array.from(nodeSet).map(id => ({ id, type: 'class' }));
    return {
        nodes,
        links: edges.map(([src, tgt]) => ({ source: src, target: tgt })),
        totalClasses: nodes.length,
        totalEdges: edges.length
    };
}

// ---------- 数据转换（新接口已返回标准格式，直接使用） ----------
function transformData(raw) {
    // 如果 raw 包含 nodes 和 links，直接返回；否则尝试兼容旧格式
    if (raw.nodes && raw.links) {
        return { nodes: raw.nodes, links: raw.links };
    }
    // 降级：尝试从 classInfoMap 和 graph 构造（兼容旧后端）
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
    // 最终降级：使用模拟数据
    return generateMockData();
}

// ---------- 渲染 3D 图 ----------
function renderGraph(data) {
    // 如果已经存在实例，先销毁
    if (forceGraph) {
        forceGraph._destructor();
        forceGraph = null;
    }

    // 检查 ForceGraph3D 是否可用
    if (typeof ForceGraph3D !== 'function') {
        console.error('ForceGraph3D 未定义，请检查 CDN 是否加载成功');
        loadingText.textContent = '3D 库加载失败，请刷新重试';
        return;
    }

    // ★★★ 修复：使用标准初始化方式 ★★★
    try {
        forceGraph = ForceGraph3D()(container);
    } catch (e) {
        console.error('初始化 3D 图失败', e);
        loadingText.textContent = '初始化 3D 图失败，请刷新重试';
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
        .onNodeClick((node, event) => {
            if (!node) return;
            const graphData = forceGraph.graphData();
            const nodes = graphData.nodes;
            nodes.forEach(n => { n.color = undefined; });
            const target = nodes.find(n => n.id === node.id);
            if (target) target.color = '#ff6b6b';
            forceGraph.graphData(graphData);
        });

    // 设置相机位置
    forceGraph.cameraPosition({ x: 25, y: 20, z: 25 });

    // 更新统计信息
    nodeCountSpan.textContent = data.nodes.length;
    edgeCountSpan.textContent = data.links.length;

    // 隐藏加载遮罩
    loadingOverlay.classList.add('hidden');
    setTimeout(() => { loadingOverlay.style.display = 'none'; }, 500);
}

// ---------- 启动 ----------
async function init() {
    try {
        loadingText.textContent = '正在获取依赖数据...';
        const raw = await fetchDependencyData();
        const graphData = transformData(raw);
        renderGraph(graphData);
    } catch (err) {
        console.error('初始化失败', err);
        loadingText.textContent = '加载失败，请刷新重试';
        loadingError.style.display = 'block';
        loadingError.textContent = err.message || '未知错误';
        // 尝试使用模拟数据
        try {
            const mock = generateMockData();
            renderGraph(mock);
        } catch (e) {
            loadingError.textContent += ' (模拟数据也失败)';
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