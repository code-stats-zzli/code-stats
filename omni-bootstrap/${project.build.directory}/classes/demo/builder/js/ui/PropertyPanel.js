/**
 * 右侧属性面板 — 含「样式」和「数据」两个 Tab
 */
export class PropertyPanel {
    constructor(rootEl, canvasManager, dataLoader) {
        this.root = rootEl;
        this.canvasManager = canvasManager;
        this.dataLoader = dataLoader || null;
        this.currentId = null;
        this.activeTab = 'style';

        // 元数据缓存
        this._meta = {
            datasources: null,
            databases: {},
            tables: {},
            columns: {}
        };

        this.canvasManager.onSelect = (el) => this.render(el);
        this.render(null);
    }

    render(element) {
        if (!element) {
            this.currentId = null;
            this.root.innerHTML = '<div class="empty-tip">👈 选中元素后可编辑属性</div>';
            return;
        }
        this.currentId = element.id;
        this.currentElement = element;

        const supportsData = typeof element.supportsDataBinding === 'function'
            && element.supportsDataBinding();

        let html = '';
        // Tab 切换
        html += `<div class="pp-tabs">
            <button class="pp-tab ${this.activeTab === 'style' ? 'active' : ''}"
                    data-tab="style">🎨 样式</button>`;
        if (supportsData) {
            html += `<button class="pp-tab ${this.activeTab === 'data' ? 'active' : ''}"
                    data-tab="data">🔌 数据</button>`;
        }
        html += `</div>`;

        // 内容
        if (this.activeTab === 'data' && supportsData) {
            html += '<div id="pp-data-content"></div>';
        } else {
            html += '<div id="pp-style-content"></div>';
        }
        this.root.innerHTML = html;

        // 绑定 tab 点击
        this.root.querySelectorAll('.pp-tab').forEach(btn => {
            btn.addEventListener('click', () => {
                this.activeTab = btn.dataset.tab;
                this.render(element);
            });
        });

        if (this.activeTab === 'data' && supportsData) {
            this.renderDataTab(element);
        } else {
            this.renderStyleTab(element);
        }
    }

    /* ============ 样式 Tab（原有逻辑） ============ */
    renderStyleTab(element) {
        const container = this.root.querySelector('#pp-style-content');
        const meta = element.constructor.meta;
        const props = element.props;
        let html = `<h3>${meta.icon} ${meta.label} 属性</h3>`;
        html += this._row('位置 X', 'x', props.x, 'number');
        html += this._row('位置 Y', 'y', props.y, 'number');
        for (const key of Object.keys(props)) {
            if (key === 'x' || key === 'y') continue;
            const value = props[key];
            const type = (typeof value === 'number') ? 'number' : 'text';
            html += this._row(key, key, value, type);
        }
        html += `<button class="btn-danger" id="deleteElBtn">🗑️ 删除元素</button>`;
        container.innerHTML = html;

        container.querySelectorAll('input[data-key]').forEach(input => {
            input.addEventListener('change', (e) => {
                const key = e.target.dataset.key;
                let val = e.target.value;
                if (e.target.type === 'number') val = parseFloat(val) || 0;
                this.canvasManager.updateProps(this.currentId, { [key]: val });
                const el = this.canvasManager.instances.get(this.currentId);
                if (el) this.render(el);
            });
        });
        const delBtn = container.querySelector('#deleteElBtn');
        if (delBtn) {
            delBtn.addEventListener('click', () => {
                this.canvasManager.remove(this.currentId);
            });
        }
    }

