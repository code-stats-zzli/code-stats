// dbclient-view-join.js - Ctrl 多选两个表/视图后右键 → 创建关联视图
const ViewJoin = (function() {
    let core = null;
    let refreshCallback = null;
    let modal = null;
    let selectedNodes = [];   // 保存当前进入弹窗时的两个节点快照

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

    // 从 multi 中提取两个节点（表或 join），保持顺序
    function getSelectedNodes() {
        const multi = core.getMultiSelected();
        const nodes = [];
        multi.forEach(key => {
            const parts = key.split(':');
            const type = parts[0];
            const idRaw = parts.slice(1).join(':');
            if (type === 'table') {
                const n = core.findNodeByName(idRaw);
                if (n) nodes.push(n);
            } else if (type === 'join') {
                const jid = parseInt(idRaw);
                const j = core.getJoinNodes().get(jid);
                if (j) {
                    const n = core.findNodeByName(j.name);
                    if (n) nodes.push(n);
                }
            }
        });
        return nodes;
    }

    function openJoinModal() {
        const nodes = getSelectedNodes();
        if (nodes.length !== 2) {
            alert('请先 Ctrl 选中两个表或视图（按住 Ctrl 键依次点击两个表/视图）');
            return;
        }

        selectedNodes = nodes;
        const [t1, t2] = nodes;

        document.getElementById('joinLeftTable').textContent = t1.name;
        document.getElementById('joinRightTable').textContent = t2.name;

        const leftFieldSelect = document.getElementById('joinLeftField');
        const rightFieldSelect = document.getElementById('joinRightField');

        leftFieldSelect.innerHTML = (t1.fields || []).map(f =>
            '<option value="' + escapeHtml(f.columnName) + '">' + escapeHtml(f.columnName) + ' (' + escapeHtml(f.typeName || '') + ')</option>'
        ).join('') || '<option disabled>无字段</option>';

        rightFieldSelect.innerHTML = (t2.fields || []).map(f =>
            '<option value="' + escapeHtml(f.columnName) + '">' + escapeHtml(f.columnName) + ' (' + escapeHtml(f.typeName || '') + ')</option>'
        ).join('') || '<option disabled>无字段</option>';

        document.getElementById('joinAlias').value = '';
        document.getElementById('joinTypeSelect').value = 'INNER JOIN';
        document.getElementById('joinOperator').value = '=';

        modal.style.display = 'flex';
    }

    // 名称是否已被占用
    function isNameUsed(n) {
        if (core.getTables().has(n)) return true;
        for (let j of core.getJoinNodes().values()) {
            if (j.name === n) return true;
        }
        return false;
    }

    function confirmJoin() {
        if (!modal || selectedNodes.length !== 2) return;
        const joinType = document.getElementById('joinTypeSelect').value;
        const leftField = document.getElementById('joinLeftField').value;
        const rightField = document.getElementById('joinRightField').value;
        const operator = document.getElementById('joinOperator').value;
        const aliasInput = document.getElementById('joinAlias').value.trim();

        if (!leftField || !rightField) {
            alert('请选择关联字段');
            return;
        }

        const [leftNode, rightNode] = selectedNodes;

        // 名称：用户填的优先，否则默认 左表名_关联，重名自动加序号
        let name = aliasInput;
        if (!name) {
            const base = leftNode.name + '_关联';
            if (!isNameUsed(base)) {
                name = base;
            } else {
                let i = 1;
                while (isNameUsed(base + '_' + i)) i++;
                name = base + '_' + i;
            }
        } else if (isNameUsed(name)) {
            alert('名称 "' + name + '" 已被占用，请换一个或留空使用默认名称');
            return;
        }

        // 合并字段（去重：同名同类型的字段只保留一份）
        const seen = new Set();
        const mergedFields = [];
        const addFields = (fields) => {
            (fields || []).forEach(f => {
                const key = f.columnName + '|' + (f.typeName || '');
                if (seen.has(key)) return;
                seen.add(key);
                mergedFields.push({ columnName: f.columnName, typeName: f.typeName });
            });
        };
        addFields(leftNode.fields);
        addFields(rightNode.fields);

        const jid = core.getNextJoinId();
        const jNode = {
            id: jid,
            name: name,
            dbName: leftNode.dbName,
            leftTable: leftNode.name,
            rightTable: rightNode.name,
            joinType: joinType,
            leftField: leftField,
            rightField: rightField,
            operator: operator,
            alias: aliasInput,
            fields: mergedFields,
            x: Math.max(leftNode.x, rightNode.x) + 300,
            y: (leftNode.y + rightNode.y) / 2,
            nodeGroup: null,
            edges: []
        };

        core.getJoinNodes().set(jid, jNode);
        core.clearMultiSelect();
        core.setSelectedNode('join', jid);

        if (refreshCallback) refreshCallback();
        if (window.ViewSql) window.ViewSql.updateSql();
        closeModal();
    }

    function closeModal() {
        if (modal) modal.style.display = 'none';
        selectedNodes = [];
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    return { init, openJoinModal };
})();

window.ViewJoin = ViewJoin;