// dbclient-view-core.js - 核心共享状态与基础工具
const ViewCore = (function() {
    let svg = null;
    let tables = new Map();
    let transformNodes = new Map();
    let joinNodes = new Map();
    let currentTransformConfig = new Map();
    let activeTableForTransform = null;
    let nextTableId = 1;
    let nextTransformId = 1;
    let nextJoinId = 1;

    // 选中状态
    let selectedNode = null;               // { type: 'table'|'join', id }
    let multiSelected = new Set();         // Set<string>，如 'table:t1' / 'join:1'

    let diagramContainer = null;
    let arrowDef = null;

    function initCore(svgElementId) {
        svg = document.getElementById(svgElementId);
        if (!svg) return false;

        const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');

        // 蓝色箭头
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

        // 橙色箭头（Join 边）
        const markerOrange = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
        markerOrange.setAttribute('id', 'arrowhead-orange');
        markerOrange.setAttribute('markerWidth', '10');
        markerOrange.setAttribute('markerHeight', '7');
        markerOrange.setAttribute('refX', '9');
        markerOrange.setAttribute('refY', '3.5');
        markerOrange.setAttribute('orient', 'auto');
        const polygonOrange = document.createElementNS('http://www.w3.org/2000/svg', 'polygon');
        polygonOrange.setAttribute('points', '0 0, 10 3.5, 0 7');
        polygonOrange.setAttribute('fill', '#f59e0b');
        markerOrange.appendChild(polygonOrange);
        defs.appendChild(markerOrange);

        svg.appendChild(defs);
        arrowDef = defs;
        return true;
    }

    function getSvg() { return svg; }
    function getTables() { return tables; }
    function getTransformNodes() { return transformNodes; }
    function getJoinNodes() { return joinNodes; }
    function getCurrentTransformConfig() { return currentTransformConfig; }
    function getActiveTableForTransform() { return activeTableForTransform; }
    function setActiveTableForTransform(val) { activeTableForTransform = val; }
    function getNextTableId() { return nextTableId++; }
    function getNextTransformId() { return nextTransformId++; }
    function getNextJoinId() { return nextJoinId++; }
    function getArrowDef() { return arrowDef; }

    // 选中状态
    function getSelectedNode() { return selectedNode; }
    function setSelectedNode(type, id) {
        selectedNode = type ? { type: type, id: id } : null;
    }
    function getMultiSelected() { return multiSelected; }
    function toggleMultiSelect(type, id) {
        const key = type + ':' + id;
        if (multiSelected.has(key)) multiSelected.delete(key);
        else multiSelected.add(key);
    }
    function clearMultiSelect() { multiSelected.clear(); }

    // ★ 新增：按名称统一查找表或 join 节点
    // 返回 { type, name, dbName, x, y, fields, node } 或 null
    function findNodeByName(name) {
        if (!name) return null;
        const table = tables.get(name);
        if (table) {
            return {
                type: 'table',
                name: table.name,
                dbName: table.dbName,
                x: table.x,
                y: table.y,
                fields: table.fields || [],
                node: table
            };
        }
        for (let j of joinNodes.values()) {
            if (j.name === name) {
                return {
                    type: 'join',
                    name: j.name,
                    dbName: j.dbName,
                    x: j.x,
                    y: j.y,
                    fields: j.fields || [],
                    node: j
                };
            }
        }
        return null;
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    // 基础刷新：只画表节点 + 转换节点（Join 由 View 层补充）
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
        if (placeholder) {
            placeholder.style.display = (tables.size === 0 && joinNodes.size === 0) ? 'block' : 'none';
        }
    }

    return {
        initCore, getSvg, getTables, getTransformNodes, getJoinNodes,
        getCurrentTransformConfig, getActiveTableForTransform, setActiveTableForTransform,
        getNextTableId, getNextTransformId, getNextJoinId, getArrowDef,
        getSelectedNode, setSelectedNode, getMultiSelected, toggleMultiSelect, clearMultiSelect,
        findNodeByName,
        escapeHtml, refreshAll
    };
})();