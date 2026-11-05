(function() {
    const customUrlInput         = document.getElementById('customApiUrl');
    const customMethodSelect     = document.getElementById('customApiMethod');
    const customHeadersTextarea  = document.getElementById('customApiHeaders'); // 可能不存在
    const sendCustomBtn          = document.getElementById('sendCustomRequestBtn');
    const customResponseDiv      = document.getElementById('customResponseArea');

    const bodyFormatSelectEl     = document.getElementById('bodyFormatSelect');
    const bodyContainer          = document.getElementById('customBodyContainer');

    // 动态创建 bodyFormatSelect（如果 HTML 没有）
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

    // 重新获取（可能刚被动态创建）
    const bodyFormatSelectElFinal = document.getElementById('bodyFormatSelect');
    const bodyEditorDiv = document.getElementById('bodyEditor');

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;'
        }[m]));
    }

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

        // ★ 核心修复：customHeadersTextarea 可能不存在
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

    function init() {
        if (bodyFormatSelectElFinal) {
            bodyFormatSelectElFinal.addEventListener('change', switchBodyEditor);
            switchBodyEditor();
        }
        if (sendCustomBtn) sendCustomBtn.addEventListener('click', sendCustomRequest);

        // 默认示例（全部 null 保护）
        if (customUrlInput && customUrlInput.value === 'https://jsonplaceholder.typicode.com/posts') {
            if (bodyFormatSelectElFinal && bodyFormatSelectElFinal.value === 'json') {
                const editor = document.getElementById('jsonEditor');
                if (editor && editor.value === '') {
                    editor.value = JSON.stringify({ title: 'foo', body: 'bar', userId: 1 }, null, 2);
                }
            }
            if (customHeadersTextarea && customHeadersTextarea.value === '') {
                customHeadersTextarea.value = '{\n  "Content-Type": "application/json"\n}';
            }
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();