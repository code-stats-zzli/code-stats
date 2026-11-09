(function () {
    'use strict';

    const subTabs = document.querySelectorAll('.subtab-btn');
    const subPanes = {
        temp:      document.getElementById('subTempPane'),
        publish:   document.getElementById('subPublishPane'),
        published: document.getElementById('subPublishedPane')
    };

    function switchSubTab(id) {
        subTabs.forEach(t => t.classList.toggle('active', t.dataset.subtab === id));
        Object.keys(subPanes).forEach(k => {
            if (subPanes[k]) subPanes[k].classList.toggle('active', k === id);
        });
        if (id === 'publish') ensureDataSourcesLoaded();
        if (id === 'published') loadPublishedApis();
    }
    subTabs.forEach(t => t.addEventListener('click', () => switchSubTab(t.dataset.subtab)));

    const customUrlInput         = document.getElementById('customApiUrl');
    const customMethodSelect     = document.getElementById('customApiMethod');
    const customHeadersTextarea  = document.getElementById('customApiHeaders');
    const sendCustomBtn          = document.getElementById('sendCustomRequestBtn');
    const customResponseDiv      = document.getElementById('customResponseArea');
    const bodyFormatSelectEl     = document.getElementById('bodyFormatSelect');
    const bodyContainer          = document.getElementById('customBodyContainer');

    if (!bodyFormatSelectEl) {
        const methodRow = customMethodSelect ? customMethodSelect.parentElement : null;
        if (methodRow) {
            const formatGroup = document.createElement('div');
            formatGroup.className = 'form-group';
            formatGroup.innerHTML = `
                <label>📄 参数格式</label>
                <select id="bodyFormatSelect">
                    <option value="json">JSON (application/json)</option>
                    <option value="form">表单 (application/x-www-form-urlencoded)</option>
                    <option value="text">纯文本 (text/plain)</option>
                </select>
            `;
            methodRow.insertAdjacentElement('afterend', formatGroup);
        }
    }
    if (!bodyContainer && customHeadersTextarea) {
        const headersGroup = customHeadersTextarea.parentElement;
        const newContainer = document.createElement('div');
        newContainer.id = 'customBodyContainer';
        newContainer.className = 'form-group';
        newContainer.innerHTML = `<label>📦 请求 Body</label><div id="bodyEditor"></div>`;
        if (headersGroup) headersGroup.insertAdjacentElement('afterend', newContainer);
    }

    const bodyFormatSelectElFinal = document.getElementById('bodyFormatSelect');
    const bodyEditorDiv = document.getElementById('bodyEditor');

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str).replace(/[&<>"']/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[m]));
    }
    function escapeAttr(str) { return escapeHtml(str); }

    function getBodyContent() {
        if (!bodyFormatSelectElFinal) return '';
        const format = bodyFormatSelectElFinal.value;
        if (format === 'json') {
            const ta = document.getElementById('jsonEditor');
            return ta ? ta.value : '';
        }
        if (format === 'form') {
            const pairs = [];
            document.querySelectorAll('.form-key-value').forEach(row => {
                const key = row.querySelector('.form-key')?.value;
                const val = row.querySelector('.form-value')?.value;
                if (key && key.trim()) pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(val || '')}`);
            });
            return pairs.join('&');
        }
        const ta = document.getElementById('textEditor');
        return ta ? ta.value : '';
    }

    function switchBodyEditor() {
        if (!bodyEditorDiv || !bodyFormatSelectElFinal) return;
        const format = bodyFormatSelectElFinal.value;

        if (format === 'json') {
            bodyEditorDiv.innerHTML = `
                <div style="display:flex;gap:8px;margin-bottom:8px;">
                    <button id="beautifyJsonBtn" class="btn-secondary" style="padding:4px 12px;">🎨 格式化</button>
                    <button id="exampleJsonBtn" class="btn-secondary" style="padding:4px 12px;">📋 示例 JSON</button>
                </div>
                <textarea id="jsonEditor" rows="8" style="width:100%;font-family:monospace;" placeholder='{"key": "value"}'></textarea>
            `;
            const ta = document.getElementById('jsonEditor');
            if (ta && !ta.value) ta.value = '{\n  "example": "value"\n}';
            document.getElementById('beautifyJsonBtn')?.addEventListener('click', () => {
                if (!ta) return;
                try { ta.value = JSON.stringify(JSON.parse(ta.value), null, 2); }
                catch (e) { alert('JSON 格式错误，无法格式化'); }
            });
            document.getElementById('exampleJsonBtn')?.addEventListener('click', () => {
                if (ta) ta.value = JSON.stringify({ id: 1, name: "test", status: "active" }, null, 2);
            });
        } else if (format === 'form') {
            bodyEditorDiv.innerHTML = `
                <div id="formFieldsContainer" style="margin-bottom:8px;">
                    <div class="form-key-value" style="display:flex;gap:8px;margin-bottom:6px;">
                        <input type="text" class="form-key" placeholder="参数名" style="flex:1;">
                        <input type="text" class="form-value" placeholder="值" style="flex:1;">
                        <button class="remove-field-btn" style="background:none;border:none;cursor:pointer;">🗑️</button>
                    </div>
                </div>
                <button id="addFormFieldBtn" class="btn-secondary" style="padding:4px 12px;">+ 添加字段</button>
            `;
            const addBtn = document.getElementById('addFormFieldBtn');
            const container = document.getElementById('formFieldsContainer');
            if (addBtn && container) {
                addBtn.addEventListener('click', () => {
                    const newRow = document.createElement('div');
                    newRow.className = 'form-key-value';
                    newRow.style.cssText = 'display:flex;gap:8px;margin-bottom:6px;';
                    newRow.innerHTML = `
                        <input type="text" class="form-key" placeholder="参数名" style="flex:1;">
                        <input type="text" class="form-value" placeholder="值" style="flex:1;">
                        <button class="remove-field-btn" style="background:none;border:none;cursor:pointer;">🗑️</button>
                    `;
                    container.appendChild(newRow);
                    newRow.querySelector('.remove-field-btn')?.addEventListener('click', () => newRow.remove());
                });
            }
            document.querySelectorAll('.remove-field-btn').forEach(btn => {
                btn.addEventListener('click', (e) => e.target.closest('.form-key-value')?.remove());
            });
        } else {
            bodyEditorDiv.innerHTML = `<textarea id="textEditor" rows="8" style="width:100%;font-family:monospace;" placeholder="纯文本内容..."></textarea>`;
        }
    }

    async function sendCustomRequest() {
        if (!customUrlInput || !customUrlInput.value.trim()) {
            alert('❌ 请输入请求地址'); return;
        }
        const url = customUrlInput.value.trim();
        const method = customMethodSelect ? customMethodSelect.value : 'GET';

        let headers = {};
        const headersRaw = customHeadersTextarea ? customHeadersTextarea.value.trim() : '';
        if (headersRaw) {
            try {
                headers = JSON.parse(headersRaw);
                if (typeof headers !== 'object' || Array.isArray(headers)) throw new Error();
            } catch (e) {
                alert('Headers 格式错误，请输入有效的 JSON 对象'); return;
            }
        }

        const format = bodyFormatSelectElFinal ? bodyFormatSelectElFinal.value : 'json';
        let body = getBodyContent();
        let contentType = '';
        if (format === 'json') {
            contentType = 'application/json';
            if (body) { try { JSON.parse(body); } catch (e) { alert('JSON 格式错误'); return; } }
        } else if (format === 'form') {
            contentType = 'application/x-www-form-urlencoded';
        } else {
            contentType = 'text/plain';
        }
        if (body && !headers['Content-Type'] && !headers['content-type']) {
            headers['Content-Type'] = contentType;
        }

        if (!customResponseDiv) return;
        customResponseDiv.style.display = 'block';
        customResponseDiv.innerHTML = '<div class="loading">⏳ 发送请求中...</div>';

        try {
            const options = { method, headers };
            if (body && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) options.body = body;
            const response = await fetch(url, options);
            const responseText = await response.text();
            let formatted = responseText;
            try { formatted = JSON.stringify(JSON.parse(responseText), null, 2); } catch (e) {}
            customResponseDiv.innerHTML = `
                <div style="margin-bottom:12px;display:flex;justify-content:space-between;">
                    <strong style="color:${response.ok ? '#10b981' : '#ef4444'}">
                        📡 状态码: ${response.status} ${response.statusText}
                    </strong>
                </div>
                <pre style="margin:0;white-space:pre-wrap;font-family:monospace;">${escapeHtml(formatted)}</pre>
            `;
        } catch (err) {
            customResponseDiv.innerHTML = `<div style="color:#ef4444;">❌ 网络或请求错误: ${err.message}</div>`;
        }
    }

    if (bodyFormatSelectElFinal) {
        bodyFormatSelectElFinal.addEventListener('change', switchBodyEditor);
        switchBodyEditor();
    }
    if (sendCustomBtn) sendCustomBtn.addEventListener('click', sendCustomRequest);

    const pubName          = document.getElementById('pubName');
    const pubPath          = document.getElementById('pubPath');
    const pubMethod        = document.getElementById('pubMethod');
    const pubDesc          = document.getElementById('pubDesc');
    const pubDs            = document.getElementById('pubDs');
    const pubDb            = document.getElementById('pubDb');
    const pubTable         = document.getElementById('pubTable');
    const pubPageEnabled   = document.getElementById('pubPageEnabled');
    const pubInputTableBody= document.getElementById('pubInputTableBody');
    const addInputBtn      = document.getElementById('addInputBtn');
    const pubOutputFields  = document.getElementById('pubOutputFields');
    const publishApiBtn    = document.getElementById('publishApiBtn');
    const saveDraftBtn     = document.getElementById('saveDraftBtn');
    const resetPublishBtn  = document.getElementById('resetPublishBtn');
    const publishResult    = document.getElementById('publishResult');

    let dataSourcesLoaded = false;

    async function ensureDataSourcesLoaded() {
        if (dataSourcesLoaded) return;
        dataSourcesLoaded = true;
        try {
            const resp = await fetch(window.apiUrl('/cube/api/datasources'));
            const list = await resp.json();
            pubDs.innerHTML = '<option value="">-- 选择数据源 --</option>' +
                (Array.isArray(list) ? list.map(d =>
                    `<option value="${escapeAttr(d.id)}">${escapeHtml(d.name)} (${escapeHtml(d.dbType)})</option>`
                ).join('') : '');
        } catch (e) {
            pubDs.innerHTML = `<option value="">加载失败: ${escapeHtml(e.message)}</option>`;
            dataSourcesLoaded = false;
        }
    }

    pubDs.addEventListener('change', async () => {
        const dsId = pubDs.value;
        pubDb.innerHTML = '<option value="">加载中...</option>';
        pubTable.innerHTML = '<option value="">-- 先选数据库 --</option>';
        pubDb.disabled = !dsId;
        pubTable.disabled = true;
        pubOutputFields.innerHTML = '<div class="empty-tip">请先选择数据表</div>';
        if (!dsId) {
            pubDb.innerHTML = '<option value="">-- 先选数据源 --</option>';
            return;
        }
        try {
            const url = window.apiUrl('/cube/api/databases?dsId=' + encodeURIComponent(dsId));
            const dbs = await (await fetch(url)).json();
            pubDb.innerHTML = '<option value="">-- 选择数据库 --</option>' +
                (Array.isArray(dbs) ? dbs.map(d => `<option value="${escapeAttr(d)}">${escapeHtml(d)}</option>`).join('') : '');
        } catch (e) {
            pubDb.innerHTML = `<option value="">加载失败: ${escapeHtml(e.message)}</option>`;
        }
    });

    pubDb.addEventListener('change', async () => {
        const dsId = pubDs.value;
        const db = pubDb.value;
        pubTable.innerHTML = '<option value="">加载中...</option>';
        pubTable.disabled = !db;
        pubOutputFields.innerHTML = '<div class="empty-tip">请先选择数据表</div>';
        if (!db) { pubTable.innerHTML = '<option value="">-- 先选数据库 --</option>'; return; }
        try {
            const url = window.apiUrl('/cube/api/tables?dsId=' + encodeURIComponent(dsId) + '&db=' + encodeURIComponent(db));
            const tables = await (await fetch(url)).json();
            pubTable.innerHTML = '<option value="">-- 选择数据表 --</option>' +
                (Array.isArray(tables) ? tables.map(t =>
                    `<option value="${escapeAttr(t.name)}">${escapeHtml(t.name)}</option>`
                ).join('') : '');
        } catch (e) {
            pubTable.innerHTML = `<option value="">加载失败: ${escapeHtml(e.message)}</option>`;
        }
    });

    pubTable.addEventListener('change', async () => {
        const dsId = pubDs.value;
        const db = pubDb.value;
        const table = pubTable.value;
        pubOutputFields.innerHTML = '<div class="empty-tip">加载中...</div>';
        if (!table) {
            pubOutputFields.innerHTML = '<div class="empty-tip">请先选择数据表</div>';
            return;
        }
        try {
            const url = window.apiUrl('/cube/api/columns?dsId='
                + encodeURIComponent(dsId) + '&db=' + encodeURIComponent(db)
                + '&table=' + encodeURIComponent(table));
            const schema = await (await fetch(url)).json();
            const cols = (schema && schema.columns) || [];
            if (!cols.length) {
                pubOutputFields.innerHTML = '<div class="empty-tip">该表无字段信息</div>';
                return;
            }
            pubOutputFields.innerHTML = cols.map(c => `
                <label class="output-field-item">
                    <input type="checkbox" class="pub-output-cb"
                           data-field="${escapeAttr(c.name)}" checked>
                    <span class="out-field-name">${escapeHtml(c.name)}</span>
                    <span class="out-field-type">${escapeHtml(c.type || '')}</span>
                </label>
            `).join('');
        } catch (e) {
            pubOutputFields.innerHTML = `<div class="empty-tip">加载失败: ${escapeHtml(e.message)}</div>`;
        }
    });

    function addInputRow(name, type, loc, required, defValue, desc) {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><input type="text" class="pub-input-name" value="${escapeAttr(name || '')}" placeholder="如 id"></td>
            <td><select class="pub-input-type">
                <option value="string" ${type === 'string' ? 'selected' : ''}>string</option>
                <option value="number" ${type === 'number' ? 'selected' : ''}>number</option>
                <option value="integer" ${type === 'integer' ? 'selected' : ''}>integer</option>
                <option value="boolean" ${type === 'boolean' ? 'selected' : ''}>boolean</option>
            </select></td>
            <td><select class="pub-input-in">
                <option value="query" ${loc === 'query' ? 'selected' : ''}>query</option>
                <option value="body" ${loc === 'body' ? 'selected' : ''}>body</option>
                <option value="path" ${loc === 'path' ? 'selected' : ''}>path</option>
            </select></td>
            <td style="text-align:center;"><input type="checkbox" class="pub-input-required" ${required ? 'checked' : ''}></td>
            <td><input type="text" class="pub-input-default" value="${escapeAttr(defValue || '')}"></td>
            <td><input type="text" class="pub-input-desc" value="${escapeAttr(desc || '')}"></td>
            <td><button class="btn-secondary btn-sm remove-input-btn" title="删除">✖</button></td>
        `;
        pubInputTableBody.appendChild(tr);
        tr.querySelector('.remove-input-btn').addEventListener('click', () => tr.remove());
    }
    addInputBtn.addEventListener('click', () => addInputRow('', 'string', 'query', false, '', ''));

    function collectInputs() {
        const inputs = [];
        pubInputTableBody.querySelectorAll('tr').forEach(tr => {
            const name = tr.querySelector('.pub-input-name').value.trim();
            if (!name) return;
            inputs.push({
                name: name,
                type: tr.querySelector('.pub-input-type').value,
                in: tr.querySelector('.pub-input-in').value,
                required: tr.querySelector('.pub-input-required').checked,
                defaultValue: tr.querySelector('.pub-input-default').value.trim(),
                description: tr.querySelector('.pub-input-desc').value.trim(),
                mapTo: name
            });
        });
        return inputs;
    }

    function collectOutputs() {
        const outputs = [];
        pubOutputFields.querySelectorAll('.pub-output-cb:checked').forEach(cb => {
            outputs.push({
                name: cb.dataset.field,
                type: 'string',
                mapTo: cb.dataset.field
            });
        });
        return outputs;
    }

    function showPublishResult(msg, isError) {
        publishResult.style.display = 'block';
        publishResult.innerHTML = `<pre style="margin:0;color:${isError ? '#ef4444' : '#22c55e'};white-space:pre-wrap;">${escapeHtml(msg)}</pre>`;
    }

    async function doPublish(saveDraftOnly) {
        const name = pubName.value.trim();
        const path = pubPath.value.trim();
        const dsId = pubDs.value;
        const db = pubDb.value;
        const table = pubTable.value;

        if (!name) { showPublishResult('❌ 请填写 API 名称', true); return; }
        if (!path.startsWith('/')) { showPublishResult('❌ API 路径必须以 / 开头', true); return; }
        if (!dsId || !db || !table) {
            showPublishResult('❌ 请完整选择数据源 / 数据库 / 表', true); return;
        }
        const outputs = collectOutputs();
        if (outputs.length === 0) {
            showPublishResult('❌ 请至少勾选一个出参字段', true); return;
        }

        const def = {
            name: name,
            path: path,
            method: pubMethod.value,
            description: pubDesc.value.trim(),
            sourceType: 'table',
            datasourceId: dsId,
            database: db,
            table: table,
            inputs: collectInputs(),
            outputs: outputs,
            pageEnabled: parseInt(pubPageEnabled.value) || 0,
            pageSize: 20,
            status: saveDraftOnly ? 0 : 1
        };

        try {
            const resp = await fetch(window.apiUrl('/api/publish/api/save'), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                body: JSON.stringify(def)
            });
            const r = await resp.json();
            if (r.success) {
                const tip = saveDraftOnly ? '✅ 草稿已保存' : '✅ 发布成功';
                showPublishResult(
                    `${tip}\nAPI ID: ${r.id}\n\n调用方式：\nPOST ${window.apiUrl('/api/gateway/execute')}\nBody: {"apiId":"${r.id}","params":{ ... }}`,
                    false
                );
                if (!saveDraftOnly) resetPublishForm();
            } else {
                showPublishResult('❌ 保存失败：' + (r.error || '未知错误'), true);
            }
        } catch (e) {
            showPublishResult('❌ 请求异常：' + e.message, true);
        }
    }

    publishApiBtn.addEventListener('click', () => doPublish(false));
    saveDraftBtn.addEventListener('click', () => doPublish(true));

    function resetPublishForm() {
        pubName.value = '';
        pubPath.value = '';
        pubDesc.value = '';
        pubMethod.value = 'POST';
        pubDs.value = '';
        pubDb.innerHTML = '<option value="">-- 先选数据源 --</option>';
        pubDb.disabled = true;
        pubTable.innerHTML = '<option value="">-- 先选数据库 --</option>';
        pubTable.disabled = true;
        pubInputTableBody.innerHTML = '';
        pubOutputFields.innerHTML = '<div class="empty-tip">请先选择数据表</div>';
        pubPageEnabled.value = '0';
        publishResult.style.display = 'none';
        addInputRow('', 'string', 'query', false, '', '');
    }
    resetPublishBtn.addEventListener('click', resetPublishForm);

    const publishedApiList = document.getElementById('publishedApiList');
    const refreshPublishedBtn = document.getElementById('refreshPublishedBtn');

    async function loadPublishedApis() {
        if (!publishedApiList) return;
        publishedApiList.innerHTML = '<div class="empty-tip">加载中...</div>';
        try {
            const resp = await fetch(window.apiUrl('/api/publish/api/list'), { cache: 'no-store' });
            const r = await resp.json();
            if (!r.success) throw new Error(r.error || '加载失败');
            const items = r.items || [];
            if (items.length === 0) {
                publishedApiList.innerHTML = '<div class="empty-tip">暂无已发布 API，请先到「📤 发布 API」创建</div>';
                return;
            }
            publishedApiList.innerHTML = items.map(it => `
                <div class="published-item" data-id="${escapeAttr(it.id)}">
                    <div class="published-head">
                        <span class="method-badge ${(it.method || 'GET').toLowerCase()}">${escapeHtml(it.method || 'GET')}</span>
                        <span class="published-path">${escapeHtml(it.path || '')}</span>
                        <span class="published-name">${escapeHtml(it.name || '')}</span>
                        <span class="status-badge ${it.status === 1 ? 'active' : 'disabled'}">
                            ${it.status === 1 ? '启用' : '草稿'}
                        </span>
                    </div>
                    <div class="published-meta">
                        ${escapeHtml(it.description || '无描述')}
                        &nbsp;·&nbsp; 数据源 ${escapeHtml(it.datasourceId || '-')}
                        &nbsp;·&nbsp; 表 ${escapeHtml(it.table || '-')}
                        &nbsp;·&nbsp; 入参 ${(it.inputs || []).length} / 出参 ${(it.outputs || []).length}
                        &nbsp;·&nbsp; 更新于 ${escapeHtml(it.updateTime || '')}
                    </div>
                    <div class="published-actions">
                        <button class="btn-secondary btn-sm" data-act="test" data-id="${escapeAttr(it.id)}">⚡ 测试</button>
                        <button class="btn-secondary btn-sm" data-act="edit" data-id="${escapeAttr(it.id)}">✏️ 编辑</button>
                        <button class="btn-secondary btn-sm" data-act="toggle" data-id="${escapeAttr(it.id)}">
                            ${it.status === 1 ? '⏸ 禁用' : '▶ 启用'}
                        </button>
                        <button class="btn-secondary btn-sm" data-act="copy" data-id="${escapeAttr(it.id)}">📋 复制调用示例</button>
                        <button class="btn-secondary btn-sm" data-act="delete" data-id="${escapeAttr(it.id)}"
                                style="color:#dc2626;">🗑 删除</button>
                    </div>
                </div>
            `).join('');

            publishedApiList.querySelectorAll('button[data-act]').forEach(btn => {
                btn.addEventListener('click', () => handlePublishedAction(btn.dataset.act, btn.dataset.id));
            });
        } catch (e) {
            publishedApiList.innerHTML = `<div class="empty-tip">加载失败: ${escapeHtml(e.message)}</div>`;
        }
    }

    async function handlePublishedAction(act, id) {
        if (act === 'test') {
            const paramsStr = prompt('输入 JSON 参数（可留空）：', '{\n  "page": 1,\n  "pageSize": 20\n}');
            if (paramsStr === null) return;
            let params = {};
            if (paramsStr.trim()) {
                try { params = JSON.parse(paramsStr); }
                catch (e) { alert('JSON 格式错误：' + e.message); return; }
            }
            try {
                const resp = await fetch(window.apiUrl('/api/gateway/execute'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                    body: JSON.stringify({ apiId: id, params: params })
                });
                const text = await resp.text();
                let pretty = text;
                try { pretty = JSON.stringify(JSON.parse(text), null, 2); } catch (e) {}
                alert('测试结果：\n\n' + pretty);
            } catch (e) {
                alert('请求异常：' + e.message);
            }
        } else if (act === 'edit') {
            await editPublishedApi(id);
        } else if (act === 'toggle') {
            try {
                const info = await (await fetch(window.apiUrl('/api/publish/api/get?id=' + encodeURIComponent(id)))).json();
                if (!info.success) { alert(info.error); return; }
                const next = info.data.status === 1 ? 0 : 1;
                const r = await (await fetch(window.apiUrl('/api/publish/api/toggle'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ id: id, status: next })
                })).json();
                if (!r.success) alert(r.error || '操作失败');
                loadPublishedApis();
            } catch (e) { alert('请求异常：' + e.message); }
        } else if (act === 'copy') {
            const example =
`POST ${window.apiUrl('/api/gateway/execute')}
Content-Type: application/json

{
  "apiId": "${id}",
  "params": {
    "page": 1,
    "pageSize": 20
  }
}`;
            try {
                await navigator.clipboard.writeText(example);
                alert('✅ 调用示例已复制到剪贴板');
            } catch (e) {
                prompt('请手动复制：', example);
            }
        } else if (act === 'delete') {
            if (!confirm('确定删除该 API 吗？')) return;
            try {
                const r = await (await fetch(window.apiUrl('/api/publish/api/delete'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ id: id })
                })).json();
                if (!r.success) alert(r.error || '删除失败');
                loadPublishedApis();
            } catch (e) { alert('请求异常：' + e.message); }
        }
    }

    async function editPublishedApi(id) {
        try {
            const r = await (await fetch(window.apiUrl('/api/publish/api/get?id=' + encodeURIComponent(id)))).json();
            if (!r.success) { alert(r.error); return; }
            const def = r.data;
            switchSubTab('publish');
            await ensureDataSourcesLoaded();

            pubName.value = def.name || '';
            pubPath.value = def.path || '';
            pubMethod.value = def.method || 'POST';
            pubDesc.value = def.description || '';
            pubPageEnabled.value = String(def.pageEnabled || 0);

            pubDs.value = def.datasourceId || '';
            if (def.datasourceId) {
                const dbResp = await (await fetch(window.apiUrl('/cube/api/databases?dsId=' + encodeURIComponent(def.datasourceId)))).json();
                pubDb.innerHTML = '<option value="">-- 选择数据库 --</option>' +
                    (Array.isArray(dbResp) ? dbResp.map(d => `<option value="${escapeAttr(d)}">${escapeHtml(d)}</option>`).join('') : '');
                pubDb.disabled = false;
                pubDb.value = def.database || '';

                if (def.database) {
                    const tblResp = await (await fetch(window.apiUrl('/cube/api/tables?dsId='
                        + encodeURIComponent(def.datasourceId) + '&db=' + encodeURIComponent(def.database)))).json();
                    pubTable.innerHTML = '<option value="">-- 选择数据表 --</option>' +
                        (Array.isArray(tblResp) ? tblResp.map(t => `<option value="${escapeAttr(t.name)}">${escapeHtml(t.name)}</option>`).join('') : '');
                    pubTable.disabled = false;
                    pubTable.value = def.table || '';

                    if (def.table) {
                        const schema = await (await fetch(window.apiUrl('/cube/api/columns?dsId='
                            + encodeURIComponent(def.datasourceId) + '&db=' + encodeURIComponent(def.database)
                            + '&table=' + encodeURIComponent(def.table)))).json();
                        const cols = (schema && schema.columns) || [];
                        const outSet = new Set((def.outputs || []).map(o => o.name));
                        pubOutputFields.innerHTML = cols.length ? cols.map(c => `
                            <label class="output-field-item">
                                <input type="checkbox" class="pub-output-cb"
                                       data-field="${escapeAttr(c.name)}"
                                       ${outSet.has(c.name) ? 'checked' : ''}>
                                <span class="out-field-name">${escapeHtml(c.name)}</span>
                                <span class="out-field-type">${escapeHtml(c.type || '')}</span>
                            </label>
                        `).join('') : '<div class="empty-tip">该表无字段信息</div>';
                    }
                }
            }

            pubInputTableBody.innerHTML = '';
            (def.inputs || []).forEach(p => {
                addInputRow(p.name, p.type, p.in, p.required, p.defaultValue, p.description);
            });
            if ((def.inputs || []).length === 0) addInputRow('', 'string', 'query', false, '', '');

            showPublishResult('✏️ 已加载原 API 数据到表单，点击「发布 API」将另存为新记录（如需替换，请先删除原记录）。', false);
        } catch (e) {
            alert('加载失败：' + e.message);
        }
    }

    refreshPublishedBtn.addEventListener('click', loadPublishedApis);

    addInputRow('', 'string', 'query', false, '', '');
    ensureDataSourcesLoaded();
    if (window.location.hash === '#published') switchSubTab('published');
})();