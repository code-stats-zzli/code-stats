(function() {
    const API_BASE = window.apiUrl('/script/api');

    const languageSelect = document.getElementById('languageSelect');
    const executionMode = document.getElementById('executionMode');
    const javaOptions = document.getElementById('javaOptions');
    const classNameInput = document.getElementById('className');
    const methodNameInput = document.getElementById('methodName');
    const paramsInput = document.getElementById('paramsInput');
    const scriptEditor = document.getElementById('scriptEditor');
    const fileInput = document.getElementById('fileInput');
    const uploadBtn = document.getElementById('uploadBtn');
    const executeBtn = document.getElementById('executeBtn');
    const clearBtn = document.getElementById('clearBtn');
    const exampleBtn = document.getElementById('exampleBtn');
    const resultArea = document.getElementById('resultArea');

    // 切换语言显示 Java 特定选项
    languageSelect.addEventListener('change', function() {
        javaOptions.style.display = this.value === 'java' ? 'flex' : 'none';
        // 自动切换示例
        if (this.value === 'rest') {
            paramsInput.value = JSON.stringify({ method: 'GET' }, null, 2);
        } else {
            paramsInput.value = JSON.stringify({ scriptParam: { key: 'value' } }, null, 2);
        }
    });
    // 手动触发一次，初始化显示
    languageSelect.dispatchEvent(new Event('change'));

    // 示例脚本
    const examples = {
        python: `# Python 示例\n# 接收参数（通过 sys.argv[1] 获取 JSON）\nimport sys, json\n\nparams = json.loads(sys.argv[1])\nprint("Hello, CodeStats!")\nprint("参数:", params)\n# 如果有数字参数可以计算\nif 'a' in params and 'b' in params:\n    result = params['a'] + params['b']\n    print(f"a + b = {result}")`,
        java: `public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, CodeStats!");\n        // 参数可从 args 获取\n        for (String arg : args) {\n            System.out.println("arg: " + arg);\n        }\n    }\n}`,
        c: `#include <stdio.h>\nint main(int argc, char *argv[]) {\n    printf("Hello, CodeStats!\\n");\n    for (int i = 0; i < argc; i++) {\n        printf("arg[%d] = %s\\n", i, argv[i]);\n    }\n    return 0;\n}`,
        cpp: `#include <iostream>\nusing namespace std;\nint main(int argc, char *argv[]) {\n    cout << "Hello, CodeStats!" << endl;\n    for (int i = 0; i < argc; i++) {\n        cout << "arg[" << i << "] = " << argv[i] << endl;\n    }\n    return 0;\n}`,
        rest: `https://httpbin.org/get`
    };

    function setExample() {
        const lang = languageSelect.value;
        scriptEditor.value = examples[lang] || '';
        if (lang === 'rest') {
            paramsInput.value = JSON.stringify({ method: 'GET' }, null, 2);
        } else {
            paramsInput.value = JSON.stringify({ scriptParam: { key: 'value' } }, null, 2);
        }
    }

    exampleBtn.addEventListener('click', setExample);

    // 执行脚本（直接发送内容）
    async function executeScript(scriptContent, extraParams) {
        const payload = {
            scriptContent: scriptContent,
            language: languageSelect.value,
            executionMode: executionMode.value,
            params: extraParams || {}
        };

        // 如果是 Java 且为 jar 模式，传递类名/方法名
        if (languageSelect.value === 'java') {
            if (classNameInput.value) payload.className = classNameInput.value;
            if (methodNameInput.value) payload.methodName = methodNameInput.value;
        }

        // 合并用户参数 JSON
        try {
            const userParams = JSON.parse(paramsInput.value || '{}');
            // 如果用户有 scriptParam，则放到 params 中（覆盖）
            if (userParams.scriptParam) {
                payload.params = userParams.scriptParam;
            } else {
                Object.assign(payload.params, userParams);
            }
        } catch (e) {
            // 忽略 JSON 解析错误
        }

        resultArea.innerHTML = '<div class="loading">⏳ 执行中...</div>';
        try {
            const resp = await fetch(API_BASE + '/execute', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await resp.json();
            if (data.success) {
                resultArea.innerHTML = `<div class="success-output">✅ 执行成功\n\n${escapeHtml(data.output)}</div>`;
            } else {
                resultArea.innerHTML = `<div class="error-output">❌ 执行失败\n\n${escapeHtml(data.error)}</div>`;
            }
        } catch (err) {
            resultArea.innerHTML = `<div class="error-output">❌ 请求异常: ${escapeHtml(err.message)}</div>`;
        }
    }

    // 上传文件并执行
    async function uploadAndExecute() {
        const file = fileInput.files[0];
        if (!file) {
            alert('请先选择文件');
            return;
        }
        const formData = new FormData();
        formData.append('scriptFile', file);
        formData.append('language', languageSelect.value);
        formData.append('executionMode', executionMode.value);
        formData.append('params', paramsInput.value || '{}');

        resultArea.innerHTML = '<div class="loading">⏳ 上传并执行中...</div>';
        try {
            const resp = await fetch(API_BASE + '/upload', {
                method: 'POST',
                body: formData
            });
            const data = await resp.json();
            if (data.success) {
                resultArea.innerHTML = `<div class="success-output">✅ 执行成功\n\n${escapeHtml(data.output)}</div>`;
            } else {
                resultArea.innerHTML = `<div class="error-output">❌ 执行失败\n\n${escapeHtml(data.error)}</div>`;
            }
        } catch (err) {
            resultArea.innerHTML = `<div class="error-output">❌ 请求异常: ${escapeHtml(err.message)}</div>`;
        }
    }

    executeBtn.addEventListener('click', () => {
        const content = scriptEditor.value;
        if (!content.trim()) {
            alert('请输入脚本内容');
            return;
        }
        executeScript(content, {});
    });

    uploadBtn.addEventListener('click', uploadAndExecute);

    clearBtn.addEventListener('click', () => {
        scriptEditor.value = '';
        resultArea.innerHTML = '<div class="empty-tip">已清空，重新输入脚本</div>';
    });

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    // 初始化示例
    setExample();
})();