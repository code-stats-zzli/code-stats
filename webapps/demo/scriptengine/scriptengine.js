(function() {
    var tabBtns = document.querySelectorAll('.sidebar-tabs .tab-btn');
    var iframe = document.getElementById('contentFrame');
    var executeBtn = document.getElementById('executeBtn');
    var resultArea = document.getElementById('resultArea');
    var copyBtn = document.getElementById('copyResultBtn');

    var currentTab = 'config';
    var configState = { language: 'python', mode: 'source', params: {} };
    var editorContent = '';
    var isExecuting = false;

    // 语言示例映射（全部 UTF-8 文本，无需额外处理）
    var examples = {
        python: '# Python 示例\nimport sys, json\nparams = json.loads(sys.argv[1])\nprint("Hello, CodeStats!")\nprint("参数:", params)\nif \'a\' in params and \'b\' in params:\n    print(f"a + b = {params[\'a\'] + params[\'b\']}")',
        java: 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, CodeStats!");\n        for (String arg : args) System.out.println("arg: " + arg);\n    }\n}',
        c: '#include <stdio.h>\nint main(int argc, char *argv[]) {\n    printf("Hello, CodeStats!\\n");\n    for (int i=0;i<argc;i++) printf("arg[%d]=%s\\n", i, argv[i]);\n    return 0;\n}',
        cpp: '#include <iostream>\nusing namespace std;\nint main(int argc, char *argv[]) {\n    cout << "Hello, CodeStats!" << endl;\n    for (int i=0;i<argc;i++) cout << "arg[" << i << "]=" << argv[i] << endl;\n    return 0;\n}',
        rest: 'https://httpbin.org/get'
    };

    function getExample(lang) { return examples[lang] || examples.python; }

    function switchTab(tab) {
        if (currentTab === tab) return;
        currentTab = tab;
        tabBtns.forEach(function(btn) {
            btn.classList.toggle('active', btn.dataset.tab === tab);
        });
        var src = tab === 'config' ? 'scriptengine-config.html' : 'scriptengine-editor.html';
        iframe.src = src;
    }

    function sendToFrame(data) {
        try {
            iframe.contentWindow.postMessage(data, '*');
        } catch (e) {
            console.warn('发送消息失败', e);
        }
    }

    window.addEventListener('message', function(e) {
        var data = e.data;
        if (!data || typeof data !== 'object') return;

        if (data.type === 'configState') {
            configState = data.data || {};
            if (currentTab === 'editor' && iframe.contentWindow) {
                sendToFrame({ type: 'setEditorContent', content: getExample(configState.language) });
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
            sendToFrame({ type: 'setEditorContent', content: getExample(configState.language) });
        } else if (data.type === 'editorReady') {
            sendToFrame({ type: 'setEditorContent', content: getExample(configState.language) });
        } else if (data.type === 'requestEditorContent') {
            sendToFrame({ type: 'setEditorContent', content: getExample(configState.language) });
        }
    });

    iframe.addEventListener('load', function() {
        if (currentTab === 'editor') {
            sendToFrame({ type: 'setEditorContent', content: getExample(configState.language) });
        }
    });

    // ========== 核心修改：明确指定 charset=UTF-8 ==========
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
                headers: {
                    'Content-Type': 'application/json; charset=UTF-8'   // ★ 明确指定 UTF-8
                },
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

    tabBtns.forEach(function(btn) {
        btn.addEventListener('click', function() {
            var tab = this.dataset.tab;
            if (tab) switchTab(tab);
        });
    });
})();