// ========== 表列表模块：按数据源绑定的数据库加载表（无数据库选择、不可修改） ==========
const DBModule = (function() {
    let tableFilter = null;
    let tableContainer = null;
    let allTables = [];
    let currentFilter = '';
    let currentRequest = null;
    let currentDbName = null;

    // ---------- 按数据库加载表（由 core 在数据源切换时调用） ----------
    async function loadTables(dbName) {
        if (!tableContainer) {
            console.warn('[DBModule] tableContainer 未初始化');
            return;
        }
        if (!dbName) {
            tableContainer.innerHTML = '<div class="empty-state">未绑定数据库</div>';
            allTables = [];
            return;
        }
        currentDbName = dbName;

        if (currentRequest) currentRequest.abort();
        currentRequest = new AbortController();
        const signal = currentRequest.signal;

        tableContainer.innerHTML = '<div class="loading">加载表列表中...</div>';
        try {
            // 请求只针对当前库，dsId 由 DB 层注入（这里手动拼，保持与 fetchJSON 逻辑一致）
            let url = DB.apiBase + '/tables?db=' + encodeURIComponent(dbName);
            if (typeof currentDataSourceId !== 'undefined' && currentDataSourceId) {
                url += '&dsId=' + currentDataSourceId;
            }

            const resp = await fetch(url, { signal });
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            const tables = await resp.json();

            // 只保留表（过滤视图等）
            allTables = Array.isArray(tables)
                ? tables.filter(t => !t.type || t.type.toUpperCase() === 'TABLE')
                : [];

            renderTableList(allTables);
        } catch (err) {
            if (err.name === 'AbortError') return;
            console.error('[DBModule] 加载表列表失败', err);
            tableContainer.innerHTML = '<div class="error">加载失败，请检查数据源配置</div>';
        } finally {
            currentRequest = null;
        }
    }

    // ---------- 渲染表列表（data-database 固定为当前库，供视图拖拽使用） ----------
    function renderTableList(tables) {
        if (!tableContainer) return;
        const filterText = currentFilter.toLowerCase();
        let filtered = tables;
        if (filterText) {
            filtered = tables.filter(t => t.name.toLowerCase().includes(filterText));
        }
        if (!filtered.length) {
            tableContainer.innerHTML = '<div class="empty-state">暂无匹配的表</div>';
            return;
        }

        const dbName = currentDbName || DB.getCurrentDb() || '';
        let html = '';
        for (const t of filtered) {
            const activeClass = (DB.getCurrentTable() === t.name) ? 'active' : '';
            html += `
                <div class="table-item ${activeClass}"
                     data-table="${escapeHtml(t.name)}"
                     data-database="${escapeHtml(dbName)}"
                     title="${escapeHtml(dbName + '.' + t.name)}">
                    <span class="icon">📄</span>
                    <span class="name">${escapeHtml(t.name)}</span>
                </div>
            `;
        }
        tableContainer.innerHTML = html;

        document.querySelectorAll('#tableListContainer .table-item').forEach(el => {
            el.addEventListener('click', (e) => {
                e.stopPropagation();
                const tableName = el.dataset.table;
                const db = el.dataset.database || DB.getCurrentDb();
                if (tableName && db) {
                    selectTable(db, tableName);
                }
            });
        });

        // 默认选中第一张表（仅当没有选中任何表时）
        if (!DB.getCurrentTable() && filtered.length > 0) {
            const firstItem = document.querySelector('#tableListContainer .table-item');
            if (firstItem) {
                const tableName = firstItem.dataset.table;
                const db = firstItem.dataset.database || DB.getCurrentDb();
                if (tableName && db) selectTable(db, tableName);
            }
        }
    }

    // ---------- 选中表（联动属性/数据/SQL/视图） ----------
    function selectTable(dbName, tableName) {
        DB.setCurrentDb(dbName);
        DB.setCurrentTable(tableName);
        document.querySelectorAll('#tableListContainer .table-item').forEach(el => {
            el.classList.toggle('active', el.dataset.table === tableName);
        });
        if (window.PropertiesModule && typeof window.PropertiesModule.loadProperties === 'function') {
            window.PropertiesModule.loadProperties(dbName, tableName);
        }
        if (window.DataModule && typeof window.DataModule.resetAndLoad === 'function') {
            window.DataModule.resetAndLoad(dbName, tableName);
        }
        if (window.__refreshSqlHints) window.__refreshSqlHints();
        if (window.ViewModule && window.ViewModule.updateCurrentViewSql) window.ViewModule.updateCurrentViewSql();
        DB.notify(dbName, tableName);
    }

    // ---------- 表名过滤（防抖） ----------
    let filterTimer = null;
    function filterTables() {
        if (filterTimer) clearTimeout(filterTimer);
        filterTimer = setTimeout(() => {
            currentFilter = tableFilter.value.trim();
            renderTableList(allTables);
        }, 300);
    }

    function clear() {
        if (tableContainer) tableContainer.innerHTML = '';
        allTables = [];
        currentDbName = null;
    }

    function init() {
        tableFilter = document.getElementById('tableFilter');
        tableContainer = document.getElementById('tableListContainer');

        if (!tableFilter || !tableContainer) {
            console.error('[DBModule] 必要元素未找到（tableFilter/tableListContainer）');
            return;
        }

        tableFilter.removeEventListener('input', filterTables);
        tableFilter.addEventListener('input', filterTables);
    }

    return { init, loadTables, clear };
})();

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => DBModule.init());
} else {
    DBModule.init();
}
window.DBModule = DBModule;