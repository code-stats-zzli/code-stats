(function() {
    var tabBtns = document.querySelectorAll('.sidebar-tabs .tab-btn');
    var iframe = document.getElementById('contentFrame');
    var executeBtn = document.getElementById('executeBtn');
    var resultArea = document.getElementById('resultArea');
    var copyBtn = document.getElementById('copyResultBtn');

    var configState = {};
    var editorState = { content: '' };
    var isExecuting = false;

    function switchTab(tab) {
        tabBtns.forEach(function(b) {
            b.classList.toggle('active', b.dataset.tab === tab);
        });
        var src = tab === 'config' ? 'scriptengine-config.html' : 'scriptengine-editor.html';
        var currentSrc = iframe.src.split('/').pop().split('?')[0];
        if (currentSrc !== src) {
            iframe.src = src;
        }
    }
    tabBtns.forEach(function(btn) {
        btn.addEventListener('click', function() {
            switchTab(btn.dataset.tab);
        });
    });

    window.addEventListener('message', function(e) {
        var data = e.data;
        if (!data || typeof data !== 'object') return;

        if (data.type === 'configState') {
            configState = data.data || {};
            if (configState._uploadExecute) {
                delete configState._uploadExecute;
                doExecute();
            }
        } else if (data.type === 'editorState') {
            editorState = data.data || { content: '' };
        } else if (data.type === 'uploadFileContent') {
            editorState.content = data.content || '';
            forwardToEditor({ type: 'setEditorContent', content: data.content || '' });
            doExecute();
        } else if (data.type === 'setEditorContent') {
            forwardToEditor(data);
        } else if (data.type === 'executeRequest') {
            doExecute();
        } else if (data.type === 'requestExample') {
            // 转发给配置子页面
            var configFrame = document.getElementById('contentFrame');
            if (configFrame && configFrame.src.indexOf('config') > -1) {
                configFrame.contentWindow.postMessage({ type: 'requestExample' }, '*');
            }
        }
    });

    function forwardToEditor(data) {
        try {
            var editorFrame = document.getElementById('contentFrame');
            if (editorFrame && editorFrame.src.indexOf('editor') > -1) {
                editorFrame.contentWindow.postMessage(data, '*');
            }
        } catch (e) {}
    }

    async function doExecute() {
        if (isExecuting) return;
        var scriptContent = editorState.content || '';
        if (!scriptContent.trim()) {
            resultArea.innerHTML = '<div class="error-output">❌ 请先在「源码编辑」页签中输入或上传脚本</div>';
            return;
        }

        isExecuting = true;
        resultArea.innerHTML = '<div class="loading">⏳ 执行中...</div>';

        var payload = {
            scriptContent: scriptContent,
            language: configState.language || 'python',
            executionMode: configState.mode || 'source',
            params: configState.params || {}
        };
        if (configState.language === 'java') {
            if (configState.className) payload.className = configState.className;
            if (configState.methodName) payload.methodName = configState.methodName;
        }

        try {
            var resp = await fetch(window.apiUrl('/script/api/execute'), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            var data = await resp.json();
            if (data.success) {
                resultArea.innerHTML = '<div class="success-output">✅ 执行成功\n\n' + escapeHtml(data.output) + '</div>';
            } else {
                resultArea.innerHTML = '<div class="error-output">❌ 执行失败\n\n' + escapeHtml(data.error) + '</div>';
            }
        } catch (err) {
            resultArea.innerHTML = '<div class="error-output">❌ 请求异常: ' + escapeHtml(err.message) + '</div>';
        } finally {
            isExecuting = false;
        }
    }

    copyBtn.addEventListener('click', function() {
        var text = resultArea.textContent || '';
        if (!text || text.includes('点击「执行脚本」')) {
            alert('没有可复制的内容');
            return;
        }
        navigator.clipboard.writeText(text).then(function() {
            alert('结果已复制到剪贴板');
        }).catch(function() {
            var range = document.createRange();
            range.selectNode(resultArea);
            window.getSelection().removeAllRanges();
            window.getSelection().addRange(range);
            document.execCommand('copy');
            alert('结果已复制到剪贴板');
        });
    });

    executeBtn.addEventListener('click', doExecute);

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, function(m) {
            if (m === '&') return '&amp;';
            if (m === '<') return '&lt;';
            if (m === '>') return '&gt;';
            return m;
        });
    }

    switchTab('config');

    iframe.addEventListener('load', function() {
        try {
            iframe.contentWindow.postMessage({ type: 'ping' }, '*');
        } catch (e) {}
    });

    window.reloadScriptEngine = function() {
        iframe.src = iframe.src;
    };
})();