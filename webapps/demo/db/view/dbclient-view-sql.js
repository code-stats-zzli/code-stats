// dbclient-view-sql.js - 按选中节点（表 / join）生成 SQL、复制、执行
// 视图 SQL 结构：
//   SELECT * FROM (
//     SELECT
//       a.<字段> AS a_<字段>,
//       b.<字段> AS b_<字段>
//     FROM <左输入> a
//     JOIN <右输入> b ON a.<x> = b.<y>
//   ) AS 视图名;
// 其中 <左输入>/<右输入> 若是表 → `db`.`table`；若是视图 → (视图完整 SQL) 作为子查询。
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

    // 渲染 FROM / JOIN 子句中的输入片段（不含别名）
    function renderInputFragment(input) {
        if (input.kind === 'table') {
            return '`' + input.dbName + '`.`' + input.tableName + '`';
        }
        const j = core.getJoinNodes().get(input.joinId);
        if (!j) return '';
        // 视图作为子查询（使用视图完整 SQL）
        return '(\n' + buildFullViewSql(j) + '\n)';
    }

    // 视图内层 SELECT
    function buildInnerSql(node) {
        const LEFT_ALIAS = 'a';
        const RIGHT_ALIAS = 'b';

        const selectParts = (node.outputFields || []).map(f => {
            const alias = f.source === 'left' ? LEFT_ALIAS : RIGHT_ALIAS;
            return alias + '.`' + f.sourceField + '` AS `' + f.name + '`';
        });

        const fromFragment = renderInputFragment(node.leftInput) + ' ' + LEFT_ALIAS;
        const joinFragment =
            node.joinType + ' ' +
            renderInputFragment(node.rightInput) + ' ' + RIGHT_ALIAS +
            ' ON ' + LEFT_ALIAS + '.`' + node.leftField + '` ' +
            node.operator + ' ' + RIGHT_ALIAS + '.`' + node.rightField + '`';

        return `SELECT\n  ${selectParts.join(',\n  ')}\nFROM ${fromFragment}\n${joinFragment}`;
    }

    // 视图完整 SQL（外层套 SELECT * FROM (...) AS 视图名）
    function buildFullViewSql(node) {
        return `SELECT * FROM (\n${buildInnerSql(node)}\n) AS \`${node.name}\``;
    }

    // 单表 SQL（含字段转换规则时）
    function buildTableSql(table) {
        const rules = (window.ViewTransform && window.ViewTransform.getTransformRulesForTable)
            ? window.ViewTransform.getTransformRulesForTable(table.name) : [];
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
            return buildFullViewSql(jNode) + ';';
        }
        return '-- 未知节点类型';
    }

    function updateSql() {
        if (sqlTextarea) sqlTextarea.value = generateViewSql();
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
        if (!sql || sql.startsWith('--')) { alert('没有有效的 SQL 语句'); return; }
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