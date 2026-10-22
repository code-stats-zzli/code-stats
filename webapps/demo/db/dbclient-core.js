// ========== 全局 DB 对象定义（必须最先加载） ==========
const DB = (function() {
    let currentDb = null;
    let currentTable = null;
    const subscribers = [];
    const apiBase = window.apiUrl('/dbclient/api');

    async function fetchJSON(url) {
        const res = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return await res.json();
    }

    function setCurrentDb(db) { currentDb = db; }
    function getCurrentDb() { return currentDb; }
    function setCurrentTable(table) { currentTable = table; }
    function getCurrentTable() { return currentTable; }
    function subscribe(callback) { subscribers.push(callback); }
    function notify(db, table) { subscribers.forEach(fn => fn(db, table)); }
    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    return {
        apiBase, fetchJSON,
        setCurrentDb, getCurrentDb, setCurrentTable, getCurrentTable,
        subscribe, notify,
        escapeHtml
    };
})();

// ========== 数据源管理（数据库来自数据源配置，前端只读） ==========
let currentDataSourceId = null;
let currentDataSource = null;   // 当前数据源完整对象（包含 databaseName）
let dataSourceList = [];        // 数据源列表缓存
let abortController = null;

async function loadDataSources() {
    const select = document.getElementById('dataSourceSelect');
    if (!select) return;
    try {
        const resp = await fetch(window.apiUrl('/datasource/api/list'));
        if (!resp.ok) throw new Error();
        const data = await resp.json();
        dataSourceList = data;

        select.innerHTML = data.map(ds =>
            `<option value="${ds.id}" ${currentDataSourceId == ds.id ? 'selected' : ''}>${escapeHtml(ds.name)} (${ds.dbType})</option>`
        ).join('');

        if (data.length === 0) {
            select.innerHTML = '<option disabled>暂无数据源，请先管理</option>';
            currentDataSourceId = null;
            currentDataSource = null;
            clearTableListAndPanels();
        } else {
            // 若当前 ID 不在列表中，强制选第一个并触发变更
            if (!currentDataSourceId || !data.find(d => d.id == currentDataSourceId)) {
                select.value = data[0].id;
                currentDataSourceId = null; // 强制 onDataSourceChange 内进入分支
            }
            onDataSourceChange();
        }
    } catch (err) {
        console.error('loadDataSources error:', err);
        select.innerHTML = '<option disabled>加载数据源失败</option>';
    }
}

/**
 * 清空左侧表列表、只读数据库标签及右侧各面板
 */
function clearTableListAndPanels() {
    const container = document.getElementById('tableListContainer');
    if (container) container.innerHTML = '<div class="empty-state">请选择数据源</div>';

    updateDbInfoLabel(null);

    DB.setCurrentDb(null);
    DB.setCurrentTable(null);
    if (window.PropertiesModule && window.PropertiesModule.clear) window.PropertiesModule.clear();
    if (window.DataModule && window.DataModule.resetAndLoad) window.DataModule.resetAndLoad(null, null);
    if (window.ViewModule && window.ViewModule.refreshCanvasSize) window.ViewModule.refreshCanvasSize();
    try { DB.notify(null, null); } catch (e) { /* ignore */ }
}

/**
 * 更新左侧只读数据库名展示
 */
function updateDbInfoLabel(dbName) {
    const wrapper = document.getElementById('dbInfoInline');
    const value = document.getElementById('dbInfoValue');
    if (!wrapper || !value) return;
    if (dbName) {
        wrapper.style.display = 'flex';
        value.textContent = dbName;
    } else {
        wrapper.style.display = 'none';
        value.textContent = '-';
    }
}

function onDataSourceChange() {
    const select = document.getElementById('dataSourceSelect');
    if (!select) return;
    const newId = select.value;
    if (currentDataSourceId === newId) return;

    currentDataSourceId = newId;
    if (abortController) abortController.abort();
    clearTableListAndPanels();

    // 从缓存的数据源对象中取出 databaseName（数据源自带，不可修改）
    currentDataSource = dataSourceList.find(d => d.id == newId) || null;
    const dbName = currentDataSource && currentDataSource.databaseName
        ? currentDataSource.databaseName
        : null;

    if (!dbName) {
        const container = document.getElementById('tableListContainer');
        if (container) container.innerHTML =
            '<div class="empty-state">该数据源未配置数据库名<br>请在「数据源配置」中补充</div>';
        updateDbInfoLabel(null);
        return;
    }

    // 直接绑定到当前库（无下拉，不可修改）
    DB.setCurrentDb(dbName);
    DB.setCurrentTable(null);
    updateDbInfoLabel(dbName);

    // 加载该库的表
    if (window.DBModule && typeof window.DBModule.loadTables === 'function') {
        window.DBModule.loadTables(dbName);
    } else {
        console.warn('DBModule.loadTables 未定义，请确保 dbclient-db.js 已加载');
    }
}

function initDropdownMenu() {
    document.body.addEventListener('click', function(e) {
        const target = e.target.closest('.dropdown-menu a');
        if (!target) return;
        e.preventDefault();
        const action = target.getAttribute('data-action');
        if (action === 'datasource') {
            const url = window.apiUrl('/demo/db/datasource-manager.html');
            window.open(url, '_blank', 'width=1200,height=800');
        } else if (action === 'driver') {
            const url = window.apiUrl('/demo/db/driver/driver-manager.html');
            window.open(url, '_blank', 'width=1200,height=800');
        }
    });
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        const select = document.getElementById('dataSourceSelect');
        if (select) select.addEventListener('change', onDataSourceChange);
        initDropdownMenu();
        loadDataSources();
    });
} else {
    const select = document.getElementById('dataSourceSelect');
    if (select) select.addEventListener('change', onDataSourceChange);
    initDropdownMenu();
    loadDataSources();
}

// 包装 DB.fetchJSON，自动附加 dsId
DB.fetchJSON = async function(url) {
    let finalUrl = url;
    if (currentDataSourceId) {
        const separator = url.includes('?') ? '&' : '?';
        finalUrl += `${separator}dsId=${currentDataSourceId}`;
    }
    const res = await fetch(finalUrl);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
};

// 包装 DB.postJSON，自动附加 dsId（POST 请求，如 SQL 执行）
DB.postJSON = async function(url, body) {
    let finalUrl = url;
    if (currentDataSourceId) {
        const separator = url.includes('?') ? '&' : '?';
        finalUrl += `${separator}dsId=${currentDataSourceId}`;
    }
    const res = await fetch(finalUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
};

window.refreshDataSourceSelect = loadDataSources;

function escapeHtml(str) {
    return DB.escapeHtml(str);
}