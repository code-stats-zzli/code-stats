// dbclient-view-drag.js - 视图拖拽核心模块（支持 Ctrl/Cmd 多选）
const ViewDrag = (function() {
    let core = null;
    let refreshCallback = null;
    let svg = null;
    let container = null;

    function initDrag(coreInstance, renderTableNode, renderTransformNode, onRefresh) {
        core = coreInstance;
        refreshCallback = onRefresh;
        svg = core.getSvg();
        container = document.getElementById('diagramCanvasContainer');
        if (!svg || !container) {
            console.warn('[ViewDrag] 初始化失败：SVG或容器未找到');
            return;
        }
        bindCanvasDrop();
        bindTableDrag();
    }

    // ---------- 左侧表拖入画布 ----------
    function bindCanvasDrop() {
        container.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'copy';
        });

        container.addEventListener('drop', async (e) => {
            e.preventDefault();
            let payload;
            try {
                const raw = e.dataTransfer.getData('text/plain');
                if (!raw) {
                    console.warn('[ViewDrag] 拖拽数据为空，请检查 dragstart 是否设置了数据');
                    return;
                }
                payload = JSON.parse(raw);
            } catch (err) {
                console.warn('[ViewDrag] 解析拖拽数据失败', err);
                return;
            }
            const { db, table } = payload || {};
            if (!db || !table) return;

            // 如果表已存在，选中它
            if (core.getTables().has(table)) {
                core.setSelectedNode('table', table);
                if (window.ViewModule && window.ViewModule.refreshSelectionHighlight) {
                    window.ViewModule.refreshSelectionHighlight();
                }
                if (window.ViewSql) window.ViewSql.updateSql();
                return;
            }

            // 计算 drop 坐标（SVG 坐标系）
            const rect = container.getBoundingClientRect();
            const svgX = e.clientX - rect.left + container.scrollLeft;
            const svgY = e.clientY - rect.top + container.scrollTop;
            // 表节点宽度 220，header 高度 34，让鼠标大致在节点中心
            const x = svgX - 110;
            const y = svgY - 17;

            try {
                const columns = await DB.fetchJSON(
                    DB.apiBase + `/columns?db=${encodeURIComponent(db)}&table=${encodeURIComponent(table)}`
                );
                const fields = columns.map(c => ({
                    columnName: c.columnName,
                    typeName: c.typeName
                }));
                const id = core.getNextTableId();
                const tableObj = {
                    id: id,
                    name: table,
                    dbName: db,
                    x: x,
                    y: y,
                    fields: fields,
                    nodeGroup: null
                };
                core.getTables().set(table, tableObj);
                core.setSelectedNode('table', table);
                if (refreshCallback) refreshCallback();
                if (window.ViewSql) window.ViewSql.updateSql();
            } catch (err) {
                console.error('[ViewDrag] 加载表列信息失败', err);
                alert('加载表列信息失败：' + err.message);
            }
        });
    }

    // ---------- 画布上表节点：Ctrl/Cmd 多选 + 拖动移动 ----------
    function bindTableDrag() {
        let dragging = null;
        let startX = 0, startY = 0;
        let originX = 0, originY = 0;

        svg.addEventListener('mousedown', (e) => {
            const group = e.target.closest('.table-node-group');
            if (!group) return;
            if (e.button !== 0) return; // 只处理左键
            const tableName = group.getAttribute('data-table-name');
            if (!tableName) return;
            const table = core.getTables().get(tableName);
            if (!table) return;

            // ★ Ctrl / Cmd 多选：只切换多选状态，不启动拖动
            if (e.ctrlKey || e.metaKey) {
                e.preventDefault();
                e.stopPropagation();
                core.toggleMultiSelect('table', tableName);
                if (window.ViewModule && window.ViewModule.refreshSelectionHighlight) {
                    window.ViewModule.refreshSelectionHighlight();
                }
                return;
            }

            // 普通点击：清空多选，单选
            core.clearMultiSelect();
            core.setSelectedNode('table', tableName);
            if (window.ViewModule && window.ViewModule.refreshSelectionHighlight) {
                window.ViewModule.refreshSelectionHighlight();
            }
            if (window.ViewSql) window.ViewSql.updateSql();

            // 开始拖动
            e.preventDefault();
            e.stopPropagation();

            dragging = { group, table };
            startX = e.clientX;
            startY = e.clientY;
            originX = table.x;
            originY = table.y;

            document.addEventListener('mousemove', onDragMove);
            document.addEventListener('mouseup', onDragEnd);
        });

        function onDragMove(e) {
            if (!dragging) return;
            const dx = e.clientX - startX;
            const dy = e.clientY - startY;
            dragging.table.x = originX + dx;
            dragging.table.y = originY + dy;
            dragging.group.setAttribute('transform',
                `translate(${dragging.table.x}, ${dragging.table.y})`);
            // 实时更新关联边和转换边
            if (window.ViewModule && window.ViewModule.updateJoinEdgesForTable) {
                window.ViewModule.updateJoinEdgesForTable(dragging.table.name);
            }
            if (window.ViewTransform && window.ViewTransform.updateEdgesForTable) {
                window.ViewTransform.updateEdgesForTable(dragging.table.name);
            }
        }

        function onDragEnd() {
            if (!dragging) return;
            // 拖动结束，整体刷新（重绘边、更新 SQL 等）
            if (refreshCallback) refreshCallback();
            if (window.ViewSql) window.ViewSql.updateSql();
            dragging = null;
            document.removeEventListener('mousemove', onDragMove);
            document.removeEventListener('mouseup', onDragEnd);
        }
    }

    return { initDrag };
})();

// 挂载到 window，供 dbclient-view.js 中的 window.ViewDrag 判断（如有）
window.ViewDrag = ViewDrag;