    _row(label, key, value, type) {
        const safeVal = (value == null) ? '' : String(value).replace(/"/g, '&quot;');
        return `
            <div class="prop-group">
                <label>${label}</label>
                <input type="${type}" data-key="${key}" value="${safeVal}">
            </div>`;
    }

    /* ============ 数据 Tab ============ */
    async renderDataTab(element) {
        const container = this.root.querySelector('#pp-data-content');
        const binding = element.dataBinding || {};
        container.innerHTML = `<div style="padding:12px;color:#94a3b8;">加载元数据...</div>`;

        // 首次加载数据源列表
        if (!this._meta.datasources) {
            try {
                const resp = await fetch(window.apiUrl('/cube/api/datasources'));
                this._meta.datasources = await resp.json();
            } catch (e) {
                container.innerHTML = `<div style="color:#dc2626;padding:12px;">
                    加载数据源失败：${e.message}</div>`;
                return;
            }
        }

        let html = `<h3>🔌 数据绑定</h3>`;

        // 数据源下拉
        html += `<div class="prop-group">
            <label>数据源</label>
            <select id="dc-ds">${
            this._renderOptions(this._meta.datasources || [], 'id', 'name',
                binding.datasourceId, '-- 选择数据源 --')
        }</select>
        </div>`;

        // 数据库下拉（依赖数据源）
        html += `<div class="prop-group">
            <label>数据库</label>
            <select id="dc-db" ${binding.datasourceId ? '' : 'disabled'}>
                <option value="">-- 先选择数据源 --</option>
            </select>
        </div>`;

        // 表下拉（依赖数据库）
        html += `<div class="prop-group">
            <label>数据表</label>
            <select id="dc-table" ${binding.database ? '' : 'disabled'}>
                <option value="">-- 先选择数据库 --</option>
            </select>
        </div>`;

        // 字段列表
        html += `<div class="prop-group" style="flex-direction:column;align-items:stretch;">
            <label>字段映射（可选）</label>
            <div id="dc-fields" style="background:#f8fafc;border-radius:8px;
                padding:8px;font-size:12px;max-height:160px;overflow:auto;
                color:#64748b;">选择表后展示字段</div>
        </div>`;

        // 应用按钮
        html += `<div style="display:flex;gap:8px;margin-top:12px;">
            <button id="dc-apply" class="btn-primary" style="flex:1;">✅ 应用绑定</button>
            <button id="dc-clear" class="btn-secondary" style="flex:1;">清空</button>
        </div>`;
        html += `<div id="dc-status" style="margin-top:8px;font-size:12px;color:#64748b;"></div>`;

        container.innerHTML = html;

        // ---- 事件绑定 ----
        const dsSel = container.querySelector('#dc-ds');
        const dbSel = container.querySelector('#dc-db');
        const tblSel = container.querySelector('#dc-table');
        const fieldsDiv = container.querySelector('#dc-fields');
        const statusDiv = container.querySelector('#dc-status');

        const setStatus = (msg, isErr = false) => {
            statusDiv.textContent = msg;
            statusDiv.style.color = isErr ? '#dc2626' : '#0066ff';
        };

        // 数据源变化
        dsSel.addEventListener('change', async () => {
            const dsId = dsSel.value;
            dbSel.innerHTML = '<option value="">加载中...</option>';
            dbSel.disabled = true;
            tblSel.innerHTML = '<option value="">-- 先选择数据库 --</option>';
            tblSel.disabled = true;
            fieldsDiv.textContent = '选择表后展示字段';
            if (!dsId) return;
            try {
                const url = window.apiUrl('/cube/api/databases?dsId=' + encodeURIComponent(dsId));
                const dbs = await (await fetch(url)).json();
                dbSel.innerHTML = '<option value="">-- 选择数据库 --</option>'
                    + (Array.isArray(dbs) ? dbs.map(d =>
                        `<option value="${d}">${d}</option>`).join('') : '');
                dbSel.disabled = false;
            } catch (e) { setStatus('加载数据库失败：' + e.message, true); }
        });

        // 数据库变化
        dbSel.addEventListener('change', async () => {
            const dsId = dsSel.value;
            const db = dbSel.value;
            tblSel.innerHTML = '<option value="">加载中...</option>';
            tblSel.disabled = true;
            fieldsDiv.textContent = '选择表后展示字段';
            if (!db) return;
            try {
                const url = window.apiUrl('/cube/api/tables?dsId='
                    + encodeURIComponent(dsId) + '&db=' + encodeURIComponent(db));
                const tables = await (await fetch(url)).json();
                tblSel.innerHTML = '<option value="">-- 选择数据表 --</option>'
                    + (Array.isArray(tables) ? tables.map(t =>
                        `<option value="${t.name}">${t.name}${t.remarks ? ' (' + t.remarks + ')' : ''}</option>`
                    ).join('') : '');
                tblSel.disabled = false;
            } catch (e) { setStatus('加载表失败：' + e.message, true); }
        });

        // 表变化 → 加载字段
        tblSel.addEventListener('change', async () => {
            const dsId = dsSel.value;
            const db = dbSel.value;
            const table = tblSel.value;
            fieldsDiv.innerHTML = '加载中...';
            if (!table) { fieldsDiv.textContent = '选择表后展示字段'; return; }
            try {
                const url = window.apiUrl('/cube/api/columns?dsId='
                    + encodeURIComponent(dsId) + '&db=' + encodeURIComponent(db)
                    + '&table=' + encodeURIComponent(table));
                const schema = await (await fetch(url)).json();
                const cols = (schema.columns) || [];
                if (!cols.length) { fieldsDiv.textContent = '（无列信息）'; return; }
                fieldsDiv.innerHTML = cols.map(c =>
                    `<label style="display:flex;gap:6px;align-items:center;
                            margin:3px 0;cursor:pointer;">
                        <input type="checkbox" class="dc-field-cb"
                               data-field="${c.name}" checked>
                        <span>${c.name}</span>
                        <span style="color:#94a3b8;font-size:11px;">${c.type}</span>
                    </label>`
                ).join('');
            } catch (e) { fieldsDiv.textContent = '加载失败：' + e.message; }
        });

        // 应用绑定
        container.querySelector('#dc-apply').addEventListener('click', async () => {
            const dsId = dsSel.value;
            const db = dbSel.value;
            const table = tblSel.value;
            if (!dsId || !db || !table) {
                setStatus('请完整选择数据源 / 数据库 / 表', true);
                return;
            }
            const checked = Array.from(fieldsDiv.querySelectorAll('.dc-field-cb:checked'))
                .map(cb => cb.dataset.field);
            element.dataBinding = {
                datasourceId: dsId,
                database: db,
                table: table,
                columns: checked.length ? checked : null,
                params: { page: 1, pageSize: 20 }
            };
            setStatus('绑定成功，加载中...');
            if (this.dataLoader) {
                await this.dataLoader.reloadElement(element);
                setStatus('✅ 已加载数据');
            }
        });

        // 清空绑定
        container.querySelector('#dc-clear').addEventListener('click', () => {
            element.dataBinding = null;
            element.runtimeData = null;
            const newDom = element.render();
            if (element.dom && element.dom.parentNode) {
                element.dom.parentNode.replaceChild(newDom, element.dom);
            }
            this.render(element);
        });

        // 恢复到已选状态（根据 binding 回填）
        if (binding.datasourceId) {
            await this._restoreSelects(binding, dsSel, dbSel, tblSel, fieldsDiv);
        }
    }

    async _restoreSelects(binding, dsSel, dbSel, tblSel, fieldsDiv) {
        try {
            // 回填数据源
            dsSel.value = binding.datasourceId;
            dsSel.dispatchEvent(new Event('change'));
            // 等待异步加载数据库
            await new Promise(r => setTimeout(r, 250));
            if (!binding.database) return;
            dbSel.value = binding.database;
            dbSel.dispatchEvent(new Event('change'));
            await new Promise(r => setTimeout(r, 250));
            if (!binding.table) return;
            tblSel.value = binding.table;
            tblSel.dispatchEvent(new Event('change'));
            await new Promise(r => setTimeout(r, 250));
            // 恢复勾选
            if (binding.columns && binding.columns.length) {
                const set = new Set(binding.columns);
                fieldsDiv.querySelectorAll('.dc-field-cb').forEach(cb => {
                    cb.checked = set.has(cb.dataset.field);
                });
            }
        } catch (e) { /* ignore */ }
    }

    _renderOptions(list, valKey, labelKey, currentVal, placeholder) {
        let html = `<option value="">${placeholder || '-- 请选择 --'}</option>`;
        if (Array.isArray(list)) {
            for (const item of list) {
                const v = item[valKey];
                const l = item[labelKey] || v;
                html += `<option value="${v}" ${String(v) === String(currentVal) ? 'selected' : ''}>${l}</option>`;
            }
        }
        return html;
    }
}