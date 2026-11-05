// dbclient-view-transform.js - 生成转换节点与箭头连线
const ViewTransform = (function() {
    let core = null;
    let svg = null;
    let tables = null;
    let transformNodes = null;
    let renderTableNodeFn = null;
    let renderTransformNodeFn = null;
    let refreshCallback = null;

    function initTransform(coreInstance, renderTable, renderTransform, onRefresh) {
        core = coreInstance;
        svg = core.getSvg();
        tables = core.getTables();
        transformNodes = core.getTransformNodes();
        renderTableNodeFn = renderTable;
        renderTransformNodeFn = renderTransform;
        refreshCallback = onRefresh;
    }

    async function generateTransformNodes() {
        const active = core.getActiveTableForTransform();
        if (!active) {
            alert('请先在画布上选中一张表');
            return;
        }
        const db = active.db;
        const table = active.table;
        const columns = active.columns;
        const rules = ViewModal.getCurrentRules();
        if (!rules || rules.length === 0) {
            alert('请先添加转换规则');
            return;
        }

        // 若表节点不存在，先添加
        if (!tables.has(table)) {
            try {
                const cols = await DB.fetchJSON(DB.apiBase + `/columns?db=${encodeURIComponent(db)}&table=${encodeURIComponent(table)}`);
                const id = core.getNextTableId();
                tables.set(table, {
                    id: id,
                    name: table,
                    dbName: db,
                    x: 100,
                    y: 100,
                    fields: cols.map(c => ({ columnName: c.columnName, typeName: c.typeName })),
                    nodeGroup: null
                });
            } catch (err) {
                console.error('添加表失败', err);
                return;
            }
        }
        const sourceTable = tables.get(table);
        if (!sourceTable) return;

        // 清除该表的旧转换节点
        for (let [id, node] of Array.from(transformNodes.entries())) {
            if (node.sourceTable === table) {
                transformNodes.delete(id);
            }
        }

        const tId = core.getNextTransformId();
        const tNode = {
            id: tId,
            name: table + '_转换',
            x: sourceTable.x + 280,
            y: sourceTable.y + 50,
            sourceTable: table,
            sourceField: rules.map(r => r.sourceField).join(','),
            expression: rules.map(r => r.expression).join('; '),
            alias: null,
            rules: rules
        };
        transformNodes.set(tId, tNode);

        if (refreshCallback) refreshCallback();
        if (window.ViewSql) window.ViewSql.updateSql();
    }

    function drawEdge(sourceTableName, targetTransformId) {
        const sourceTable = tables.get(sourceTableName);
        const targetNode = transformNodes.get(targetTransformId);
        if (!sourceTable || !targetNode || !svg) return null;

        const sourceGroup = sourceTable.nodeGroup;
        const targetGroup = targetNode.nodeGroup;
        if (!sourceGroup || !targetGroup) return null;

        const sourceRect = sourceGroup.getBBox();
        const targetRect = targetGroup.getBBox();
        const startX = sourceTable.x + sourceRect.width;
        const startY = sourceTable.y + sourceRect.height / 2;
        const endX = targetNode.x;
        const endY = targetNode.y + targetRect.height / 2;
        const pathData = `M ${startX} ${startY} C ${startX + 50} ${startY}, ${endX - 50} ${endY}, ${endX} ${endY}`;

        const line = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        line.setAttribute('d', pathData);
        line.setAttribute('class', 'edge');
        line.setAttribute('marker-end', 'url(#arrowhead)');
        svg.appendChild(line);

        const label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        label.setAttribute('class', 'edge-label');
        label.setAttribute('x', (startX + endX) / 2);
        label.setAttribute('y', (startY + endY) / 2 - 5);
        label.textContent = '转换';
        svg.appendChild(label);

        return { line, label, sourceTable: sourceTableName, targetId: targetTransformId };
    }

    // 完整刷新后重绘所有转换节点边
    function redrawAllTransformEdges() {
        for (let [id, node] of Array.from(transformNodes.entries())) {
            if (node.edge) {
                if (node.edge.line && node.edge.line.parentNode) node.edge.line.remove();
                if (node.edge.label && node.edge.label.parentNode) node.edge.label.remove();
                node.edge = null;
            }
        }
        for (let [id, node] of transformNodes.entries()) {
            node.edge = drawEdge(node.sourceTable, node.id);
        }
    }

    function updateEdgesForTable(tableName) {
        for (let [id, node] of transformNodes.entries()) {
            if (node.sourceTable === tableName && node.edge) {
                if (node.edge.line && node.edge.line.parentNode) node.edge.line.remove();
                if (node.edge.label && node.edge.label.parentNode) node.edge.label.remove();
                node.edge = drawEdge(tableName, id);
            }
        }
    }

    function updateSingleEdge(transformId) {
        const node = transformNodes.get(transformId);
        if (node && node.edge) {
            if (node.edge.line && node.edge.line.parentNode) node.edge.line.remove();
            if (node.edge.label && node.edge.label.parentNode) node.edge.label.remove();
            node.edge = drawEdge(node.sourceTable, transformId);
        }
    }

    function getTransformRulesForTable(tableName) {
        const node = Array.from(transformNodes.values()).find(n => n.sourceTable === tableName);
        if (node && node.rules) return node.rules;
        return [];
    }

    return {
        initTransform,
        generateTransformNodes,
        updateEdgesForTable,
        updateSingleEdge,
        redrawAllTransformEdges,
        getTransformRulesForTable
    };
})();

// ★ 关键修复：挂载到 window，供 dbclient-view.js 中 window.ViewTransform 判断使用
window.ViewTransform = ViewTransform;