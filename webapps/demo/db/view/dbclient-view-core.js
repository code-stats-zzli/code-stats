// dbclient-view-core.js - 核心共享状态与基础工具
const ViewCore = (function() {
    // 共享状态
    let svg = null;
    let tables = new Map();          // 表节点 { id, name, dbName, x, y, fields, nodeGroup }
    let transformNodes = new Map();  // 转换节点 { id, name, x, y, sourceTable, sourceField, expression, alias, nodeGroup, edge }
    let currentTransformConfig = new Map(); // 表名 -> 转换规则列表 [{sourceField, expression, alias}]
    let activeTableForTransform = null;     // 当前正在配置转换的表 { db, table, columns }
    let nextTableId = 1;
    let nextTransformId = 1;

    // DOM 元素
    let diagramContainer = null;

    // 箭头定义（用于 SVG）
    let arrowDef = null;

    function initCore(svgElementId) {
        svg = document.getElementById(svgElementId);
        if (!svg) return false;
        // 添加箭头标记
        const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
        const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
        marker.setAttribute('id', 'arrowhead');
        marker.setAttribute('markerWidth', '10');
        marker.setAttribute('markerHeight', '7');
        marker.setAttribute('refX', '9');
        marker.setAttribute('refY', '3.5');
        marker.setAttribute('orient', 'auto');
        const polygon = document.createElementNS('http://www.w3.org/2000/svg', 'polygon');
        polygon.setAttribute('points', '0 0, 10 3.5, 0 7');
        polygon.setAttribute('fill', '#0066ff');
        marker.appendChild(polygon);
        defs.appendChild(marker);
        svg.appendChild(defs);
        arrowDef = defs;
        return true;
    }

    function getSvg() { return svg; }
    function getTables() { return tables; }
    function getTransformNodes() { return transformNodes; }
    function getCurrentTransformConfig() { return currentTransformConfig; }
    function getActiveTableForTransform() { return activeTableForTransform; }
    function setActiveTableForTransform(val) { activeTableForTransform = val; }
    function getNextTableId() { return nextTableId++; }
    function getNextTransformId() { return nextTransformId++; }
    function getArrowDef() { return arrowDef; }

    // 工具函数
    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    // 刷新整个 SVG 内容（由其他模块调用）
    function refreshAll(renderTableNodeFn, renderTransformNodeFn) {
        if (!svg) return;
        svg.innerHTML = '';
        if (arrowDef) svg.appendChild(arrowDef);
        for (let table of tables.values()) {
            svg.appendChild(renderTableNodeFn(table));
        }
        for (let tNode of transformNodes.values()) {
            svg.appendChild(renderTransformNodeFn(tNode));
        }
        const placeholder = document.getElementById('diagramPlaceholder');
        if (placeholder) placeholder.style.display = tables.size === 0 ? 'block' : 'none';
    }

    return {
        initCore,
        getSvg,
        getTables,
        getTransformNodes,
        getCurrentTransformConfig,
        getActiveTableForTransform,
        setActiveTableForTransform,
        getNextTableId,
        getNextTransformId,
        escapeHtml,
        refreshAll
    };
})();