(function() {
    var tabBtns = document.querySelectorAll('.sidebar-tabs .tab-btn');
    var iframe = document.getElementById('contentFrame');
    var executeBtn = document.getElementById('executeBtn');
    var resultArea = document.getElementById('resultArea');
    var copyBtn = document.getElementById('copyResultBtn');

    var currentTab = 'config';
    var configState = { language: 'java', mode: 'source', params: {} }; // 默认 java
    var editorContent = '';
    var isExecuting = false;
    var isEditorLoaded = false; // 标记编辑器是否已加载

    var examples = {
        python: '# Python 示例\nimport sys, json\nparams = json.loads(sys.argv[1])\nprint("Hello, CodeStats!")\nprint("参数:", params)\nif \'a\' in params and \'b\' in params:\n    print(f"a + b = {params[\'a\'] + params[\'b\']}")',
        java: 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, CodeStats!");\n        for (String arg : args) System.out.println("arg: " + arg);\n    }\n}',
        c: '#include <stdio.h>\nint main(int argc, char *argv[]) {\n    printf("Hello, CodeStats!\\n");\n    for (int i=0;i<argc;i++) printf("arg[%d]=%s\\n", i, argv[i]);\n    return 0;\n}',
        cpp: '#include <iostream>\nusing namespace std;\nint main(int argc, char *argv[]) {\n    cout << "Hello, CodeStats!" << endl;\n    for (int i=0;i<argc;i++) cout << "arg[" << i << "]=" << argv[i] << endl;\n    return 0;\n}',
        rest: 'https://httpbin.org/get'
    };

    function getExample(lang) { return examples[lang] || examples.java; }

    function sendToFrame(data) {
        try {
            iframe.contentWindow.postMessage(data, '*');
        } catch (e) {
            console.warn('sendToFrame error', e);
        }
    }

    // 切换标签
    function switchTab(tab) {
        if (currentTab === tab) return;
        currentTab = tab;
        tabBtns.forEach(function(btn) {
            btn.classList.toggle('active', btn.dataset.tab === tab);
        });
        var src = tab === 'config' ? 'scriptengine-config.html' : 'scriptengine-editor.html';
        iframe.src = src;
        // 当加载完成后，会触发 load 事件
    }

    // iframe 加载完成
    iframe.addEventListener('load', function() {
        if (currentTab === 'editor') {
            isEditorLoaded = true;
            // 如果编辑器内容为空，则发送默认示例；否则发送当前内容（保留用户修改）
            if (!editorContent.trim()) {
                var defaultContent = getExample(configState.language);
                editorContent = defaultContent;
                sendToFrame({ type: 'setEditorContent', content: defaultContent });
            } else {
                sendToFrame({ type: 'setEditorContent', content: editorContent });
            }
        } else {
            // 配置页加载完成，无需操作
        }
    });

    // 接收子页面消息
    window.addEventListener('message', function(e) {
        var data = e.data;
        if (!data || typeof data !== 'object') return;

        if (data.type === 'configState') {
            var oldLang = configState.language;
            configState = data.data || {};
            // 如果语言变化且编辑器已加载，且编辑器内容为空或是当前语言的示例（可简单判断内容是否等于旧语言的示例），则更新
            // 更稳妥：如果编辑器内容等于旧语言的示例，则更新为新语言示例
            if (configState.language !== oldLang && isEditorLoaded) {
                var currentEditorContent = editorContent;
                var oldExample = getExample(oldLang);
                var newExample = getExample(configState.language);
                // 如果编辑器内容等于旧示例，则更新为新示例
                if (currentEditorContent === oldExample || !currentEditorContent.trim()) {
                    editorContent = newExample;
                    sendToFrame({ type: 'setEditorContent', content: newExample });
                }
                // 否则不覆盖用户修改
            }
        } else if (data.type === 'editorState') {
            editorContent = data.data.content || '';
        } else if (data.type === 'uploadFileContent') {
            editorContent = data.content || '';
            sendToFrame({ type: 'setEditorContent', content: data.content || '' });
            doExecute();
        } else if (data.type === 'executeRequest') {
            doExecute();
        } else if (data.type === 'requestExample') {
            // 编辑器请求示例（点击示例按钮）
            var example = getExample(configState.language);
            editorContent = example;
            sendToFrame({ type: 'setEditorContent', content: example });
        } else if (data.type === 'editorReady') {
            isEditorLoaded = true;
            // 如果编辑器内容为空，发送默认示例；否则保留
            if (!editorContent.trim()) {
                var def = getExample(configState.language);
                editorContent = def;
                sendToFrame({ type: 'setEditorContent', content: def });
            } else {
                sendToFrame({ type: 'setEditorContent', content: editorContent });
            }
        } else if (data.type === 'requestEditorContent') {
            // 编辑器主动请求内容，发送当前内容
            if (editorContent.trim()) {
                sendToFrame({ type: 'setEditorContent', content: editorContent });
            } else {
                var def = getExample(configState.language);
                editorContent = def;
                sendToFrame({ type: 'setEditorContent', content: def });
            }
        }
    });

    // 执行脚本（同上）
    async function doExecute() {
        if (isExecuting) return;
        var script = editorContent || '';
        if (!script.trim()) {
            resultArea.innerHTML = '<div class="error-output">❌ 请先在「源码编辑」页签中输入或上传脚本</div>';
            return;
        }
        isExecuting = true;
        resultArea.innerHTML = '<div class="loading">⏳ 执行中...</div>';
        var payload = {
            scriptContent: script,
            language: configState.language || 'java',
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

    // 复制结果等...
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

    tabBtns.forEach(function(btn) {
        btn.addEventListener('click', function() {
            var tab = this.dataset.tab;
            if (tab) switchTab(tab);
        });
    });

    // 初始默认显示配置页，编辑器未加载
})();