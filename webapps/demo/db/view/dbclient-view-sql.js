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

    // 生成表/视图引用（表名 → `db`.`table`；join 名 → `join名`）
    function buildTableRef(name) {
        const t = core.getTables().get(name);
        if (t) return '`' + t.dbName + '`.`' + t.name + '`';
        return '`' + name + '`';
    }

    // 生成字段前缀（表用表名，join 用 join 名）
    function fieldRef(ownerName, fieldName) {
        return '`' + ownerName + '`.`' + fieldName + '`';
    }

    function generateViewSql() {
        const node = core.getSelectedNode();
        if (!node) return '-- 未选中视图\n-- 点击画布上的表节点或关联节点查看对应 SQL';

        if (node.type === 'table') {
            const table = core.getTables().get(node.id);
            if (!table) return '-- 表节点不存在';
            const rules = (window.ViewTransform && window.ViewTransform.getTransformRulesForTable)
                ? window.ViewTransform.getTransformRulesForTable(table.name)
                : [];
            if (!rules || rules.length === 0) {
                return 'SELECT * FROM `' + table.dbName + '`.`' + table.name + '`;';
            }
            const allFields = (table.fields || []).map(f => '`' + f.columnName + '`').join(', ');
            const transformFields = rules.map(rule => {
                let expr = rule.expression || '';
                expr = expr.replace(/\{field\}/g, rule.sourceField);
                expr = expr.replace(new RegExp('\\b' + rule.sourceField + '\\b', 'g'), '`' + rule.sourceField + '`');
                const alias = rule.alias ? ' AS `' + rule.alias + '`' : '';
                return expr + alias;
            }).join(', ');
            return 'SELECT ' + allFields + ', ' + transformFields + ' FROM `' + table.dbName + '`.`' + table.name + '`;';
        }

        if (node.type === 'join') {
            const jNode = core.getJoinNodes().get(node.id);
            if (!jNode) return '-- 关联节点不存在';

            const leftRef = buildTableRef(jNode.leftTable);
            const rightRef = buildTableRef(jNode.rightTable);
            const onClause =
                fieldRef(jNode.leftTable, jNode.leftField) + ' ' +
                jNode.operator + ' ' +
                fieldRef(jNode.rightTable, jNode.rightField);

            return 'SELECT * FROM ' + leftRef + ' ' + jNode.joinType + ' ' + rightRef +
                ' ON ' + onClause + ';';
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

window.ViewSql = ViewSql;