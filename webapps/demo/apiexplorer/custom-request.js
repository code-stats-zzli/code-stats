(function() {
    // DOM 元素
    const customUrlInput = document.getElementById('customApiUrl');
    const customMethodSelect = document.getElementById('customApiMethod');
    const customHeadersTextarea = document.getElementById('customApiHeaders');
    const sendCustomBtn = document.getElementById('sendCustomRequestBtn');
    const customResponseDiv = document.getElementById('customResponseArea');

    // 新增元素：参数格式选择器、Body 容器
    const bodyFormatSelect = document.getElementById('bodyFormatSelect');
    const bodyContainer = document.getElementById('customBodyContainer');
    // 如果原 HTML 中没有这些元素，我们动态创建
    if (!bodyFormatSelect) {
        const methodRow = customMethodSelect.parentElement;
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
    if (!bodyContainer) {
        const headersGroup = customHeadersTextarea.parentElement;
        const newContainer = document.createElement('div');
        newContainer.id = 'customBodyContainer';
        newContainer.className = 'form-group';
        newContainer.innerHTML = `<label>📦 请求 Body</label><div id="bodyEditor"></div>`;
        headersGroup.insertAdjacentElement('afterend', newContainer);
    }

    const bodyFormatSelectEl = document.getElementById('bodyFormatSelect');
    const bodyEditorDiv = document.getElementById('bodyEditor');

    // 存储当前活跃的编辑器 DOM 元素
    let currentEditor = null;

    // 辅助函数：转义 HTML
    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    // 获取当前 Body 内容（根据格式转换为字符串）
    function getBodyContent() {
        const format = bodyFormatSelectEl.value;
        if (format === 'json') {
            const textarea = document.getElementById('jsonEditor');
            return textarea ? textarea.value : '';
        } else if (format === 'form') {
            const pairs = [];
            const inputs = document.querySelectorAll('.form-key-value');
            inputs.forEach(input => {
                const key = input.querySelector('.form-key')?.value;
                const val = input.querySelector('.form-value')?.value;
                if (key && key.trim()) {
                    pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(val || '')}`);
                }
            });
            return pairs.join('&');
        } else {
            const textarea = document.getElementById('textEditor');
            return textarea ? textarea.value : '';
        }
    }

    // 根据格式切换编辑器 UI
    function switchBodyEditor() {
        const format = bodyFormatSelectEl.value;
        if (!bodyEditorDiv) return;

        if (format === 'json') {
            bodyEditorDiv.innerHTML = `
                <div style="display: flex; gap: 8px; margin-bottom: 8px;">
                    <button id="beautifyJsonBtn" class="btn-secondary" style="padding: 4px 12px;">🎨 格式化</button>
                    <button id="exampleJsonBtn" class="btn-secondary" style="padding: 4px 12px;">📋 示例 JSON</button>
                </div>
                <textarea id="jsonEditor" rows="8" style="width:100%; font-family:monospace;" placeholder='{"key": "value"}'></textarea>
            `;
            const textarea = document.getElementById('jsonEditor');
            if (textarea && !textarea.value) {
                textarea.value = '{\n  "example": "value"\n}';
            }
            document.getElementById('beautifyJsonBtn')?.addEventListener('click', () => {
                const editor = document.getElementById('jsonEditor');
                if (editor) {
                    try {
                        const parsed = JSON.parse(editor.value);
                        editor.value = JSON.stringify(parsed, null, 2);
                    } catch (e) {
                        alert('JSON 格式错误，无法格式化');
                    }
                }
            });
            document.getElementById('exampleJsonBtn')?.addEventListener('click', () => {
                const editor = document.getElementById('jsonEditor');
                if (editor) {
                    editor.value = JSON.stringify({ id: 1, name: "test", status: "active" }, null, 2);
                }
            });
        } else if (format === 'form') {
            bodyEditorDiv.innerHTML = `
                <div id="formFieldsContainer" style="margin-bottom: 8px;">
                    <div class="form-key-value" style="display: flex; gap: 8px; margin-bottom: 6px;">
                        <input type="text" class="form-key" placeholder="参数名" style="flex:1;">
                        <input type="text" class="form-value" placeholder="值" style="flex:1;">
                        <button class="remove-field-btn" style="background: none; border: none; cursor:pointer;">🗑️</button>
                    </div>
                </div>
                <button id="addFormFieldBtn" class="btn-secondary" style="padding: 4px 12px;">+ 添加字段</button>
            `;
            // 绑定添加字段事件
            document.getElementById('addFormFieldBtn')?.addEventListener('click', () => {
                const container = document.getElementById('formFieldsContainer');
                const newDiv = document.createElement('div');
                newDiv.className = 'form-key-value';
                newDiv.style.display = 'flex';
                newDiv.style.gap = '8px';
                newDiv.style.marginBottom = '6px';
                newDiv.innerHTML = `
                    <input type="text" class="form-key" placeholder="参数名" style="flex:1;">
                    <input type="text" class="form-value" placeholder="值" style="flex:1;">
                    <button class="remove-field-btn" style="background: none; border: none; cursor:pointer;">🗑️</button>
                `;
                container.appendChild(newDiv);
                newDiv.querySelector('.remove-field-btn').addEventListener('click', () => newDiv.remove());
            });
            // 为已有的删除按钮绑定事件
            document.querySelectorAll('.remove-field-btn').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.target.closest('.form-key-value')?.remove();
                });
            });
        } else {
            bodyEditorDiv.innerHTML = `
                <textarea id="textEditor" rows="8" style="width:100%; font-family:monospace;" placeholder="纯文本内容..."></textarea>
            `;
        }
    }

    // 发送自定义请求
    async function sendCustomRequest() {
        const url = customUrlInput.value.trim();
        if (!url) {
            alert('❌ 请输入请求地址');
            return;
        }

        const method = customMethodSelect.value;
        let headers = {};
        const headersRaw = customHeadersTextarea.value.trim();
        if (headersRaw) {
            try {
                headers = JSON.parse(headersRaw);
                if (typeof headers !== 'object' || Array.isArray(headers)) throw new Error();
            } catch (e) {
                alert('Headers 格式错误，请输入有效的 JSON 对象');
                return;
            }
        }

        const format = bodyFormatSelectEl.value;
        let body = getBodyContent();
        let contentType = '';
        if (format === 'json') {
            contentType = 'application/json';
            if (body) {
                try {
                    JSON.parse(body);
                } catch (e) {
                    alert('JSON 格式错误，请检查');
                    return;
                }
            }
        } else if (format === 'form') {
            contentType = 'application/x-www-form-urlencoded';
        } else {
            contentType = 'text/plain';
        }
        if (body && !headers['Content-Type'] && !headers['content-type']) {
            headers['Content-Type'] = contentType;
        }

        customResponseDiv.style.display = 'block';
        customResponseDiv.innerHTML = '<div class="loading">⏳ 发送请求中...</div>';

        try {
            const options = {
                method: method,
                headers: headers,
            };
            if (body && (method === 'POST' || method === 'PUT' || method === 'PATCH' || method === 'DELETE')) {
                options.body = body;
            }

            const response = await fetch(url, options);
            const responseText = await response.text();
            let formatted = responseText;
            try {
                const json = JSON.parse(responseText);
                formatted = JSON.stringify(json, null, 2);
            } catch (e) {}
            customResponseDiv.innerHTML = `
                <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
                    <strong style="color: ${response.ok ? '#10b981' : '#ef4444'}">📡 状态码: ${response.status} ${response.statusText}</strong>
                    <span style="font-size: 12px; background: #1e293b; padding: 2px 8px; border-radius: 20px;">${response.headers.get('content-type') || 'unknown'}</span>
                </div>
                <pre style="margin:0; white-space:pre-wrap; font-family: monospace;">${escapeHtml(formatted)}</pre>
            `;
        } catch (err) {
            customResponseDiv.innerHTML = `<div style="color:#ef4444;">❌ 网络或请求错误: ${err.message}</div>`;
        }
    }

    // 初始化：为已有的元素绑定事件，并创建格式选择器等
    function init() {
        if (bodyFormatSelectEl) {
            bodyFormatSelectEl.addEventListener('change', switchBodyEditor);
            switchBodyEditor(); // 初始渲染
        }
        if (sendCustomBtn) {
            sendCustomBtn.addEventListener('click', sendCustomRequest);
        }
        // 设置默认示例
        if (customUrlInput && customUrlInput.value === 'https://jsonplaceholder.typicode.com/posts') {
            if (bodyFormatSelectEl && bodyFormatSelectEl.value === 'json') {
                const editor = document.getElementById('jsonEditor');
                if (editor && editor.value === '') {
                    editor.value = JSON.stringify({ title: 'foo', body: 'bar', userId: 1 }, null, 2);
                }
            }
            if (customHeadersTextarea.value === '') {
                customHeadersTextarea.value = '{\n  "Content-Type": "application/json"\n}';
            }
        }
    }

    init();
})();