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

        (table.fields || []).forEach(field => {
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

    // ============ ★ 渲染 Join 节点（卡片样式，与表节点一致） ============
    function renderJoinNode(jNode) {
        const group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        group.setAttribute('class', 'join-node-group');
        group.setAttribute('data-join-id', jNode.id);
        group.setAttribute('data-join-name', jNode.name);
        group.setAttribute('transform', `translate(${jNode.x}, ${jNode.y})`);

        const width = 220, headerHeight = 34, rowHeight = 26;
        let yOffset = 0;

        // 头部（橙色，与表节点蓝色区分）
        const headerRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        headerRect.setAttribute('width', width);
        headerRect.setAttribute('height', headerHeight);
        headerRect.setAttribute('x', 0);
        headerRect.setAttribute('y', 0);
        headerRect.setAttribute('fill', '#f59e0b');
        headerRect.setAttribute('rx', '6');
        group.appendChild(headerRect);

        const titleText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        titleText.setAttribute('x', width / 2);
        titleText.setAttribute('y', headerHeight / 2);
        titleText.setAttribute('text-anchor', 'middle');
        titleText.setAttribute('dominant-baseline', 'middle');
        titleText.setAttribute('fill', 'white');
        titleText.setAttribute('font-size', '13');
        titleText.setAttribute('font-weight', 'bold');
        titleText.textContent = '🔗 ' + jNode.name;
        group.appendChild(titleText);
        yOffset += headerHeight;

        // 字段列表
        (jNode.fields || []).forEach(field => {
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

        jNode.nodeGroup = group;
        return group;
    }

    // ============ 绘制 Join 边（两输入节点 -> join 节点） ============
    function drawJoinEdges(jNode) {
        const svg = core.getSvg();
        if (!svg) return;

        const leftNode = core.findNodeByName(jNode.leftTable);
        const rightNode = core.findNodeByName(jNode.rightTable);
        if (!leftNode || !rightNode) return;

        if (jNode.edges) {
            jNode.edges.forEach(edge => {
                if (edge.line && edge.line.parentNode) edge.line.remove();
                if (edge.label && edge.label.parentNode) edge.label.remove();
            });
        }
        jNode.edges = [];

        // join 节点垂直中心
        const joinNodeHeight = 34 + (jNode.fields ? jNode.fields.length : 0) * 26;
        const joinCenterY = jNode.y + joinNodeHeight / 2;

        function drawOneEdge(fromNode, labelText) {
            const startX = fromNode.x + 220;
            const startY = fromNode.y + 17;
            const endX = jNode.x;
            const endY = joinCenterY;
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

            jNode.edges.push({ line, label: lbl });
        }

        drawOneEdge(leftNode, '左');
        drawOneEdge(rightNode, '右');
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
    function bindNodeContextMenu() {
        document.addEventListener('contextmenu', (e) => {
            const target = e.target.closest('.table-node-group, .join-node-group');
            if (!target) return;
            e.preventDefault();

            const isJoin = target.classList.contains('join-node-group');
            const nodeName = isJoin
                ? target.getAttribute('data-join-name')
                : target.getAttribute('data-table-name');
            if (!nodeName) return;

            const multi = core.getMultiSelected();
            const canJoin = multi.size === 2 && Array.from(multi).some(k => k.endsWith(':' + nodeName));

            showContextMenu(e.clientX, e.clientY, nodeName, canJoin, isJoin);
        });
    }

    function showContextMenu(x, y, nodeName, canJoin, isJoin) {
        const menu = document.getElementById('contextMenu');
        if (!menu) return;
        menu.style.position = 'fixed';
        menu.style.zIndex = '9999';
        menu.style.left = x + 'px';
        menu.style.top = y + 'px';
        menu.style.display = 'block';

        const joinItem = menu.querySelector('[data-action="createJoin"]');
        if (joinItem) joinItem.style.display = canJoin ? 'block' : 'none';

        // join 节点不显示：查看数据 / 查看结构 / 字段转换
        const viewDataItem = menu.querySelector('[data-action="viewData"]');
        const viewStructItem = menu.querySelector('[data-action="viewStructure"]');
        const transformItem = menu.querySelector('[data-action="fieldTransform"]');
        if (isJoin) {
            if (viewDataItem) viewDataItem.style.display = 'none';
            if (viewStructItem) viewStructItem.style.display = 'none';
            if (transformItem) transformItem.style.display = 'none';
        } else {
            if (viewDataItem) viewDataItem.style.display = 'block';
            if (viewStructItem) viewStructItem.style.display = 'block';
            if (transformItem) transformItem.style.display = 'block';
        }

        const handler = (ev) => {
            const item = ev.target.closest('.context-item');
            const action = item ? item.dataset.action : null;

            if (action === 'deleteTable') {
                if (isJoin) {
                    // 删除 join 节点，以及引用它的其他 join 节点
                    const toRemove = [];
                    for (let [jid, jNode] of core.getJoinNodes().entries()) {
                        if (jNode.name === nodeName) toRemove.push(jid);
                    }
                    toRemove.forEach(jid => core.getJoinNodes().delete(jid));
                    // 二次清理：其他 join 引用了此节点
                    for (let [jid, jNode] of Array.from(core.getJoinNodes().entries())) {
                        if (jNode.leftTable === nodeName || jNode.rightTable === nodeName) {
                            core.getJoinNodes().delete(jid);
                        }
                    }
                } else {
                    core.getTables().delete(nodeName);
                    for (let [jid, jNode] of Array.from(core.getJoinNodes().entries())) {
                        if (jNode.leftTable === nodeName || jNode.rightTable === nodeName) {
                            core.getJoinNodes().delete(jid);
                        }
                    }
                }
                const sel = core.getSelectedNode();
                if (sel && sel.id === nodeName) core.setSelectedNode(null, null);
                fullRefresh();
                if (window.ViewSql) window.ViewSql.updateSql();
            } else if (action === 'fieldTransform') {
                const sel = core.getSelectedNode();
                if (!sel || sel.type !== 'table' || sel.id !== nodeName) {
                    core.setSelectedNode('table', nodeName);
                    refreshSelectionHighlight();
                    if (window.ViewSql) window.ViewSql.updateSql();
                }
                ViewModal.openModal();
            } else if (action === 'viewData') {
                const table = core.getTables().get(nodeName);
                if (table) {
                    DB.setCurrentDb(table.dbName);
                    DB.setCurrentTable(table.name);
                    document.querySelector('.tab-btn[data-tab="data"]').click();
                }
            } else if (action === 'viewStructure') {
                const table = core.getTables().get(nodeName);
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

    bindNodeContextMenu();

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