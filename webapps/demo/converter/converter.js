(function () {
    'use strict';

    const API = window.apiUrl('/converter/api');
    const $ = id => document.getElementById(id);

    const sourceTypeSel = $('sourceType');
    const targetTypeSel = $('targetType');
    const fileInput = $('fileInput');
    const sourceText = $('sourceText');
    const textRow = $('textRow');
    const convertBtn = $('convertBtn');
    const toggleTextBtn = $('toggleTextBtn');
    const clearBtn = $('clearBtn');
    const asyncMode = $('asyncMode');
    const resultPanel = $('resultPanel');
    const resultInfo = $('resultInfo');
    const resultPreview = $('resultPreview');
    const downloadBtn = $('downloadBtn');
    const closeResultBtn = $('closeResultBtn');

    let textMode = false;
    let currentDownloadUrl = null;
    let pollTimer = null;

    const ALL_FORMATS = [
        'json', 'xml', 'csv', 'yaml', 'properties', 'xlsx',
        'png', 'jpg', 'bmp', 'gif', 'pdf', 'txt', 'html', 'md'
    ];

    async function loadSupported() {
        try {
            const resp = await fetch(API + '/supported', { cache: 'no-store' });
            const data = await resp.json();
            if (!data.success) throw new Error(data.error || '加载失败');
            const targetSet = new Set();
            const sourceSet = new Set();
            const converters = data.converters || [];
            for (let i = 0; i < converters.length; i++) {
                const c = converters[i];
                (c.targets || []).forEach(function (t) { targetSet.add(t); });
                (c.sources || []).forEach(function (s) { sourceSet.add(s); });
            }
            sourceTypeSel.innerHTML = '<option value="">自动识别（推荐）</option>';
            Array.from(sourceSet).sort().forEach(function (f) {
                const opt = document.createElement('option');
                opt.value = f;
                opt.textContent = f.toUpperCase();
                sourceTypeSel.appendChild(opt);
            });
            targetTypeSel.innerHTML = '';
            Array.from(targetSet).sort().forEach(function (f) {
                const opt = document.createElement('option');
                opt.value = f;
                opt.textContent = f.toUpperCase();
                targetTypeSel.appendChild(opt);
            });
            if (targetTypeSel.options.length) targetTypeSel.selectedIndex = 0;
        } catch (e) {
            console.error('加载支持列表失败', e);
            sourceTypeSel.innerHTML = '<option value="">自动识别</option>';
            targetTypeSel.innerHTML = ALL_FORMATS.map(function (f) {
                return '<option value="' + f + '">' + f.toUpperCase() + '</option>';
            }).join('');
        }
    }

    function toggleTextMode() {
        textMode = !textMode;
        textRow.style.display = textMode ? 'flex' : 'none';
        fileInput.parentElement.style.display = textMode ? 'none' : 'flex';
        toggleTextBtn.textContent = textMode ? '切换文件模式' : '切换文本模式';
    }

    async function doConvert() {
        const target = targetTypeSel.value;
        if (!target) { alert('请选择目标格式'); return; }
        const source = sourceTypeSel.value;
        convertBtn.disabled = true;
        convertBtn.textContent = '⏳ 转换中...';
        currentDownloadUrl = null;
        downloadBtn.style.display = 'none';
        try {
            if (textMode) {
                if (!sourceText.value.trim()) { alert('请输入文本内容'); return; }
                const payload = {
                    source: sourceText.value,
                    target: target,
                    sourceType: source || null
                };
                const resp = await fetch(API + '/text', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                    body: JSON.stringify(payload)
                });
                const data = await resp.json();
                if (!data.success) throw new Error(data.error || '转换失败');
                showTextResult(data);
            } else {
                const file = fileInput.files[0];
                if (!file) { alert('请选择文件'); return; }
                const fd = new FormData();
                fd.append('file', file);
                fd.append('target', target);
                if (source) fd.append('sourceType', source);
                if (asyncMode.checked) fd.append('async', 'true');
                const resp = await fetch(API + '/upload', { method: 'POST', body: fd });
                const data = await resp.json();
                if (!data.success) throw new Error(data.error || '转换失败');
                if (data.async && data.taskId) {
                    showAsyncPending(data.taskId);
                    startPolling(data.taskId);
                } else {
                    showFileResult(data);
                }
            }
        } catch (e) {
            alert('转换失败: ' + e.message);
            resultPanel.style.display = 'none';
        } finally {
            convertBtn.disabled = false;
            convertBtn.textContent = '🚀 开始转换';
        }
    }

    function showTextResult(data) {
        resultPanel.style.display = 'block';
        resultInfo.textContent = '目标格式：' + data.targetType
            + '  |  文本长度：' + ((data.text || '').length);
        resultPreview.textContent = data.text || '（空）';
    }

    function showFileResult(data) {
        resultPanel.style.display = 'block';
        resultInfo.textContent = '文件名：' + data.fileName
            + '  |  目标格式：' + data.targetType
            + '  |  大小：' + (data.size / 1024).toFixed(1) + ' KB';
        resultPreview.textContent = '转换成功，请点击下方下载按钮。';
        currentDownloadUrl = window.apiUrl(data.downloadUrl);
        downloadBtn.style.display = 'inline-block';
    }

    function showAsyncPending(taskId) {
        resultPanel.style.display = 'block';
        resultInfo.textContent = '异步任务已提交：' + taskId;
        resultPreview.textContent = '正在后台转换...';
    }

    function startPolling(taskId) {
        stopPolling();
        pollTimer = setInterval(async function () {
            try {
                const resp = await fetch(API + '/status?taskId=' + encodeURIComponent(taskId));
                const data = await resp.json();
                if (!data.success) throw new Error(data.error || '查询失败');
                if (data.state === 'RUNNING') {
                    resultPreview.textContent = '转换中... 已耗时 ' + (data.elapsedMs / 1000).toFixed(1) + 's';
                    return;
                }
                stopPolling();
                if (data.state === 'COMPLETED' && data.storedName) {
                    const r = data.result || {};
                    resultInfo.textContent = '文件名：' + (r.fileName || data.storedName)
                        + '  |  大小：' + ((r.size || 0) / 1024).toFixed(1) + ' KB';
                    resultPreview.textContent = '异步转换完成，请点击下载。';
                    currentDownloadUrl = window.apiUrl('/converter/api/download?name='
                        + encodeURIComponent(data.storedName));
                    downloadBtn.style.display = 'inline-block';
                } else {
                    resultInfo.textContent = '❌ 转换失败';
                    resultPreview.textContent = data.errorMessage || '未知错误';
                }
            } catch (e) {
                stopPolling();
                resultInfo.textContent = '❌ 查询失败: ' + e.message;
            }
        }, 1000);
    }

    function stopPolling() {
        if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    }

    function download() {
        if (currentDownloadUrl) window.open(currentDownloadUrl, '_blank');
    }

    function clearAll() {
        fileInput.value = '';
        sourceText.value = '';
        resultPanel.style.display = 'none';
        currentDownloadUrl = null;
        downloadBtn.style.display = 'none';
        stopPolling();
    }

    convertBtn.addEventListener('click', doConvert);
    toggleTextBtn.addEventListener('click', toggleTextMode);
    clearBtn.addEventListener('click', clearAll);
    downloadBtn.addEventListener('click', download);
    closeResultBtn.addEventListener('click', function () {
        resultPanel.style.display = 'none';
    });
    window.addEventListener('beforeunload', stopPolling);
    loadSupported();
})();

/* ==================== 左侧 Tab 切换 ==================== */
(function () {
    'use strict';
    const tabs = document.querySelectorAll('.conv-tab');
    const panels = document.querySelectorAll('.conv-panel');
    if (!tabs.length) return;
    function switchTab(tabId) {
        tabs.forEach(t => t.classList.toggle('active', t.dataset.tab === tabId));
        panels.forEach(p => p.classList.toggle('active', p.id === 'panel-' + tabId));
    }
    tabs.forEach(t => {
        t.addEventListener('click', () => {
            const id = t.dataset.tab;
            if (id) switchTab(id);
        });
    });
    const hash = window.location.hash.replace('#', '');
    if (hash && document.getElementById('panel-' + hash)) switchTab(hash);
})();