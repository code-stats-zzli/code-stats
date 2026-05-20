// ========== 数据源管理扩展 ==========
let currentDataSourceId = null;

async function loadDataSources() {
    const select = document.getElementById('dataSourceSelect');
    if (!select) return;
    try {
        const resp = await fetch(window.apiUrl('/datasource/api/list'));
        if (!resp.ok) throw new Error();
        const data = await resp.json();
        select.innerHTML = '<option value="">【默认数据源】</option>' +
            data.map(ds => `<option value="${ds.id}" ${currentDataSourceId == ds.id ? 'selected' : ''}>${escapeHtml(ds.name)} (${ds.dbType})</option>`).join('');
        if (data.length === 0) {
            select.innerHTML += '<option disabled>暂无数据源，请先管理</option>';
        }
    } catch (err) {
        select.innerHTML = '<option disabled>加载数据源失败</option>';
    }
}

window.refreshDataSourceSelect = loadDataSources;

function onDataSourceChange() {
    const select = document.getElementById('dataSourceSelect');
    if (!select) return;
    const newId = select.value ? parseInt(select.value) : null;
    if (currentDataSourceId !== newId) {
        currentDataSourceId = newId;
        // 重置当前数据库/表选择
        DB.setCurrentDb(null);
        DB.setCurrentTable(null);
        // 重新加载数据库树（需要调用树模块的刷新）
        if (window.TreeModule && window.TreeModule.initConnections) {
            window.TreeModule.initConnections();
        }
        if (window.DBModule && window.DBModule.init) {
            window.DBModule.init();  // 重新初始化数据库下拉和表列表
        }
        // 同时清空右侧内容
        if (window.PropertiesModule && window.PropertiesModule.loadProperties) {
            document.getElementById('propertiesContent').innerHTML = '<h2>请从左侧选择一张表</h2>';
        }
        if (window.DataModule) window.DataModule.resetAndLoad(null, null);
    }
}

// 绑定事件和初始化
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        const select = document.getElementById('dataSourceSelect');
        if (select) select.addEventListener('change', onDataSourceChange);
        const manageBtn = document.getElementById('manageDataSourceBtn');
        if (manageBtn) {
            manageBtn.addEventListener('click', () => {
                window.open(window.apiUrl('/demo/db/datasource-manager.html'), '_blank', 'width=1200,height=800');
            });
        }
        loadDataSources();
    });
} else {
    const select = document.getElementById('dataSourceSelect');
    if (select) select.addEventListener('change', onDataSourceChange);
    const manageBtn = document.getElementById('manageDataSourceBtn');
    if (manageBtn) {
        manageBtn.addEventListener('click', () => {
            window.open(window.apiUrl('/demo/db/datasource-manager.html'), '_blank', 'width=1200,height=800');
        });
    }
    loadDataSources();
}

// 修改 fetchJSON 函数，自动添加 dsId 参数
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

// 辅助函数
function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
}