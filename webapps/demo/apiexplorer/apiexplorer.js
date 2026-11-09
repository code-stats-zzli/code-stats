(function () {
    'use strict';

    /* ============================================================
     * ★ 顶级 Tab 切换
     * ============================================================ */
    const topTabs = document.querySelectorAll('.top-tab');
    const panes = {
        debug: document.getElementById('paneDebug'),
        custom: document.getElementById('paneCustom')
    };

    function switchTopTab(id) {
        topTabs.forEach(t => t.classList.toggle('active', t.dataset.topTab === id));
        Object.keys(panes).forEach(k => {
            if (panes[k]) panes[k].classList.toggle('active', k === id);
        });
        if (id === 'custom' && window.CustomApi && window.CustomApi.onShow) {
            window.CustomApi.onShow();
        }
    }

    topTabs.forEach(tab => {
        tab.addEventListener('click', () => switchTopTab(tab.dataset.topTab));
    });

    /* ============================================================
     * ★ 接口调试面板（原有逻辑保留）
     * ============================================================ */
    let modules = [];
    let currentApi = null;
    let allApis = [];

    const apiTree = document.getElementById('apiTree');
    const apiDetailDiv = document.getElementById('apiDetail');
    const searchInput = document.getElementById('apiSearch');

    function getDefaultParamValue(name, typeHint) {
        const lowerName = (name || '').toLowerCase();
        if (lowerName.includes('id')) return '1';
        if (lowerName.includes('name')) return 'example_name';
        if (lowerName.includes('page')) return '1';
        if (lowerName.includes('size') || lowerName.includes('limit')) return '10';
        if (lowerName.includes('email')) return 'test@example.com';
        if (lowerName.includes('status')) return 'active';
        if (typeHint === 'number' || typeHint === 'integer') return '0';
        if (typeHint === 'boolean') return 'true';
        return 'value';
    }

    function formatJsonString(s) {
        try { return JSON.stringify(JSON.parse(s), null, 2); } catch (e) { return null; }
    }

    function fillParamExamples() {
        document.querySelectorAll('.param-input').forEach(input => {
            const name = input.dataset.name;
            const typeSelect = input.closest('tr')?.querySelector('.param-type-select');
            const typeHint = typeSelect ? typeSelect.value : 'string';
            if (!input.value) input.value = getDefaultParamValue(name, typeHint);
        });
    }

    function fillExampleJson() {
        const ta = document.getElementById('requestBody');
        if (ta) ta.value = JSON.stringify({ example: "value", id: 1, name: "sample" }, null, 2);
    }

    function beautifyRequestBody() {
        const ta = document.getElementById('requestBody');
        if (!ta) return;
        const f = formatJsonString(ta.value);
        if (f) ta.value = f; else alert('当前内容不是合法的 JSON，无法格式化');
    }

    async function loadModules() {
        try {
            const resp = await fetch(window.apiUrl('/apiexplorer/api/modules'));
            if (!resp.ok) throw new Error('加载失败');
            modules = await resp.json();
            allApis = [];
            modules.forEach(m => m.apis.forEach(a => { a.moduleName = m.moduleName; allApis.push(a); }));
            renderTree(modules);
        } catch (err) {
            apiTree.innerHTML = `<li class="loading" style="color:red;">❌ ${err.message}</li>`;
        }
    }

    function escapeHtml(s) {
        if (!s) return '';
        return String(s).replace(/[&<>"]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[m]));
    }

    function renderTree(mods) {
        if (!mods.length) { apiTree.innerHTML = '<li class="loading">暂无接口</li>'; return; }
        apiTree.innerHTML = mods.map(m => `
            <li class="tree-node module-node">
                <div class="tree-item module">
                    <span class="toggle toggle-collapsed">▶</span>
                    <span class="icon">📁</span>
                    <span class="name">${escapeHtml(m.moduleName)}</span>
                    <span class="badge">${m.apis.length}</span>
                </div>
                <ul class="tree-children">
                    ${m.apis.map(a => `
                        <li class="tree-node api-node" data-id="${a.id}" data-path="${escapeHtml(a.path)}" data-method="${a.method}">
                            <div class="tree-item api">
                                <span class="toggle-placeholder" style="width:20px;"></span>
                                <span class="icon">📄</span>
                                <span class="api-method-badge ${a.method.toLowerCase()}">${a.method}</span>
                                <span class="name">${escapeHtml(a.summary || a.path)}</span>
                            </div>
                        </li>
                    `).join('')}
                </ul>
            </li>
        `).join('');

        apiTree.querySelectorAll('.module-node > .tree-item.module .toggle').forEach(toggle => {
            toggle.addEventListener('click', e => {
                e.stopPropagation();
                const ul = toggle.closest('.module-node').querySelector(':scope > .tree-children');
                const open = ul.classList.toggle('open');
                toggle.textContent = open ? '▼' : '▶';
            });
        });
        apiTree.querySelectorAll('.api-node').forEach(node => {
            node.addEventListener('click', e => { e.stopPropagation(); selectApi(node.dataset.id); });
        });
        const first = apiTree.querySelector('.module-node');
        if (first) {
            const toggle = first.querySelector('.toggle');
            const ul = first.querySelector(':scope > .tree-children');
            if (ul) { ul.classList.add('open'); if (toggle) toggle.textContent = '▼'; }
            const firstApi = first.querySelector('.api-node');
            if (firstApi) selectApi(firstApi.dataset.id);
        }
    }

    function selectApi(id) {
        const api = allApis.find(a => a.id === id);
        if (!api) return;
        currentApi = api;
        document.querySelectorAll('.api-node').forEach(n => n.classList.toggle('active', n.dataset.id === id));
        renderApiDetail(api);
    }

    function renderApiDetail(api) {
        let paramsHtml = '';
        if (api.parameters && api.parameters.length) {
            paramsHtml = `
                <div class="params-section">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                        <h4>📌 请求参数</h4>
                        <button id="fillParamsBtn" class="btn-secondary" style="padding:4px 12px;font-size:12px;">⚡ 填充示例</button>
                    </div>
                    <table class="params-table">
                        <thead><tr><th>参数名</th><th>位置</th><th>类型</th><th>必填</th><th>值</th></tr></thead>
                        <tbody>
                            ${api.parameters.map(p => `
                                <tr>
                                    <td>${escapeHtml(p.name)}</td>
                                    <td>${p.in}</td>
                                    <td>
                                        <select class="param-type-select" data-name="${p.name}">
                                            <option value="string">string</option>
                                            <option value="number">number</option>
                                            <option value="boolean">boolean</option>
                                            <option value="integer">integer</option>
                                        </select>
                                    </td>
                                    <td>${p.required ? '是' : '否'}</td>
                                    <td><input type="text" class="param-input" data-name="${p.name}" data-in="${p.in}" placeholder="值"></td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>`;
        }
        let bodyHtml = '';
        if (api.requestBody) {
            bodyHtml = `
                <div class="request-body">
                    <div style="display:flex;gap:12px;align-items:center;margin-bottom:8px;">
                        <h4>📦 Body (JSON)</h4>
                        <button id="beautifyJsonBtn" class="btn-secondary" style="padding:4px 12px;font-size:12px;">🎨 格式化</button>
                        <button id="exampleJsonBtn" class="btn-secondary" style="padding:4px 12px;font-size:12px;">📋 示例</button>
                    </div>
                    <textarea id="requestBody" placeholder='{"key":"value"}'></textarea>
                </div>`;
        }
        apiDetailDiv.innerHTML = `
            <div class="detail-header">
                <div class="detail-path">${api.method} ${escapeHtml(api.path)}</div>
                <div class="detail-desc">${escapeHtml(api.description || api.summary || '')}</div>
            </div>
            ${paramsHtml}
            ${bodyHtml}
            <button id="sendRequestBtn" class="send-btn">🚀 发送请求</button>
            <div id="responseArea" class="response-area" style="display:none;"></div>
        `;
        document.getElementById('sendRequestBtn').addEventListener('click', () => sendRequest(api));
        document.getElementById('beautifyJsonBtn')?.addEventListener('click', beautifyRequestBody);
        document.getElementById('exampleJsonBtn')?.addEventListener('click', fillExampleJson);
        document.getElementById('fillParamsBtn')?.addEventListener('click', fillParamExamples);
    }

    async function sendRequest(api) {
        let url = window.apiUrl(api.path);
        const queryParams = [];
        const pathParams = {};
        if (api.parameters) {
            document.querySelectorAll('.param-input').forEach(input => {
                const name = input.dataset.name;
                const val = input.value.trim();
                const loc = input.dataset.in;
                if (val) {
                    if (loc === 'query') queryParams.push(`${encodeURIComponent(name)}=${encodeURIComponent(val)}`);
                    else if (loc === 'path') pathParams[name] = val;
                }
            });
        }
        let finalPath = api.path;
        for (const [k, v] of Object.entries(pathParams)) finalPath = finalPath.replace(`{${k}}`, encodeURIComponent(v));
        url = window.apiUrl(finalPath);
        if (queryParams.length) url += '?' + queryParams.join('&');

        let body = null;
        let contentType = 'application/json';
        if (api.requestBody) {
            const ta = document.getElementById('requestBody');
            if (ta && ta.value.trim()) {
                try { JSON.parse(ta.value); body = ta.value; }
                catch (e) { body = ta.value; contentType = 'text/plain'; }
            }
        }
        const respDiv = document.getElementById('responseArea');
        respDiv.style.display = 'block';
        respDiv.innerHTML = '<div class="loading">⏳ 请求中...</div>';
        try {
            const options = { method: api.method, headers: { 'Content-Type': contentType } };
            if (body) options.body = body;
            const resp = await fetch(url, options);
            const text = await resp.text();
            let formatted = text;
            try { formatted = JSON.stringify(JSON.parse(text), null, 2); } catch (e) {}
            respDiv.innerHTML = `
                <div style="margin-bottom:8px;color:${resp.ok ? '#10b981' : '#ef4444'}">
                    <strong>状态码: ${resp.status} ${resp.statusText}</strong>
                </div>
                <pre style="margin:0;white-space:pre-wrap;">${escapeHtml(formatted)}</pre>`;
        } catch (err) {
            respDiv.innerHTML = `<div style="color:#ef4444;">❌ 请求失败: ${err.message}</div>`;
        }
    }

    searchInput.addEventListener('input', e => {
        const kw = e.target.value.toLowerCase();
        if (!kw) { renderTree(modules); return; }
        const filtered = modules.map(m => ({
            moduleName: m.moduleName,
            apis: m.apis.filter(a =>
                a.path.toLowerCase().includes(kw)
                || (a.summary && a.summary.toLowerCase().includes(kw)))
        })).filter(m => m.apis.length);
        renderTree(filtered);
        apiTree.querySelectorAll('.module-node > .tree-item.module .toggle').forEach(t => {
            const ul = t.closest('.module-node').querySelector(':scope > .tree-children');
            if (ul) { ul.classList.add('open'); t.textContent = '▼'; }
        });
    });

    loadModules();
})();