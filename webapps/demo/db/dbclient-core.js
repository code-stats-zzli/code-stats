// ========== 公共核心模块：API请求、工具函数、全局状态 ==========
const DB = (function() {
    // 私有状态
    let currentDb = null;
    let currentTable = null;
    let listeners = [];

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

    // 监听数据库/表变化
    function subscribe(fn) {
        listeners.push(fn);
    }
    function notify() {
        listeners.forEach(fn => fn(currentDb, currentTable));
    }

    // 公开接口
    return {
        init: function() {
            console.log('DB Core initialized');
        },
        getCurrentDb: function() { return currentDb; },
        getCurrentTable: function() { return currentTable; },
        setCurrentDb: function(db) {
            if (currentDb !== db) {
                currentDb = db;
                currentTable = null;   // 切换数据库时重置表
                notify();
            }
        },
        setCurrentTable: function(table) {
            if (currentTable !== table) {
                currentTable = table;
                notify();
            }
        },
        subscribe: subscribe,
        apiBase: apiBase,
        escapeHtml: escapeHtml,
        fetchJSON: fetchJSON
    };
})();