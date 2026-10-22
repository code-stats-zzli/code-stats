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

    // 单表 SQL（含字段转换规则时）
    function buildTableSql(table) {
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

    // ★ 关联视图 SQL：真实表平铺 + a/b/c 别名
    function buildJoinSql(jNode) {
        const tables = jNode.flatTables || [];
        const conditions = jNode.flatConditions || [];
        const outputFields = jNode.outputFields || [];

        if (!tables.length) return '-- 无表数据';

        const firstTable = tables[0];
        let sql = 'SELECT\n';

        if (outputFields.length > 0) {
            sql += '  ' + outputFields.map(f =>
                `${f.alias}.\`${f.columnName}\` AS \`${f.alias}_${f.columnName}\``
            ).join(',\n  ') + '\n';
        } else {
            sql += '  *\n';
        }

        sql += `FROM \`${firstTable.dbName}\`.\`${firstTable.name}\` ${firstTable.alias}\n`;

        // 依次 JOIN 后续表，用条件匹配
        for (let i = 1; i < tables.length; i++) {
            const t = tables[i];
            // 找引用该表别名的条件
            const cond = conditions.find(c =>
                c.leftAlias === t.alias || c.rightAlias === t.alias
            );
            if (!cond) {
                // 没匹配到条件时退化为 CROSS JOIN
                sql += `CROSS JOIN \`${t.dbName}\`.\`${t.name}\` ${t.alias}\n`;
                continue;
            }
            const jt = cond.joinType || 'INNER JOIN';
            sql += `${jt} \`${t.dbName}\`.\`${t.name}\` ${t.alias} ON `;
            sql += `${cond.leftAlias}.\`${cond.leftField}\` ${cond.operator} ${cond.rightAlias}.\`${cond.rightField}\`\n`;
        }

        return sql.replace(/\n+$/, '') + ';';
    }

    function generateViewSql() {
        const node = core.getSelectedNode();
        if (!node) return '-- 未选中视图\n-- 点击画布上的表节点或关联节点查看对应 SQL';

        if (node.type === 'table') {
            const table = core.getTables().get(node.id);
            if (!table) return '-- 表节点不存在';
            return buildTableSql(table);
        }

        if (node.type === 'join') {
            const jNode = core.getJoinNodes().get(node.id);
            if (!jNode) return '-- 关联节点不存在';
            return buildJoinSql(jNode);
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