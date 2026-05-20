// ========== 表列表模块：加载数据源下所有表（跨数据库） ==========
const DBModule = (function() {
    let tableFilter = null;
    let tableContainer = null;
    let allTables = [];
    let currentFilter = '';
    let currentRequest = null;

    async function loadAllTables() {
        if (!tableContainer) {
            console.error('tableContainer 未找到');
            return;
        }
        if (!currentDataSourceId) {
            tableContainer.innerHTML = '<div class="empty-state">请选择数据源</div>';
            return;
        }

        if (currentRequest) currentRequest.abort();
        currentRequest = new AbortController();
        const signal = currentRequest.signal;

        tableContainer.innerHTML = '<div class="loading">加载表列表中...</div>';
        try {
            const url = DB.apiBase + '/tables/all';
            const resp = await fetch(url + (url.includes('?') ? '&' : '?') + `dsId=${currentDataSourceId}`, { signal });
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            const tables = await resp.json();
            allTables = tables;
            renderTableList(allTables);
        } catch (err) {
            if (err.name === 'AbortError') return;
            console.error('加载表列表失败', err);
            tableContainer.innerHTML = '<div class="error">加载失败，请检查网络或数据源配置</div>';
        } finally {
            currentRequest = null;
        }
    }

    function renderTableList(tables) {
        if (!tableContainer) return;
        const filterText = currentFilter.toLowerCase();
        let filtered = tables;
        if (filterText) {
            filtered = tables.filter(t =>
                t.name.toLowerCase().includes(filterText) ||
                t.database.toLowerCase().includes(filterText)
            );
        }
        if (!filtered.length) {
            tableContainer.innerHTML = '<div class="empty-state">暂无匹配的表</div>';
            return;
        }
        let html = '';
        for (const t of filtered) {
            const activeClass = (DB.getCurrentTable() === t.name && DB.getCurrentDb() === t.database) ? 'active' : '';
            html += `
                <div class="table-item ${activeClass}" data-table="${escapeHtml(t.name)}" data-database="${escapeHtml(t.database)}">
                    <span class="icon">📄</span>
                    <span class="name">${escapeHtml(t.name)}</span>
                </div>
            `;
        }
        tableContainer.innerHTML = html;

        document.querySelectorAll('.table-item').forEach(el => {
            el.addEventListener('click', (e) => {
                e.stopPropagation();
                const tableName = el.dataset.table;
                const dbName = el.dataset.database;
                if (tableName && dbName) {
                    selectTable(dbName, tableName);
                }
            });
        });

        // 默认选中第一张表（仅当没有选中任何表时）
        if (!DB.getCurrentTable() && filtered.length > 0) {
            const firstItem = document.querySelector('.table-item');
            if (firstItem) {
                const dbName = firstItem.dataset.database;
                const tableName = firstItem.dataset.table;
                if (dbName && tableName) {
                    selectTable(dbName, tableName);
                }
            }
        }
    }

    function selectTable(dbName, tableName) {
        DB.setCurrentDb(dbName);
        DB.setCurrentTable(tableName);
        // 更新高亮
        document.querySelectorAll('.table-item').forEach(el => {
            el.classList.toggle('active', el.dataset.table === tableName && el.dataset.database === dbName);
        });
        // 触发右侧模块刷新（使用全局模块，确保它们已初始化）
        if (window.PropertiesModule && typeof window.PropertiesModule.loadProperties === 'function') {
            window.PropertiesModule.loadProperties(dbName, tableName);
        } else {
            console.error('PropertiesModule.loadProperties 未定义');
        }
        if (window.DataModule && typeof window.DataModule.resetAndLoad === 'function') {
            window.DataModule.resetAndLoad(dbName, tableName);
        } else {
            console.error('DataModule.resetAndLoad 未定义');
        }
        if (window.__refreshSqlHints) window.__refreshSqlHints();
        if (window.ViewModule && window.ViewModule.updateCurrentViewSql) window.ViewModule.updateCurrentViewSql();
        DB.notify(dbName, tableName);
    }

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
    }

    function init() {
        tableFilter = document.getElementById('tableFilter');
        tableContainer = document.getElementById('tableListContainer');
        if (!tableFilter || !tableContainer) {
            console.error('DBModule: 必要元素未找到');
            return;
        }
        tableFilter.addEventListener('input', filterTables);
    }

    return { init, loadAllTables, clear };
})();

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => DBModule.init());
} else {
    DBModule.init();
}
window.DBModule = DBModule;