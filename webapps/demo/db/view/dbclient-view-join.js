// dbclient-view-join.js - Ctrl 多选两表后右键 → 创建关联视图
const ViewJoin = (function() {
    let core = null;
    let refreshCallback = null;
    let modal = null;
    let leftTableName = null;
    let rightTableName = null;

    function init(coreInstance, onRefresh) {
        core = coreInstance;
        refreshCallback = onRefresh;

        modal = document.getElementById('joinModal');
        if (!modal) {
            console.warn('[ViewJoin] joinModal 元素未找到');
            return;
        }

        const confirmBtn = document.getElementById('confirmJoinBtn');
        const cancelBtn = document.getElementById('cancelJoinBtn');
        const closeBtn = document.getElementById('closeJoinModalBtn');

        if (confirmBtn) confirmBtn.addEventListener('click', confirmJoin);
        if (cancelBtn) cancelBtn.addEventListener('click', closeModal);
        if (closeBtn) closeBtn.addEventListener('click', closeModal);
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeModal();
        });
    }

    function openJoinModal() {
        const multi = core.getMultiSelected();
        const tablesInMulti = Array.from(multi)
            .filter(k => k.startsWith('table:'))
            .map(k => k.substring(6));

        if (tablesInMulti.length !== 2) {
            alert('请先 Ctrl 选中两个表（按住 Ctrl 键依次点击两个表）');
            return;
        }

        const tablesMap = core.getTables();
        const t1 = tablesMap.get(tablesInMulti[0]);
        const t2 = tablesMap.get(tablesInMulti[1]);
        if (!t1 || !t2) {
            alert('选中的表不存在');
            return;
        }

        leftTableName = t1.name;
        rightTableName = t2.name;

        document.getElementById('joinLeftTable').textContent = t1.name;
        document.getElementById('joinRightTable').textContent = t2.name;

        const leftFieldSelect = document.getElementById('joinLeftField');
        const rightFieldSelect = document.getElementById('joinRightField');

        leftFieldSelect.innerHTML = t1.fields.map(f =>
            '<option value="' + escapeHtml(f.columnName) + '">' + escapeHtml(f.columnName) + ' (' + escapeHtml(f.typeName || '') + ')</option>'
        ).join('') || '<option disabled>无字段</option>';

        rightFieldSelect.innerHTML = t2.fields.map(f =>
            '<option value="' + escapeHtml(f.columnName) + '">' + escapeHtml(f.columnName) + ' (' + escapeHtml(f.typeName || '') + ')</option>'
        ).join('') || '<option disabled>无字段</option>';

        document.getElementById('joinAlias').value = '';
        document.getElementById('joinTypeSelect').value = 'INNER JOIN';
        document.getElementById('joinOperator').value = '=';

        modal.style.display = 'flex';
    }

    function confirmJoin() {
        if (!modal) return;
        const joinType = document.getElementById('joinTypeSelect').value;
        const leftField = document.getElementById('joinLeftField').value;
        const rightField = document.getElementById('joinRightField').value;
        const operator = document.getElementById('joinOperator').value;
        const alias = document.getElementById('joinAlias').value.trim();

        if (!leftField || !rightField) {
            alert('请选择关联字段');
            return;
        }

        const tablesMap = core.getTables();
        const leftTable = tablesMap.get(leftTableName);
        const rightTable = tablesMap.get(rightTableName);
        if (!leftTable || !rightTable) {
            alert('表不存在');
            return;
        }

        // 计算 join 节点位置（在两表右侧）
        const x = Math.max(leftTable.x, rightTable.x) + 300;
        const y = (leftTable.y + rightTable.y) / 2;

        const jid = core.getNextJoinId();
        const jNode = {
            id: jid,
            leftTable: leftTableName,
            rightTable: rightTableName,
            joinType: joinType,
            leftField: leftField,
            rightField: rightField,
            operator: operator,
            alias: alias,
            x: x,
            y: y,
            nodeGroup: null,
            edges: []
        };

        core.getJoinNodes().set(jid, jNode);

        // 清空多选，选中新建的 join 节点
        core.clearMultiSelect();
        core.setSelectedNode('join', jid);

        if (refreshCallback) refreshCallback();
        if (window.ViewSql) window.ViewSql.updateSql();

        closeModal();
    }

    function closeModal() {
        if (modal) modal.style.display = 'none';
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    return { init, openJoinModal };
})();