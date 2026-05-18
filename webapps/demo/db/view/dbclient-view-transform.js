// dbclient-view-transform.js - 生成转换节点与箭头连线
const ViewTransform = (function() {
    let core = null;
    let svg = null;
    let tables = null;
    let transformNodes = null;
    let renderTableNodeFn = null;
    let renderTransformNodeFn = null;

    function initTransform(coreInstance, renderTable, renderTransform, onRefresh) {
        core = coreInstance;
        svg = core.getSvg();
        tables = core.getTables();
        transformNodes = core.getTransformNodes();
        renderTableNodeFn = renderTable;
        renderTransformNodeFn = renderTransform;
        refreshCallback = onRefresh;
    }

    // 生成转换节点（基于当前表的规则）
    async function generateTransformNodes() {
        const active = core.getActiveTableForTransform();
        if (!active) {
            alert('请先在左侧选择一张表');
            return;
        }
        const { db, table, columns } = active;
        const rules = ViewModal.getCurrentRules();
        if (rules.length === 0) {
            alert('请先添加转换规则');
            return;
        }
        // 如果表节点不存在，先添加
        if (!tables.has(table)) {
            // 需要调用拖拽模块的添加方法，但为了避免循环依赖，直接操作 tables Map 并刷新
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
                core.refreshAll(renderTableNodeFn, renderTransformNodeFn);
            } catch (err) {
                console.error('添加表失败', err);
                return;
            }
        }
        const sourceTable = tables.get(table);
        if (!sourceTable) return;

        // 为每条规则创建转换节点（整体一个节点，名称统一为"原表名_转换"）
        // 但规则可能有多条，实际上我们生成一个整体转换节点？根据需求“转换是一个整体视图名叫前面视图_转换”，
        // 意味着只需要一个转换节点代表所有转换。这里我们改为只生成一个转换节点，包含所有规则。
        // 先清除该表的旧转换节点
        for (let [id, node] of transformNodes.entries()) {
            if (node.sourceTable === table) {
                transformNodes.delete(id);
            }
        }
        const tId = core.getNextTransformId();
        const tNode = {
            id: tId,
            name: `${table}_转换`,
            x: sourceTable.x + 280,
            y: sourceTable.y + 50,
            sourceTable: table,
            sourceField: rules.map(r => r.sourceField).join(','),
            expression: rules.map(r => r.expression).join('; '),
            alias: null,
            rules: rules  // 保存完整规则供 SQL 生成
        };
        transformNodes.set(tId, tNode);
        // 绘制从源表到转换节点的箭头（统一一条箭头）
        drawEdge(table, tId);
        core.refreshAll(renderTableNodeFn, renderTransformNodeFn);
        if (window.ViewSql && window.ViewSql.updateSql) window.ViewSql.updateSql();
    }

    function drawEdge(sourceTableName, targetTransformId) {
        const sourceTable = tables.get(sourceTableName);
        const targetNode = transformNodes.get(targetTransformId);
        if (!sourceTable || !targetNode) return null;
        const sourceGroup = sourceTable.nodeGroup;
        const targetGroup = targetNode.nodeGroup;
        if (!sourceGroup || !targetGroup) return;
        const sourceRect = sourceGroup.getBBox();
        const targetRect = targetGroup.getBBox();
        const startX = sourceTable.x + sourceRect.width;
        const startY = sourceTable.y + sourceRect.height/2;
        const endX = targetNode.x;
        const endY = targetNode.y + targetRect.height/2;
        const pathData = `M ${startX} ${startY} C ${startX + 50} ${startY}, ${endX - 50} ${endY}, ${endX} ${endY}`;
        const line = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        line.setAttribute('d', pathData);
        line.setAttribute('class', 'edge');
        line.setAttribute('marker-end', 'url(#arrowhead)');
        svg.appendChild(line);
        const label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        label.setAttribute('class', 'edge-label');
        label.setAttribute('x', (startX + endX)/2);
        label.setAttribute('y', (startY + endY)/2 - 5);
        label.textContent = '转换';
        svg.appendChild(label);
        const edge = { line, label, sourceTable: sourceTableName, targetId: targetTransformId };
        targetNode.edge = edge;
        return edge;
    }

    function updateEdgesForTable(tableName) {
        for (let [id, node] of transformNodes.entries()) {
            if (node.sourceTable === tableName && node.edge) {
                node.edge.line.remove();
                node.edge.label.remove();
                node.edge = drawEdge(tableName, id);
            }
        }
    }

    function updateSingleEdge(transformId) {
        const node = transformNodes.get(transformId);
        if (node && node.edge) {
            node.edge.line.remove();
            node.edge.label.remove();
            node.edge = drawEdge(node.sourceTable, transformId);
        }
    }

    // 获取转换规则（供 SQL 生成使用）
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
        getTransformRulesForTable
    };
})();