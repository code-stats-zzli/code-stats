// dbclient-view-sql.js - 按选中节点（表 / join）生成 SQL、复制、执行
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
        const node = core.getSelectedNode();
        if (!node) return '-- 未选中视图\n-- 点击画布上的表节点或关联节点查看对应 SQL';

        if (node.type === 'table') {
            const table = core.getTables().get(node.id);
            if (!table) return '-- 表节点不存在';
            const db = table.dbName;
            const tableName = table.name;
            const rules = (window.ViewTransform && window.ViewTransform.getTransformRulesForTable)
                ? window.ViewTransform.getTransformRulesForTable(tableName)
                : [];
            if (!rules || rules.length === 0) {
                return 'SELECT * FROM `' + db + '`.`' + tableName + '`;';
            }
            const allFields = table.fields.map(f => '`' + f.columnName + '`').join(', ');
            const transformFields = rules.map(rule => {
                let expr = rule.expression || '';
                expr = expr.replace(/\{field\}/g, rule.sourceField);
                expr = expr.replace(new RegExp('\\b' + rule.sourceField + '\\b', 'g'), '`' + rule.sourceField + '`');
                const alias = rule.alias ? ' AS `' + rule.alias + '`' : '';
                return expr + alias;
            }).join(', ');
            return 'SELECT ' + allFields + ', ' + transformFields + ' FROM `' + db + '`.`' + tableName + '`;';
        }

        if (node.type === 'join') {
            const jNode = core.getJoinNodes().get(node.id);
            if (!jNode) return '-- 关联节点不存在';
            const tables = core.getTables();
            const leftTable = tables.get(jNode.leftTable);
            const rightTable = tables.get(jNode.rightTable);
            if (!leftTable || !rightTable) return '-- 关联表不存在';
            const leftRef = '`' + leftTable.dbName + '`.`' + jNode.leftTable + '`';
            const rightRef = '`' + rightTable.dbName + '`.`' + jNode.rightTable + '`';
            const onClause =
                '`' + jNode.leftTable + '`.`' + jNode.leftField + '` ' +
                jNode.operator + ' ' +
                '`' + jNode.rightTable + '`.`' + jNode.rightField + '`';
            return 'SELECT * FROM ' + leftRef + ' ' + jNode.joinType + ' ' + rightRef + ' ON ' + onClause + ';';
        }

        return '-- 未知节点类型';
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
        const sql = sqlTextarea ? sqlTextarea.value : '';
        if (!sql || sql.startsWith('--')) {
            alert('没有有效的 SQL 语句');
            return;
        }
        const sqlTabBtn = document.querySelector('.tab-btn[data-tab="sql"]');
        if (sqlTabBtn) sqlTabBtn.click();
        const sqlEditor = document.getElementById('sqlEditor');
        if (sqlEditor) {
            sqlEditor.value = sql;
            setTimeout(function() {
                const executeBtn = document.getElementById('executeSqlBtn');
                if (executeBtn) executeBtn.click();
            }, 100);
        }
    }

    return { initSql, updateSql };
})();

// ★ 关键修复：挂载到 window，供 dbclient-view.js 中 window.ViewSql 判断使用
window.ViewSql = ViewSql;