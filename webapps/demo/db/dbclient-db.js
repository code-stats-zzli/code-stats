// ========== 数据库模块：直接加载数据源下的所有表（跨数据库） ==========
const DBModule = (function() {
    let tableFilter = null;
    let tableContainer = null;
    let allTables = [];      // 存储 { database, name, type }
    let currentFilter = '';

    async function loadAllTables() {
        if (!tableContainer) return;
        if (!currentDataSourceId) {
            tableContainer.innerHTML = '<div class="empty-state">请选择数据源</div>';
            return;
        }
        tableContainer.innerHTML = '<div class="loading">加载表列表中...</div>';
        try {
            const url = DB.apiBase + '/tables/all';
            const tables = await DB.fetchJSON(url);
            allTables = tables;
            renderTableList(allTables);
        } catch (err) {
            console.error('加载表列表失败', err);
            tableContainer.innerHTML = '<div class="error">加载失败</div>';
        }
    }

    function renderTableList(tables) {
        if (!tableContainer) return;
        if (!tables.length) {
            tableContainer.innerHTML = '<div class="empty-state">暂无表</div>';
            return;
        }
        const currentTable = DB.getCurrentTable();
        const currentDb = DB.getCurrentDb();
        let html = '';
        for (const t of tables) {
            const activeClass = (currentTable === t.name && currentDb === t.database) ? 'active' : '';
            const displayName = `${t.database}.${t.name}`;
            html += `
                <div class="table-item ${activeClass}" data-table="${escapeHtml(t.name)}" data-database="${escapeHtml(t.database)}">
                    <span class="icon">📄</span>
                    <span class="name">${escapeHtml(displayName)}</span>
                </div>
            `;
        }
        tableContainer.innerHTML = html;

        document.querySelectorAll('.table-item').forEach(el => {
            el.addEventListener('click', () => {
                const tableName = el.dataset.table;
                const dbName = el.dataset.database;
                if (tableName && dbName) {
                    selectTable(dbName, tableName);
                }
            });
        });
    }

    function selectTable(dbName, tableName) {
        DB.setCurrentDb(dbName);
        DB.setCurrentTable(tableName);
        document.querySelectorAll('.table-item').forEach(el => {
            el.classList.toggle('active', el.dataset.table === tableName && el.dataset.database === dbName);
        });
        if (dbName && tableName) {
            PropertiesModule.loadProperties(dbName, tableName);
            DataModule.resetAndLoad(dbName, tableName);
            if (window.__refreshSqlHints) window.__refreshSqlHints();
            if (window.ViewModule && window.ViewModule.updateCurrentViewSql) window.ViewModule.updateCurrentViewSql();
        }
    }

    function filterTables() {
        const filterText = tableFilter.value.trim().toLowerCase();
        currentFilter = filterText;
        if (!filterText) {
            renderTableList(allTables);
        } else {
            const filtered = allTables.filter(t =>
                t.name.toLowerCase().includes(filterText) ||
                t.database.toLowerCase().includes(filterText)
            );
            renderTableList(filtered);
        }
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

window.DBModule = DBModule;