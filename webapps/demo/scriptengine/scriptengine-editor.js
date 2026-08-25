(function() {
    var editor = document.getElementById('scriptEditor');
    var cursorInfo = document.getElementById('cursorInfo');
    var charCount = document.getElementById('charCount');
    var exampleBtn = document.getElementById('editorExampleBtn');
    var clearBtn = document.getElementById('editorClearBtn');

    function sendState() {
        window.parent.postMessage({
            type: 'editorState',
            data: { content: editor.value }
        }, '*');
    }

    function updateStats() {
        charCount.textContent = editor.value.length + ' 字符';
        var lines = editor.value.substr(0, editor.selectionStart).split('\n');
        cursorInfo.textContent = '行 ' + lines.length + ', 列 ' + (lines[lines.length - 1].length + 1);
    }

    editor.addEventListener('input', function() {
        sendState();
        updateStats();
    });
    editor.addEventListener('click keyup', updateStats);

    // 示例按钮：请求父级发送当前语言的示例
    exampleBtn.addEventListener('click', function() {
        window.parent.postMessage({ type: 'requestExample' }, '*');
    });

    clearBtn.addEventListener('click', function() {
        editor.value = '';
        sendState();
        updateStats();
    });

    window.addEventListener('message', function(e) {
        var data = e.data;
        if (data && data.type === 'setEditorContent') {
            editor.value = data.content || '';
            sendState();
            updateStats();
        }
        if (data && data.type === 'ping') {
            sendState();
        }
    });

    // 初始化：等待父级发送内容，暂不设置默认值
    // 但为防止白屏，可以显示一个占位提示
    editor.placeholder = '等待加载示例...';
})();