// dbclient-view.js - 主入口：渲染表节点/转换节点/Join节点，处理右键、选中、完整刷新
(function() {
    // ============ 类型图标映射 ============
    function getTypeIcon(typeName) {
        if (!typeName) return '⚪';
        const t = String(typeName).toUpperCase();
        if (/\b(INT|BIGINT|SMALLINT|TINYINT|MEDIUMINT|INTEGER|SERIAL|BIGSERIAL|NUMBER)\b/.test(t)) return '🔢';
        if (/\b(DECIMAL|NUMERIC|FLOAT|DOUBLE|REAL|MONEY)\b/.test(t)) return '💰';
        if (/\b(VARCHAR|CHAR|TEXT|LONGTEXT|MEDIUMTEXT|TINYTEXT|CLOB|STRING)\b/.test(t)) return '📝';
        if (/\b(DATE|TIME|DATETIME|TIMESTAMP|YEAR)\b/.test(t)) return '📅';
        if (/\b(BOOL|BOOLEAN|BIT)\b/.test(t)) return '✅';
        if (/\b(BLOB|BINARY|VARBINARY|IMAGE|LONGBLOB)\b/.test(t)) return '🗄️';
        if (/\b(JSON)\b/.test(t)) return '{}';
        if (/\b(ENUM)\b/.test(t)) return '🏷️';
        if (/\b(UUID)\b/.test(t)) return '🔑';
        return '⚪';
    }

    // ============ 渲染表节点 ============
    function renderTableNode(table) {
        const group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        group.setAttribute('class', 'table-node-group');
        group.setAttribute('data-table-id', table.id);
        group.setAttribute('data-table-name', table.name);
        group.setAttribute('transform', `translate(${table.x}, ${table.y})`);

        const width = 220, headerHeight = 34, rowHeight = 26;
        let yOffset = 0;

        const headerRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        headerRect.setAttribute('class', 'header-rect');
        headerRect.setAttribute('width', width);
        headerRect.setAttribute('height', headerHeight);
        headerRect.setAttribute('x', 0);
        headerRect.setAttribute('y', 0);
        group.appendChild(headerRect);

        const titleText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        titleText.setAttribute('class', 'header-text');
        titleText.setAttribute('x', width / 2);
        titleText.setAttribute('y', headerHeight / 2);
        titleText.textContent = table.name;
        group.appendChild(titleText);
        yOffset += headerHeight;

        table.fields.forEach(field => {
            const fieldRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
            fieldRect.setAttribute('width', width - 16);
            fieldRect.setAttribute('height', rowHeight - 2);
            fieldRect.setAttribute('x', 8);
            fieldRect.setAttribute('y', yOffset);
            fieldRect.setAttribute('class', 'field-rect');
            group.appendChild(fieldRect);

            const iconText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            iconText.setAttribute('x', 16);
            iconText.setAttribute('y', yOffset + rowHeight / 2);
            iconText.setAttribute('font-size', '12px');
            iconText.setAttribute('dominant-baseline', 'middle');
            iconText.textContent = getTypeIcon(field.typeName);
            group.appendChild(iconText);

            const fieldText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            fieldText.setAttribute('x', 36);
            fieldText.setAttribute('y', yOffset + rowHeight / 2);
            fieldText.setAttribute('font-size', '12px');
            fieldText.setAttribute('dominant-baseline', 'middle');
            fieldText.setAttribute('fill', '#1e293b');
            fieldText.textContent = field.columnName;
            group.appendChild(fieldText);

            const typeText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            typeText.setAttribute('x', width - 16);
            typeText.setAttribute('y', yOffset + rowHeight / 2);
            typeText.setAttribute('font-size', '10px');
            typeText.setAttribute('text-anchor', 'end');
            typeText.setAttribute('dominant-baseline', 'middle');
            typeText.setAttribute('fill', '#94a3b8');
            typeText.textContent = field.typeName || '';
            group.appendChild(typeText);

            yOffset += rowHeight;
        });

        table.nodeGroup = group;
        return group;
    }

    // ============ 渲染转换节点 ============
    function renderTransformNode(tNode) {
        const group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        group.setAttribute('class', 'transform-node-group');
        group.setAttribute('data-transform-id', tNode.id);
        group.setAttribute('transform', `translate(${tNode.x}, ${tNode.y})`);
        const width = 180, height = 50;
        const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        rect.setAttribute('width', width);
        rect.setAttribute('height', height);
        rect.setAttribute('fill', '#f59e0b');
        rect.setAttribute('rx', '8');
        group.appendChild(rect);
        const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        text.setAttribute('x', width / 2);
        text.setAttribute('y', height / 2);
        text.setAttribute('fill', 'white');
        text.setAttribute('text-anchor', 'middle');
        text.setAttribute('dominant-baseline', 'middle');
        text.textContent = tNode.name;
        group.appendChild(text);
        tNode.nodeGroup = group;
        return group;
    }

    // ============ 渲染 Join 节点 ============
    function renderJoinNode(jNode) {
        const group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        group.setAttribute('class', 'join-node-group');
        group.setAttribute('data-join-id', jNode.id);
        group.setAttribute('transform', `translate(${jNode.x}, ${jNode.y})`);

        const width = 220, height = 64;
        const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        rect.setAttribute('width', width);
        rect.setAttribute('height', height);
        rect.setAttribute('rx', '10');
        rect.setAttribute('fill', '#f59e0b');
        rect.setAttribute('stroke', '#d97706');
        rect.setAttribute('stroke-width', '2');
        group.appendChild(rect);

        const titleText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        titleText.setAttribute('x', width / 2);
        titleText.setAttribute('y', 24);
        titleText.setAttribute('text-anchor', 'middle');
        titleText.setAttribute('fill', 'white');
        titleText.setAttribute('font-size', '13');
        titleText.setAttribute('font-weight', 'bold');
        titleText.textContent = `🔗 ${jNode.leftTable} ⋈ ${jNode.rightTable}`;
        group.appendChild(titleText);

        const subText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        subText.setAttribute('x', width / 2);
        subText.setAttribute('y', 44);
        subText.setAttribute('text-anchor', 'middle');
        subText.setAttribute('fill', 'rgba(255,255,255,0.9)');
        subText.setAttribute('font-size', '11');
        subText.textContent = `${jNode.joinType} ON ${jNode.leftField} ${jNode.operator} ${jNode.rightField}`;
        group.appendChild(subText);

        jNode.nodeGroup = group;
        return group;
    }

    // ============ 绘制 Join 边（两表 -> join 节点） ============
    function drawJoinEdges(jNode) {
        const svg = core.getSvg();
        const tablesMap = core.getTables();
        const leftTable = tablesMap.get(jNode.leftTable);
        const rightTable = tablesMap.get(jNode.rightTable);
        if (!leftTable || !rightTable || !svg) return;

        if (jNode.edges) {
            jNode.edges.forEach(edge => {
                if (edge.line && edge.line.parentNode) edge.line.remove();
                if (edge.label && edge.label.parentNode) edge.label.remove();
            });
        }
        jNode.edges = [];

        function drawOneEdge(table, labelText) {
            const startX = table.x + 220;
            const startY = table.y + 17;
            const endX = jNode.x;
            const endY = jNode.y + 32;
            const pathData = `M ${startX} ${startY} C ${startX + 80} ${startY}, ${endX - 80} ${endY}, ${endX} ${endY}`;

            const line = document.createElementNS('http://www.w3.org/2000/svg', 'path');
            line.setAttribute('d', pathData);
            line.setAttribute('stroke', '#f59e0b');
            line.setAttribute('stroke-width', '2');
            line.setAttribute('fill', 'none');
            line.setAttribute('stroke-dasharray', '5 4');
            line.setAttribute('marker-end', 'url(#arrowhead-orange)');
            svg.appendChild(line);

            const lbl = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            lbl.setAttribute('x', (startX + endX) / 2);
            lbl.setAttribute('y', (startY + endY) / 2 - 5);
            lbl.setAttribute('text-anchor', 'middle');
            lbl.setAttribute('fill', '#d97706');
            lbl.setAttribute('font-size', '10');
            lbl.textContent = labelText;
            svg.appendChild(lbl);

            jNode.edges.push({ line, label: lbl, tableName: table.name });
        }

        drawOneEdge(leftTable, '左表');
        drawOneEdge(rightTable, '右表');
    }

    function updateJoinEdgesForTable(tableName) {
        for (let jNode of core.getJoinNodes().values()) {
            if (jNode.leftTable === tableName || jNode.rightTable === tableName) {
                drawJoinEdges(jNode);
            }
        }
    }

    // ============ 选中高亮刷新 ============
    function refreshSelectionHighlight() {
        document.querySelectorAll('.table-node-group, .join-node-group, .transform-node-group').forEach(g => {
            g.classList.remove('selected', 'multi-selected');
        });

        const selected = core.getSelectedNode();
        if (selected) {
            const el = findNodeElement(selected.type, selected.id);
            if (el) el.classList.add('selected');
        }

        core.getMultiSelected().forEach(key => {
            const parts = key.split(':');
            const type = parts[0];
            const idRaw = parts.slice(1).join(':');
            const id = (type === 'join') ? parseInt(idRaw) : idRaw;
            const el = findNodeElement(type, id);
            if (el) el.classList.add('multi-selected');
        });
    }

    function findNodeElement(type, id) {
        if (type === 'table') {
            const t = core.getTables().get(id);
            return t && t.nodeGroup ? t.nodeGroup : null;
        }
        if (type === 'join') {
            const j = core.getJoinNodes().get(id);
            return j && j.nodeGroup ? j.nodeGroup : null;
        }
        return null;
    }

    // ============ 完整刷新 ============
    function fullRefresh() {
        // 1. 表 + 转换节点
        core.refreshAll(renderTableNode, renderTransformNode);

        const svg = core.getSvg();
        if (!svg) return;

        // 2. 重绘转换节点边
        if (window.ViewTransform && window.ViewTransform.redrawAllTransformEdges) {
            window.ViewTransform.redrawAllTransformEdges();
        }

        // 3. 先画 Join 边，再画 Join 节点（节点在上层）
        for (let jNode of core.getJoinNodes().values()) {
            drawJoinEdges(jNode);
        }
        for (let jNode of core.getJoinNodes().values()) {
            svg.appendChild(renderJoinNode(jNode));
        }

        // 4. placeholder
        const placeholder = document.getElementById('diagramPlaceholder');
        if (placeholder) {
            placeholder.style.display =
                (core.getTables().size === 0 && core.getJoinNodes().size === 0) ? 'block' : 'none';
        }

        // 5. 高亮
        refreshSelectionHighlight();
    }

    // ============ 初始化 ============
    const core = ViewCore;
    core.initCore('diagramSvg');

    ViewDrag.initDrag(core, renderTableNode, renderTransformNode, fullRefresh);
    ViewModal.initModal(core, () => { ViewTransform.generateTransformNodes(); });
    ViewTransform.initTransform(core, renderTableNode, renderTransformNode, fullRefresh);
    ViewSql.initSql(core);
    if (window.ViewJoin) ViewJoin.init(core, fullRefresh);

    // ============ 右键菜单 ============
    function bindTableContextMenu() {
        document.addEventListener('contextmenu', (e) => {
            const target = e.target.closest('.table-node-group');
            if (!target) return;
            e.preventDefault();
            const tableName = target.getAttribute('data-table-name');
            if (!tableName) return;

            const multi = core.getMultiSelected();
            const tablesInMulti = Array.from(multi)
                .filter(k => k.startsWith('table:'))
                .map(k => k.substring(6));
            const canJoin = tablesInMulti.length === 2 && tablesInMulti.indexOf(tableName) >= 0;

            showContextMenu(e.clientX, e.clientY, tableName, canJoin);
        });
    }

    function showContextMenu(x, y, tableName, canJoin) {
        const menu = document.getElementById('contextMenu');
        if (!menu) return;
        // ★ 确保菜单定位正确
        menu.style.position = 'fixed';
        menu.style.zIndex = '9999';
        menu.style.left = x + 'px';
        menu.style.top = y + 'px';
        menu.style.display = 'block';

        const joinItem = menu.querySelector('[data-action="createJoin"]');
        if (joinItem) joinItem.style.display = canJoin ? 'block' : 'none';

        const handler = (ev) => {
            const item = ev.target.closest('.context-item');
            const action = item ? item.dataset.action : null;
            if (action === 'deleteTable') {
                core.getTables().delete(tableName);
                for (let [jid, jNode] of Array.from(core.getJoinNodes().entries())) {
                    if (jNode.leftTable === tableName || jNode.rightTable === tableName) {
                        core.getJoinNodes().delete(jid);
                    }
                }
                const sel = core.getSelectedNode();
                if (sel && sel.type === 'table' && sel.id === tableName) core.setSelectedNode(null, null);
                fullRefresh();
                if (window.ViewSql) window.ViewSql.updateSql();
            } else if (action === 'fieldTransform') {
                const sel = core.getSelectedNode();
                if (!sel || sel.type !== 'table' || sel.id !== tableName) {
                    core.setSelectedNode('table', tableName);
                    refreshSelectionHighlight();
                    if (window.ViewSql) window.ViewSql.updateSql();
                }
                ViewModal.openModal();
            } else if (action === 'viewData') {
                const table = core.getTables().get(tableName);
                if (table) {
                    DB.setCurrentDb(table.dbName);
                    DB.setCurrentTable(table.name);
                    document.querySelector('.tab-btn[data-tab="data"]').click();
                }
            } else if (action === 'viewStructure') {
                const table = core.getTables().get(tableName);
                if (table) {
                    DB.setCurrentDb(table.dbName);
                    DB.setCurrentTable(table.name);
                    document.querySelector('.tab-btn[data-tab="properties"]').click();
                }
            } else if (action === 'createJoin') {
                if (window.ViewJoin) ViewJoin.openJoinModal();
            }
            menu.style.display = 'none';
            document.removeEventListener('click', handler);
        };
        setTimeout(() => document.addEventListener('click', handler), 10);
    }

    bindTableContextMenu();

    // ============ 点击画布空白清空选中 ============
    document.addEventListener('click', (e) => {
        if (e.target.closest('.table-node-group, .join-node-group, .transform-node-group')) return;
        if (e.target.closest('#contextMenu')) return;
        if (e.target.closest('.modal')) return;
        if (e.target.closest('.canvas-toolbar')) return;
        if (e.target.closest('.btn-secondary') || e.target.closest('.btn-primary')) return;

        const canvasContainer = document.getElementById('diagramCanvasContainer');
        if (canvasContainer && canvasContainer.contains(e.target)) {
            core.setSelectedNode(null, null);
            core.clearMultiSelect();
            refreshSelectionHighlight();
            if (window.ViewSql) window.ViewSql.updateSql();
        }
    });

    // ============ 左侧 DB 变化联动：若画板上已有该表，选中它 ============
    DB.subscribe((db, table) => {
        if (db && table) {
            if (core.getTables().has(table)) {
                core.setSelectedNode('table', table);
                refreshSelectionHighlight();
                if (window.ViewSql) window.ViewSql.updateSql();
            }
        }
    });

    // ============ 暴露全局 ============
    window.ViewModule = {
        refreshCanvasSize: () => {},
        updateCurrentViewSql: () => { if (window.ViewSql) window.ViewSql.updateSql(); },
        openTransformModal: () => ViewModal.openModal(),
        fullRefresh: fullRefresh,
        refreshSelectionHighlight: refreshSelectionHighlight,
        updateJoinEdgesForTable: updateJoinEdgesForTable,
        redrawJoinEdges: drawJoinEdges
    };
})();