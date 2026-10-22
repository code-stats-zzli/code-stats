// dbclient-view-join.js - Ctrl 多选两个表/视图后右键 → 创建关联视图
// 视图设计：
//   每个视图是独立结果集，内部所有输入（表或视图）统一按 a/b 分配字母，
//   字段 AS 成 字母_字段名（a_id、b_user_id 等）；
//   视图再作为输入时，整体作为子查询嵌入，用对外字段名参与 JOIN。
// 弹窗支持拖动，内容超出时内部滚动。
const ViewJoin = (function() {
    let core = null;
    let refreshCallback = null;
    let modal = null;
    let currentState = null;

    function init(coreInstance, onRefresh) {
        core = coreInstance;
        refreshCallback = onRefresh;
        modal = document.getElementById('joinModal');
        if (!modal) {
            console.warn('[ViewJoin] joinModal 元素未找到');
            return;
        }

        document.getElementById('confirmJoinBtn').addEventListener('click', confirmJoin);
        document.getElementById('cancelJoinBtn').addEventListener('click', closeModal);
        document.getElementById('closeJoinModalBtn').addEventListener('click', closeModal);
        modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });

        const selAll = document.getElementById('selectAllOutputBtn');
        const selNone = document.getElementById('selectNoneOutputBtn');
        if (selAll) selAll.addEventListener('click', () => {
            document.querySelectorAll('#outputFieldsContainer input.output-field-cb')
                .forEach(cb => cb.checked = true);
        });
        if (selNone) selNone.addEventListener('click', () => {
            document.querySelectorAll('#outputFieldsContainer input.output-field-cb')
                .forEach(cb => cb.checked = false);
        });

        // ★ 弹窗拖动
        setupModalDrag(modal);
    }

    // ★ 通用弹窗拖动：按住 header 拖动整个 content
    function setupModalDrag(modalEl) {
        const content = modalEl.querySelector('.modal-content');
        const header = modalEl.querySelector('.modal-header');
        if (!content || !header) return;
        let dragging = false;
        let startX = 0, startY = 0, baseX = 0, baseY = 0;

        header.addEventListener('mousedown', (e) => {
            if (e.button !== 0) return;
            // 点关闭按钮 / 输入控件时不拖动
            if (e.target.closest('button') || e.target.closest('input') ||
                e.target.closest('select') || e.target.closest('a')) return;
            dragging = true;
            startX = e.clientX;
            startY = e.clientY;
            baseX = 0;
            baseY = 0;
            const m = (content.style.transform || '').match(/translate\(([-\d.]+)px,\s*([-\d.]+)px\)/);
            if (m) {
                baseX = parseFloat(m[1]);
                baseY = parseFloat(m[2]);
            }
            content.style.animation = 'none';
            e.preventDefault();
        });

        document.addEventListener('mousemove', (e) => {
            if (!dragging) return;
            const dx = e.clientX - startX + baseX;
            const dy = e.clientY - startY + baseY;
            content.style.transform = `translate(${dx}px, ${dy}px)`;
        });

        document.addEventListener('mouseup', () => { dragging = false; });
    }

    // 打开前重置位置
    function resetModalPosition() {
        const content = modal && modal.querySelector('.modal-content');
        if (content) {
            content.style.transform = '';
            content.style.animation = '';
        }
    }

    function getInputFields(input) {
        if (input.kind === 'table') {
            const t = core.getTables().get(input.tableName);
            if (!t) return [];
            return (t.fields || []).map(f => ({
                display: `${input.tableName}.${f.columnName}`,
                ownerName: input.tableName,
                sourceField: f.columnName,
                typeName: f.typeName
            }));
        }
        const j = core.getJoinNodes().get(input.joinId);
        if (!j) return [];
        return (j.outputFields || []).map(f => ({
            display: `${j.name}.${f.name}`,
            ownerName: j.name,
            sourceField: f.name,
            typeName: f.typeName
        }));
    }

    function openJoinModal() {
        const multi = core.getMultiSelected();
        if (multi.size !== 2) {
            alert('请先 Ctrl 选中两个表或视图（按住 Ctrl 键依次点击两个）');
            return;
        }

        const nodes = [];
        multi.forEach(key => {
            const parts = key.split(':');
            const type = parts[0];
            const idRaw = parts.slice(1).join(':');
            if (type === 'table') {
                const n = core.findNodeByName(idRaw);
                if (n) nodes.push({ kind: 'table', name: n.name, node: n.node });
            } else if (type === 'join') {
                const jid = parseInt(idRaw);
                const j = core.getJoinNodes().get(jid);
                if (j) nodes.push({ kind: 'join', name: j.name, node: j });
            }
        });

        if (nodes.length !== 2) { alert('选中的节点无效'); return; }
        const [leftNode, rightNode] = nodes;

        const leftInput = leftNode.kind === 'table'
            ? { kind: 'table', dbName: leftNode.node.dbName, tableName: leftNode.node.name }
            : { kind: 'join', joinId: leftNode.node.id };
        const rightInput = rightNode.kind === 'table'
            ? { kind: 'table', dbName: rightNode.node.dbName, tableName: rightNode.node.name }
            : { kind: 'join', joinId: rightNode.node.id };

        const leftFields = getInputFields(leftInput);
        const rightFields = getInputFields(rightInput);

        if (!leftFields.length || !rightFields.length) {
            alert('左右输入至少一侧没有字段，无法创建关联');
            return;
        }

        document.getElementById('joinLeftTable').textContent = leftNode.name;
        document.getElementById('joinRightTable').textContent = rightNode.name;
        document.getElementById('joinTypeSelect').value = 'INNER JOIN';
        document.getElementById('joinOperator').value = '=';
        document.getElementById('joinAlias').value = '';

        const leftFieldSelect = document.getElementById('joinLeftField');
        leftFieldSelect.innerHTML = leftFields.map((f, idx) =>
            `<option value="${idx}">${f.ownerName}.${f.sourceField}  (${f.typeName || ''})</option>`
        ).join('');

        const rightFieldSelect = document.getElementById('joinRightField');
        rightFieldSelect.innerHTML = rightFields.map((f, idx) =>
            `<option value="${idx}">${f.ownerName}.${f.sourceField}  (${f.typeName || ''})</option>`
        ).join('');

        const outputContainer = document.getElementById('outputFieldsContainer');
        let html = '';
        leftFields.forEach((f, idx) => {
            html += `
            <label style="display:inline-flex; align-items:center; gap:6px; margin:3px 14px 3px 0;
                          font-size:13px; font-family:'JetBrains Mono',monospace; cursor:pointer;">
                <input type="checkbox" class="output-field-cb" data-source="left" data-field-idx="${idx}" checked>
                <span>${f.ownerName}.${f.sourceField}</span>
                <span style="color:#94a3b8; font-size:11px;">(${f.typeName || ''})</span>
            </label>`;
        });
        rightFields.forEach((f, idx) => {
            html += `
            <label style="display:inline-flex; align-items:center; gap:6px; margin:3px 14px 3px 0;
                          font-size:13px; font-family:'JetBrains Mono',monospace; cursor:pointer;">
                <input type="checkbox" class="output-field-cb" data-source="right" data-field-idx="${idx}" checked>
                <span>${f.ownerName}.${f.sourceField}</span>
                <span style="color:#94a3b8; font-size:11px;">(${f.typeName || ''})</span>
            </label>`;
        });
        outputContainer.innerHTML = html;

        currentState = { leftNode, rightNode, leftInput, rightInput, leftFields, rightFields };

        resetModalPosition();
        modal.style.display = 'flex';
    }

    function isNameUsed(n) {
        if (core.getTables().has(n)) return true;
        for (let j of core.getJoinNodes().values()) {
            if (j.name === n) return true;
        }
        return false;
    }

    function confirmJoin() {
        if (!currentState) return;
        const { leftNode, rightNode, leftInput, rightInput, leftFields, rightFields } = currentState;

        const joinType = document.getElementById('joinTypeSelect').value;
        const leftFieldIdx = parseInt(document.getElementById('joinLeftField').value);
        const rightFieldIdx = parseInt(document.getElementById('joinRightField').value);
        const operator = document.getElementById('joinOperator').value;
        const aliasInput = document.getElementById('joinAlias').value.trim();

        if (isNaN(leftFieldIdx) || isNaN(rightFieldIdx)) {
            alert('请选择关联字段'); return;
        }
        const leftJoinField = leftFields[leftFieldIdx].sourceField;
        const rightJoinField = rightFields[rightFieldIdx].sourceField;

        let name = aliasInput;
        if (!name) {
            const base = leftNode.name + '_关联';
            if (!isNameUsed(base)) name = base;
            else { let i = 1; while (isNameUsed(base + '_' + i)) i++; name = base + '_' + i; }
        } else if (isNameUsed(name)) {
            alert('名称 "' + name + '" 已被占用，请换一个或留空'); return;
        }

        const LEFT_ALIAS = 'a';
        const RIGHT_ALIAS = 'b';

        const outputFields = [];
        document.querySelectorAll('#outputFieldsContainer input.output-field-cb:checked').forEach(cb => {
            const source = cb.dataset.source;
            const fieldIdx = parseInt(cb.dataset.fieldIdx);
            const fields = source === 'left' ? leftFields : rightFields;
            const f = fields[fieldIdx];
            const alias = source === 'left' ? LEFT_ALIAS : RIGHT_ALIAS;
            const newName = alias + '_' + f.sourceField;
            outputFields.push({
                name: newName,
                typeName: f.typeName,
                source: source,
                sourceField: f.sourceField,
                ownerName: f.ownerName
            });
        });

        if (outputFields.length === 0) {
            alert('请至少勾选一个输出字段'); return;
        }

        const jid = core.getNextJoinId();
        const jNode = {
            id: jid,
            name: name,
            dbName: leftInput.kind === 'table' ? leftInput.dbName : (leftNode.node.dbName || ''),
            leftInput: leftInput,
            rightInput: rightInput,
            leftField: leftJoinField,
            rightField: rightJoinField,
            operator: operator,
            joinType: joinType,
            alias: aliasInput,
            fields: outputFields.map(f => ({ name: f.name, typeName: f.typeName })),
            outputFields: outputFields,
            leftTable: leftNode.name,
            rightTable: rightNode.name,
            x: Math.max(leftNode.node.x, rightNode.node.x) + 300,
            y: (leftNode.node.y + rightNode.node.y) / 2,
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
        currentState = null;
    }

    return { init, openJoinModal };
})();

window.ViewJoin = ViewJoin;