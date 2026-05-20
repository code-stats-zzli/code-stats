// ========== 属性Tab模块：显示表结构 ==========
const PropertiesModule = (function() {
    let propertiesContent = null;

    async function loadProperties(dbName, tableName) {
        if (!propertiesContent) {
            console.error('propertiesContent 未初始化');
            return;
        }
        if (!dbName || !tableName) {
            propertiesContent.innerHTML = '<h2>请从左侧选择一张表</h2>';
            return;
        }
        propertiesContent.innerHTML = '<div class="loading">加载列信息...</div>';
        try {
            const columns = await DB.fetchJSON(DB.apiBase + '/columns?db=' + encodeURIComponent(dbName) + '&table=' + encodeURIComponent(tableName));
            renderProperties(columns, dbName, tableName);
        } catch (err) {
            console.error('加载列信息失败', err);
            propertiesContent.innerHTML = `<div class="error">加载失败: ${err.message}</div>`;
        }
    }

    function renderProperties(columns, dbName, tableName) {
        let html = `<h2>${DB.escapeHtml(dbName)}.${DB.escapeHtml(tableName)}</h2>`;
        html += '<table class="props-table"><tbody>';
        html += '<tr><td>引擎</td><td>InnoDB</td></tr>';
        html += '<tr><td>字符集</td><td>utf8mb4</td></tr>';
        html += '<tr><td>排序规则</td><td>utf8mb4_0900_ai_ci</td></tr>';
        html += '</tbody></table>';

        html += '<h3>列信息</h3>';
        html += '<table class="data-table"><thead><tr>';
        html += '<th>列名</th><th>类型</th><th>大小</th><th>可空</th><th>自增</th><th>备注</th>';
        html += '</tr></thead><tbody>';

        for (const col of columns) {
            html += `<tr>
                <td>${DB.escapeHtml(col.columnName)}</td>
                <td>${DB.escapeHtml(col.typeName)}</td>
                <td>${col.columnSize || ''}</td>
                <td>${col.nullable === 'YES' ? '是' : '否'}</td>
                <td>${col.autoIncrement === 'YES' ? '是' : '否'}</td>
                <td>${DB.escapeHtml(col.remarks || '-')}</td>
            </tr>`;
        }
        html += '</tbody></table>';
        propertiesContent.innerHTML = html;
    }

    function clear() {
        if (propertiesContent) propertiesContent.innerHTML = '<h2>请从左侧选择一张表</h2>';
    }

    function init() {
        propertiesContent = document.getElementById('propertiesContent');
        if (!propertiesContent) {
            console.error('PropertiesModule: propertiesContent not found');
        }
    }

    return { init, loadProperties, clear };
})();

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => PropertiesModule.init());
} else {
    PropertiesModule.init();
}
window.PropertiesModule = PropertiesModule;