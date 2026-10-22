// dbclient-view-modal.js - 字段转换弹窗
const ViewModal = (function() {
    let modal = null;
    let modalSourceFields = null;
    let modalTransformList = null;
    let addRuleBtn = null;
    let applyModalBtn = null;
    let closeModalBtn = null;
    let cancelModalBtn = null;

    let core = null;
    let onApplyCallback = null;

    function initModal(coreInstance, onApply) {
        core = coreInstance;
        onApplyCallback = onApply;

        modal = document.getElementById('transformModal');
        modalSourceFields = document.getElementById('modalSourceFields');
        modalTransformList = document.getElementById('modalTransformList');
        addRuleBtn = document.getElementById('addModalRuleBtn');
        applyModalBtn = document.getElementById('applyModalBtn');
        closeModalBtn = document.getElementById('closeModalBtn');
        cancelModalBtn = document.getElementById('cancelModalBtn');

        if (!modal) return;

        if (addRuleBtn) addRuleBtn.addEventListener('click', () => addModalTransformRule(''));
        if (applyModalBtn) applyModalBtn.addEventListener('click', () => {
            if (onApplyCallback) onApplyCallback();
            modal.style.display = 'none';
        });
        if (closeModalBtn) closeModalBtn.addEventListener('click', () => modal.style.display = 'none');
        if (cancelModalBtn) cancelModalBtn.addEventListener('click', () => modal.style.display = 'none');

        setupModalDrop();
    }

    function openModal() {
        // ★ 改为从画板选中节点获取表信息
        const node = core.getSelectedNode();
        if (!node || node.type !== 'table') {
            alert('请先在画布上选中一张表节点');
            return;
        }
        const table = core.getTables().get(node.id);
        if (!table) {
            alert('表节点不存在');
            return;
        }
        loadCurrentTableColumns(table.dbName, table.name).then(() => {
            renderModalSourceFields();
            renderModalTransformRules();
            modal.style.display = 'flex';
        });
    }

    async function loadCurrentTableColumns(db, table) {
        try {
            const columns = await DB.fetchJSON(DB.apiBase + `/columns?db=${encodeURIComponent(db)}&table=${encodeURIComponent(table)}`);
            core.setActiveTableForTransform({ db: db, table: table, columns: columns });
            const configMap = core.getCurrentTransformConfig();
            if (!configMap.has(table)) {
                configMap.set(table, []);
            }
        } catch (err) {
            console.error('加载表字段失败', err);
            core.setActiveTableForTransform(null);
        }
    }

    function renderModalSourceFields() {
        if (!modalSourceFields) return;
        const active = core.getActiveTableForTransform();
        if (!active) {
            modalSourceFields.innerHTML = '<div class="empty-hint">无字段信息</div>';
            return;
        }
        const columns = active.columns;
        if (!columns || !columns.length) {
            modalSourceFields.innerHTML = '<div class="empty-hint">无字段信息</div>';
            return;
        }
        modalSourceFields.innerHTML = columns.map(col => `
            <div class="field-item" draggable="true" data-field="${core.escapeHtml(col.columnName)}" data-type="${core.escapeHtml(col.typeName)}">
                📌 ${core.escapeHtml(col.columnName)} <span style="color:#64748b; font-size:11px;">(${core.escapeHtml(col.typeName)})</span>
            </div>
        `).join('');
        document.querySelectorAll('#modalSourceFields .field-item').forEach(el => {
            el.addEventListener('dragstart', (e) => {
                e.dataTransfer.setData('text/plain', JSON.stringify({
                    field: el.dataset.field,
                    type: el.dataset.type
                }));
                e.dataTransfer.effectAllowed = 'copy';
            });
        });
    }

    function renderModalTransformRules() {
        if (!modalTransformList) return;
        const active = core.getActiveTableForTransform();
        if (!active) {
            modalTransformList.innerHTML = '<div class="empty-hint">暂无转换规则</div>';
            return;
        }
        const tableName = active.table;
        const configMap = core.getCurrentTransformConfig();
        const rules = configMap.get(tableName) || [];
        if (rules.length === 0) {
            modalTransformList.innerHTML = '<div class="empty-hint">暂无转换规则，请从左侧拖拽或点击"+"添加</div>';
            return;
        }
        modalTransformList.innerHTML = rules.map((rule, idx) => `
            <div class="transform-item" data-idx="${idx}">
                <div class="transform-header">
                    <span>🔁 ${core.escapeHtml(rule.sourceField || '')} → ${core.escapeHtml(rule.alias || '')}</span>
                    <button class="remove-transform" data-idx="${idx}">✖</button>
                </div>
                <input type="text" class="transform-expression" value="${core.escapeHtml(rule.expression || '')}" placeholder="转换表达式" data-idx="${idx}">
                <div style="font-size:11px; margin-top:4px;">别名: <input type="text" class="transform-alias" value="${core.escapeHtml(rule.alias || '')}" placeholder="别名" data-idx="${idx}" style="width:100px;"></div>
            </div>
        `).join('');
        document.querySelectorAll('#modalTransformList .remove-transform').forEach(btn => {
            btn.addEventListener('click', () => {
                const idx = parseInt(btn.dataset.idx);
                const arr = configMap.get(tableName) || [];
                arr.splice(idx, 1);
                configMap.set(tableName, arr);
                renderModalTransformRules();
                if (window.ViewSql) window.ViewSql.updateSql();
            });
        });
        document.querySelectorAll('#modalTransformList .transform-expression').forEach(inp => {
            inp.addEventListener('input', () => {
                const idx = parseInt(inp.dataset.idx);
                const arr = configMap.get(tableName) || [];
                if (arr[idx]) arr[idx].expression = inp.value;
                configMap.set(tableName, arr);
                if (window.ViewSql) window.ViewSql.updateSql();
            });
        });
        document.querySelectorAll('#modalTransformList .transform-alias').forEach(inp => {
            inp.addEventListener('input', () => {
                const idx = parseInt(inp.dataset.idx);
                const arr = configMap.get(tableName) || [];
                if (arr[idx]) arr[idx].alias = inp.value;
                configMap.set(tableName, arr);
                if (window.ViewSql) window.ViewSql.updateSql();
            });
        });
    }

    function addModalTransformRule(field) {
        const active = core.getActiveTableForTransform();
        if (!active) return;
        const tableName = active.table;
        const configMap = core.getCurrentTransformConfig();
        const rules = configMap.get(tableName) || [];
        const defaultExpr = field ? field : '';
        rules.push({
            sourceField: field || '',
            expression: defaultExpr,
            alias: field ? 'trans_' + field : 'new_field'
        });
        configMap.set(tableName, rules);
        renderModalTransformRules();
        if (window.ViewSql) window.ViewSql.updateSql();
    }

    function setupModalDrop() {
        if (!modalTransformList) return;
        modalTransformList.addEventListener('dragover', (e) => e.preventDefault());
        modalTransformList.addEventListener('drop', (e) => {
            e.preventDefault();
            const rawData = e.dataTransfer.getData('text/plain');
            if (!rawData) return;
            try {
                const data = JSON.parse(rawData);
                if (data.field) addModalTransformRule(data.field);
            } catch (err) { /* ignore */ }
        });
    }

    function getCurrentRules() {
        const active = core.getActiveTableForTransform();
        if (!active) return [];
        const configMap = core.getCurrentTransformConfig();
        return configMap.get(active.table) || [];
    }

    return { initModal, openModal, getCurrentRules };
})();