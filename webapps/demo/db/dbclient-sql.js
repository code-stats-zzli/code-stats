// ========== SQL查询Tab模块 ==========
const SqlModule = (function() {
    let sqlEditor = null;
    let sqlResultArea = null;
    let exampleSqlBtn = null;
    let executeSqlBtn = null;

    let currentDbHint = null;
    let currentTableHint = null;

    function updateCurrentTableHint(db, table) {
        currentDbHint = db;
        currentTableHint = table;
    }

    function generateExampleSql() {
        if (currentDbHint && currentTableHint) {
            return `SELECT * FROM \`${currentTableHint}\` LIMIT 20`;
        } else {
            return 'SELECT 1 AS test;';
        }
    }

    function fillExampleSql() {
        if (!sqlEditor) return;
        const exampleSql = generateExampleSql();
        sqlEditor.value = exampleSql;
    }

    async function executeSql() {
        if (!sqlEditor || !sqlResultArea) return;
        const sql = sqlEditor.value.trim();
        if (!sql) {
            alert('请输入SQL语句');
            return;
        }
        if (!currentDbHint) {
            alert('请先从左侧选择一个数据库');
            return;
        }

        sqlResultArea.innerHTML = '<div class="loading">执行中...</div>';
        try {
            const resp = await fetch(DB.apiBase + '/sqlQuery', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ db: currentDbHint, sql: sql })
            });
            const data = await resp.json();
            if (data.error) {
                sqlResultArea.innerHTML = `<div class="error">${data.error}</div>`;
                return;
            }

            if (Array.isArray(data) && data.length > 0) {
                const columns = Object.keys(data[0]);
                let html = '<table class="sql-result-table"><thead><tr>';
                columns.forEach(col => html += `<th>${DB.escapeHtml(col)}</th>`);
                html += '</tr></thead><tbody>';
                data.forEach(row => {
                    html += '<tr>';
                    columns.forEach(col => {
                        let val = row[col];
                        if (val === null || val === undefined) val = '';
                        html += `<td>${DB.escapeHtml(String(val))}</td>`;
                    });
                    html += '</tr>';
                });
                html += '</tbody></table>';
                sqlResultArea.innerHTML = html;
            } else {
                sqlResultArea.innerHTML = '<p>查询成功，无结果返回。</p>';
            }
        } catch (err) {
            sqlResultArea.innerHTML = `<div class="error">请求失败: ${err.message}</div>`;
        }
    }

    return {
        init: function() {
            sqlEditor = document.getElementById('sqlEditor');
            sqlResultArea = document.getElementById('sqlResultArea');
            exampleSqlBtn = document.getElementById('exampleSqlBtn');
            executeSqlBtn = document.getElementById('executeSqlBtn');

            if (exampleSqlBtn) exampleSqlBtn.addEventListener('click', fillExampleSql);
            if (executeSqlBtn) executeSqlBtn.addEventListener('click', executeSql);

            // 设置默认示例
            if (sqlEditor && sqlEditor.value === '') {
                sqlEditor.value = 'SELECT 1 AS test;\n-- 点击"示例SQL"按钮可获取当前表的示例查询';
            }
        },
        updateCurrentTableHint: updateCurrentTableHint
    };
})();