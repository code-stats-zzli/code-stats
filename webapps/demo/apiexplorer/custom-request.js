(function() {
    // 获取自定义请求面板的 DOM 元素
    const customUrlInput = document.getElementById('customApiUrl');
    const customMethodSelect = document.getElementById('customApiMethod');
    const customHeadersTextarea = document.getElementById('customApiHeaders');
    const customBodyTextarea = document.getElementById('customApiBodyText');
    const sendCustomBtn = document.getElementById('sendCustomRequestBtn');
    const customResponseDiv = document.getElementById('customResponseArea');

    // 辅助函数：HTML 转义
    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
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
                alert('Headers 格式错误，请输入有效的 JSON 对象（例如：{"Content-Type": "application/json"}）');
                return;
            }
        }

        let body = customBodyTextarea.value.trim();
        // 自动补充 Content-Type（如果用户未指定且 body 不为空）
        if (body && !headers['Content-Type'] && !headers['content-type']) {
            try {
                JSON.parse(body);
                headers['Content-Type'] = 'application/json';
            } catch (e) {
                headers['Content-Type'] = 'text/plain';
            }
        }

        customResponseDiv.style.display = 'block';
        customResponseDiv.innerHTML = '<div class="loading">⏳ 发送请求中...</div>';

        try {
            const options = {
                method: method,
                headers: headers,
            };
            // 仅当方法支持 body 且 body 非空时添加
            if (body && (method === 'POST' || method === 'PUT' || method === 'PATCH' || method === 'DELETE')) {
                options.body = body;
            }

            const response = await fetch(url, options);
            const responseText = await response.text();
            let formatted = responseText;
            try {
                const json = JSON.parse(responseText);
                formatted = JSON.stringify(json, null, 2);
            } catch (e) {
                // 保持原文
            }

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

    // 绑定发送按钮事件
    if (sendCustomBtn) {
        sendCustomBtn.addEventListener('click', sendCustomRequest);
    }

    // 提供友好的默认示例（若输入框为空则填充示例数据）
    if (customBodyTextarea && customBodyTextarea.value.trim() === '') {
        customBodyTextarea.placeholder = '支持 JSON / 文本，例如：\n{\n  "title": "foo",\n  "body": "bar"\n}';
    }
    if (customUrlInput && customUrlInput.value === 'https://jsonplaceholder.typicode.com/posts') {
        if (customBodyTextarea.value === '') {
            customBodyTextarea.value = JSON.stringify({
                title: 'API Explorer',
                body: '自定义请求测试',
                userId: 1
            }, null, 2);
        }
        if (customHeadersTextarea.value === '') {
            customHeadersTextarea.value = '{\n  "Content-Type": "application/json"\n}';
        }
    }
})();