// ==================== 全局 Context Path 工具 ====================
(function() {
    // 获取当前应用的 Context Path，例如 /codestats 或空字符串
    function getContextPath() {
        var path = window.location.pathname;
        var parts = path.split('/');
        if (parts.length > 1 && parts[1]) {
            return '/' + parts[1];
        }
        return '';
    }
    window.CONTEXT_PATH = getContextPath();

    // 便捷方法：拼接 API 路径
    window.apiUrl = function(path) {
        if (path.startsWith('/')) {
            return window.CONTEXT_PATH + path;
        }
        return window.CONTEXT_PATH + '/' + path;
    };

    console.log('Global JS loaded, CONTEXT_PATH =', window.CONTEXT_PATH);
})();