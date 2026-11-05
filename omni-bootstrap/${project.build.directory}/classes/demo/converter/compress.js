(function () {
    'use strict';

    const API = window.apiUrl ? window.apiUrl('/compress/api') : '/compress/api';
    const $ = id => document.getElementById(id);

    const fileInput = $('compressFileInput');
    const targetSel = $('compressTarget');
    const pathMapInput = $('compressPathMap');
    const compressBtn = $('compressBtn');
    const clearBtn = $('compressClearBtn');
    const progress = $('compressProgress');
    const progressFill = document.querySelector('#compressProgress .unzip-progress-fill');
    const progressText = document.querySelector('#compressProgress .unzip-progress-text');
    const resultPanel = $('compressResultPanel');
    const summaryEl = $('compressSummary');
    const downloadArchiveBtn = $('compressDownloadArchiveBtn');
    const downloadMergedBtn = $('compressDownloadMergedBtn');

    let currentTaskId = null;

    function esc(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"]/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
        }[m]));
    }
    function humanSize(b) {
        if (b == null || b < 0) return '-';
        if (b < 1024) return b + ' B';
        if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
        if (b < 1024 * 1024 * 1024) return (b / 1024 / 1024).toFixed(2) + ' MB';
        return (b / 1024 / 1024 / 1024).toFixed(2) + ' GB';
    }
    function setProgress(pct, text) {
        progress.style.display = 'block';
        progressFill.style.width = pct + '%';
        progressText.textContent = text || '';
        if (pct >= 100) setTimeout(() => { progress.style.display = 'none'; }, 1200);
    }
    function showMsg(msg, isErr) {
        const t = document.createElement('div');
        t.style.cssText = 'position:fixed;bottom:20px;right:20px;padding:10px 18px;border-radius:40px;' +
            'color:#fff;z-index:9999;font-size:13px;background:' + (isErr ? '#dc2626' : '#22c55e');
        t.textContent = msg;
        document.body.appendChild(t);
        setTimeout(() => t.remove(), 3000);
    }

    function parsePathMap() {
        const map = {};
        const text = pathMapInput.value || '';
        for (const line of text.split('\n')) {
            const s = line.trim();
            if (!s || s.startsWith('#')) continue;
            const eq = s.indexOf('=');
            if (eq < 0) continue;
            const key = s.substring(0, eq).trim();
            const val = s.substring(eq + 1).trim();
            if (key && val) map[key] = val;
        }
        return map;
    }

    async function doCompress() {
        const files = Array.from(fileInput.files || []);
        if (!files.length) { alert('请选择文件'); return; }
        const target = targetSel.value;
        if (target === 'gz' && files.length > 1) {
            alert('GZ 格式仅支持单文件压缩，请改用 ZIP / TAR / TAR.GZ');
            return;
        }

        compressBtn.disabled = true;
        compressBtn.textContent = '⏳ 压缩中...';
        resultPanel.style.display = 'none';
        currentTaskId = null;
        setProgress(10, '正在上传文件...');

        try {
            const fd = new FormData();
            const pathMap = parsePathMap();
            files.forEach((f, idx) => {
                const fieldName = 'file_' + idx;
                const relPath = pathMap[f.name] || f.name;
                // 第三个参数作为 multipart 的 fileName，后端会用它作为条目相对路径
                fd.append(fieldName, f, relPath);
            });
            fd.append('target', target);
            fd.append('encoding', 'UTF-8');

            setProgress(40, '正在压缩并生成 merge-file...');
            const resp = await fetch(API + '/upload', { method: 'POST', body: fd });
            const data = await resp.json();
            if (!data.success) throw new Error(data.error || '压缩失败');

            currentTaskId = data.taskId;
            setProgress(100, '压缩完成');
            showMsg('✅ 压缩完成：' + data.fileCount + ' 个文件，压缩包 ' + humanSize(data.archiveBytes), false);

            resultPanel.style.display = 'block';
            const ratio = data.rawBytes > 0
                ? (100 - data.archiveBytes * 100 / data.rawBytes).toFixed(1) + '%'
                : '-';
            summaryEl.innerHTML =
                '<div><strong>压缩包：</strong>' + esc(data.archiveFileName) + '（' + esc(data.archiveType) + '）</div>' +
                '<div><strong>文件数量：</strong>' + data.fileCount + ' 个</div>' +
                '<div><strong>原始大小：</strong>' + humanSize(data.rawBytes) + '</div>' +
                '<div><strong>压缩后大小：</strong>' + humanSize(data.archiveBytes) + '</div>' +
                '<div><strong>压缩率：</strong>' + ratio + '</div>' +
                '<div><strong>耗时：</strong>' + data.durationMs + ' ms</div>' +
                (data.mergedFileCount != null
                    ? '<div><strong>合并文件：</strong>' + data.mergedFileCount + ' 个（文本 ' +
                    data.mergedTextFiles + ' / 二进制 ' + data.mergedBinaryFiles + '）</div>' : '') +
                '<div style="color:#0066ff;margin-top:6px;">💡 提示：下载「合并文件」后，到 <b>代码分析 → 代码导入</b> 粘贴/上传即可完整还原（含二进制 base64）</div>';
        } catch (err) {
            setProgress(0, '压缩失败');
            showMsg('❌ ' + err.message, true);
            console.error(err);
        } finally {
            compressBtn.disabled = false;
            compressBtn.textContent = '🚀 开始压缩';
        }
    }

    function downloadArchive() {
        if (!currentTaskId) return;
        window.open(API + '/download-archive?taskId=' + encodeURIComponent(currentTaskId), '_blank');
    }
    function downloadMerged() {
        if (!currentTaskId) return;
        window.open(API + '/download-merged?taskId=' + encodeURIComponent(currentTaskId), '_blank');
        showMsg('📄 合并文件已开始下载，可到「代码分析 → 代码导入」完整还原', false);
    }
    function clearAll() {
        fileInput.value = '';
        pathMapInput.value = '';
        targetSel.value = 'zip';
        resultPanel.style.display = 'none';
        progress.style.display = 'none';
        currentTaskId = null;
    }

    if (compressBtn) compressBtn.addEventListener('click', doCompress);
    if (clearBtn) clearBtn.addEventListener('click', clearAll);
    if (downloadArchiveBtn) downloadArchiveBtn.addEventListener('click', downloadArchive);
    if (downloadMergedBtn) downloadMergedBtn.addEventListener('click', downloadMerged);
})();