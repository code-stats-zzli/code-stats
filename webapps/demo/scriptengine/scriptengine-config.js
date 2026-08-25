(function() {
    var languageSelect = document.getElementById('languageSelect');
    var executionMode = document.getElementById('executionMode');
    var javaOptions = document.getElementById('javaOptions');
    var classNameInput = document.getElementById('className');
    var methodNameInput = document.getElementById('methodName');
    var paramsInput = document.getElementById('paramsInput');
    var fileInput = document.getElementById('fileInput');
    var uploadBtn = document.getElementById('uploadBtn');
    var exampleBtn = document.getElementById('exampleBtn');
    var clearBtn = document.getElementById('clearBtn');
    var formatJsonBtn = document.getElementById('formatJsonBtn');

    function sendState(extra) {
        var state = {
            language: languageSelect.value,
            mode: executionMode.value,
            className: classNameInput.value,
            methodName: methodNameInput.value,
            params: parseParams(),
            ...extra
        };
        window.parent.postMessage({ type: 'configState', data: state }, '*');
    }

    function parseParams() {
        try {
            var raw = paramsInput.value.trim();
            if (!raw) return {};
            var parsed = JSON.parse(raw);
            if (parsed.scriptParam) return parsed.scriptParam;
            return parsed;
        } catch (e) {
            return {};
        }
    }

    var examples = {
        python: '# Python 示例\nimport sys, json\nparams = json.loads(sys.argv[1])\nprint("Hello, CodeStats!")\nprint("参数:", params)\nif \'a\' in params and \'b\' in params:\n    print(f"a + b = {params[\'a\'] + params[\'b\']}")',
        java: 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, CodeStats!");\n        for (String arg : args) System.out.println("arg: " + arg);\n    }\n}',
        c: '#include <stdio.h>\nint main(int argc, char *argv[]) {\n    printf("Hello, CodeStats!\\n");\n    for (int i=0;i<argc;i++) printf("arg[%d]=%s\\n", i, argv[i]);\n    return 0;\n}',
        cpp: '#include <iostream>\nusing namespace std;\nint main(int argc, char *argv[]) {\n    cout << "Hello, CodeStats!" << endl;\n    for (int i=0;i<argc;i++) cout << "arg[" << i << "]=" << argv[i] << endl;\n    return 0;\n}',
        rest: 'https://httpbin.org/get'
    };

    function loadExample() {
        var lang = languageSelect.value;
        var content = examples[lang] || '';
        window.parent.postMessage({
            type: 'setEditorContent',
            content: content
        }, '*');

        if (lang === 'rest') {
            paramsInput.value = JSON.stringify({ method: 'GET' }, null, 2);
        } else {
            paramsInput.value = JSON.stringify({ scriptParam: { name: 'hello', version: '1.0' } }, null, 2);
        }
        sendState();
    }

    languageSelect.addEventListener('change', function() {
        javaOptions.style.display = this.value === 'java' ? 'flex' : 'none';
        loadExample();
    });

    executionMode.addEventListener('change', sendState);
    classNameInput.addEventListener('input', sendState);
    methodNameInput.addEventListener('input', sendState);
    paramsInput.addEventListener('input', sendState);

    formatJsonBtn.addEventListener('click', function() {
        try {
            var parsed = JSON.parse(paramsInput.value);
            paramsInput.value = JSON.stringify(parsed, null, 2);
            sendState();
        } catch (e) {
            alert('JSON 格式错误，无法格式化');
        }
    });

    exampleBtn.addEventListener('click', loadExample);

    clearBtn.addEventListener('click', function() {
        paramsInput.value = '';
        window.parent.postMessage({ type: 'setEditorContent', content: '' }, '*');
        sendState();
    });

    uploadBtn.addEventListener('click', function() {
        var file = fileInput.files[0];
        if (!file) {
            alert('请先选择文件');
            return;
        }
        var reader = new FileReader();
        reader.onload = function(e) {
            var content = e.target.result;
            window.parent.postMessage({
                type: 'uploadFileContent',
                content: content
            }, '*');
        };
        reader.readAsText(file);
    });

    window.addEventListener('message', function(e) {
        if (e.data && e.data.type === 'ping') {
            sendState();
        }
        if (e.data && e.data.type === 'requestExample') {
            loadExample();
        }
    });

    setTimeout(function() {
        languageSelect.dispatchEvent(new Event('change'));
        // loadExample 会在 change 事件中被调用，无需再次调用
    }, 100);
})();