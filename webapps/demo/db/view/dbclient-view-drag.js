// dbclient-view-drag.js - 拖拽画布逻辑（从左侧表列表拖拽添加，节点拖拽移动）
const ViewDrag = (function() {
    let svg = null;
    let tables = null;
    let transformNodes = null;
    let refreshCallback = null; // 用于刷新画布的回调
    let core = null;

    function initDrag(coreInstance, renderTableNodeFn, renderTransformNodeFn, onRefresh) {
        core = coreInstance;
        svg = core.getSvg();
        tables = core.getTables();
        transformNodes = core.getTransformNodes();
        refreshCallback = onRefresh;

        setupDragAndDrop(core, renderTableNodeFn, renderTransformNodeFn);
        setupNodeDragging(core, renderTableNodeFn, renderTransformNodeFn);
    }

    // 从左侧表列表拖拽到画布
    function setupDragAndDrop(core, renderTableNodeFn, renderTransformNodeFn) {
        document.addEventListener('dragstart', (e) => {
            const target = e.target.closest('.table-item');
            if (!target) return;
            // 直接从元素上读取 data-database 和 data-table
            const dbName = target.dataset.database;
            const tableName = target.dataset.table;
            if (dbName && tableName) {
                e.dataTransfer.setData('text/plain', JSON.stringify({ dbName, tableName }));
                e.dataTransfer.effectAllowed = 'copy';
            }
        });

        const canvas = core.getSvg();
        if (canvas) {
            canvas.addEventListener('dragover', (e) => e.preventDefault());
            canvas.addEventListener('drop', async (e) => {
                e.preventDefault();
                const rect = canvas.getBoundingClientRect();
                const x = e.clientX - rect.left;
                const y = e.clientY - rect.top;
                const raw = e.dataTransfer.getData('text/plain');
                if (!raw) return;
                const { dbName, tableName } = JSON.parse(raw);
                await addTableFromDrag(dbName, tableName, x, y, core, renderTableNodeFn, renderTransformNodeFn);
            });
        }
    }

    async function addTableFromDrag(dbName, tableName, x, y, core, renderTableNodeFn, renderTransformNodeFn) {
        const tablesMap = core.getTables();
        if (tablesMap.has(tableName)) return;
        try {
            const columns = await DB.fetchJSON(DB.apiBase + `/columns?db=${encodeURIComponent(dbName)}&table=${encodeURIComponent(tableName)}`);
            const id = core.getNextTableId();
            const tableObj = {
                id: id,
                name: tableName,
                dbName: dbName,
                x: x,
                y: y,
                fields: columns.map(c => ({ columnName: c.columnName, typeName: c.typeName })),
                nodeGroup: null
            };
            tablesMap.set(tableName, tableObj);
            core.refreshAll(renderTableNodeFn, renderTransformNodeFn);
            setupNodeDragging(core, renderTableNodeFn, renderTransformNodeFn);
        } catch (err) {
            console.error('加载表失败', err);
        }
    }

    // 节点拖拽移动（表节点和转换节点）
    function setupNodeDragging(core, renderTableNodeFn, renderTransformNodeFn) {
        const tablesMap = core.getTables();
        const transformNodesMap = core.getTransformNodes();
        const svg = core.getSvg();

        svg.removeEventListener('mousedown', onMouseDown);
        svg.removeEventListener('mousemove', onMouseMove);
        svg.removeEventListener('mouseup', onMouseUp);
        svg.addEventListener('mousedown', onMouseDown);
        svg.addEventListener('mousemove', onMouseMove);
        svg.addEventListener('mouseup', onMouseUp);

        let dragTarget = null;
        let dragStart = null;
        let dragType = null;
        let dragId = null;

        function onMouseDown(e) {
            const target = e.target.closest('.table-node-group, .transform-node-group');
            if (!target) return;
            e.preventDefault();
            dragTarget = target;
            if (target.classList.contains('table-node-group')) {
                const tableId = target.getAttribute('data-table-id');
                for (let [name, tbl] of tablesMap.entries()) {
                    if (tbl.id == tableId) {
                        dragType = 'table';
                        dragId = name;
                        dragStart = { x: e.clientX - tbl.x, y: e.clientY - tbl.y };
                        break;
                    }
                }
            } else if (target.classList.contains('transform-node-group')) {
                const transformId = target.getAttribute('data-transform-id');
                const tNode = transformNodesMap.get(parseInt(transformId));
                if (tNode) {
                    dragType = 'transform';
                    dragId = tNode.id;
                    dragStart = { x: e.clientX - tNode.x, y: e.clientY - tNode.y };
                }
            }
        }

        function onMouseMove(e) {
            if (!dragStart) return;
            if (dragType === 'table') {
                const tbl = tablesMap.get(dragId);
                if (tbl) {
                    tbl.x = e.clientX - dragStart.x;
                    tbl.y = e.clientY - dragStart.y;
                    tbl.nodeGroup.setAttribute('transform', `translate(${tbl.x}, ${tbl.y})`);
                    if (window.ViewTransform && window.ViewTransform.updateEdgesForTable) {
                        window.ViewTransform.updateEdgesForTable(dragId);
                    }
                }
            } else if (dragType === 'transform') {
                const tNode = transformNodesMap.get(dragId);
                if (tNode) {
                    tNode.x = e.clientX - dragStart.x;
                    tNode.y = e.clientY - dragStart.y;
                    tNode.nodeGroup.setAttribute('transform', `translate(${tNode.x}, ${tNode.y})`);
                    if (tNode.edge && window.ViewTransform && window.ViewTransform.updateSingleEdge) {
                        window.ViewTransform.updateSingleEdge(tNode.id);
                    }
                }
            }
        }

        function onMouseUp() {
            dragStart = null;
            dragTarget = null;
            dragType = null;
            dragId = null;
        }
    }

    return { initDrag };
})();