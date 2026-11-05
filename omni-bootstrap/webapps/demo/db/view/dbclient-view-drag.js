// dbclient-view-drag.js - 视图拖拽核心模块（表拖入、表/Join 拖动、Ctrl 多选）
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
        bindNodeDrag();
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
                if (!raw) return;
                payload = JSON.parse(raw);
            } catch (err) {
                console.warn('[ViewDrag] 解析拖拽数据失败', err);
                return;
            }
            const { db, table } = payload || {};
            if (!db || !table) return;

            if (core.getTables().has(table)) {
                core.setSelectedNode('table', table);
                if (window.ViewModule && window.ViewModule.refreshSelectionHighlight) {
                    window.ViewModule.refreshSelectionHighlight();
                }
                if (window.ViewSql) window.ViewSql.updateSql();
                return;
            }

            const rect = container.getBoundingClientRect();
            const svgX = e.clientX - rect.left + container.scrollLeft;
            const svgY = e.clientY - rect.top + container.scrollTop;
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

    // ---------- 画布上表 / Join 节点：Ctrl 多选 + 拖动 ----------
    function bindNodeDrag() {
        let dragging = null;
        let startX = 0, startY = 0;
        let originX = 0, originY = 0;

        svg.addEventListener('mousedown', (e) => {
            if (e.button !== 0) return;
            const group = e.target.closest('.table-node-group, .join-node-group');
            if (!group) return;

            const isJoin = group.classList.contains('join-node-group');
            let nodeName = null;
            let joinId = null;
            let node = null;

            if (isJoin) {
                joinId = parseInt(group.getAttribute('data-join-id'));
                nodeName = group.getAttribute('data-join-name');
                node = core.getJoinNodes().get(joinId);
            } else {
                nodeName = group.getAttribute('data-table-name');
                node = core.getTables().get(nodeName);
            }
            if (!node || !nodeName) return;

            // ★ Ctrl / Cmd 多选
            if (e.ctrlKey || e.metaKey) {
                e.preventDefault();
                e.stopPropagation();
                if (isJoin) {
                    core.toggleMultiSelect('join', joinId);
                } else {
                    core.toggleMultiSelect('table', nodeName);
                }
                if (window.ViewModule && window.ViewModule.refreshSelectionHighlight) {
                    window.ViewModule.refreshSelectionHighlight();
                }
                return;
            }

            // 普通点击：清空多选，单选
            core.clearMultiSelect();
            if (isJoin) {
                core.setSelectedNode('join', joinId);
            } else {
                core.setSelectedNode('table', nodeName);
            }
            if (window.ViewModule && window.ViewModule.refreshSelectionHighlight) {
                window.ViewModule.refreshSelectionHighlight();
            }
            if (window.ViewSql) window.ViewSql.updateSql();

            // 开始拖动
            e.preventDefault();
            e.stopPropagation();

            dragging = { group, node, isJoin, nodeName, joinId };
            startX = e.clientX;
            startY = e.clientY;
            originX = node.x;
            originY = node.y;

            document.addEventListener('mousemove', onDragMove);
            document.addEventListener('mouseup', onDragEnd);
        });

        function onDragMove(e) {
            if (!dragging) return;
            const dx = e.clientX - startX;
            const dy = e.clientY - startY;
            dragging.node.x = originX + dx;
            dragging.node.y = originY + dy;
            dragging.group.setAttribute('transform',
                `translate(${dragging.node.x}, ${dragging.node.y})`);
            // 更新与该节点相关的边
            if (dragging.isJoin) {
                // 自身作为 join 的边（连到左右表）
                if (window.ViewModule && window.ViewModule.redrawJoinEdges) {
                    window.ViewModule.redrawJoinEdges(dragging.node);
                }
                // 其他 join 引用此 join 的边
                for (let jNode of core.getJoinNodes().values()) {
                    if (jNode.id !== dragging.joinId &&
                        (jNode.leftTable === dragging.nodeName || jNode.rightTable === dragging.nodeName)) {
                        if (window.ViewModule && window.ViewModule.redrawJoinEdges) {
                            window.ViewModule.redrawJoinEdges(jNode);
                        }
                    }
                }
            } else {
                // 表节点被拖动：更新引用此表的 join 边
                if (window.ViewModule && window.ViewModule.updateJoinEdgesForTable) {
                    window.ViewModule.updateJoinEdgesForTable(dragging.nodeName);
                }
            }
            if (window.ViewTransform && window.ViewTransform.updateEdgesForTable) {
                window.ViewTransform.updateEdgesForTable(dragging.nodeName);
            }
        }

        function onDragEnd() {
            if (!dragging) return;
            if (refreshCallback) refreshCallback();
            if (window.ViewSql) window.ViewSql.updateSql();
            dragging = null;
            document.removeEventListener('mousemove', onDragMove);
            document.removeEventListener('mouseup', onDragEnd);
        }
    }

    return { initDrag };
})();

window.ViewDrag = ViewDrag;