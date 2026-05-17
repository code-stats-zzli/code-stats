// ========== 公共核心模块：API请求、工具函数、全局状态 ==========
const DB = (function() {
    // 私有状态
    let currentDb = null;
    let currentTable = null;

    // API 基础路径
    const apiBase = window.apiUrl('/dbclient/api');

    // 工具函数
    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    async function fetchJSON(url) {
        const res = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return await res.json();
    }

    // 公开接口
    return {
        init: function() {
            console.log('DB Core initialized');
        },
        getCurrentDb: function() { return currentDb; },
        getCurrentTable: function() { return currentTable; },
        setCurrentDb: function(db) { currentDb = db; },
        setCurrentTable: function(table) { currentTable = table; },
        apiBase: apiBase,
        escapeHtml: escapeHtml,
        fetchJSON: fetchJSON
    };
})();