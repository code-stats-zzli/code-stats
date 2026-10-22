// dbclient-view-join.js - Ctrl 多选两个表/视图后右键 → 创建关联视图
// 支持：递归展平输入、真实表 a/b/c 别名、输出字段选择
const ViewJoin = (function() {
    let core = null;
    let refreshCallback = null;
    let modal = null;
    let currentState = null;   // { leftNode, rightNode, merged }

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
    }

    /**
     * 展平输入节点为 { tables, conditions, fields }
     * table 节点：1 张表，无内部条件
     * join 节点：直接复用其 flatTables / flatConditions / flatFields
     */
    function flattenInput(node) {
        if (!node) return { tables: [], conditions: [], fields: [] };

        if (node.type === 'table') {
            const rawFields = (node.node && node.node.fields) || [];
            return {
                tables: [{
                    name: node.name,
                    dbName: node.dbName,
                    alias: 'a',
                    fields: rawFields.map(f => ({ ...f }))
                }],
                conditions: [],
                fields: rawFields.map(f => ({
                    columnName: f.columnName,
                    typeName: f.typeName,
                    ownerName: node.name,
                    alias: 'a'
                }))
            };
        }

        // join 节点：深拷贝，避免后续修改污染原节点
        const jNode = node.node;
        return {
            tables: (jNode.flatTables || []).map(t => ({
                name: t.name,
                dbName: t.dbName,
                alias: t.alias,
                fields: (t.fields || []).map(f => ({ ...f }))
            })),
            conditions: (jNode.flatConditions || []).map(c => ({ ...c })),
            fields: (jNode.flatFields || []).map(f => ({ ...f }))
        };
    }

    /**
     * 合并左右输入，分配 a/b/c 别名
     * 左输入保持自己已有的 alias（因为其内部 conditions 里已引用）
     * 右输入基于左输入表数量，从下一个字母继续分配
     */
    function mergeInputs(leftNode, rightNode) {
        const left = flattenInput(leftNode);
        const right = flattenInput(rightNode);

        const letters = 'abcdefghijklmnopqrstuvwxyz';

        // 左输入：如果它是 table（初始 alias 全部是 'a'），重新从 a 开始分配
        // 如果它是 join，其 flatTables 已经带别名，不重排
        if (leftNode.type === 'table') {
            left.tables.forEach((t, idx) => {
                t.alias = letters[idx] || ('t' + idx);
            });
            left.fields.forEach(f => {
                const ownerTbl = left.tables.find(t => t.name === f.ownerName);
                if (ownerTbl) f.alias = ownerTbl.alias;
            });
        }

        // 右输入：从 nextIdx 开始分配
        let nextIdx = left.tables.length;
        if (rightNode.type === 'table') {
            right.tables.forEach((t) => {
                t.alias = letters[nextIdx++] || ('t' + nextIdx);
            });
            right.fields.forEach(f => {
                const ownerTbl = right.tables.find(t => t.name === f.ownerName);
                if (ownerTbl) f.alias = ownerTbl.alias;
            });
        } else {
            // 右输入是 join：需要把它的 alias 重新映射到 nextIdx 起始的字母
            const aliasMap = {};
            right.tables.forEach((t) => {
                const newAlias = letters[nextIdx++] || ('t' + nextIdx);
                aliasMap[t.alias] = newAlias;
                t.alias = newAlias;
            });
            // 更新右 join 内部 conditions 的 alias 引用
            right.conditions.forEach(c => {
                if (aliasMap[c.leftAlias]) c.leftAlias = aliasMap[c.leftAlias];
                if (aliasMap[c.rightAlias]) c.rightAlias = aliasMap[c.rightAlias];
            });
            // 更新右 join 字段的 alias
            right.fields.forEach(f => {
                if (aliasMap[f.alias]) f.alias = aliasMap[f.alias];
            });
        }

        return {
            tables: [...left.tables, ...right.tables],
            conditions: [...left.conditions, ...right.conditions],
            leftFields: left.fields,
            rightFields: right.fields
        };
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

        if (nodes.length !== 2) {
            alert('选中的节点无效');
            return;
        }

        const [leftNode, rightNode] = nodes;
        const merged = mergeInputs(leftNode, rightNode);

        if (!merged.leftFields.length || !merged.rightFields.length) {
            alert('左右输入至少一侧没有字段，无法创建关联');
            return;
        }

        document.getElementById('joinLeftTable').textContent = leftNode.name;
        document.getElementById('joinRightTable').textContent = rightNode.name;
        document.getElementById('joinTypeSelect').value = 'INNER JOIN';
        document.getElementById('joinOperator').value = '=';
        document.getElementById('joinAlias').value = '';

        // 左字段下拉
        const leftFieldSelect = document.getElementById('joinLeftField');
        leftFieldSelect.innerHTML = merged.leftFields.map(f =>
            `<option value="${f.alias}|${f.columnName}">${f.alias}.${f.columnName}  (${f.ownerName})</option>`
        ).join('');

        // 右字段下拉
        const rightFieldSelect = document.getElementById('joinRightField');
        rightFieldSelect.innerHTML = merged.rightFields.map(f =>
            `<option value="${f.alias}|${f.columnName}">${f.alias}.${f.columnName}  (${f.ownerName})</option>`
        ).join('');

        // 输出字段多选：左右所有字段，按 alias 顺序展示
        const outputContainer = document.getElementById('outputFieldsContainer');
        const allFields = [...merged.leftFields, ...merged.rightFields];
        // 按 alias 排序，让 a.* 在前
        allFields.sort((x, y) => {
            if (x.alias < y.alias) return -1;
            if (x.alias > y.alias) return 1;
            return 0;
        });
        outputContainer.innerHTML = allFields.map((f, idx) => `
            <label style="display:inline-flex; align-items:center; gap:6px; margin:3px 14px 3px 0;
                          font-size:13px; font-family:'JetBrains Mono',monospace; cursor:pointer;">
                <input type="checkbox" class="output-field-cb"
                       data-idx="${idx}"
                       data-alias="${f.alias}"
                       data-column="${f.columnName}"
                       data-type="${f.typeName || ''}"
                       data-owner="${f.ownerName}"
                       checked>
                <span>${f.alias}.${f.columnName}</span>
                <span style="color:#94a3b8; font-size:11px;">(${f.typeName || ''})</span>
            </label>
        `).join('');

        currentState = { leftNode, rightNode, merged };
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
        const { leftNode, rightNode, merged } = currentState;

        const joinType = document.getElementById('joinTypeSelect').value;
        const leftFieldValue = document.getElementById('joinLeftField').value;
        const rightFieldValue = document.getElementById('joinRightField').value;
        const operator = document.getElementById('joinOperator').value;
        const aliasInput = document.getElementById('joinAlias').value.trim();

        if (!leftFieldValue || !rightFieldValue || leftFieldValue === '无字段') {
            alert('请选择关联字段');
            return;
        }

        const [leftAlias, leftFieldName] = leftFieldValue.split('|');
        const [rightAlias, rightFieldName] = rightFieldValue.split('|');

        // 名称
        let name = aliasInput;
        if (!name) {
            const base = leftNode.name + '_关联';
            if (!isNameUsed(base)) name = base;
            else {
                let i = 1;
                while (isNameUsed(base + '_' + i)) i++;
                name = base + '_' + i;
            }
        } else if (isNameUsed(name)) {
            alert('名称 "' + name + '" 已被占用，请换一个或留空使用默认');
            return;
        }

        // 收集勾选的输出字段
        const outputFields = [];
        document.querySelectorAll('#outputFieldsContainer input.output-field-cb:checked').forEach(cb => {
            outputFields.push({
                alias: cb.dataset.alias,
                columnName: cb.dataset.column,
                typeName: cb.dataset.type,
                ownerName: cb.dataset.owner
            });
        });
        if (outputFields.length === 0) {
            alert('请至少勾选一个输出字段');
            return;
        }

        // 当前新 join 的条件
        const currentCond = {
            leftAlias: leftAlias,
            leftField: leftFieldName,
            operator: operator,
            rightAlias: rightAlias,
            rightField: rightFieldName,
            joinType: joinType
        };

        const flatTables = merged.tables.map(t => ({
            name: t.name,
            dbName: t.dbName,
            alias: t.alias,
            fields: (t.fields || []).map(f => ({ ...f }))
        }));
        const flatConditions = [...merged.conditions, currentCond];
        const flatFields = [...merged.leftFields, ...merged.rightFields].map(f => ({
            columnName: f.columnName,
            typeName: f.typeName,
            ownerName: f.ownerName,
            alias: f.alias
        }));

        const jid = core.getNextJoinId();
        const jNode = {
            id: jid,
            name: name,
            dbName: leftNode.dbName || (flatTables[0] && flatTables[0].dbName),
            leftTable: leftNode.name,
            rightTable: rightNode.name,
            joinType: joinType,
            leftField: leftFieldName,
            rightField: rightFieldName,
            leftAlias: leftAlias,
            rightAlias: rightAlias,
            operator: operator,
            alias: aliasInput,
            // 卡片显示的字段（带 alias 前缀）
            fields: flatFields,
            // 平铺 SQL 所需
            flatTables,
            flatConditions,
            flatFields,
            outputFields,
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
        currentState = null;
    }

    return { init, openJoinModal };
})();

window.ViewJoin = ViewJoin;