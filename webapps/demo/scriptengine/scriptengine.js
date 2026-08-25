(function() {
    // DOM 元素
    var tabBtns = document.querySelectorAll('.sidebar-tabs .tab-btn');
    var iframe = document.getElementById('contentFrame');
    var executeBtn = document.getElementById('executeBtn');
    var resultArea = document.getElementById('resultArea');
    var copyBtn = document.getElementById('copyResultBtn');

    // 状态
    var currentTab = 'config';
    var configState = { language: 'python', mode: 'source', params: {} };
    var editorContent = '';
    var isExecuting = false;

    // 语言示例映射
    var examples = {
        python: '# Python 示例\nimport sys, json\nparams = json.loads(sys.argv[1])\nprint("Hello, CodeStats!")\nprint("参数:", params)\nif \'a\' in params and \'b\' in params:\n    print(f"a + b = {params[\'a\'] + params[\'b\']}")',
        java: 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, CodeStats!");\n        for (String arg : args) System.out.println("arg: " + arg);\n    }\n}',
        c: '#include <stdio.h>\nint main(int argc, char *argv[]) {\n    printf("Hello, CodeStats!\\n");\n    for (int i=0;i<argc;i++) printf("arg[%d]=%s\\n", i, argv[i]);\n    return 0;\n}',
        cpp: '#include <iostream>\nusing namespace std;\nint main(int argc, char *argv[]) {\n    cout << "Hello, CodeStats!" << endl;\n    for (int i=0;i<argc;i++) cout << "arg[" << i << "]=" << argv[i] << endl;\n    return 0;\n}',
        rest: 'https://httpbin.org/get'
    };

    function getExample(lang) { return examples[lang] || examples.python; }

    // ---------- 切换标签 ----------
    function switchTab(tab) {
        if (currentTab === tab) return;
        currentTab = tab;
        // 更新按钮激活状态
        tabBtns.forEach(function(btn) {
            btn.classList.toggle('active', btn.dataset.tab === tab);
        });
        // 切换 iframe src
        var src = tab === 'config' ? 'scriptengine-config.html' : 'scriptengine-editor.html';
        iframe.src = src;
        // 注意：iframe 加载完成后会触发 load 事件，在那里发送初始化消息
    }

    // ---------- 向 iframe 发送消息 ----------
    function sendToFrame(data) {
        try {
            iframe.contentWindow.postMessage(data, '*');
        } catch (e) {
            console.warn('发送消息失败', e);
        }
    }

    // ---------- 处理 from 子页面的消息 ----------
    window.addEventListener('message', function(e) {
        var data = e.data;
        if (!data || typeof data !== 'object') return;

        if (data.type === 'configState') {
            configState = data.data || {};
            // 如果当前是编辑器标签且已加载，则更新内容
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
            // 配置页面请求示例
            sendToFrame({ type: 'setEditorContent', content: getExample(configState.language) });
        } else if (data.type === 'editorReady') {
            // 编辑器就绪，发送当前语言的示例
            sendToFrame({ type: 'setEditorContent', content: getExample(configState.language) });
        } else if (data.type === 'requestEditorContent') {
            // 编辑器主动请求内容
            sendToFrame({ type: 'setEditorContent', content: getExample(configState.language) });
        }
    });

    // ---------- iframe 加载完成事件 ----------
    iframe.addEventListener('load', function() {
        // 如果当前是编辑器标签，且尚未发送内容，则发送
        if (currentTab === 'editor') {
            sendToFrame({ type: 'setEditorContent', content: getExample(configState.language) });
        } else {
            // 配置标签，不需要额外操作，配置页面自己会请求示例
        }
    });

    // ---------- 执行脚本 ----------
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

    // ---------- 复制结果 ----------
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

    // ---------- 执行按钮 ----------
    executeBtn.addEventListener('click', doExecute);

    // ---------- 辅助 ----------
    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, function(m) {
            if (m === '&') return '&amp;';
            if (m === '<') return '&lt;';
            if (m === '>') return '&gt;';
            return m;
        });
    }

    // ---------- 绑定标签点击 ----------
    tabBtns.forEach(function(btn) {
        btn.addEventListener('click', function() {
            var tab = this.dataset.tab;
            if (tab) switchTab(tab);
        });
    });

    // ---------- 初始启动 ----------
    // 默认显示配置页，无需额外操作
})();