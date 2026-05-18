// dbclient-view.js - 主入口，整合拖拽、弹窗、转换、SQL 模块
(function() {
    // 渲染表节点的函数（需在 Core 中定义，但为了模块独立，在这里定义并传递给 Core）
    function renderTableNode(table) {
        const group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        group.setAttribute('class', 'table-node-group');
        group.setAttribute('data-table-id', table.id);
        group.setAttribute('transform', `translate(${table.x}, ${table.y})`);
        const width = 220, headerHeight = 36, rowHeight = 24;
        let yOffset = 0;
        const headerRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        headerRect.setAttribute('class', 'header-rect');
        headerRect.setAttribute('width', width);
        headerRect.setAttribute('height', headerHeight);
        headerRect.setAttribute('x', 0);
        headerRect.setAttribute('y', yOffset);
        group.appendChild(headerRect);
        const titleText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        titleText.setAttribute('class', 'header-text');
        titleText.setAttribute('x', width/2);
        titleText.setAttribute('y', yOffset + headerHeight/2);
        titleText.textContent = table.name;
        group.appendChild(titleText);
        yOffset += headerHeight;
        table.fields.forEach(field => {
            const fieldRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
            fieldRect.setAttribute('width', width - 20);
            fieldRect.setAttribute('height', rowHeight - 2);
            fieldRect.setAttribute('x', 10);
            fieldRect.setAttribute('y', yOffset);
            fieldRect.setAttribute('fill', '#ffffff');
            fieldRect.setAttribute('stroke', '#cbd5e1');
            fieldRect.setAttribute('rx', '4');
            group.appendChild(fieldRect);
            const fieldText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            fieldText.setAttribute('x', 18);
            fieldText.setAttribute('y', yOffset + rowHeight/2);
            fieldText.textContent = field.columnName;
            fieldText.setAttribute('font-size', '12px');
            fieldText.setAttribute('dominant-baseline', 'middle');
            group.appendChild(fieldText);
            yOffset += rowHeight;
        });
        table.nodeGroup = group;
        return group;
    }

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
        text.setAttribute('x', width/2);
        text.setAttribute('y', height/2);
        text.setAttribute('fill', 'white');
        text.setAttribute('text-anchor', 'middle');
        text.setAttribute('dominant-baseline', 'middle');
        text.textContent = tNode.name;
        group.appendChild(text);
        tNode.nodeGroup = group;
        return group;
    }

    // 初始化 Core
    const core = ViewCore;
    core.initCore('diagramSvg');

    // 初始化各模块，传入依赖
    ViewDrag.initDrag(core, renderTableNode, renderTransformNode, () => {
        core.refreshAll(renderTableNode, renderTransformNode);
    });

    ViewModal.initModal(core, () => {
        ViewTransform.generateTransformNodes();
    });

    ViewTransform.initTransform(core, renderTableNode, renderTransformNode, () => {
        core.refreshAll(renderTableNode, renderTransformNode);
    });

    ViewSql.initSql(core);

    // 右键菜单绑定（需要在画布表节点上触发，由 Core 提供表节点获取）
    function bindTableContextMenu() {
        document.addEventListener('contextmenu', (e) => {
            const target = e.target.closest('.table-node-group');
            if (target) {
                e.preventDefault();
                const tableId = target.getAttribute('data-table-id');
                let table = null;
                for (let t of core.getTables().values()) {
                    if (t.id == tableId) table = t;
                }
                if (table) {
                    showContextMenu(e.clientX, e.clientY, table.name);
                }
            }
        });
    }

    function showContextMenu(x, y, tableName) {
        const menu = document.getElementById('contextMenu');
        if (!menu) return;
        menu.style.left = x + 'px';
        menu.style.top = y + 'px';
        menu.style.display = 'block';
        const handler = (e) => {
            const action = e.target.closest('.context-item')?.dataset.action;
            if (action) {
                if (action === 'deleteTable') {
                    core.getTables().delete(tableName);
                    core.refreshAll(renderTableNode, renderTransformNode);
                } else if (action === 'fieldTransform') {
                    ViewModal.openModal();
                } else if (action === 'viewData') {
                    const db = core.getTables().get(tableName).dbName;
                    DB.setCurrentDb(db);
                    DB.setCurrentTable(tableName);
                    document.querySelector('.tab-btn[data-tab="data"]').click();
                } else if (action === 'viewStructure') {
                    const db = core.getTables().get(tableName).dbName;
                    DB.setCurrentDb(db);
                    DB.setCurrentTable(tableName);
                    document.querySelector('.tab-btn[data-tab="properties"]').click();
                }
            }
            menu.style.display = 'none';
            document.removeEventListener('click', handler);
        };
        setTimeout(() => document.addEventListener('click', handler), 10);
    }

    bindTableContextMenu();

    // 监听数据库/表切换，更新 SQL 和左侧源字段（弹窗中已处理）
    DB.subscribe((db, table) => {
        if (db && table) {
            // 重新加载当前表信息到 activeTableForTransform
            (async () => {
                try {
                    const columns = await DB.fetchJSON(DB.apiBase + `/columns?db=${encodeURIComponent(db)}&table=${encodeURIComponent(table)}`);
                    core.setActiveTableForTransform({ db, table, columns });
                    if (!core.getCurrentTransformConfig().has(table)) {
                        core.getCurrentTransformConfig().set(table, []);
                    }
                    ViewSql.updateSql();
                } catch (err) {
                    console.error(err);
                }
            })();
        }
    });

    // 初始化加载当前表
    const currentDb = DB.getCurrentDb();
    const currentTable = DB.getCurrentTable();
    if (currentDb && currentTable) {
        (async () => {
            try {
                const columns = await DB.fetchJSON(DB.apiBase + `/columns?db=${encodeURIComponent(currentDb)}&table=${encodeURIComponent(currentTable)}`);
                core.setActiveTableForTransform({ db: currentDb, table: currentTable, columns });
                if (!core.getCurrentTransformConfig().has(currentTable)) {
                    core.getCurrentTransformConfig().set(currentTable, []);
                }
                ViewSql.updateSql();
            } catch (err) {}
        })();
    }

    // 暴露一些方法供外部调用（如果需要）
    window.ViewModule = {
        refreshCanvasSize: () => {},
        updateCurrentViewSql: ViewSql.updateSql,
        openTransformModal: ViewModal.openModal
    };
})();