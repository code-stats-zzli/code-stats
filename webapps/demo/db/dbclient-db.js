// ========== 表列表模块：加载数据源下所有表（跨数据库） ==========
const DBModule = (function() {
    let tableFilter = null;
    let tableContainer = null;
    let allTables = [];           // 存储 { database, name, type }
    let currentFilter = '';
    let currentRequest = null;    // AbortController 引用

    // 加载所有表（支持请求取消）
    async function loadAllTables() {
        if (!tableContainer) {
            console.error('tableContainer 未找到');
            return;
        }
        if (!currentDataSourceId) {
            tableContainer.innerHTML = '<div class="empty-state">请选择数据源</div>';
            return;
        }

        // 取消正在进行的请求
        if (currentRequest) currentRequest.abort();
        currentRequest = new AbortController();
        const signal = currentRequest.signal;

        tableContainer.innerHTML = '<div class="loading">加载表列表中...</div>';
        try {
            // 注意：DB.fetchJSON 会自动附加 dsId 参数
            const url = DB.apiBase + '/tables/all';
            const resp = await fetch(url + (url.includes('?') ? '&' : '?') + `dsId=${currentDataSourceId}`, { signal });
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            const tables = await resp.json();
            allTables = tables;
            renderTableList(allTables);
        } catch (err) {
            if (err.name === 'AbortError') {
                console.log('表加载请求已取消');
                return;
            }
            console.error('加载表列表失败', err);
            tableContainer.innerHTML = '<div class="error">加载失败，请检查网络或数据源配置</div>';
        } finally {
            currentRequest = null;
        }
    }

    // 渲染表列表（支持过滤）
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
            const displayName = `${escapeHtml(t.database)}.${escapeHtml(t.name)}`;
            html += `
                <div class="table-item ${activeClass}" data-table="${escapeHtml(t.name)}" data-database="${escapeHtml(t.database)}">
                    <span class="icon">📄</span>
                    <span class="name">${displayName}</span>
                </div>
            `;
        }
        tableContainer.innerHTML = html;

        // 绑定点击事件
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
    }

    // 选中表，触发各模块更新
    function selectTable(dbName, tableName) {
        DB.setCurrentDb(dbName);
        DB.setCurrentTable(tableName);
        // 更新高亮
        document.querySelectorAll('.table-item').forEach(el => {
            el.classList.toggle('active', el.dataset.table === tableName && el.dataset.database === dbName);
        });
        // 通知右侧模块刷新
        if (window.PropertiesModule && window.PropertiesModule.loadProperties) {
            window.PropertiesModule.loadProperties(dbName, tableName);
        }
        if (window.DataModule && window.DataModule.resetAndLoad) {
            window.DataModule.resetAndLoad(dbName, tableName);
        }
        if (window.__refreshSqlHints) window.__refreshSqlHints();
        if (window.ViewModule && window.ViewModule.updateCurrentViewSql) window.ViewModule.updateCurrentViewSql();
        // 触发自定义事件，方便其他模块监听
        DB.notify(dbName, tableName);
    }

    // 过滤输入防抖
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

// 初始化 DBModule（在页面 DOM 加载完成后）
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => DBModule.init());
} else {
    DBModule.init();
}
window.DBModule = DBModule;