
(function() {
    let apis = [];
    let currentApi = null;

    const apiListDiv = document.getElementById('apiList');
    const apiDetailDiv = document.getElementById('apiDetail');
    const searchInput = document.getElementById('apiSearch');

    async function loadApis() {
        try {
            const resp = await fetch(window.apiUrl('/apiexplorer/api/apis'));
            if (!resp.ok) throw new Error('加载失败');
            apis = await resp.json();
            renderApiList(apis);
            if (apis.length > 0) selectApi(apis[0].id);
        } catch (err) {
            apiListDiv.innerHTML = `<div class="loading" style="color:red;">❌ ${err.message}</div>`;
        }
    }

    function renderApiList(apiArray) {
        if (!apiArray.length) {
            apiListDiv.innerHTML = '<div class="loading">暂无接口</div>';
            return;
        }
        apiListDiv.innerHTML = apiArray.map(api => `
            <div class="api-item" data-id="${api.id}">
                <div>
                    <span class="api-method post">${api.method}</span>
                    <span class="api-path">${escapeHtml(api.path)}</span>
                </div>
                <div class="api-summary">${escapeHtml(api.summary || api.id)}</div>
            </div>
        `).join('');

        document.querySelectorAll('.api-item').forEach(el => {
            el.addEventListener('click', () => {
                const id = el.dataset.id;
                selectApi(id);
            });
        });
    }

    function selectApi(id) {
        const api = apis.find(a => a.id === id);
        if (!api) return;
        currentApi = api;
        document.querySelectorAll('.api-item').forEach(el => {
            el.classList.toggle('active', el.dataset.id === id);
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
                                    <td>${p.type}</td>
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

        // 绑定发送事件
        document.getElementById('sendRequestBtn').addEventListener('click', () => sendRequest(api));
    }

    async function sendRequest(api) {
        let url = window.apiUrl(api.path);
        const queryParams = [];
        const pathParams = {};

        // 收集参数
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

        // 替换路径参数
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

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    // 搜索过滤
    searchInput.addEventListener('input', (e) => {
        const keyword = e.target.value.toLowerCase();
        const filtered = apis.filter(api =>
            api.path.toLowerCase().includes(keyword) ||
            (api.summary && api.summary.toLowerCase().includes(keyword))
        );
        renderApiList(filtered);
        if (filtered.length && currentApi && filtered.some(a => a.id === currentApi.id)) {
            // 保持当前选中
        } else if (filtered.length) {
            selectApi(filtered[0].id);
        }
    });

    loadApis();
})();