(function() {
    let modules = [];
    let currentApi = null;
    let allApis = [];

    const apiTree = document.getElementById('apiTree');
    const apiDetailDiv = document.getElementById('apiDetail');
    const searchInput = document.getElementById('apiSearch');

    // 自定义 API 面板元素
    const toggleCustomBtn = document.getElementById('toggleCustomBtn');
    const customApiBody = document.getElementById('customApiBody');
    const sendCustomRequestBtn = document.getElementById('sendCustomRequestBtn');

    async function loadModules() {
        try {
            const resp = await fetch(window.apiUrl('/apiexplorer/api/modules'));
            if (!resp.ok) throw new Error('加载失败');
            modules = await resp.json();
            allApis = [];
            modules.forEach(module => {
                module.apis.forEach(api => {
                    api.moduleName = module.moduleName;
                    allApis.push(api);
                });
            });
            renderTree(modules);
        } catch (err) {
            apiTree.innerHTML = `<li class="loading" style="color:red;">❌ ${err.message}</li>`;
        }
    }

    function renderTree(modulesData) {
        if (!modulesData.length) {
            apiTree.innerHTML = '<li class="loading">暂无接口</li>';
            return;
        }

        const treeHtml = modulesData.map(module => {
            const moduleId = 'module-' + encodeURIComponent(module.moduleName);
            const apiItems = module.apis.map(api => `
                <li class="tree-node api-node" data-id="${api.id}" data-path="${api.path}" data-method="${api.method}">
                    <div class="tree-item api">
                        <span class="toggle-placeholder" style="width:20px;"></span>
                        <span class="icon">📄</span>
                        <span class="api-method-badge ${api.method.toLowerCase()}">${api.method}</span>
                        <span class="name">${escapeHtml(api.summary || api.path)}</span>
                    </div>
                </li>
            `).join('');

            return `
                <li class="tree-node module-node" data-module="${escapeHtml(module.moduleName)}">
                    <div class="tree-item module">
                        <span class="toggle toggle-collapsed">▶</span>
                        <span class="icon">📁</span>
                        <span class="name">${escapeHtml(module.moduleName)}</span>
                        <span class="badge">${module.apis.length}</span>
                    </div>
                    <ul class="tree-children">${apiItems}</ul>
                </li>
            `;
        }).join('');

        apiTree.innerHTML = treeHtml;

        // 绑定模块展开/折叠事件
        document.querySelectorAll('.module-node > .tree-item.module .toggle').forEach(toggle => {
            toggle.addEventListener('click', (e) => {
                e.stopPropagation();
                const moduleNode = toggle.closest('.module-node');
                const childrenUl = moduleNode.querySelector(':scope > .tree-children');
                if (childrenUl) {
                    const isOpen = childrenUl.classList.contains('open');
                    if (isOpen) {
                        childrenUl.classList.remove('open');
                        toggle.textContent = '▶';
                    } else {
                        childrenUl.classList.add('open');
                        toggle.textContent = '▼';
                    }
                }
            });
        });

        // 绑定API点击事件
        document.querySelectorAll('.api-node').forEach(node => {
            node.addEventListener('click', (e) => {
                e.stopPropagation();
                const id = node.dataset.id;
                selectApi(id);
            });
        });

        // 默认展开第一个模块
        const firstModule = document.querySelector('.module-node');
        if (firstModule) {
            const toggle = firstModule.querySelector('.toggle');
            const childrenUl = firstModule.querySelector(':scope > .tree-children');
            if (childrenUl) {
                childrenUl.classList.add('open');
                if (toggle) toggle.textContent = '▼';
            }
            // 选中第一个API
            const firstApi = firstModule.querySelector('.api-node');
            if (firstApi) {
                selectApi(firstApi.dataset.id);
            }
        }
    }

    function selectApi(id) {
        const api = allApis.find(a => a.id === id);
        if (!api) return;
        currentApi = api;

        // 高亮
        document.querySelectorAll('.api-node').forEach(node => {
            node.classList.toggle('active', node.dataset.id === id);
        });

        renderApiDetail(api);
    }

    function renderApiDetail(api) {
        let paramsHtml = '';
        if (api.parameters && api.parameters.length) {
            paramsHtml = `
                <div class="params-section">
                    <h4>📌 请求参数</h4>
                    <table class="params-table">
                        <thead><tr><th>参数名</th><th>位置</th><th>类型</th><th>必填</th><th>值</th></tr></thead>
                        <tbody>
                            ${api.parameters.map(p => `
                                <tr>
                                    <td>${escapeHtml(p.name)}</td>
                                    <td>${p.in}</td>
                                    <td>
                                        <select class="param-type-select" data-name="${p.name}" data-in="${p.in}">
                                            <option value="string">string</option>
                                            <option value="number">number</option>
                                            <option value="boolean">boolean</option>
                                            <option value="integer">integer</option>
                                        </select>
                                    </td>
                                    <td>${p.required ? '是' : '否'}</td>
                                    <td><input type="text" class="param-input" data-name="${p.name}" data-in="${p.in}" placeholder="输入值"></td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            `;
        }

        let bodyHtml = '';
        if (api.requestBody) {
            bodyHtml = `
                <div class="request-body">
                    <h4>📦 请求 Body (JSON)</h4>
                    <textarea id="requestBody" placeholder='{"key": "value"}'></textarea>
                </div>
            `;
        }

        let responseHtml = '';
        if (api.responseContentType) {
            responseHtml = `
                <div class="response-section">
                    <h4>📤 响应格式</h4>
                    <div class="response-content-type">${escapeHtml(api.responseContentType)}</div>
                    ${api.responseExample ? `<div class="response-example">${escapeHtml(api.responseExample)}</div>` : ''}
                </div>
            `;
        }

        apiDetailDiv.innerHTML = `
            <div class="detail-header">
                <div class="detail-path">${api.method} ${escapeHtml(api.path)}</div>
                <div class="detail-desc">${escapeHtml(api.description || api.summary || '')}</div>
            </div>
            ${paramsHtml}
            ${bodyHtml}
            ${responseHtml}
            <button id="sendRequestBtn" class="send-btn">🚀 发送请求</button>
            <div id="responseArea" class="response-area" style="display:none;"></div>
        `;

        document.getElementById('sendRequestBtn').addEventListener('click', () => sendRequest(api));
    }

    async function sendRequest(api) {
        let url = window.apiUrl(api.path);
        const queryParams = [];
        const pathParams = {};

        if (api.parameters) {
            const inputs = document.querySelectorAll('.param-input');
            inputs.forEach(input => {
                const name = input.dataset.name;
                const value = input.value.trim();
                const location = input.dataset.in;
                if (value) {
                    if (location === 'query') {
                        queryParams.push(`${encodeURIComponent(name)}=${encodeURIComponent(value)}`);
                    } else if (location === 'path') {
                        pathParams[name] = value;
                    }
                }
            });
        }

        let finalPath = api.path;
        for (const [key, val] of Object.entries(pathParams)) {
            finalPath = finalPath.replace(`{${key}}`, encodeURIComponent(val));
        }
        url = window.apiUrl(finalPath);

        if (queryParams.length) {
            url += '?' + queryParams.join('&');
        }

        let body = null;
        let contentType = 'application/json';
        if (api.requestBody) {
            const bodyText = document.getElementById('requestBody')?.value;
            if (bodyText && bodyText.trim()) {
                try {
                    JSON.parse(bodyText);
                    body = bodyText;
                } catch (e) {
                    body = bodyText;
                    contentType = 'text/plain';
                }
            }
        }

        const responseDiv = document.getElementById('responseArea');
        responseDiv.style.display = 'block';
        responseDiv.innerHTML = '<div class="loading">⏳ 请求中...</div>';

        try {
            const options = {
                method: api.method,
                headers: { 'Content-Type': contentType }
            };
            if (body) {
                options.body = body;
            }
            const resp = await fetch(url, options);
            const responseText = await resp.text();
            let formatted = responseText;
            try {
                const json = JSON.parse(responseText);
                formatted = JSON.stringify(json, null, 2);
            } catch (e) {}
            responseDiv.innerHTML = `
                <div style="margin-bottom: 8px; color: ${resp.ok ? '#10b981' : '#ef4444'}">
                    <strong>状态码: ${resp.status} ${resp.statusText}</strong>
                </div>
                <pre style="margin:0; white-space:pre-wrap;">${escapeHtml(formatted)}</pre>
            `;
        } catch (err) {
            responseDiv.innerHTML = `<div style="color:#ef4444;">❌ 请求失败: ${err.message}</div>`;
        }
    }

    // 自定义请求功能
    let customPanelVisible = false;
    toggleCustomBtn.addEventListener('click', () => {
        customPanelVisible = !customPanelVisible;
        customApiBody.style.display = customPanelVisible ? 'block' : 'none';
        toggleCustomBtn.textContent = customPanelVisible ? '收起' : '展开';
    });

    sendCustomRequestBtn.addEventListener('click', async () => {
        const url = document.getElementById('customApiUrl').value.trim();
        if (!url) {
            alert('请输入请求地址');
            return;
        }
        const method = document.getElementById('customApiMethod').value;
        let headers = {};
        const headersText = document.getElementById('customApiHeaders').value.trim();
        if (headersText) {
            try {
                headers = JSON.parse(headersText);
            } catch (e) {
                alert('Headers 格式错误，请输入有效的 JSON');
                return;
            }
        }
        let body = document.getElementById('customApiBodyText').value.trim();
        if (body && !headers['Content-Type']) {
            headers['Content-Type'] = 'application/json';
        }

        const responseDiv = document.getElementById('customResponseArea');
        responseDiv.style.display = 'block';
        responseDiv.innerHTML = '<div class="loading">⏳ 请求中...</div>';

        try {
            const options = {
                method: method,
                headers: headers
            };
            if (body) {
                options.body = body;
            }
            const resp = await fetch(url, options);
            const responseText = await resp.text();
            let formatted = responseText;
            try {
                const json = JSON.parse(responseText);
                formatted = JSON.stringify(json, null, 2);
            } catch (e) {}
            responseDiv.innerHTML = `
                <div style="margin-bottom: 8px; color: ${resp.ok ? '#10b981' : '#ef4444'}">
                    <strong>状态码: ${resp.status} ${resp.statusText}</strong>
                </div>
                <pre style="margin:0; white-space:pre-wrap;">${escapeHtml(formatted)}</pre>
            `;
        } catch (err) {
            responseDiv.innerHTML = `<div style="color:#ef4444;">❌ 请求失败: ${err.message}</div>`;
        }
    });

    // 搜索过滤（实时过滤树）
    searchInput.addEventListener('input', (e) => {
        const keyword = e.target.value.toLowerCase();
        if (!keyword) {
            renderTree(modules);
            return;
        }

        const filteredModules = [];
        modules.forEach(module => {
            const filteredApis = module.apis.filter(api =>
                api.path.toLowerCase().includes(keyword) ||
                (api.summary && api.summary.toLowerCase().includes(keyword))
            );
            if (filteredApis.length) {
                filteredModules.push({
                    moduleName: module.moduleName,
                    moduleDescription: module.moduleDescription,
                    apis: filteredApis
                });
            }
        });
        renderTree(filteredModules);

        // 展开所有模块以便查看搜索结果
        document.querySelectorAll('.module-node > .tree-item.module .toggle').forEach(toggle => {
            const childrenUl = toggle.closest('.module-node').querySelector(':scope > .tree-children');
            if (childrenUl && !childrenUl.classList.contains('open')) {
                childrenUl.classList.add('open');
                toggle.textContent = '▼';
            }
        });
    });

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    loadModules();
})();