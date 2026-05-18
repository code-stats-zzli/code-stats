// dbclient-view-sql.js - SQL 生成、复制、执行
const ViewSql = (function() {
    let sqlTextarea = null;
    let copySqlBtn = null;
    let executeSqlBtn = null;
    let core = null;

    function initSql(coreInstance) {
        core = coreInstance;
        sqlTextarea = document.getElementById('viewSqlText');
        copySqlBtn = document.getElementById('copySqlBtn');
        executeSqlBtn = document.getElementById('executeSqlBtnView');
        if (copySqlBtn) copySqlBtn.addEventListener('click', copySql);
        if (executeSqlBtn) executeSqlBtn.addEventListener('click', executeCurrentViewSql);
        updateSql();
    }

    function generateViewSql() {
        const active = core.getActiveTableForTransform();
        if (!active) return '';
        const { db, table, columns } = active;
        // 获取转换规则（从转换节点中获取）
        let rules = [];
        if (window.ViewTransform && window.ViewTransform.getTransformRulesForTable) {
            rules = window.ViewTransform.getTransformRulesForTable(table);
        }
        if (rules.length === 0) {
            return `SELECT * FROM \`${db}\`.\`${table}\`;`;
        }
        const allFields = columns.map(c => `\`${c.columnName}\``).join(', ');
        const transformFields = rules.map(rule => {
            let expr = rule.expression;
            expr = expr.replace(/\{field\}/g, rule.sourceField);
            expr = expr.replace(new RegExp(`\\b${rule.sourceField}\\b`, 'g'), `\`${rule.sourceField}\``);
            const alias = rule.alias ? ` AS \`${rule.alias}\`` : '';
            return `${expr}${alias}`;
        }).join(', ');
        return `SELECT ${allFields}, ${transformFields} FROM \`${db}\`.\`${table}\`;`;
    }

    function updateSql() {
        if (sqlTextarea) {
            sqlTextarea.value = generateViewSql();
        }
    }

    function copySql() {
        if (sqlTextarea) {
            sqlTextarea.select();
            document.execCommand('copy');
            alert('SQL 已复制到剪贴板');
        }
    }

    function executeCurrentViewSql() {
        const sql = sqlTextarea?.value;
        if (!sql || sql.startsWith('--')) {
            alert('没有有效的 SQL 语句');
            return;
        }
        document.querySelector('.tab-btn[data-tab="sql"]').click();
        const sqlEditor = document.getElementById('sqlEditor');
        if (sqlEditor) {
            sqlEditor.value = sql;
            setTimeout(() => {
                const executeBtn = document.getElementById('executeSqlBtn');
                if (executeBtn) executeBtn.click();
            }, 100);
        }
    }

    return { initSql, updateSql };
})();