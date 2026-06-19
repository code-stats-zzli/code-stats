// ---------- API 基础路径 ----------
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

// ---------- 获取数据 ----------
async function fetchDependencyData() {
    const params = new URLSearchParams();
    const rootDir = '../../../';
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
        return data;
    } catch (err) {
        console.warn('后端数据获取失败，使用模拟数据', err);
        return generateMockData();
    }
}

// ---------- 模拟数据 ----------
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
    return {
        nodes,
        links: edges.map(([src, tgt]) => ({ source: src, target: tgt })),
        totalClasses: nodes.length,
        totalEdges: edges.length
    };
}

// ---------- 数据转换 ----------
function transformData(raw) {
    if (raw.nodes && raw.links) {
        return { nodes: raw.nodes, links: raw.links };
    }
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
    return generateMockData();
}

// ---------- 渲染 3D 图 ----------
function renderGraph(data) {
    if (forceGraph) {
        forceGraph._destructor();
        forceGraph = null;
    }

    if (typeof ForceGraph3D !== 'function') {
        console.error('ForceGraph3D 未定义');
        loadingText.textContent = '3D 库加载失败，请刷新重试';
        return;
    }

    try {
        forceGraph = ForceGraph3D()(container);
    } catch (e) {
        console.error('初始化 3D 图失败', e);
        loadingText.textContent = '初始化 3D 图失败，请刷新重试';
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
        .onNodeClick((node, event) => {
            if (!node) return;
            const graphData = forceGraph.graphData();
            const nodes = graphData.nodes;
            nodes.forEach(n => { n.color = undefined; });
            const target = nodes.find(n => n.id === node.id);
            if (target) target.color = '#ff6b6b';
            forceGraph.graphData(graphData);
        });

    forceGraph.cameraPosition({ x: 25, y: 20, z: 25 });

    nodeCountSpan.textContent = data.nodes.length;
    edgeCountSpan.textContent = data.links.length;

    loadingOverlay.classList.add('hidden');
    setTimeout(() => { loadingOverlay.style.display = 'none'; }, 500);
}

// ---------- 侧边栏切换 ----------
function initSidebar() {
    const navItems = document.querySelectorAll('.nav-item');
    const panes = {
        dependency: document.getElementById('tab-dependency'),
        scene2: document.getElementById('tab-scene2'),
        scene3: document.getElementById('tab-scene3')
    };

    navItems.forEach(item => {
        item.addEventListener('click', () => {
            // 更新 active 状态
            navItems.forEach(n => n.classList.remove('active'));
            item.classList.add('active');

            // 切换 pane
            const tab = item.dataset.tab;
            Object.keys(panes).forEach(key => {
                const pane = panes[key];
                if (key === tab) {
                    pane.classList.add('active');
                } else {
                    pane.classList.remove('active');
                }
            });

            // 如果是依赖分析 tab，且 forceGraph 未初始化，则重新初始化（或 resize）
            if (tab === 'dependency' && forceGraph) {
                // 可以触发 resize 来适应容器
                setTimeout(() => {
                    if (forceGraph) forceGraph._destructor();
                    // 重新获取数据并渲染
                    init();
                }, 100);
            }
        });
    });
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
        try {
            const mock = generateMockData();
            renderGraph(mock);
        } catch (e) {
            loadingError.textContent += ' (模拟数据也失败)';
        }
    }
}

// ---------- 页面加载后执行 ----------
if (document.readyState === 'complete') {
    initSidebar();
    init();
} else {
    window.addEventListener('load', () => {
        initSidebar();
        init();
    });
}

// 暴露重新加载函数
window.reload3DGraph = init;