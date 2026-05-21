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

// ========== 数据源管理（增强版） ==========
let currentDataSourceId = null;
let abortController = null;

async function loadDataSources() {
    const select = document.getElementById('dataSourceSelect');
    if (!select) return;
    try {
        const resp = await fetch(window.apiUrl('/datasource/api/list'));
        if (!resp.ok) throw new Error();
        const data = await resp.json();
        select.innerHTML = data.map(ds => `<option value="${ds.id}" ${currentDataSourceId == ds.id ? 'selected' : ''}>${escapeHtml(ds.name)} (${ds.dbType})</option>`).join('');
        if (data.length === 0) {
            select.innerHTML = '<option disabled>暂无数据源，请先管理</option>';
            currentDataSourceId = null;
            clearTableListAndPanels();
        } else {
            if (!currentDataSourceId || !data.find(d => d.id == currentDataSourceId)) {
                // 默认选中第一个
                //currentDataSourceId = data[0].id;
                select.value = currentDataSourceId;
            }
            onDataSourceChange();
        }
    } catch (err) {
        console.error('loadDataSources error:', err);
        select.innerHTML = '<option disabled>加载数据源失败</option>';
    }
}

function clearTableListAndPanels() {
    const container = document.getElementById('tableListContainer');
    if (container) container.innerHTML = '<div class="empty-state">请选择数据源</div>';
    DB.setCurrentDb(null);
    DB.setCurrentTable(null);
    if (window.PropertiesModule && window.PropertiesModule.clear) window.PropertiesModule.clear();
    if (window.DataModule && window.DataModule.resetAndLoad) window.DataModule.resetAndLoad(null, null);
    if (window.ViewModule && window.ViewModule.refreshCanvasSize) window.ViewModule.refreshCanvasSize();
}

function onDataSourceChange() {
    const select = document.getElementById('dataSourceSelect');
    if (!select) return;
    const newId = select.value;
    if (currentDataSourceId !== newId) {
        currentDataSourceId = newId;
        if (abortController) abortController.abort();
        clearTableListAndPanels();
        // 强制重新加载表列表
        if (window.DBModule && typeof window.DBModule.loadAllTables === 'function') {
            window.DBModule.loadAllTables();
        } else {
            console.warn('DBModule.loadAllTables 未定义，请确保 dbclient-db.js 已加载');
        }
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
const originalFetchJSON = DB.fetchJSON;
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

window.refreshDataSourceSelect = loadDataSources;

function escapeHtml(str) {
    return DB.escapeHtml(str);
}