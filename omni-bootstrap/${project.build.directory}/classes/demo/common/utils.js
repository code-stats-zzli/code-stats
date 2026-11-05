// 公用工具函数：分页、表格渲染、防抖等

(function(window) {
    const Utils = {};

    // 转义HTML
    Utils.escapeHtml = function(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, function(m) {
            if (m === '&') return '&amp;';
            if (m === '<') return '&lt;';
            if (m === '>') return '&gt;';
            return m;
        });
    };

    // 格式化文件大小
    Utils.formatSize = function(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    };

    // 生成分页HTML
    Utils.renderPagination = function(currentPage, totalPages, onPageChange) {
        let html = '<div class="pagination">';
        if (currentPage > 1) {
            html += `<button data-page="${currentPage-1}">上一页</button>`;
        }
        for (let i = 1; i <= totalPages; i++) {
            if (i === currentPage) {
                html += `<button class="active" disabled>${i}</button>`;
            } else {
                html += `<button data-page="${i}">${i}</button>`;
            }
        }
        if (currentPage < totalPages) {
            html += `<button data-page="${currentPage+1}">下一页</button>`;
        }
        html += '</div>';
        const container = document.createElement('div');
        container.innerHTML = html;
        container.querySelectorAll('button[data-page]').forEach(btn => {
            btn.addEventListener('click', () => onPageChange(parseInt(btn.dataset.page)));
        });
        return container;
    };

    // 通用表格渲染（数据数组，列配置）
    Utils.renderTable = function(container, columns, data) {
        if (!container) return;
        if (!data.length) {
            container.innerHTML = '<div class="empty-tip">暂无数据</div>';
            return;
        }
        let html = '<table class="data-table"><thead><tr>';
        columns.forEach(col => {
            html += `<th>${Utils.escapeHtml(col.title)}</th>`;
        });
        html += '</tr></thead><tbody>';
        data.forEach(row => {
            html += '<tr>';
            columns.forEach(col => {
                let value = row[col.key];
                if (value === undefined || value === null) value = '';
                html += `<td>${Utils.escapeHtml(String(value))}</td>`;
            });
            html += '</tr>';
        });
        html += '</tbody></table>';
        container.innerHTML = html;
    };

    // 防抖
    Utils.debounce = function(fn, delay) {
        let timer = null;
        return function() {
            clearTimeout(timer);
            timer = setTimeout(() => fn.apply(this, arguments), delay);
        };
    };

    window.Utils = Utils;
})(window);