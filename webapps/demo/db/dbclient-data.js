// ========== 数据Tab模块：分页显示表数据 ==========
const DataModule = (function() {
    let dataContent = null;
    let paginationBar = null;

    let currentPage = 1;
    let pageSize = 20;
    let totalRecords = 0;
    let currentDb = null;
    let currentTable = null;

    async function loadTableData() {
        if (!dataContent || !paginationBar) {
            console.error('DataModule 元素未找到');
            return;
        }
        if (!currentDb || !currentTable) {
            dataContent.innerHTML = '<p>请从左侧选择一张表</p>';
            paginationBar.innerHTML = '';
            return;
        }

        dataContent.innerHTML = '<div class="loading">加载数据中...</div>';
        try {
            const url = `${DB.apiBase}/tableData?db=${encodeURIComponent(currentDb)}&table=${encodeURIComponent(currentTable)}&page=${currentPage}&pageSize=${pageSize}`;
            const result = await DB.fetchJSON(url);
            if (result.error) {
                dataContent.innerHTML = `<div class="error">${result.error}</div>`;
                paginationBar.innerHTML = '';
                return;
            }
            totalRecords = result.total || 0;
            renderDataTable(result.records || []);
            renderPagination();
        } catch (err) {
            console.error('加载表数据失败', err);
            dataContent.innerHTML = `<div class="error">加载失败: ${err.message}</div>`;
            paginationBar.innerHTML = '';
        }
    }

    function renderDataTable(records) {
        if (!records || records.length === 0) {
            dataContent.innerHTML = '<p>该表无数据</p>';
            return;
        }
        const columns = Object.keys(records[0]);
        let html = '<div style="overflow-x: auto;"><table class="data-table"><thead><tr>';
        columns.forEach(col => html += `<th>${DB.escapeHtml(col)}</th>`);
        html += '</tr></thead><tbody>';

        records.forEach(row => {
            html += '<tr>';
            columns.forEach(col => {
                let val = row[col];
                if (val === null || val === undefined) val = '';
                html += `<td>${DB.escapeHtml(String(val))}</td>`;
            });
            html += '</tr>';
        });
        html += '</tbody></table></div>';
        dataContent.innerHTML = html;
    }

    function renderPagination() {
        const totalPages = Math.ceil(totalRecords / pageSize);
        if (totalPages <= 1) {
            paginationBar.innerHTML = '';
            return;
        }

        let html = '<div class="pagination">';
        html += `<button ${currentPage === 1 ? 'disabled' : ''} data-page="${currentPage-1}">上一页</button>`;
        for (let i = 1; i <= totalPages; i++) {
            if (i === currentPage) {
                html += `<button class="active" disabled>${i}</button>`;
            } else {
                html += `<button data-page="${i}">${i}</button>`;
            }
        }
        html += `<button ${currentPage === totalPages ? 'disabled' : ''} data-page="${currentPage+1}">下一页</button>`;
        html += ` <span>共 ${totalRecords} 条</span>`;
        html += '</div>';
        paginationBar.innerHTML = html;

        paginationBar.querySelectorAll('button[data-page]').forEach(btn => {
            btn.addEventListener('click', () => {
                const page = parseInt(btn.dataset.page);
                if (!isNaN(page) && page !== currentPage) {
                    currentPage = page;
                    loadTableData();
                }
            });
        });
    }

    function resetAndLoad(db, table) {
        currentDb = db;
        currentTable = table;
        currentPage = 1;
        loadTableData();
    }

    function init() {
        dataContent = document.getElementById('dataContent');
        paginationBar = document.getElementById('paginationBar');
        if (!dataContent) console.error('DataModule: dataContent not found');
        if (!paginationBar) console.error('DataModule: paginationBar not found');
    }

    return { init, loadTableData, resetAndLoad };
})();

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => DataModule.init());
} else {
    DataModule.init();
}
window.DataModule = DataModule;