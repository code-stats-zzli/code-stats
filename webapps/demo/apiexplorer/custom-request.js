/**
 * 自定义 API 模块：已发布列表 + 详情 + 发布表单（支持 custom/table/system 三种类型）
 */
(function () {
    'use strict';

    const API_PUBLISH   = window.apiUrl('/api/publish/api');
    const API_CUBE      = window.apiUrl('/cube/api');

    // ========== 全局状态 ==========
    let apiList = [];
    let selectedApiId = null;
    let editingMode = false;
    let currentApiType = 'table';
    let metaCache = { datasources: null, systemModules: null };
    let tmp = {};

    let els = {};
    const $ = id => document.getElementById(id);

    function bindDom() {
        els = {
            apiList: $('customApiList'),
            main: $('customMain'),
            newBtn: $('newApiBtn')
        };
    }

    // ========== 工具 ==========
    function escapeHtml(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"']/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[m]));
    }
    function escapeAttr(s) { return escapeHtml(s); }
    function safeId(s) { return String(s || '').replace(/[^A-Za-z0-9_]/g, '_'); }

    // ========== 列表加载 ==========
    async function loadApiList() {
        if (!els.apiList) return;
        els.apiList.innerHTML = '<div class="loading">加载中...</div>';
        try {
            const resp = await fetch(API_PUBLISH + '/list', { cache: 'no-store' });
            const data = await resp.json();
            if (!data.success) throw new Error(data.error || '加载失败');
            apiList = data.items || [];
            renderApiList();
        } catch (e) {
            els.apiList.innerHTML = `<div class="error-tip">❌ ${escapeHtml(e.message)}</div>`;
        }
    }

    const TYPE_META = {
        custom: { icon: '🔌', label: '自定义' },
        table:  { icon: '📦', label: '数据库' },
        system: { icon: '⚙️', label: '系统' }
    };

    function renderApiList() {
        if (!apiList.length) {
            els.apiList.innerHTML = '<div class="empty-tip">暂无已发布 API<br>点击上方「+ 发布新 API」开始</div>';
            return;
        }
        els.apiList.innerHTML = apiList.map(it => {
            const t = TYPE_META[it.apiType] || TYPE_META.table;
            const activeCls = (it.id === selectedApiId && !editingMode) ? 'active' : '';
            return `
                <div class="custom-api-item ${activeCls}" data-id="${escapeAttr(it.id)}">
                    <div class="custom-api-head">
                        <span class="type-icon">${t.icon}</span>
                        <span class="type-label">${t.label}</span>
                        <span class="method-badge ${(it.method || 'POST').toLowerCase()}">${escapeHtml(it.method || 'POST')}</span>
                    </div>
                    <div class="custom-api-name">${escapeHtml(it.name || '-')}</div>
                    <div class="custom-api-path">${escapeHtml(it.path || '')}</div>
                    <div class="custom-api-meta">
                        <span class="status-badge ${it.status === 1 ? 'active' : 'disabled'}">
                            ${it.status === 1 ? '启用' : '草稿'}
                        </span>
                    </div>
                </div>`;
        }).join('');

        els.apiList.querySelectorAll('.custom-api-item').forEach(el => {
            el.addEventListener('click', () => showApiDetail(el.dataset.id));
        });
    }

    // ========== 详情面板 ==========
    function showApiDetail(id) {
        selectedApiId = id;
        editingMode = false;
        renderApiList();
        const api = apiList.find(x => x.id === id);
        if (!api) return;

        const t = TYPE_META[api.apiType] || TYPE_META.table;
        let typeBlock = '';
        if (api.apiType === 'table') {
            typeBlock = `
                <div class="detail-kv">
                    <div class="k">数据源</div><div class="v">${escapeHtml(api.datasourceId || '-')}</div>
                    <div class="k">数据库</div><div class="v">${escapeHtml(api.database || '-')}</div>
                    <div class="k">数据表</div><div class="v">${escapeHtml(api.table || '-')}</div>
                    <div class="k">分页</div><div class="v">${api.pageEnabled === 1 ? '开启（每页 ' + (api.pageSize || 20) + ' 条）' : '关闭'}</div>
                </div>`;
        } else if (api.apiType === 'custom') {
            typeBlock = `
                <div class="detail-kv">
                    <div class="k">后端 URL</div><div class="v">${escapeHtml(api.backendUrl || '-')}</div>
                    <div class="k">Headers</div><div class="v"><pre style="margin:0;white-space:pre-wrap;font-size:11.5px;">${escapeHtml(api.headers || '(无)')}</pre></div>
                </div>`;
        } else if (api.apiType === 'system') {
            typeBlock = `
                <div class="detail-kv">
                    <div class="k">系统模块</div><div class="v">${escapeHtml(api.systemModule || '-')}</div>
                    <div class="k">系统路径</div><div class="v">${escapeHtml(api.systemPath || '-')}</div>
                    <div class="k">系统方法</div><div class="v">${escapeHtml(api.systemMethod || 'POST')}</div>
                </div>`;
        }

        const renderParams = list => {
            if (!list || !list.length) return '<div class="empty-tip" style="padding:16px;">无</div>';
            return `
                <table class="param-table">
                    <thead><tr><th>参数名</th><th>类型</th><th>位置</th><th>必填</th><th>默认值</th><th>描述</th></tr></thead>
                    <tbody>
                        ${list.map(p => `
                            <tr>
                                <td>${escapeHtml(p.name)}</td>
                                <td>${escapeHtml(p.type || 'string')}</td>
                                <td>${escapeHtml(p.in || 'query')}</td>
                                <td>${p.required ? '是' : '否'}</td>
                                <td>${escapeHtml(p.defaultValue || '')}</td>
                                <td>${escapeHtml(p.description || '')}</td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>`;
        };

        els.main.innerHTML = `
            <div class="api-detail-panel">
                <div class="detail-header">
                    <div>
                        <h2>${escapeHtml(api.name)}</h2>
                        <div class="detail-path">
                            <span class="method-badge ${(api.method || 'POST').toLowerCase()}"
                                  style="font-size:12px;padding:2px 10px;margin-right:6px;">${escapeHtml(api.method || 'POST')}</span>
                            ${escapeHtml(api.path || '')}
                        </div>
                        <div class="detail-desc">${escapeHtml(api.description || '无描述')}</div>
                        <div style="margin-top:6px;">
                            <span class="type-icon">${t.icon}</span>
                            <span style="font-size:12px;color:var(--text-muted);">${t.label} 类型</span>
                        </div>
                    </div>
                    <div class="detail-actions">
                        <button id="testApiBtn" class="btn-primary btn-sm">⚡ 测试</button>
                        <button id="editApiBtn" class="btn-secondary btn-sm">✏️ 编辑</button>
                        <button id="toggleApiBtn" class="btn-secondary btn-sm">${api.status === 1 ? '⏸ 禁用' : '▶ 启用'}</button>
                        <button id="deleteApiBtn" class="btn-secondary btn-sm" style="color:#dc2626;">🗑 删除</button>
                    </div>
                </div>

                <div class="detail-section">
                    <div class="detail-section-title">📌 类型配置</div>
                    ${typeBlock}
                </div>

                <div class="detail-section">
                    <div class="detail-section-title">📥 入参</div>
                    ${renderParams(api.inputs)}
                </div>

                <div class="detail-section">
                    <div class="detail-section-title">📤 出参</div>
                    ${renderParams(api.outputs)}
                </div>
            </div>
        `;

        $('testApiBtn').addEventListener('click', () => openTestModal(api.id));
        $('editApiBtn').addEventListener('click', () => showPublishForm(api));
        $('toggleApiBtn').addEventListener('click', () => toggleApi(api));
        $('deleteApiBtn').addEventListener('click', () => deleteApi(api));
    }

    async function toggleApi(api) {
        const next = api.status === 1 ? 0 : 1;
        try {
            const r = await (await fetch(API_PUBLISH + '/toggle', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: api.id, status: next })
            })).json();
            if (!r.success) { alert(r.error || '操作失败'); return; }
            await loadApiList();
            showApiDetail(api.id);
        } catch (e) { alert('请求异常：' + e.message); }
    }

    async function deleteApi(api) {
        if (!confirm(`确定删除 API「${api.name}」？`)) return;
        try {
            const r = await (await fetch(API_PUBLISH + '/delete', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: api.id })
            })).json();
            if (!r.success) { alert(r.error || '删除失败'); return; }
            selectedApiId = null;
            editingMode = false;
            await loadApiList();
            els.main.innerHTML = `
                <div class="empty-state">
                    <span class="empty-icon">📝</span>
                    <p>点击左侧「+ 发布新 API」开始，或选择已发布 API 查看详情</p>
                </div>`;
        } catch (e) { alert('请求异常：' + e.message); }
    }

    // ========== 元数据懒加载 ==========
    async function ensureMeta() {
        if (!metaCache.datasources) {
            try {
                const r = await (await fetch(API_CUBE + '/datasources')).json();
                metaCache.datasources = Array.isArray(r) ? r : [];
            } catch (e) { metaCache.datasources = []; }
        }
        if (!metaCache.systemModules) {
            try {
                const r = await (await fetch(API_PUBLISH + '/system-interfaces')).json();
                metaCache.systemModules = r.success ? (r.modules || []) : [];
            } catch (e) { metaCache.systemModules = []; }
        }
    }

    // ========== 发布/编辑表单 ==========
    async function showPublishForm(api) {
        editingMode = true;
        selectedApiId = api ? api.id : null;
        currentApiType = api ? (api.apiType || 'table') : 'table';
        renderApiList();
        await ensureMeta();

        const isEdit = !!api;
        const data = api || {
            name: '', path: '', method: 'POST', description: '',
            apiType: 'table',
            inputs: [], outputs: [],
            pageEnabled: 0, pageSize: 20, status: 1
        };

        els.main.innerHTML = `
            <div class="publish-form">
                <h2>${isEdit ? '✏️ 编辑 API' : '📤 发布新 API'}</h2>

                <!-- 基本信息 -->
                <div class="form-section">
                    <div class="form-section-title">① 基本信息</div>
                    <div class="form-row-2">
                        <div class="form-field">
                            <label>API 名称<span class="req">*</span></label>
                            <input id="fName" type="text" value="${escapeAttr(data.name)}" placeholder="如：查询用户列表">
                        </div>
                        <div class="form-field">
                            <label>API 路径<span class="req">*</span></label>
                            <input id="fPath" type="text" value="${escapeAttr(data.path)}" placeholder="/custom/api/user/list">
                        </div>
                    </div>
                    <div class="form-row-3" style="margin-top:12px;">
                        <div class="form-field">
                            <label>HTTP 方法</label>
                            <select id="fMethod">
                                ${['GET','POST','PUT','DELETE'].map(m =>
            `<option value="${m}" ${data.method === m ? 'selected' : ''}>${m}</option>`).join('')}
                            </select>
                        </div>
                        <div class="form-field" style="grid-column: span 2;">
                            <label>描述</label>
                            <input id="fDesc" type="text" value="${escapeAttr(data.description || '')}" placeholder="可选">
                        </div>
                    </div>
                </div>

                <!-- 接口类型 -->
                <div class="form-section">
                    <div class="form-section-title">② 接口类型<span class="req">*</span></div>
                    <div class="api-type-picker" id="apiTypePicker">
                        <div class="api-type-option ${currentApiType === 'table' ? 'active' : ''}" data-type="table">
                            <div class="opt-title">📦 数据库表查询</div>
                            <div class="opt-desc">把数据源中的表暴露为 REST 接口，支持分页、字段过滤</div>
                        </div>
                        <div class="api-type-option ${currentApiType === 'custom' ? 'active' : ''}" data-type="custom">
                            <div class="opt-title">🔌 自定义接口</div>
                            <div class="opt-desc">调用任意外部 HTTP/HTTPS 接口，透传参数</div>
                        </div>
                        <div class="api-type-option ${currentApiType === 'system' ? 'active' : ''}" data-type="system">
                            <div class="opt-title">⚙️ 系统接口</div>
                            <div class="opt-desc">把系统内部 Controller 接口暴露为对外 API</div>
                        </div>
                    </div>
                </div>

                <!-- 类型相关配置 -->
                <div class="form-section">
                    <div class="form-section-title">③ 类型配置</div>
                    <div id="typeConfigArea"></div>
                </div>

                <!-- 入参 / 出参 -->
                <div class="form-section">
                    <div class="form-section-title">
                        ④ 入参
                        <button type="button" id="addInputBtn" class="btn-secondary btn-sm"
                                style="float:right;padding:3px 12px;font-size:11px;">＋ 添加</button>
                    </div>
                    <div class="param-table-wrap">
                        <table class="param-table" id="inputTable">
                            <thead><tr>
                                <th style="min-width:110px;">参数名</th>
                                <th style="min-width:90px;">类型</th>
                                <th style="min-width:90px;">位置</th>
                                <th style="min-width:50px;">必填</th>
                                <th style="min-width:90px;">默认值</th>
                                <th style="min-width:120px;">描述</th>
                                <th style="min-width:50px;">操作</th>
                            </tr></thead>
                            <tbody id="inputTableBody"></tbody>
                        </table>
                    </div>
                </div>

                <div class="form-section">
                    <div class="form-section-title">⑤ 出参（表格类型专用：勾选需要的字段）</div>
                    <div class="output-fields" id="outputFields">
                        <div class="empty-tip" style="padding:12px;">请先选择数据库表（仅 table 类型需要）</div>
                    </div>
                </div>

                <!-- 提交 -->
                <div class="form-actions">
                    <button id="submitPublishBtn" class="btn-primary">📤 发布 API</button>
                    <button id="saveDraftBtn" class="btn-secondary">💾 保存草稿</button>
                    <button id="cancelFormBtn" class="btn-secondary">取消</button>
                </div>
            </div>
        `;

        // 类型选择
        const picker = $('apiTypePicker');
        picker.querySelectorAll('.api-type-option').forEach(opt => {
            opt.addEventListener('click', () => {
                currentApiType = opt.dataset.type;
                picker.querySelectorAll('.api-type-option').forEach(o =>
                    o.classList.toggle('active', o === opt));
                renderTypeConfig(data);
            });
        });

        // 参数表初始化
        renderInputTable(data.inputs || []);

        $('addInputBtn').addEventListener('click', () => {
            data.inputs = collectInputs();
            data.inputs.push({ name: '', type: 'string', in: 'query', required: false, defaultValue: '', description: '' });
            renderInputTable(data.inputs);
        });

        $('cancelFormBtn').addEventListener('click', () => {
            if (isEdit) showApiDetail(api.id);
            else {
                editingMode = false;
                selectedApiId = null;
                renderApiList();
                els.main.innerHTML = `
                    <div class="empty-state">
                        <span class="empty-icon">📝</span>
                        <p>点击左侧「+ 发布新 API」开始，或选择已发布 API 查看详情</p>
                    </div>`;
            }
        });
        $('submitPublishBtn').addEventListener('click', () => submitForm(false, data, isEdit));
        $('saveDraftBtn').addEventListener('click', () => submitForm(true, data, isEdit));

        // 首次渲染类型配置
        renderTypeConfig(data);
    }

    // ========== 类型相关配置渲染 ==========
    function renderTypeConfig(data) {
        const area = $('typeConfigArea');
        if (!area) return;

        if (currentApiType === 'table') {
            area.innerHTML = `
                <div class="form-row-2">
                    <div class="form-field">
                        <label>数据源<span class="req">*</span></label>
                        <select id="fDs">
                            <option value="">-- 选择数据源 --</option>
                            ${(metaCache.datasources || []).map(d =>
                `<option value="${escapeAttr(d.id)}" ${data.datasourceId === d.id ? 'selected' : ''}>
                                    ${escapeHtml(d.name)} (${escapeHtml(d.dbType)})
                                </option>`).join('')}
                        </select>
                    </div>
                    <div class="form-field">
                        <label>数据库<span class="req">*</span></label>
                        <select id="fDb"><option value="">-- 先选数据源 --</option></select>
                    </div>
                </div>
                <div class="form-row-3" style="margin-top:12px;">
                    <div class="form-field">
                        <label>数据表<span class="req">*</span></label>
                        <select id="fTable"><option value="">-- 先选数据库 --</option></select>
                    </div>
                    <div class="form-field">
                        <label>分页</label>
                        <select id="fPageEnabled">
                            <option value="0" ${data.pageEnabled !== 1 ? 'selected' : ''}>关闭</option>
                            <option value="1" ${data.pageEnabled === 1 ? 'selected' : ''}>开启</option>
                        </select>
                    </div>
                    <div class="form-field">
                        <label>每页条数</label>
                        <input id="fPageSize" type="number" value="${data.pageSize || 20}">
                    </div>
                </div>
            `;
            bindTableConfig(data);
        } else if (currentApiType === 'custom') {
            area.innerHTML = `
                <div class="form-field">
                    <label>后端 URL<span class="req">*</span></label>
                    <input id="fBackendUrl" type="text" value="${escapeAttr(data.backendUrl || '')}"
                           placeholder="https://api.example.com/v1/chat/completions">
                </div>
                <div class="form-field" style="margin-top:12px;">
                    <label>Headers（JSON 格式，可选）</label>
                    <textarea id="fHeaders" placeholder='{"X-Api-Key": "your-key"}'>${escapeHtml(data.headers || '')}</textarea>
                    <div class="hint">值会作为请求头发送；敏感信息建议在数据源或配置中管理</div>
                </div>
            `;
        } else if (currentApiType === 'system') {
            area.innerHTML = `
                <div class="form-field">
                    <label>系统接口<span class="req">*</span></label>
                    <input id="fSystemDisplay" type="text" readonly
                           value="${data.systemPath ? escapeHtml(data.systemModule + ' / ' + data.systemPath) : '请从下方选择'}"
                           style="background:#f8fafc;">
                </div>
                <div style="margin-top:12px;">
                    <div class="system-picker" id="systemPicker">
                        <div class="system-module-list" id="systemModuleList">
                            ${(metaCache.systemModules || []).map(m =>
                `<div class="system-module-item" data-module="${escapeAttr(m.moduleName)}">
                                    ${escapeHtml(m.moduleName)}
                                </div>`).join('') || '<div class="empty-tip" style="padding:12px;">无可用接口</div>'}
                        </div>
                        <div class="system-api-list" id="systemApiList">
                            <div class="empty-tip" style="padding:12px;">请选择左侧模块</div>
                        </div>
                    </div>
                </div>
            `;
            bindSystemPicker(data);
        }
    }

    async function bindTableConfig(data) {
        const dsSel = $('fDs');
        const dbSel = $('fDb');
        const tblSel = $('fTable');
        if (!dsSel) return;

        const loadDbs = async (dsId) => {
            if (!dsId) {
                dbSel.innerHTML = '<option value="">-- 先选数据源 --</option>';
                dbSel.disabled = true;
                tblSel.innerHTML = '<option value="">-- 先选数据库 --</option>';
                tblSel.disabled = true;
                $('outputFields').innerHTML = '<div class="empty-tip" style="padding:12px;">请先选择数据源</div>';
                return;
            }
            try {
                const dbs = await (await fetch(API_CUBE + '/databases?dsId=' + encodeURIComponent(dsId))).json();
                dbSel.innerHTML = '<option value="">-- 选择数据库 --</option>'
                    + (Array.isArray(dbs) ? dbs.map(d => `<option value="${escapeAttr(d)}" ${data.database === d ? 'selected' : ''}>${escapeHtml(d)}</option>`).join('') : '');
                dbSel.disabled = false;
                if (data.database) loadTables(data.database);
            } catch (e) { dbSel.innerHTML = `<option value="">加载失败: ${escapeHtml(e.message)}</option>`; }
        };

        const loadTables = async (dbName) => {
            const dsId = dsSel.value;
            if (!dsId || !dbName) return;
            try {
                const tables = await (await fetch(API_CUBE + '/tables?dsId=' + encodeURIComponent(dsId) + '&db=' + encodeURIComponent(dbName))).json();
                tblSel.innerHTML = '<option value="">-- 选择数据表 --</option>'
                    + (Array.isArray(tables) ? tables.map(t => `<option value="${escapeAttr(t.name)}" ${data.table === t.name ? 'selected' : ''}>${escapeHtml(t.name)}</option>`).join('') : '');
                tblSel.disabled = false;
                if (data.table) loadColumns(dbName, data.table);
            } catch (e) { tblSel.innerHTML = `<option value="">加载失败: ${escapeHtml(e.message)}</option>`; }
        };

        const loadColumns = async (dbName, tableName) => {
            const dsId = dsSel.value;
            if (!dsId || !dbName || !tableName) return;
            try {
                const schema = await (await fetch(API_CUBE + '/columns?dsId=' + encodeURIComponent(dsId) + '&db=' + encodeURIComponent(dbName) + '&table=' + encodeURIComponent(tableName))).json();
                const cols = (schema && schema.columns) || [];
                const selected = new Set((data.outputs || []).map(o => o.name));
                const out = $('outputFields');
                if (!cols.length) {
                    out.innerHTML = '<div class="empty-tip" style="padding:12px;">该表无字段</div>';
                    return;
                }
                out.innerHTML = cols.map(c => `
                    <label class="output-field-item">
                        <input type="checkbox" class="output-cb"
                               data-field="${escapeAttr(c.name)}"
                               ${selected.size === 0 || selected.has(c.name) ? 'checked' : ''}>
                        <span>${escapeHtml(c.name)}</span>
                        <span style="color:#94a3b8;font-size:11px;">${escapeHtml(c.type || '')}</span>
                    </label>
                `).join('');
            } catch (e) {
                $('outputFields').innerHTML = `<div class="error-tip">列信息加载失败: ${escapeHtml(e.message)}</div>`;
            }
        };

        dsSel.addEventListener('change', () => {
            data.datasourceId = dsSel.value;
            data.database = '';
            data.table = '';
            data.outputs = [];
            loadDbs(dsSel.value);
        });
        dbSel.addEventListener('change', () => {
            data.database = dbSel.value;
            data.table = '';
            data.outputs = [];
            loadTables(dbSel.value);
        });
        tblSel.addEventListener('change', () => {
            data.table = tblSel.value;
            data.outputs = [];
            if (tblSel.value) loadColumns(dbSel.value, tblSel.value);
        });

        if (data.datasourceId) loadDbs(data.datasourceId);
    }

    async function bindSystemPicker(data) {
        const moduleList = $('systemModuleList');
        const apiList = $('systemApiList');
        if (!moduleList || !apiList) return;

        const renderApis = (moduleName) => {
            const m = (metaCache.systemModules || []).find(x => x.moduleName === moduleName);
            if (!m) { apiList.innerHTML = '<div class="empty-tip" style="padding:12px;">无接口</div>'; return; }
            apiList.innerHTML = m.apis.map((a, idx) => `
                <div class="system-api-item ${data.systemPath === a.path ? 'active' : ''}"
                     data-idx="${idx}">
                    <div class="api-name">
                        <span class="method-badge ${a.method.toLowerCase()}"
                              style="font-size:10px;padding:1px 6px;margin-right:4px;">${a.method}</span>
                        ${escapeHtml(a.path)}
                    </div>
                    <div class="api-desc">${escapeHtml(a.summary || a.description || '')}</div>
                </div>
            `).join('');

            apiList.querySelectorAll('.system-api-item').forEach(item => {
                item.addEventListener('click', () => {
                    const api = m.apis[parseInt(item.dataset.idx, 10)];
                    data.systemModule = m.moduleName;
                    data.systemPath = api.path;
                    data.systemMethod = api.method;
                    // 自动提取入参
                    data.inputs = (api.parameters || []).map(p => ({
                        name: p.name,
                        type: p.type || 'string',
                        in: p.in || 'query',
                        required: !!p.required,
                        defaultValue: '',
                        description: p.description || ''
                    }));
                    $('fSystemDisplay').value = m.moduleName + ' / ' + api.path;
                    apiList.querySelectorAll('.system-api-item').forEach(x =>
                        x.classList.toggle('active', x === item));
                    renderInputTable(data.inputs);
                });
            });
        };

        moduleList.querySelectorAll('.system-module-item').forEach(item => {
            item.addEventListener('click', () => {
                moduleList.querySelectorAll('.system-module-item').forEach(x =>
                    x.classList.toggle('active', x === item));
                renderApis(item.dataset.module);
            });
        });

        // 如果已选模块，自动展开
        if (data.systemModule) {
            const first = moduleList.querySelector(`[data-module="${data.systemModule}"]`);
            if (first) first.click();
        } else if ((metaCache.systemModules || []).length > 0) {
            const first = moduleList.querySelector('.system-module-item');
            if (first) first.click();
        }
    }

    // ========== 入参表格 ==========
    function renderInputTable(inputs) {
        const tbody = $('inputTableBody');
        if (!tbody) return;
        tbody.innerHTML = inputs.map((p, idx) => `
            <tr data-idx="${idx}">
                <td><input type="text" class="in-name" value="${escapeAttr(p.name || '')}" placeholder="如 id"></td>
                <td>
                    <select class="in-type">
                        ${['string','number','integer','boolean'].map(t =>
            `<option value="${t}" ${p.type === t ? 'selected' : ''}>${t}</option>`).join('')}
                    </select>
                </td>
                <td>
                    <select class="in-in">
                        ${['query','path','body'].map(t =>
            `<option value="${t}" ${p.in === t ? 'selected' : ''}>${t}</option>`).join('')}
                    </select>
                </td>
                <td style="text-align:center;">
                    <input type="checkbox" class="in-required" ${p.required ? 'checked' : ''}>
                </td>
                <td><input type="text" class="in-default" value="${escapeAttr(p.defaultValue || '')}"></td>
                <td><input type="text" class="in-desc" value="${escapeAttr(p.description || '')}"></td>
                <td><button type="button" class="remove-input-btn" data-idx="${idx}">✖</button></td>
            </tr>
        `).join('');

        tbody.querySelectorAll('.remove-input-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const idx = parseInt(btn.dataset.idx, 10);
                const current = collectInputs();
                current.splice(idx, 1);
                renderInputTable(current);
            });
        });
    }

    function collectInputs() {
        const tbody = $('inputTableBody');
        if (!tbody) return [];
        return Array.from(tbody.querySelectorAll('tr')).map(tr => ({
            name: tr.querySelector('.in-name').value.trim(),
            type: tr.querySelector('.in-type').value,
            in: tr.querySelector('.in-in').value,
            required: tr.querySelector('.in-required').checked,
            defaultValue: tr.querySelector('.in-default').value.trim(),
            description: tr.querySelector('.in-desc').value.trim()
        })).filter(p => p.name);
    }

    // ========== 提交表单 ==========
    async function submitForm(saveDraftOnly, data, isEdit) {
        const name = $('fName').value.trim();
        const path = $('fPath').value.trim();
        const method = $('fMethod').value;
        const desc = $('fDesc').value.trim();

        if (!name) { alert('请填写 API 名称'); return; }
        if (!path.startsWith('/')) { alert('API 路径必须以 / 开头'); return; }

        const def = {
            id: isEdit ? (data.id || null) : null,
            name: name,
            path: path,
            method: method,
            description: desc,
            apiType: currentApiType,
            status: saveDraftOnly ? 0 : 1,
            inputs: collectInputs()
        };

        if (currentApiType === 'table') {
            def.datasourceId = $('fDs')?.value || '';
            def.database = $('fDb')?.value || '';
            def.table = $('fTable')?.value || '';
            def.pageEnabled = parseInt($('fPageEnabled')?.value || '0', 10);
            def.pageSize = parseInt($('fPageSize')?.value || '20', 10);
            const outputs = [];
            document.querySelectorAll('#outputFields .output-cb:checked').forEach(cb => {
                outputs.push({ name: cb.dataset.field, type: 'string', mapTo: cb.dataset.field });
            });
            def.outputs = outputs;
            if (!def.datasourceId || !def.database || !def.table) {
                alert('请完整选择数据源 / 数据库 / 表'); return;
            }
        } else if (currentApiType === 'custom') {
            def.backendUrl = $('fBackendUrl')?.value.trim() || '';
            def.headers = $('fHeaders')?.value.trim() || '';
            if (!def.backendUrl) { alert('请填写后端 URL'); return; }
        } else if (currentApiType === 'system') {
            def.systemPath = data.systemPath;
            def.systemMethod = data.systemMethod;
            def.systemModule = data.systemModule;
            if (!def.systemPath) { alert('请选择系统接口'); return; }
        }

        try {
            const r = await (await fetch(API_PUBLISH + '/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                body: JSON.stringify(def)
            })).json();
            if (r.success) {
                alert(saveDraftOnly ? '✅ 草稿已保存' : '✅ 发布成功');
                editingMode = false;
                selectedApiId = r.id;
                await loadApiList();
                showApiDetail(r.id);
            } else {
                alert('❌ 保存失败：' + (r.error || '未知错误'));
            }
        } catch (e) {
            alert('❌ 请求异常：' + e.message);
        }
    }

    // ========== 测试弹窗（保留原有） ==========
    let currentTestApiId = null;

    async function openTestModal(id) {
        currentTestApiId = id;
        const modal = $('apiTestModal');
        const title = $('apiTestModalTitle');
        const meta = $('apiTestMeta');
        const form = $('apiTestForm');
        const resultWrap = $('apiTestResultWrap');
        const resultContent = $('apiTestResultContent');
        const resultStatus = $('apiTestResultStatus');
        const invokeBtn = $('apiTestInvokeBtn');

        form.innerHTML = '<div class="empty-tip">加载中...</div>';
        meta.innerHTML = '';
        resultWrap.style.display = 'none';
        resultContent.textContent = '';
        resultStatus.textContent = '';
        resultStatus.className = 'api-test-result-status';
        invokeBtn.disabled = true;
        invokeBtn.textContent = '🚀 调用';
        modal.style.display = 'flex';

        try {
            const r = await (await fetch(API_PUBLISH + '/get?id=' + encodeURIComponent(id))).json();
            if (!r.success) throw new Error(r.error || '加载失败');
            const def = r.data || {};
            title.textContent = `⚡ 测试 API：${def.name || def.path || ''}`;
            const inputs = def.inputs || [];
            const t = TYPE_META[def.apiType] || TYPE_META.table;
            meta.innerHTML = `
                <div><b>类型：</b>${t.icon} ${t.label}</div>
                <div><b>方法：</b>${escapeHtml(def.method || 'POST')} &nbsp;·&nbsp;
                     <b>路径：</b>${escapeHtml(def.path || '')}</div>
                ${def.apiType === 'table' ? `<div><b>数据源：</b>${escapeHtml(def.datasourceId || '-')}
                     · <b>库：</b>${escapeHtml(def.database || '-')}
                     · <b>表：</b>${escapeHtml(def.table || '-')}</div>` : ''}
                ${def.apiType === 'custom' ? `<div><b>URL：</b>${escapeHtml(def.backendUrl || '-')}</div>` : ''}
                ${def.apiType === 'system' ? `<div><b>系统接口：</b>${escapeHtml(def.systemModule || '')} / ${escapeHtml(def.systemPath || '')}</div>` : ''}
            `;
            if (!inputs.length) {
                form.innerHTML = '<div class="empty-tip">该 API 没有入参，直接点击下方「调用」即可</div>';
            } else {
                form.innerHTML = inputs.map((p, idx) => renderTestField(p, idx)).join('');
                inputs.forEach((p, idx) => {
                    const el = document.getElementById('apiTestField_' + idx);
                    if (!el) return;
                    if (el.type === 'checkbox') {
                        el.checked = (p.defaultValue === 'true' || p.defaultValue === '1');
                    } else if (p.defaultValue) {
                        el.value = p.defaultValue;
                    }
                });
            }
            invokeBtn.disabled = false;
        } catch (e) {
            form.innerHTML = `<div style="color:#dc2626;padding:12px;">❌ ${escapeHtml(e.message)}</div>`;
        }
    }

    function renderTestField(p, idx) {
        const fieldId = 'apiTestField_' + idx;
        const name = p.name || '';
        const type = (p.type || 'string').toLowerCase();
        const required = !!p.required;
        const inLoc = p.in || 'query';
        const reqMark = required ? '<span class="required-mark">*</span>' : '';
        const badge = `<span class="field-type-badge">${escapeHtml(type)}</span>
                       <span class="field-in-badge">${escapeHtml(inLoc)}</span>`;
        const hint = p.description ? `<div class="api-test-field-hint">${escapeHtml(p.description)}</div>` : '';
        let inputHtml;
        if (type === 'boolean') {
            inputHtml = `<div class="api-test-checkbox-wrap">
                <input type="checkbox" id="${fieldId}" data-name="${escapeAttr(name)}" data-type="boolean">
                <label for="${fieldId}" style="font-size:12px;color:var(--text-muted);cursor:pointer;">勾选为 true</label>
            </div>`;
        } else if (type === 'number' || type === 'integer') {
            inputHtml = `<input type="number" class="api-test-input" id="${fieldId}"
                data-name="${escapeAttr(name)}" data-type="${escapeAttr(type)}"
                data-required="${required ? '1' : '0'}" placeholder="${escapeAttr(p.defaultValue || '')}">`;
        } else {
            inputHtml = `<input type="text" class="api-test-input" id="${fieldId}"
                data-name="${escapeAttr(name)}" data-type="string"
                data-required="${required ? '1' : '0'}" placeholder="${escapeAttr(p.defaultValue || '')}">`;
        }
        return `<div class="api-test-field" data-field="${escapeAttr(name)}">
            <div class="api-test-field-label">${reqMark}<span>${escapeHtml(name)}</span>${badge}</div>
            ${inputHtml}${hint}
        </div>`;
    }

    async function invokeTestApi() {
        if (!currentTestApiId) return;
        const invokeBtn = $('apiTestInvokeBtn');
        const resultWrap = $('apiTestResultWrap');
        const resultContent = $('apiTestResultContent');
        const resultStatus = $('apiTestResultStatus');

        const form = $('apiTestForm');
        const params = {};
        const missing = [];
        form.querySelectorAll('[data-name]').forEach(el => {
            const name = el.dataset.name;
            if (el.type === 'checkbox') { params[name] = el.checked; return; }
            const raw = el.value;
            if (!raw) {
                el.classList.add('error');
                if (el.dataset.required === '1') missing.push(name);
                return;
            }
            el.classList.remove('error');
            if (el.dataset.type === 'number' || el.dataset.type === 'integer') {
                const n = Number(raw);
                if (Number.isNaN(n)) { el.classList.add('error'); if (el.dataset.required === '1') missing.push(name + '(非数字)'); return; }
                params[name] = n;
            } else {
                params[name] = raw;
            }
        });
        if (missing.length) {
            resultWrap.style.display = 'block';
            resultStatus.textContent = '⚠ 参数校验未通过';
            resultStatus.className = 'api-test-result-status err';
            resultContent.textContent = '以下参数未填写或格式错误：\n  • ' + missing.join('\n  • ');
            return;
        }

        invokeBtn.disabled = true;
        invokeBtn.textContent = '⏳ 调用中...';
        resultWrap.style.display = 'block';
        resultStatus.textContent = '调用中...';
        resultStatus.className = 'api-test-result-status';
        resultContent.textContent = '请求发送中...';

        const t0 = Date.now();
        try {
            const resp = await fetch(window.apiUrl('/api/gateway/execute'), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                body: JSON.stringify({ apiId: currentTestApiId, params })
            });
            const text = await resp.text();
            let pretty = text;
            try { pretty = JSON.stringify(JSON.parse(text), null, 2); } catch (e) {}
            const elapsed = Date.now() - t0;
            resultStatus.textContent = `HTTP ${resp.status} · ${elapsed} ms`;
            resultStatus.className = 'api-test-result-status ' + (resp.ok ? 'ok' : 'err');
            resultContent.textContent = pretty || '(空响应)';
        } catch (e) {
            resultStatus.textContent = '请求异常';
            resultStatus.className = 'api-test-result-status err';
            resultContent.textContent = e.message || String(e);
        } finally {
            invokeBtn.disabled = false;
            invokeBtn.textContent = '🚀 调用';
        }
    }

    function closeTestModal() {
        const modal = $('apiTestModal');
        if (modal) modal.style.display = 'none';
        currentTestApiId = null;
    }

    $('apiTestModalClose')?.addEventListener('click', closeTestModal);
    $('apiTestCancelBtn')?.addEventListener('click', closeTestModal);
    $('apiTestInvokeBtn')?.addEventListener('click', invokeTestApi);
    $('apiTestModal')?.addEventListener('click', e => {
        if (e.target && e.target.id === 'apiTestModal') closeTestModal();
    });
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape') {
            const modal = $('apiTestModal');
            if (modal && modal.style.display === 'flex') closeTestModal();
        }
    });

    // ========== 对外 API ==========
    window.CustomApi = {
        onShow: () => {
            if (!els.apiList) bindDom();
            loadApiList();
        },
        openTestModal
    };

    // ========== 初始化 ==========
    function init() {
        bindDom();
        if (els.newBtn) {
            els.newBtn.addEventListener('click', () => showPublishForm(null));
        }
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();