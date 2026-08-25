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

    exampleBtn.addEventListener('click', function() {
        window.parent.postMessage({ type: 'requestExample' }, '*');
    });

    clearBtn.addEventListener('click', function() {
        editor.value = '';
        sendState();
        updateStats();
    });

    // 监听父页面消息
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

    // 通知父页面已就绪
    function notifyReady() {
        window.parent.postMessage({ type: 'editorReady' }, '*');
        // 主动请求内容（以防父页面未及时发送）
        setTimeout(function() {
            window.parent.postMessage({ type: 'requestEditorContent' }, '*');
        }, 100);
    }

    if (document.readyState === 'complete') {
        notifyReady();
    } else {
        window.addEventListener('load', notifyReady);
    }

    editor.placeholder = '等待加载示例...';
})();