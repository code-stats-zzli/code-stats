(function () {
    'use strict';

    const API = window.apiUrl ? window.apiUrl('/unzip/api') : '/unzip/api';
    const $ = id => document.getElementById(id);

    const fileInput        = $('unzipFileInput');
    const encodingSel      = $('unzipEncoding');
    const unzipBtn         = $('unzipBtn');
    const clearBtn         = $('unzipClearBtn');
    const progress         = $('unzipProgress');
    const progressFill     = document.querySelector('#unzipProgress .unzip-progress-fill');
    const progressText     = document.querySelector('#unzipProgress .unzip-progress-text');
    const resultPanel      = $('unzipResultPanel');
    const summaryEl        = $('unzipSummary');
    const treeEl           = $('unzipTree');
    const previewTitleEl   = $('unzipPreviewTitle');
    const previewMetaEl    = $('unzipPreviewMeta');
    const previewContentEl = $('unzipPreviewContent');
    const downloadZipBtn   = $('unzipDownloadZipBtn');
    const downloadMergedBtn= $('unzipDownloadMergedBtn');

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

    async function doUnzip() {
        const file = fileInput.files[0];
        if (!file) { alert('请选择压缩包'); return; }
        unzipBtn.disabled = true;
        unzipBtn.textContent = '⏳ 解压中...';
        resultPanel.style.display = 'none';
        currentTaskId = null;
        setProgress(5, '正在上传压缩包...');
        try {
            const fd = new FormData();
            fd.append('file', file);
            fd.append('encoding', encodingSel.value || 'UTF-8');
            setProgress(30, '正在解压...');
            const resp = await fetch(API + '/upload', { method: 'POST', body: fd });
            const data = await resp.json();
            if (!data.success) throw new Error(data.error || '解压失败');
            currentTaskId = data.taskId;
            setProgress(100, '解压完成');
            showMsg('✅ 解压完成：' + data.totalFiles + ' 个文件，' + humanSize(data.totalBytes), false);
            resultPanel.style.display = 'block';
            summaryEl.innerHTML =
                '<div><strong>原始压缩包：</strong>' + esc(data.originalFileName || file.name) + '</div>' +
                '<div><strong>压缩格式：</strong>' + esc(data.archiveType || '-') + '</div>' +
                '<div><strong>文件数量：</strong>' + data.totalFiles + ' 个（其中二进制 ' + (data.binaryFiles || 0) + '）</div>' +
                '<div><strong>解压后大小：</strong>' + humanSize(data.totalBytes) + '</div>' +
                '<div><strong>耗时：</strong>' + data.durationMs + ' ms</div>' +
                '<div style="color:#0066ff;margin-top:6px;">💡 提示：下载「合并文件（可导入）」后，在 <b>代码分析 → 代码导入</b> 中粘贴/上传，即可完整还原（含二进制）</div>';
            renderTree(data.entries || []);
            previewTitleEl.textContent = '📄 文件预览';
            previewMetaEl.textContent = '';
            previewContentEl.textContent = '从左侧选择文件预览';
        } catch (err) {
            setProgress(0, '解压失败');
            showMsg('❌ ' + err.message, true);
            console.error(err);
        } finally {
            unzipBtn.disabled = false;
            unzipBtn.textContent = '🚀 开始解压';
        }
    }

    function buildTree(entries) {
        const root = { name: '', type: 'dir', children: {}, files: [] };
        for (const e of entries) {
            if (e.directory) continue;
            const parts = e.path.split('/');
            let node = root;
            for (let i = 0; i < parts.length - 1; i++) {
                const seg = parts[i];
                if (!node.children[seg]) node.children[seg] = { name: seg, type: 'dir', children: {}, files: [] };
                node = node.children[seg];
            }
            node.files.push(e);
        }
        return root;
    }

    function renderTree(entries) {
        treeEl.innerHTML = '';
        const tree = buildTree(entries);
        renderNodeChildren(tree.children, treeEl, 0);
        if (treeEl.children.length === 0) {
            treeEl.innerHTML = '<div style="padding:20px;text-align:center;color:#94a3b8;">无文件</div>';
        }
    }

    function renderNodeChildren(childrenMap, parentEl, depth) {
        Object.keys(childrenMap).sort().forEach(d => {
            const node = childrenMap[d];
            const el = document.createElement('div');
            el.className = 'tree-node';
            el.style.paddingLeft = (depth * 16 + 8) + 'px';
            el.innerHTML = '<span class="icon">📁</span><span class="name">' + esc(node.name) + '</span>';
            parentEl.appendChild(el);
            const box = document.createElement('div');
            parentEl.appendChild(box);
            box.style.display = 'none';
            let open = false;
            el.addEventListener('click', (e) => {
                e.stopPropagation();
                open = !open;
                box.style.display = open ? 'block' : 'none';
                el.querySelector('.icon').textContent = open ? '📂' : '📁';
            });
            renderNodeChildren(node.children, box, depth + 1);
            node.files.sort((a, b) => a.path.localeCompare(b.path));
            for (const f of node.files) {
                const fileName = f.path.split('/').pop();
                const fe = document.createElement('div');
                fe.className = 'tree-node';
                fe.style.paddingLeft = ((depth + 1) * 16 + 8) + 'px';
                fe.innerHTML = '<span class="icon">' + (f.binary ? '🧱' : '📄') + '</span>' +
                    '<span class="name">' + esc(fileName) + '</span>' +
                    '<span class="size">' + humanSize(f.size) + '</span>';
                fe.dataset.path = f.path;
                fe.addEventListener('click', (e) => {
                    e.stopPropagation();
                    selectPreview(f.path);
                });
                box.appendChild(fe);
            }
        });
    }

    async function selectPreview(path) {
        if (!currentTaskId) return;
        treeEl.querySelectorAll('.tree-node').forEach(el => {
            el.classList.toggle('active', el.dataset.path === path);
        });
        previewTitleEl.textContent = '📄 ' + path;
        previewMetaEl.textContent = '';
        previewContentEl.textContent = '加载中...';
        try {
            const url = API + '/preview?taskId=' + encodeURIComponent(currentTaskId)
                + '&path=' + encodeURIComponent(path);
            const resp = await fetch(url);
            const data = await resp.json();
            if (!data.success) {
                previewContentEl.textContent = '❌ ' + (data.error || '预览失败');
                return;
            }
            previewMetaEl.textContent = (data.language || '') + ' · ' + humanSize(data.size);
            previewContentEl.textContent = data.content || '（空）';
        } catch (e) {
            previewContentEl.textContent = '❌ 请求失败: ' + e.message;
        }
    }

    function downloadZip() {
        if (!currentTaskId) return;
        window.open(API + '/download-zip?taskId=' + encodeURIComponent(currentTaskId), '_blank');
    }
    function downloadMerged() {
        if (!currentTaskId) return;
        window.open(API + '/download-merged?taskId=' + encodeURIComponent(currentTaskId), '_blank');
        showMsg('📄 合并文件已开始下载，可到「代码分析 → 代码导入」粘贴/上传完整还原', false);
    }
    function clearAll() {
        fileInput.value = '';
        encodingSel.value = 'UTF-8';
        resultPanel.style.display = 'none';
        progress.style.display = 'none';
        treeEl.innerHTML = '';
        previewTitleEl.textContent = '📄 文件预览';
        previewMetaEl.textContent = '';
        previewContentEl.textContent = '从左侧选择文件预览';
        currentTaskId = null;
    }

    if (unzipBtn) unzipBtn.addEventListener('click', doUnzip);
    if (clearBtn) clearBtn.addEventListener('click', clearAll);
    if (downloadZipBtn) downloadZipBtn.addEventListener('click', downloadZip);
    if (downloadMergedBtn) downloadMergedBtn.addEventListener('click', downloadMerged);
})();