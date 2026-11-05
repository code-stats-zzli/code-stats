// 从当前页面 URL 中提取 context path
// 例如 http://localhost:8080/codestats/face/index.html -> /codestats
export function getContextPath() {
    const path = window.location.pathname;
    const parts = path.split('/');
    if (parts.length > 1 && parts[1]) {
        return '/' + parts[1];
    }
    return '';
}

export const CONTEXT_PATH = getContextPath();

/**
 * 构建完整的 API URL
 * @param {string} path - 以 / 开头的 API 路径，如 '/api/info'
 * @returns {string} 完整 URL
 */
export function apiUrl(path) {
    const normalized = path.startsWith('/') ? path : '/' + path;
    return CONTEXT_PATH + normalized;
}

/**
 * 封装的 fetch 函数，自动添加 context path
 */
export async function apiFetch(path, options = {}) {
    const url = apiUrl(path);
    const response = await fetch(url, options);
    if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    return response;
}

// 挂载到 window 方便调试
window.__API_BASE__ = CONTEXT_PATH;