/**
 * 代码导入模块 —— 内嵌在「代码分析」Tab 下（mode = "import"）
 * 依赖：
 *   - global.js          （window.apiUrl）
 *   - codeStats.js       （switchMode 会调用 window.ImportModule.onShow）
 *   - codeStats.css      （已追加 .import-* 样式）
 * 后端：
 *   - /import/api/formats   GET
 *   - /import/api/parse     POST  { content, format }
 *   - /import/api/import    POST  { content, format, targetRoot, conflict, selectedPaths }
 */
(function () {
    const API = window.apiUrl('/import/api');
    const FILES_API = window.apiUrl('/files/api');

    // ==================== 状态 ====================
    let parsedFiles = [];
    let parsedPack = null;
    let parsedFormat = null;
    let selectedPaths = new Set();
    let currentPreviewPath = null;
    let collapsedDirs = new Set();
    let inited = false;

    // ==================== DOM 引用 ====================
    let d = {};
    function $(id) { return document.getElementById(id); }

    function bindDom() {
        d = {
            workspace:        $('importWorkspace'),
            dirTree:          $('importDirTree'),
            refreshTree:      $('importRefreshTreeBtn'),
            dirTip:           $('importDirTip'),
            mdInput:          $('importMdInput'),
            formatSel:        $('importFormatSelect'),
            parseBtn:         $('importParseBtn'),
            pasteBtn:         $('importPasteBtn'),
            uploadBtn:        $('importUploadBtn'),
            fileInput:        $('importFileInput'),
            clearBtn:         $('importClearBtn'),
            // ★ 新增
            copyFormatBtn:    $('importCopyFormatBtn'),
            downloadFormatBtn:$('importDownloadFormatBtn'),
            parseStatus:      $('importParseStatus'),
            fileTree:         $('importFileTree'),
            treeSummary:      $('importTreeSummary'),
            expandAllBtn:     $('importExpandAllBtn'),
            collapseAllBtn:   $('importCollapseAllBtn'),
            selectAllBtn:     $('importSelectAllBtn'),
            selectNoneBtn:    $('importSelectNoneBtn'),
            previewTitle:     $('importPreviewTitle'),
            previewMeta:      $('importPreviewMeta'),
            previewContent:   $('importPreviewContent'),
            copyFileBtn:      $('importCopyFileBtn'),
            targetRoot:       $('importTargetRoot'),
            conflictSel:      $('importConflictSelect'),
            execBtn:          $('importExecBtn'),
            result:           $('importResult')
        };
    }

    // ==================== 工具 ====================
    function esc(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[m]));
    }
    function humanSize(b) {
        if (b == null) return '';
        if (b < 1024) return b + ' B';
        if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
        return (b / 1024 / 1024).toFixed(2) + ' MB';
    }
    function normalizeSlashes(p) { return (p || '').replace(/\\/g, '/'); }
    function setStatus(msg, color) {
        d.parseStatus.textContent = msg || '';
        d.parseStatus.style.color = color || 'var(--text-muted)';
    }

    // ==================== ★ 新增：解析格式说明（Markdown） ====================
    /**
     * 生成完整解析格式说明（Markdown）。
     * 结构与合并文件末尾的「AI 提示 · 代码导入解析格式」保持一致，
     * 便于 AI 与人工双向理解。
     */
    function getFormatSpecMarkdown() {
        return [
            '# CodeStats 代码导入解析格式说明',
            '',
            '> 本文件由 CodeStats「代码分析 → 代码导入」自动生成，用于说明导入文件支持的三种格式。',
            '> 如需从 AI 生成的回答中恢复为独立源代码文件，请让 AI 使用以下任一格式输出。',
            '',
            '## 一、Merge File 格式（合并文件）',
            '',
            'CodeStats 代码分析生成的合并文件本身即为此格式：',
            '',
            '```text',
            '// ======== <文件路径> ========',
            '<文件原始内容>',
            '// ======== <文件路径> ========',
            '<文件原始内容>',
            '```',
            '',
            '**规则**：',
            '- 分隔头必须独占一行，行首严格为 `// ======== `（8 个等号 + 空格）',
            '- `<文件路径>` 建议使用相对路径（相对目标根目录）',
            '- 分隔头之后紧跟文件原始内容，原样保留，不转义、不缩进',
            '- 支持 .java / .js / .ts / .py / .go / .c / .cpp / .h / .md / .xml / .json / .yml / .yaml / .properties / .sql / .html / .css',
            '',
            '**示例**：',
            '',
            '```text',
            '// ======== src/main/java/com/example/Foo.java ========',
            'package com.example;',
            'public class Foo {',
            '    public static void main(String[] args) {',
            '        System.out.println("Hello");',
            '    }',
            '}',
            '',
            '// ======== src/main/resources/config.yaml ========',
            'name: demo',
            'version: 1.0',
            '```',
            '',
            '---',
            '',
            '## 二、File Block 格式（Markdown 代码块）',
            '',
            'AI 在 Markdown 回答中常用的多文件输出格式：',
            '',
            '````text',
            '```file:src/main/java/com/example/Foo.java',
            'package com.example;',
            'public class Foo { ... }',
            '```',
            '',
            '```file:src/main/resources/config.yaml',
            'name: demo',
            'version: 1.0',
            '```',
            '````',
            '',
            '**规则**：',
            '- 每个代码块以 ` ```file:<文件路径> ` 开始',
            '- 以 ` ``` ` 结束',
            '- 路径同样建议使用相对路径',
            '',
            '---',
            '',
            '## 三、Omni Pack 格式（扩展格式）',
            '',
            '在 File Block 格式基础上增加 JSON 元数据块，可指定目标根目录与冲突策略：',
            '',
            '````text',
            '```omni-pack',
            '{',
            '  "name": "demo-pack",',
            '  "version": "1.0",',
            '  "targetRoot": "D:/import",',
            '  "conflict": "OVERWRITE"',
            '}',
            '```',
            '',
            '```file:src/main/java/com/example/Foo.java',
            'package com.example;',
            'public class Foo { ... }',
            '```',
            '````',
            '',
            '**元数据字段**：',
            '',
            '| 字段 | 说明 | 可选值 |',
            '|------|------|--------|',
            '| name | 包名 | 字符串 |',
            '| version | 版本 | 字符串 |',
            '| description | 描述 | 字符串 |',
            '| targetRoot | 目标根目录 | 绝对路径 |',
            '| conflict | 冲突处理 | OVERWRITE / SKIP / RENAME |',
            '| encoding | 编码 | UTF-8 |',
            '',
            '---',
            '',
            '## 四、目标根目录与冲突处理',
            '',
            '- 所有导入文件写入「目标根目录」',
            '- 相对路径 → `目标根目录/相对路径`',
            '- 绝对路径 → 仅当在白名单内才允许（安全校验）',
            '',
            '| 策略 | 说明 |',
            '|------|------|',
            '| OVERWRITE | 直接覆盖（默认） |',
            '| SKIP | 已存在则跳过 |',
            '| RENAME | 已存在则重命名为 `xxx_1.ext` |',
            '',
            '---',
            '',
            '## 五、给 AI 的提示模板（可直接复制使用）',
            '',
            '> 请将以下内容作为「代码导入」的输入。要求：',
            '> 1. 使用 File Block 格式输出多文件（```` ```file:路径 ````）',
            '> 2. 路径使用相对路径，以 `src/` 开头',
            '> 3. 每个文件独立成块，块外不要添加解释文字',
            '> 4. 示例：',
            '>    ```` ```file:src/main/java/com/example/Demo.java ````',
            '>    `package com.example; ...`',
            '>    ```` ``` ````',
            '',
            '---',
            '',
            '*本说明由 CodeStats 自动生成 · 可在「代码分析 → 代码导入」中使用*',
            ''
        ].join('\n');
    }

    // ==================== ★ 新增：复制格式说明 ====================
    async function copyFormatSpec() {
        const md = getFormatSpecMarkdown();
        // 优先使用 Clipboard API
        if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
            try {
                await navigator.clipboard.writeText(md);
                setStatus('✅ 格式说明已复制到剪贴板（' + md.length + ' 字符）', '#15803d');
                return;
            } catch (e) {
                console.warn('[ImportModule] Clipboard API 复制失败，降级到 execCommand', e);
            }
        }
        // 降级方案
        try {
            const ta = document.createElement('textarea');
            ta.value = md;
            ta.style.position = 'fixed';
            ta.style.left = '-9999px';
            ta.style.top = '0';
            document.body.appendChild(ta);
            ta.focus();
            ta.select();
            const ok = document.execCommand('copy');
            document.body.removeChild(ta);
            if (ok) {
                setStatus('✅ 格式说明已复制到剪贴板（' + md.length + ' 字符）', '#15803d');
            } else {
                setStatus('❌ 复制失败：浏览器不支持自动复制，请手动选择内容', '#dc2626');
            }
        } catch (e) {
            setStatus('❌ 复制失败：' + e.message, '#dc2626');
        }
    }

    // ==================== ★ 新增：下载格式说明 ====================
    function downloadFormatSpec() {
        const md = getFormatSpecMarkdown();
        const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'CodeStats-代码导入解析格式说明.md';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        setStatus('✅ 格式说明已下载（' + md.length + ' 字符）', '#15803d');
    }

    // ==================== 加载可用格式 ====================
    async function loadFormats() {
        try {
            const resp = await fetch(API + '/formats');
            const data = await resp.json();
            if (!data.success) return;
            const sel = d.formatSel;
            sel.innerHTML = '<option value="auto">自动识别</option>';
            (data.formats || []).forEach(f => {
                const opt = document.createElement('option');
                opt.value = f.name;
                opt.textContent = f.displayName || f.name;
                sel.appendChild(opt);
            });
        } catch (e) {
            console.warn('[ImportModule] 加载格式列表失败', e);
        }
    }

    // ==================== 目录树（用于选择目标根） ====================
    function createDirNode(node, level, onDirClick) {
        const li = document.createElement('li');
        li.className = 'tree-node';
        const div = document.createElement('div');
        div.className = 'tree-item';
        div.style.paddingLeft = (level * 16 + 8) + 'px';
        div.dataset.path = node.path || '';

        const isDir = node.type === 'directory';
        const hasChildren = node.hasChildren === true;

        const toggle = document.createElement('span');
        toggle.className = 'toggle';
        toggle.textContent = (isDir && hasChildren) ? '▶' : ' ';
        if (!(isDir && hasChildren)) toggle.style.opacity = '0';

        const icon = document.createElement('span');
        icon.className = 'icon';
        icon.textContent = isDir ? '📁' : '📄';

        const name = document.createElement('span');
        name.className = 'name';
        name.textContent = node.name;

        div.appendChild(toggle);
        div.appendChild(icon);
        div.appendChild(name);
        li.appendChild(div);

        let childrenUl = null;
        if (isDir && hasChildren) {
            childrenUl = document.createElement('ul');
            childrenUl.className = 'tree-children';
            li.appendChild(childrenUl);
        }

        async function expand() {
            if (!isDir || !hasChildren || !childrenUl) return;
            const isOpen = childrenUl.classList.contains('open');
            if (!isOpen) {
                if (childrenUl.children.length === 0) {
                    try {
                        const url = `${FILES_API}/tree?rootType=local&path=${encodeURIComponent(node.path)}&withChildren=true`;
                        const res = await fetch(url);
                        const data = await res.json();
                        (data.children || []).forEach(c =>
                            childrenUl.appendChild(createDirNode(c, level + 1, onDirClick)));
                    } catch (e) { /* ignore */ }
                }
                childrenUl.classList.add('open');
                toggle.textContent = '▼';
            } else {
                childrenUl.classList.remove('open');
                toggle.textContent = '▶';
            }
        }

        toggle.addEventListener('click', (e) => { e.stopPropagation(); expand(); });
        div.addEventListener('click', (e) => {
            e.stopPropagation();
            if (isDir) {
                d.dirTree.querySelectorAll('.tree-item.selected').forEach(el => el.classList.remove('selected'));
                div.classList.add('selected');
                if (onDirClick) onDirClick(node);
            }
        });
        return li;
    }

    async function loadDirTree() {
        if (!d.dirTree) return;
        d.dirTree.innerHTML = '<div class="loading">加载中...</div>';
        try {
            const rootRes = await fetch(`${FILES_API}/root?rootType=local`);
            const rootInfo = await rootRes.json();
            const absolute = normalizeSlashes(rootInfo.absolutePath || '');

            const res = await fetch(`${FILES_API}/tree?rootType=local&path=&withChildren=true`);
            if (!res.ok) throw new Error('获取目录树失败');
            const rootNode = await res.json();

            d.dirTree.innerHTML = '';
            const onDirClick = (node) => {
                const rel = node.path ? '/' + node.path : '';
                d.targetRoot.value = absolute + rel;
                d.dirTip.textContent = '已选择：' + d.targetRoot.value;
            };
            d.dirTree.appendChild(createDirNode(rootNode, 0, onDirClick));

            const rootToggle = d.dirTree.querySelector('.tree-item .toggle');
            if (rootToggle && rootToggle.textContent === '▶') rootToggle.click();
        } catch (e) {
            d.dirTree.innerHTML = `<div class="error-tip">❌ ${esc(e.message)}</div>`;
        }
    }

    // ==================== 解析 ====================
    async function doParse() {
        const text = d.mdInput.value;
        if (!text.trim()) { setStatus('⚠️ 内容为空', '#dc2626'); return; }
        d.parseBtn.disabled = true;
        setStatus('⏳ 解析中...', '#0066ff');
        try {
            const resp = await fetch(API + '/parse', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                body: JSON.stringify({
                    content: text,
                    format: d.formatSel.value
                })
            });
            const data = await resp.json();
            if (!data.success) { setStatus('❌ ' + (data.error || '解析失败'), '#dc2626'); return; }

            parsedFiles = (data.files || []).filter(f => f.error == null);
            parsedPack = data.pack || null;
            parsedFormat = data.format || null;
            selectedPaths = new Set(parsedFiles.map(f => f.path));
            currentPreviewPath = null;
            collapsedDirs.clear();
            renderTree();
            renderPreviewEmpty();

            let msg = `✅ 解析成功 [${data.formatDisplayName || data.format}]：${data.valid} 个有效文件 / 共 ${data.total} 项，合计 ${humanSize(data.totalBytes)}`;
            if (data.warnings && data.warnings.length) msg += `（${data.warnings.length} 条警告）`;
            setStatus(msg, '#15803d');
            d.execBtn.disabled = parsedFiles.length === 0;

            if (parsedPack) {
                if (parsedPack.conflict && ['SKIP', 'OVERWRITE', 'RENAME'].includes(parsedPack.conflict.toUpperCase())) {
                    d.conflictSel.value = parsedPack.conflict.toUpperCase();
                }
                if (parsedPack.targetRoot && !d.targetRoot.value) {
                    d.targetRoot.value = parsedPack.targetRoot;
                }
            }
        } catch (e) {
            setStatus('❌ 请求异常：' + e.message, '#dc2626');
        } finally {
            d.parseBtn.disabled = false;
        }
    }

    // ==================== 文件树渲染 ====================
    function buildTree(files) {
        const root = { name: '', type: 'dir', children: {}, files: [] };
        files.forEach(f => {
            const parts = f.path.split('/');
            let node = root;
            for (let i = 0; i < parts.length - 1; i++) {
                const dir = parts[i];
                if (!node.children[dir]) {
                    node.children[dir] = { name: dir, type: 'dir', children: {}, files: [] };
                }
                node = node.children[dir];
            }
            node.files.push(f);
        });
        return root;
    }

    function renderTree() {
        if (parsedFiles.length === 0) {
            d.fileTree.innerHTML = '<div class="empty-tip">没有可预览的文件</div>';
            d.treeSummary.textContent = '0 个文件';
            return;
        }
        const tree = buildTree(parsedFiles);
        d.fileTree.innerHTML = '';
        Object.keys(tree.children).sort().forEach(k =>
            d.fileTree.appendChild(renderDir(tree.children[k], k, 0, k)));
        tree.files.forEach(f => d.fileTree.appendChild(renderFile(f, 0)));
        updateSummary();
    }

    function renderDir(node, dirName, depth, fullPath) {
        const wrap = document.createElement('div');
        const isCollapsed = collapsedDirs.has(fullPath);

        const line = document.createElement('div');
        line.className = 'tn-dir';
        line.innerHTML = `
            <span class="tn-toggle">${isCollapsed ? '▶' : '▼'}</span>
            <span class="tn-icon">📁</span>
            <span class="tn-name">${esc(dirName)}</span>`;

        const children = document.createElement('div');
        children.className = 'tn-children' + (isCollapsed ? '' : ' open');

        Object.keys(node.children).sort().forEach(k => {
            const childFull = fullPath + '/' + k;
            children.appendChild(renderDir(node.children[k], k, depth + 1, childFull));
        });
        node.files.forEach(f => children.appendChild(renderFile(f, depth + 1)));

        line.addEventListener('click', () => {
            if (collapsedDirs.has(fullPath)) collapsedDirs.delete(fullPath);
            else collapsedDirs.add(fullPath);
            renderTree();
        });
        wrap.appendChild(line);
        wrap.appendChild(children);
        return wrap;
    }

    function renderFile(f, depth) {
        const div = document.createElement('div');
        div.className = 'tn-file' + (currentPreviewPath === f.path ? ' active' : '');

        const cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.className = 'tn-check';
        cb.checked = selectedPaths.has(f.path);
        cb.addEventListener('click', e => e.stopPropagation());
        cb.addEventListener('change', () => {
            if (cb.checked) selectedPaths.add(f.path);
            else selectedPaths.delete(f.path);
            updateSummary();
        });

        const name = f.path.split('/').pop();
        div.appendChild(cb);
        div.insertAdjacentHTML('beforeend', `
            <span class="tn-icon">📄</span>
            <span class="tn-name" title="${esc(f.path)}">${esc(name)}</span>
            <span class="tn-meta">${humanSize(f.size)}</span>`);
        div.addEventListener('click', () => showPreview(f));
        return div;
    }

    function updateSummary() {
        d.treeSummary.textContent = `${selectedPaths.size} / ${parsedFiles.length} 个文件已选`;
    }

    // ==================== 预览 ====================
    function showPreview(f) {
        currentPreviewPath = f.path;
        d.previewTitle.textContent = '📄 ' + f.path;
        d.previewMeta.textContent = `${f.language || 'text'} · ${f.lineCount} 行 · ${humanSize(f.size)}`;
        d.previewContent.textContent = f.content;
        renderTree();
    }
    function renderPreviewEmpty() {
        d.previewTitle.textContent = '📄 文件预览';
        d.previewMeta.textContent = '';
        d.previewContent.innerHTML = '<div class="empty-tip">从左侧选择文件预览</div>';
    }

    // ==================== 导入 ====================
    async function doImport() {
        const targetRoot = d.targetRoot.value.trim();
        if (!targetRoot) { alert('请填写/选择目标根目录'); return; }
        if (selectedPaths.size === 0) { alert('请至少勾选一个文件'); return; }
        const conflict = d.conflictSel.value;

        if (!confirm(`确认将 ${selectedPaths.size} 个文件写入：\n${targetRoot}\n\n冲突处理：${conflict}？`)) return;

        d.execBtn.disabled = true;
        d.execBtn.textContent = '⏳ 导入中...';
        try {
            const resp = await fetch(API + '/import', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                body: JSON.stringify({
                    content: d.mdInput.value,
                    format: d.formatSel.value,
                    targetRoot: targetRoot,
                    conflict: conflict,
                    selectedPaths: Array.from(selectedPaths)
                })
            });
            const data = await resp.json();
            if (!data.success) { alert('导入失败：' + (data.error || '未知错误')); return; }
            renderImportResult(data.result);
        } catch (e) {
            alert('请求异常：' + e.message);
        } finally {
            d.execBtn.disabled = false;
            d.execBtn.textContent = '🚀 执行导入';
        }
    }

    function renderImportResult(r) {
        d.result.style.display = 'block';
        let html = `
            <div class="res-line">
                <strong>目标：</strong>${esc(r.targetRoot)}<br>
                <strong>总计：</strong>${r.total}，
                <span class="res-ok">成功 ${r.success}</span>，
                <span class="res-skip">跳过 ${r.skipped}</span>，
                <span class="res-err">失败 ${r.failed}</span>
            </div>
            <hr style="margin:8px 0;border-color:rgba(0,102,255,0.15);">`;
        (r.items || []).forEach(it => {
            const cls = it.status === 'SUCCESS' ? 'res-ok'
                : it.status === 'SKIPPED' ? 'res-skip' : 'res-err';
            html += `<div class="res-line ${cls}">
                [${it.status}] ${esc(it.path)}
                <br>&nbsp;&nbsp;${esc(it.message || '')}
            </div>`;
        });
        d.result.innerHTML = html;
    }

    // ==================== 事件绑定 ====================
    function bindEvents() {
        d.parseBtn.addEventListener('click', doParse);

        d.clearBtn.addEventListener('click', () => {
            d.mdInput.value = '';
            parsedFiles = [];
            selectedPaths.clear();
            currentPreviewPath = null;
            collapsedDirs.clear();
            d.result.style.display = 'none';
            renderTree();
            renderPreviewEmpty();
            setStatus('');
        });

        d.pasteBtn.addEventListener('click', async () => {
            try {
                const text = await navigator.clipboard.readText();
                if (text) { d.mdInput.value = text; setStatus('✅ 已从剪贴板粘贴'); }
            } catch (e) { alert('读取剪贴板失败：' + e.message); }
        });

        d.uploadBtn.addEventListener('click', () => d.fileInput.click());
        d.fileInput.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = (ev) => {
                d.mdInput.value = ev.target.result;
                setStatus('✅ 已加载：' + file.name);
            };
            reader.readAsText(file, 'UTF-8');
        });

        d.expandAllBtn.addEventListener('click', () => {
            collapsedDirs.clear();
            renderTree();
        });

        d.collapseAllBtn.addEventListener('click', () => {
            const collect = (node, full) => {
                if (!node) return;
                collapsedDirs.add(full);
                Object.keys(node.children).forEach(k => collect(node.children[k], full + '/' + k));
            };
            const tree = buildTree(parsedFiles);
            Object.keys(tree.children).forEach(k => collect(tree.children[k], k));
            renderTree();
        });

        d.selectAllBtn.addEventListener('click', () => {
            parsedFiles.forEach(f => selectedPaths.add(f.path));
            renderTree();
        });
        d.selectNoneBtn.addEventListener('click', () => {
            selectedPaths.clear();
            renderTree();
        });

        d.copyFileBtn.addEventListener('click', async () => {
            if (!currentPreviewPath) return;
            const f = parsedFiles.find(x => x.path === currentPreviewPath);
            if (!f) return;
            try {
                await navigator.clipboard.writeText(f.content);
                d.copyFileBtn.textContent = '✅ 已复制';
                setTimeout(() => d.copyFileBtn.textContent = '📋 复制', 1500);
            } catch (e) { alert('复制失败：' + e.message); }
        });

        // ★ 新增：复制 / 下载解析格式说明
        if (d.copyFormatBtn) d.copyFormatBtn.addEventListener('click', copyFormatSpec);
        if (d.downloadFormatBtn) d.downloadFormatBtn.addEventListener('click', downloadFormatSpec);

        d.execBtn.addEventListener('click', doImport);
        d.refreshTree.addEventListener('click', loadDirTree);
    }

    // ==================== 对外入口 ====================
    function init() {
        if (inited) return;
        inited = true;
        bindDom();
        bindEvents();
        loadFormats();
        loadDirTree();
    }

    function onShow() {
        if (!inited) init();
        if (d.dirTree && d.dirTree.querySelector('.loading')) {
            loadDirTree();
        }
    }

    window.ImportModule = { init, onShow };
})();