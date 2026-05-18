// ========== 数据库模块：下拉框 + 表列表 + 过滤 ==========
const DBModule = (function() {
    let dbSelect = null;
    let tableFilter = null;
    let tableContainer = null;
    let allTables = [];
    let currentFilter = '';

    async function loadDatabases() {
        try {
            const databases = await DB.fetchJSON(DB.apiBase + '/databases');
            dbSelect.innerHTML = '<option value="">请选择数据库</option>';
            for (const db of databases) {
                const option = document.createElement('option');
                option.value = db;
                option.textContent = db;
                dbSelect.appendChild(option);
            }
            const currentDb = DB.getCurrentDb();
            if (currentDb && databases.includes(currentDb)) {
                dbSelect.value = currentDb;
                await onDatabaseSelected(currentDb);
            } else if (databases.length > 0) {
                dbSelect.value = databases[0];
                await onDatabaseSelected(databases[0]);
            }
        } catch (err) {
            console.error('加载数据库列表失败', err);
            dbSelect.innerHTML = '<option>加载失败</option>';
        }
    }

    async function onDatabaseSelected(dbName) {
        if (!dbName) return;
        DB.setCurrentDb(dbName);
        await loadTables(dbName);
    }

    async function loadTables(dbName) {
        try {
            const tables = await DB.fetchJSON(DB.apiBase + '/tables?db=' + encodeURIComponent(dbName));
            allTables = tables.map(t => t.name);
            renderTableList(allTables);
        } catch (err) {
            console.error('加载表列表失败', err);
            tableContainer.innerHTML = '<div class="error">加载失败</div>';
        }
    }// dbclient-db.js - 修改默认选中第一张表
// 在原 DBModule 的 loadTables 方法最后添加自动选中逻辑
    async function loadTables(dbName) {
        try {
            const tables = await DB.fetchJSON(DB.apiBase + '/tables?db=' + encodeURIComponent(dbName));
            allTables = tables.map(t => t.name);
            renderTableList(allTables);
            // ★ 新增：自动选中第一张表
            if (allTables.length > 0 && !DB.getCurrentTable()) {
                const firstTable = allTables[0];
                DB.setCurrentTable(firstTable);
                // 触发各模块加载
                PropertiesModule.loadProperties(dbName, firstTable);
                DataModule.resetAndLoad(dbName, firstTable);
                if (window.__refreshSqlHints) window.__refreshSqlHints();
                // 更新视图源字段
                if (window.ViewModule) window.ViewModule.updateCurrentViewSql();
            }
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
        let html = '';
        for (const t of tables) {
            const activeClass = (currentTable === t) ? 'active' : '';
            html += `
                <div class="table-item ${activeClass}" data-table="${DB.escapeHtml(t)}" draggable="true">
                    <span class="icon">📄</span>
                    <span class="name">${DB.escapeHtml(t)}</span>
                </div>
            `;
        }
        tableContainer.innerHTML = html;

        document.querySelectorAll('.table-item').forEach(el => {
            el.addEventListener('click', () => {
                const tableName = el.dataset.table;
                if (tableName) {
                    selectTable(tableName);
                }
            });
        });
    }

    function selectTable(tableName) {
        DB.setCurrentTable(tableName);
        document.querySelectorAll('.table-item').forEach(el => {
            el.classList.toggle('active', el.dataset.table === tableName);
        });
        const db = DB.getCurrentDb();
        if (db && tableName) {
            PropertiesModule.loadProperties(db, tableName);
            DataModule.resetAndLoad(db, tableName);
            if (window.__refreshSqlHints) window.__refreshSqlHints();
        }
    }

    function filterTables() {
        const filterText = tableFilter.value.trim().toLowerCase();
        currentFilter = filterText;
        if (!filterText) {
            renderTableList(allTables);
        } else {
            const filtered = allTables.filter(t => t.toLowerCase().includes(filterText));
            renderTableList(filtered);
        }
    }

    function init() {
        dbSelect = document.getElementById('dbSelect');
        tableFilter = document.getElementById('tableFilter');
        tableContainer = document.getElementById('tableListContainer');
        if (!dbSelect || !tableFilter || !tableContainer) {
            console.error('DBModule: 必要元素未找到');
            return;
        }
        dbSelect.addEventListener('change', (e) => {
            onDatabaseSelected(e.target.value);
        });
        tableFilter.addEventListener('input', filterTables);
        loadDatabases();
    }

    return { init };
})();

window.DBModule = DBModule;