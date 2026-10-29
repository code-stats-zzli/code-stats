(function() {
    const API_CODE = window.apiUrl('/code/api');
    const API_FILES = window.apiUrl('/files/api');

    let dependencyData = null;
    let classInfoMap = null;
    let currentMergeFilePath = null;

    let localCurrentPath = '';
    let localCurrentEditPath = null;
    let localInited = false;
    let activePicker = null;

    // ==================== DOM ====================
    const modeList = document.getElementById('modeList');
    const serverWorkspace = document.getElementById('serverWorkspace');
    const localWorkspace = document.getElementById('localWorkspace');
    const spiderWorkspace = document.getElementById('spiderWorkspace');
    const qbWorkspace = document.getElementById('qbWorkspace');
    const systemcheckWorkspace = document.getElementById('systemcheckWorkspace');

    const serverDirTree = document.getElementById('serverDirTree');
    const serverRefreshTreeBtn = document.getElementById('serverRefreshTreeBtn');
    const serverDirTip = document.getElementById('serverDirTip');
    const rootDirInput = document.getElementById('rootDir');
    const extensionsInput = document.getElementById('extensions');
    const excludePathsInput = document.getElementById('excludePaths');
    const extraPathsInput = document.getElementById('extraPaths');
    const excludeCommentsCheck = document.getElementById('excludeComments');
    const threadCountInput = document.getElementById('threadCount');
    const sortBySelect = document.getElementById('sortBy');
    const mergeEnabledCheck = document.getElementById('mergeEnabled');
    const mergeRow = document.getElementById('mergeRow');
    const mergeOutputFileInput = document.getElementById('mergeOutputFile');
    const useGitIgnoreCheck = document.getElementById('useGitIgnore');
    const analyzeBtn = document.getElementById('analyzeBtn');
    const defaultConfigBtn = document.getElementById('defaultConfigBtn');
    const clearConfigBtn = document.getElementById('clearConfigBtn');
    const progressBar = document.getElementById('progressBar');
    const progressFill = document.getElementById('progressFill');
    const progressText = document.getElementById('progressText');
    const fileTableBody = document.getElementById('fileTableBody');
    const summaryInfo = document.getElementById('summaryInfo');
    const languageInfo = document.getElementById('languageInfo');
    const resultTabs = document.querySelectorAll('.result-tabs .tab');
    const filesTable = document.getElementById('filesTable');
    const summaryTab = document.getElementById('summaryContent');
    const dependencyTab = document.getElementById('dependencyContent');
    const exportCsvBtn = document.getElementById('exportCsvBtn');
    const downloadMergeBtn = document.getElementById('downloadMergeBtn');

    const localDirTree = document.getElementById('localDirTree');
    const localRefreshTreeBtn = document.getElementById('localRefreshTreeBtn');
    const localCurrentPathSpan = document.getElementById('localCurrentPath');
    const localFileTableBody = document.getElementById('localFileTableBody');
    const localFileListPanel = document.getElementById('localFileListPanel');
    const localEditorPanel = document.getElementById('localEditorPanel');
    const localFileEditor = document.getElementById('localFileEditor');
    const localEditFileNameSpan = document.getElementById('localEditFileName');
    const localNewFolderBtn = document.getElementById('localNewFolderBtn');
    const localNewFileBtn = document.getElementById('localNewFileBtn');
    const localUploadBtn = document.getElementById('localUploadBtn');
    const localRefreshBtn = document.getElementById('localRefreshBtn');
    const localCloseEditorBtn = document.getElementById('localCloseEditorBtn');
    const localSaveFileBtn = document.getElementById('localSaveFileBtn');

    // ==================== 工具 ====================
    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>"]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[m]));
    }
    function truncatePath(path) { return path.length > 60 ? '...' + path.slice(-57) : path; }
    function formatSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }
    function normalizePath(p) { return (p || '').replace(/\\/g, '/'); }
    function showMsg(msg) { if (progressText) progressText.textContent = msg; }

    // ==================== 模式切换 ====================
    function switchMode(mode) {
        document.querySelectorAll('.mode-item').forEach(li => li.classList.remove('active'));
        const target = document.querySelector(`.mode-item[data-mode="${mode}"]`);
        if (target) target.classList.add('active');

        serverWorkspace.style.display = 'none';
        localWorkspace.style.display = 'none';
        spiderWorkspace.style.display = 'none';
        qbWorkspace.style.display = 'none';
        if (systemcheckWorkspace) systemcheckWorkspace.style.display = 'none';

        if (mode === 'server') serverWorkspace.style.display = 'flex';
        else if (mode === 'local') { localWorkspace.style.display = 'flex'; initLocalFileManager(); }
        else if (mode === 'webspider') spiderWorkspace.style.display = 'flex';
        else if (mode === 'questionbank') qbWorkspace.style.display = 'flex';
        else if (mode === 'systemcheck') systemcheckWorkspace.style.display = 'flex';
    }

    modeList.addEventListener('click', (e) => {
        const item = e.target.closest('.mode-item');
        if (!item) return;
        const mode = item.dataset.mode;
        if (mode) switchMode(mode);
    });

    // ==================== 目录树 ====================
    function containerHighlight(targetDiv) {
        if (!targetDiv) return;
        const container = targetDiv.closest('.dir-tree-wrapper');
        if (container) container.querySelectorAll('.tree-item.selected').forEach(el => el.classList.remove('selected'));
        targetDiv.classList.add('selected');
    }

    function createTreeNodeWithHighlight(node, level, onDirectoryClick, onFileClick, pathContext) {
        const li = document.createElement('li');
        li.className = 'tree-node';
        const div = document.createElement('div');
        div.className = 'tree-item';
        div.style.paddingLeft = (level * 16 + 8) + 'px';
        div.dataset.path = node.path || '';
        div.dataset.nodeType = node.type;

        const isDir = node.type === 'directory';
        const hasChildren = node.hasChildren === true;

        const toggle = document.createElement('span');
        toggle.className = 'toggle';
        toggle.textContent = (isDir && hasChildren) ? '▶' : ' ';
        if (!(isDir && hasChildren)) toggle.style.opacity = '0';

        const icon = document.createElement('span');
        icon.className = 'icon';
        icon.textContent = isDir ? '📁' : '📄';

        const nameSpan = document.createElement('span');
        nameSpan.className = 'name';
        nameSpan.textContent = node.name;

        div.appendChild(toggle);
        div.appendChild(icon);
        div.appendChild(nameSpan);
        li.appendChild(div);

        let childrenUl = null;
        if (isDir && hasChildren) {
            childrenUl = document.createElement('ul');
            childrenUl.className = 'tree-children';
            li.appendChild(childrenUl);
        }

        async function expandOrCollapse() {
            if (!isDir || !hasChildren || !childrenUl) return;
            const isOpen = childrenUl.classList.contains('open');
            if (!isOpen) {
                if (childrenUl.children.length === 0) {
                    try {
                        const url = `${API_FILES}/tree?rootType=local&path=${encodeURIComponent(node.path)}&withChildren=true`;
                        const res = await fetch(url);
                        if (res.ok) {
                            const data = await res.json();
                            node.children = data.children || [];
                            node.hasChildren = data.hasChildren;
                            childrenUl.innerHTML = '';
                            node.children.forEach(child =>
                                childrenUl.appendChild(createTreeNodeWithHighlight(child, level + 1, onDirectoryClick, onFileClick, pathContext))
                            );
                        }
                    } catch (err) { console.error('懒加载失败', err); }
                }
                childrenUl.classList.add('open');
                toggle.textContent = '▼';
            } else {
                childrenUl.classList.remove('open');
                toggle.textContent = '▶';
            }
            node.expanded = !isOpen;
        }

        toggle.addEventListener('click', async (e) => { e.stopPropagation(); await expandOrCollapse(); });

        div.addEventListener('click', (e) => {
            e.stopPropagation();
            if (isDir) {
                containerHighlight(div);
                if (onDirectoryClick) onDirectoryClick(node, pathContext);
            } else if (onFileClick) onFileClick(node, pathContext);
        });
        return li;
    }

    function renderDirectoryTreeWithHighlight(container, rootNode, onDirectoryClick, onFileClick, pathContext) {
        container.innerHTML = '';
        if (!rootNode) return null;
        const rootLi = createTreeNodeWithHighlight(rootNode, 0, onDirectoryClick, onFileClick, pathContext);
        container.appendChild(rootLi);
        return rootLi;
    }

    function autoExpandNode(li) {
        if (!li) return;
        const t = li.querySelector(':scope > .tree-item > .toggle');
        if (t && t.textContent === '▶') t.click();
    }

    // ==================== 目录拾取 ====================
    function setPickerTarget(target) {
        activePicker = target;
        document.querySelectorAll('.dir-picker-btn').forEach(b =>
            b.classList.toggle('active', b.dataset.target === target));
        if (target === 'rootDir') {
            serverDirTree.classList.add('picking-mode');
            serverDirTip.textContent = '👉 请点击下方目录树选择根目录';
        } else if (target === 'extraPaths') {
            serverDirTree.classList.add('picking-mode');
            serverDirTip.textContent = '👉 连续点击可追加多个额外目录（自动去重）';
        } else {
            serverDirTree.classList.remove('picking-mode');
            serverDirTip.textContent = '点击目录填充分析根路径';
        }
    }

    document.querySelectorAll('.dir-picker-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const target = btn.dataset.target;
            setPickerTarget(activePicker === target ? null : target);
        });
    });

    function handleServerDirectoryClick(node, pathContext) {
        const absolutePath = normalizePath(pathContext.absolutePath);
        const fullPath = node.path ? normalizePath(absolutePath + '/' + node.path) : absolutePath;
        if (activePicker === 'rootDir') {
            rootDirInput.value = fullPath;
            setPickerTarget(null);
        } else if (activePicker === 'extraPaths') {
            const current = extraPathsInput.value.trim();
            const items = current ? current.split(',').map(s => s.trim()).filter(s => s) : [];
            if (!items.includes(fullPath)) { items.push(fullPath); extraPathsInput.value = items.join(','); }
        } else {
            rootDirInput.value = fullPath;
        }
    }

    async function initServerDirectoryTree() {
        serverDirTree.innerHTML = '<div class="loading">加载中...</div>';
        try {
            const rootRes = await fetch(`${API_FILES}/root?rootType=local`);
            const rootInfo = await rootRes.json();
            const absolutePath = normalizePath(rootInfo.absolutePath || '');
            const res = await fetch(`${API_FILES}/tree?rootType=local&path=&withChildren=true`);
            if (!res.ok) throw new Error('获取目录树失败');
            const rootNode = await res.json();
            const rootLi = renderDirectoryTreeWithHighlight(serverDirTree, rootNode,
                handleServerDirectoryClick, null, { absolutePath });
            autoExpandNode(rootLi);
        } catch (err) {
            serverDirTree.innerHTML = `<div class="error-tip">❌ ${escapeHtml(err.message)}</div>`;
        }
    }

    // ==================== 服务器分析 ====================
    async function loadDefaultConfig() {
        try {
            const resp = await fetch(API_CODE + '/config');
            if (resp.ok) {
                const config = await resp.json();
                rootDirInput.value = normalizePath(config.rootDir || '');
                extensionsInput.value = config.extensions || 'java';
                excludePathsInput.value = config.excludePaths || '';
                extraPathsInput.value = normalizePath(config.extraPaths || '');
                excludeCommentsCheck.checked = config.excludeComments !== false;
                threadCountInput.value = config.threadCount || 4;
                sortBySelect.value = config.sortBy || 'lines';
                mergeEnabledCheck.checked = config.mergeEnabled !== false;
                mergeOutputFileInput.value = normalizePath(config.mergeOutputFile || '');
                useGitIgnoreCheck.checked = config.useGitIgnore === true;
                toggleMergeRow();
            }
        } catch (e) { console.error('加载默认配置失败', e); }
    }

    function clearAllConfig() {
        rootDirInput.value = '';
        extensionsInput.value = 'java';
        excludePathsInput.value = '';
        extraPathsInput.value = '';
        excludeCommentsCheck.checked = true;
        threadCountInput.value = 4;
        sortBySelect.value = 'lines';
        mergeEnabledCheck.checked = false;
        mergeOutputFileInput.value = '';
        useGitIgnoreCheck.checked = false;
        toggleMergeRow();
        setPickerTarget(null);
    }

    function toggleMergeRow() { mergeRow.style.display = mergeEnabledCheck.checked ? 'flex' : 'none'; }
    mergeEnabledCheck.addEventListener('change', toggleMergeRow);

    // ★ base64 转换：先按 UTF-8 编码，再 btoa
    function utf8ToBase64(str) {
        const bytes = new TextEncoder().encode(str);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
        return btoa(binary);
    }

    async function analyze() {
        if (downloadMergeBtn) downloadMergeBtn.disabled = true;
        currentMergeFilePath = null;
        dependencyData = null;
        classInfoMap = null;

        const params = new URLSearchParams();
        params.append('rootDir', rootDirInput.value);
        params.append('extensions', extensionsInput.value);
        params.append('excludePaths', excludePathsInput.value);
        params.append('extraPaths', extraPathsInput.value);
        params.append('excludeComments', excludeCommentsCheck.checked);
        params.append('threadCount', threadCountInput.value);
        params.append('sortBy', sortBySelect.value);
        params.append('mergeEnabled', mergeEnabledCheck.checked);
        params.append('mergeOutputFile', mergeOutputFileInput.value);
        params.append('useGitIgnore', useGitIgnoreCheck.checked);

        progressBar.style.display = 'block';
        progressFill.style.width = '10%';
        showMsg('正在分析（大工程请耐心等待）...');

        try {
            const resp = await fetch(API_CODE + '/analyze?' + params.toString());
            progressFill.style.width = '80%';
            const data = await resp.json();
            if (data.error) { showMsg('❌ 分析失败：' + data.error); progressBar.style.display = 'none'; return; }
            renderResults(data);

            // ★ 后端返回的 mergeOutput 已是归一化后的绝对路径，直接回填输入框
            if (data.mergeOutput && data.mergeOutput.trim() !== '') {
                currentMergeFilePath = data.mergeOutput;
                if (mergeOutputFileInput) mergeOutputFileInput.value = data.mergeOutput;
                if (downloadMergeBtn) downloadMergeBtn.disabled = false;
            }
            progressFill.style.width = '100%';
            showMsg('✅ 分析完成（依赖分析请切到「依赖关系」页签）');
            setTimeout(() => progressBar.style.display = 'none', 2000);
        } catch (e) {
            showMsg('❌ 请求失败：' + e.message);
            progressBar.style.display = 'none';
        }
    }

    function renderResults(data) {
        const files = data.files || [];
        fileTableBody.innerHTML = files.map(f => `
            <tr>
                <td title="${escapeHtml(f.path)}">${escapeHtml(truncatePath(f.path))}</td>
                <td>${f.lines}</td><td>${f.codeLines}</td>
                <td>${f.commentLines}</td><td>${f.blankLines}</td>
                <td>${escapeHtml(f.language)}</td><td>${f.complexity}</td>
            </tr>
        `).join('');
        const summary = data.summary || {};
        summaryInfo.innerHTML = `
            <h3>总体统计</h3>
            <p>总文件数: ${summary.totalFiles || 0}, 总行数: ${summary.totalLines || 0}</p>
            <p>代码行: ${summary.totalCodeLines || 0}, 注释行: ${summary.totalCommentLines || 0}, 空行: ${summary.totalBlankLines || 0}</p>
            <p>耗时: ${summary.durationMs || 0} ms</p>
        `;
        const langs = data.languages || {};
        languageInfo.innerHTML = `<h3>语言分布</h3>` + Object.entries(langs).map(([lang, stat]) =>
            `<div class="language-item"><strong>${escapeHtml(lang)}</strong>: 文件 ${stat.files}, 行数 ${stat.lines}, 代码 ${stat.code}, 注释 ${stat.comments}, 空行 ${stat.blanks}</div>`
        ).join('');
        resultTabs.forEach(btn => btn.classList.remove('active'));
        resultTabs[0].classList.add('active');
        filesTable.style.display = 'block';
        summaryTab.style.display = 'none';
        if (dependencyTab) dependencyTab.style.display = 'none';
    }

    // ==================== 依赖分析 ====================
    async function loadDependencyData() {
        const depStatsDiv = document.getElementById('depStats');
        if (depStatsDiv) depStatsDiv.innerHTML = '<div class="loading">加载依赖分析中...</div>';

        const depParams = new URLSearchParams();
        depParams.append('rootDir', rootDirInput.value);
        depParams.append('extensions', extensionsInput.value);
        depParams.append('excludePaths', excludePathsInput.value);
        depParams.append('extraPaths', extraPathsInput.value);
        depParams.append('threadCount', threadCountInput.value);
        depParams.append('excludeComments', excludeCommentsCheck.checked);
        depParams.append('useGitIgnore', useGitIgnoreCheck.checked);
        try {
            const resp = await fetch(API_CODE + '/dependencies?' + depParams.toString());
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            const data = await resp.json();
            if (data.error) {
                if (depStatsDiv) depStatsDiv.innerHTML = `<div class="error-tip">❌ ${escapeHtml(data.error)}</div>`;
                return;
            }
            dependencyData = data;
            classInfoMap = data.classInfoMap || {};
            renderDependencyStats(data);
            populateClassSelect(classInfoMap);
        } catch (err) {
            console.error('依赖分析请求失败', err);
            if (depStatsDiv) depStatsDiv.innerHTML = `<div class="error-tip">❌ ${escapeHtml(err.message)}</div>`;
        }
    }

    function renderDependencyStats(data) {
        const depStatsDiv = document.getElementById('depStats');
        if (!depStatsDiv) return;
        const cycles = data.cycles || [];
        const failed = data.failedFiles || [];
        let failedHtml = '';
        if (failed.length) {
            failedHtml = `<details style="margin-top:8px;color:#dc2626;">
                <summary>⚠️ ${failed.length} 个文件解析失败（点击查看）</summary>
                <ul style="max-height:160px;overflow:auto;font-size:12px;font-family:monospace;">
                    ${failed.map(f => `<li>${escapeHtml(f)}</li>`).join('')}
                </ul>
            </details>`;
        }
        depStatsDiv.innerHTML = `
            <h3>📊 依赖统计</h3>
            <div style="display: flex; gap: 20px; flex-wrap: wrap;">
                <div><strong>总类数:</strong> ${data.totalClasses || 0}</div>
                <div><strong>平均出向依赖数:</strong> ${(data.averageOutgoingDependencies || 0).toFixed(2)}</div>
                <div><strong>最大出向依赖数:</strong> ${data.maxOutgoingDependencies || 0} (类: ${(data.maxOutgoingClasses || []).join(', ') || '无'})</div>
                <div><strong>循环依赖组数:</strong> ${cycles.length}</div>
            </div>
            <div style="font-size:12px;color:#64748b;margin-top:4px;">
                💡 依赖分析仅支持 Java 源码；其他语言的统计请查看「文件列表」页签
            </div>
            ${cycles.length ? `<details><summary>🔍 查看循环依赖组</summary><pre>${escapeHtml(JSON.stringify(cycles, null, 2))}</pre></details>` : ''}
            ${failedHtml}
            <hr>
        `;
    }

    function populateClassSelect(classMap) {
        const classSelect = document.getElementById('classSelect');
        if (!classSelect) return;
        const classNames = Object.keys(classMap).sort();
        if (classNames.length === 0) { classSelect.innerHTML = '<option value="">无类信息</option>'; return; }
        let options = '<option value="">请选择类</option>';
        for (const fqName of classNames) {
            const info = classMap[fqName];
            const display = `${info.simpleName} (${info.packageName || 'default'})`;
            options += `<option value="${escapeHtml(fqName)}">${escapeHtml(display)}</option>`;
        }
        classSelect.innerHTML = options;
        classSelect.removeEventListener('change', classSelectChangeHandler);
        classSelect.addEventListener('change', classSelectChangeHandler);
    }

    function classSelectChangeHandler(e) {
        const className = e.target.value;
        if (className) loadDependenciesForClass(className);
        else {
            document.getElementById('outgoingList').innerHTML = '<li>请选择一个类</li>';
            document.getElementById('incomingList').innerHTML = '<li>请选择一个类</li>';
        }
    }

    async function loadDependenciesForClass(className) {
        const outgoingList = document.getElementById('outgoingList');
        const incomingList = document.getElementById('incomingList');
        try {
            const [outResp, inResp] = await Promise.all([
                fetch(API_CODE + `/dependencies/outgoing?className=${encodeURIComponent(className)}`),
                fetch(API_CODE + `/dependencies/incoming?className=${encodeURIComponent(className)}`)
            ]);
            const outgoingData = await outResp.json();
            const incomingData = await inResp.json();
            outgoingList.innerHTML = outgoingData.outgoing?.length
                ? outgoingData.outgoing.map(dep => `<li>${escapeHtml(dep)}</li>`).join('')
                : '<li>无</li>';
            incomingList.innerHTML = incomingData.incoming?.length
                ? incomingData.incoming.map(dep => `<li>${escapeHtml(dep)}</li>`).join('')
                : '<li>无</li>';
        } catch (err) {
            console.error('加载类依赖详情失败', err);
            outgoingList.innerHTML = '<li>加载失败</li>';
            incomingList.innerHTML = '<li>加载失败</li>';
        }
    }

    function switchTab(tabName) {
        filesTable.style.display = 'none';
        summaryTab.style.display = 'none';
        if (dependencyTab) dependencyTab.style.display = 'none';
        if (tabName === 'files') filesTable.style.display = 'block';
        else if (tabName === 'summary') summaryTab.style.display = 'block';
        else if (tabName === 'dependency') {
            if (dependencyTab) dependencyTab.style.display = 'block';
            if (!dependencyData && rootDirInput.value) loadDependencyData();
            else if (dependencyData && classInfoMap &&
                Object.keys(classInfoMap).length > 0 &&
                document.getElementById('classSelect')?.options.length <= 1) {
                populateClassSelect(classInfoMap);
            }
        }
        resultTabs.forEach(btn => btn.classList.toggle('active', btn.dataset.tab === tabName));
    }
    resultTabs.forEach(btn => btn.addEventListener('click', () => switchTab(btn.dataset.tab)));

    function exportToCsv() {
        const rows = document.querySelectorAll('#fileTableBody tr');
        if (!rows.length) { showMsg('没有数据可导出'); return; }
        const headers = [];
        document.querySelector('#filesTable table thead tr')?.querySelectorAll('th').forEach(th => headers.push(th.innerText.trim()));
        const csvRows = [headers.join(',')];
        for (let row of rows) {
            const cells = row.querySelectorAll('td');
            const rowData = [];
            for (let cell of cells) {
                let text = cell.innerText.trim();
                if (text.includes(',') || text.includes('"') || text.includes('\n')) text = `"${text.replace(/"/g, '""')}"`;
                rowData.push(text);
            }
            csvRows.push(rowData.join(','));
        }
        const blob = new Blob(["\uFEFF" + csvRows.join('\n')], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        const url = URL.createObjectURL(blob);
        link.href = url;
        link.setAttribute('download', `code_stats_${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.csv`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    // ==================== ★ 下载合并文件（修复版） ====================
    async function downloadMergeFile() {
        if (!currentMergeFilePath) {
            showMsg('没有可用的合并文件，请先执行分析');
            return;
        }

        let encodedPath;
        try {
            encodedPath = utf8ToBase64(currentMergeFilePath);
        } catch (e) {
            showMsg('文件路径编码失败：' + e.message);
            return;
        }

        // ★ 关键修复 1：base64 必须 encodeURIComponent，否则 + 会变成空格
        const downloadUrl = window.apiUrl(
            `/code/api/download?filePath=${encodeURIComponent(encodedPath)}&addTimestamp=false`
        );

        if (downloadMergeBtn) downloadMergeBtn.disabled = true;
        showMsg('⏳ 正在下载合并文件...');

        try {
            const response = await fetch(downloadUrl);
            const contentType = (response.headers.get('content-type') || '').toLowerCase();

            // ★ 关键修复 2：非 200 或非二进制 → 解析 JSON 错误后提示
            if (!response.ok || !contentType.includes('application/octet-stream')) {
                let errMsg = `HTTP ${response.status} ${response.statusText}`;
                try {
                    const text = await response.text();
                    try {
                        const json = JSON.parse(text);
                        if (json && json.error) errMsg = json.error;
                        else if (json && json.message) errMsg = json.message;
                    } catch (_) {
                        if (text && text.length < 500) errMsg = text;
                    }
                } catch (_) {}
                showMsg('❌ 下载失败：' + errMsg);
                return;
            }

            // ★ 关键修复 3：从 Content-Disposition 解析真实文件名
            let filename = currentMergeFilePath.split(/[\\/]/).pop() || 'merged.txt';
            const cd = response.headers.get('content-disposition') || '';
            const m1 = cd.match(/filename\*=UTF-8''([^;]+)/i);
            const m2 = cd.match(/filename="([^"]+)"/i);
            if (m1 && m1[1]) {
                try { filename = decodeURIComponent(m1[1]); } catch (_) { filename = m1[1]; }
            } else if (m2 && m2[1]) {
                filename = m2[1];
            }

            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', filename);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            setTimeout(() => URL.revokeObjectURL(url), 2000);

            showMsg('✅ 合并文件已开始下载：' + filename);
        } catch (err) {
            // ★ 关键修复 4：fetch 失败（大文件内存/跨域/网络）→ 用 window.open 兜底
            console.error('[downloadMergeFile] fetch 失败，尝试 window.open 兜底', err);
            try {
                window.open(downloadUrl, '_blank');
                showMsg('⚠️ 已改为浏览器直接下载（fetch 失败：' + err.message + '）');
            } catch (e2) {
                showMsg('❌ 下载失败：' + err.message);
            }
        } finally {
            if (downloadMergeBtn) downloadMergeBtn.disabled = false;
        }
    }

    // ==================== 事件绑定 ====================
    analyzeBtn.addEventListener('click', analyze);
    defaultConfigBtn.addEventListener('click', loadDefaultConfig);
    clearConfigBtn.addEventListener('click', clearAllConfig);
    serverRefreshTreeBtn.addEventListener('click', initServerDirectoryTree);
    if (exportCsvBtn) exportCsvBtn.addEventListener('click', exportToCsv);
    if (downloadMergeBtn) downloadMergeBtn.addEventListener('click', downloadMergeFile);

    // ==================== 本地目录管理 ====================
    async function initLocalFileManager() {
        if (localInited) {
            await loadLocalDirectoryTree();
            await loadLocalFileListAndRender(localCurrentPath);
            return;
        }
        localInited = true;
        await loadLocalDirectoryTree();
        await loadLocalFileListAndRender('');
    }

    async function loadLocalDirectoryTree() {
        localDirTree.innerHTML = '<div class="loading">加载中...</div>';
        try {
            const res = await fetch(`${API_FILES}/tree?rootType=local&path=&withChildren=true`);
            if (!res.ok) throw new Error('获取目录树失败');
            const rootNode = await res.json();
            const rootLi = renderDirectoryTreeWithHighlight(localDirTree, rootNode,
                (node) => navigateLocalTo(node.path || ''),
                (node) => openLocalEditor(node.path), {});
            autoExpandNode(rootLi);
        } catch (err) {
            localDirTree.innerHTML = `<div class="error-tip">❌ ${escapeHtml(err.message)}</div>`;
        }
    }

    async function navigateLocalTo(path) {
        localCurrentPath = path;
        localCurrentPathSpan.textContent = path === '' ? '/' : '/' + path;
        try {
            await loadLocalFileListAndRender(path);
            localEditorPanel.style.display = 'none';
            localFileListPanel.style.display = 'block';
        } catch (err) { showMsg(err.message); }
    }

    async function loadLocalFileListAndRender(path) {
        const url = `${API_FILES}/list?rootType=local&path=${encodeURIComponent(path)}`;
        const res = await fetch(url);
        if (!res.ok) throw new Error('获取文件列表失败');
        const list = await res.json();
        renderLocalFileList(list);
    }

    function renderLocalFileList(files) {
        localFileTableBody.innerHTML = '';
        if (files.length === 0) {
            localFileTableBody.innerHTML = '<tr><td colspan="5" style="text-align:center;">目录为空</td></tr>';
            return;
        }
        files.forEach(file => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td class="file-name" data-path="${escapeHtml(file.path)}" data-type="${file.type}">${escapeHtml(file.name)}</td>
                <td>${file.type === 'directory' ? '目录' : '文件'}</td>
                <td>${file.type === 'file' ? formatSize(file.size) : '-'}</td>
                <td>${file.modified || '-'}</td>
                <td class="actions">
                    ${file.type === 'file' ? `<span class="action-icon" data-action="edit" data-path="${escapeHtml(file.path)}">✏️ 编辑</span>` : ''}
                    <span class="action-icon" data-action="download" data-path="${escapeHtml(file.path)}" data-type="${file.type}">⬇️ 下载</span>
                    <span class="action-icon" data-action="rename" data-path="${escapeHtml(file.path)}" data-name="${escapeHtml(file.name)}">✏️ 重命名</span>
                    <span class="action-icon" data-action="delete" data-path="${escapeHtml(file.path)}">🗑️ 删除</span>
                </td>
            `;
            localFileTableBody.appendChild(row);
        });

        localFileTableBody.querySelectorAll('.file-name').forEach(el => {
            el.addEventListener('click', () => {
                const path = el.dataset.path;
                const type = el.dataset.type;
                if (type === 'directory') navigateLocalTo(path);
                else openLocalEditor(path);
            });
        });

        localFileTableBody.querySelectorAll('.action-icon').forEach(icon => {
            icon.addEventListener('click', (e) => {
                e.stopPropagation();
                const action = icon.dataset.action;
                const path = icon.dataset.path;
                const type = icon.dataset.type;
                const name = icon.dataset.name;
                if (action === 'edit') openLocalEditor(path);
                else if (action === 'download') localDownloadItem(path, type);
                else if (action === 'rename') localRenameItem(path, name);
                else if (action === 'delete') localDeleteItem(path);
            });
        });
    }

    async function openLocalEditor(path) {
        try {
            const res = await fetch(`${API_FILES}/read?rootType=local&path=${encodeURIComponent(path)}`);
            if (!res.ok) throw new Error('读取文件失败');
            const data = await res.json();
            localCurrentEditPath = path;
            localFileEditor.value = data.content;
            localEditFileNameSpan.textContent = path;
            localFileListPanel.style.display = 'none';
            localEditorPanel.style.display = 'flex';
        } catch (err) { showMsg(err.message); }
    }

    async function saveLocalFile() {
        if (!localCurrentEditPath) return;
        const content = localFileEditor.value;
        try {
            const res = await fetch(`${API_FILES}/save`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: 'local', path: localCurrentEditPath, content })
            });
            const data = await res.json();
            if (data.success) { showMsg('保存成功'); navigateLocalTo(localCurrentPath); loadLocalDirectoryTree(); }
            else showMsg(data.error || '保存失败');
        } catch (err) { showMsg(err.message); }
    }

    async function localRenameItem(oldPath, oldName) {
        const newName = prompt('请输入新名称', oldName);
        if (!newName) return;
        try {
            const res = await fetch(`${API_FILES}/rename`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: 'local', path: oldPath, newName })
            });
            const data = await res.json();
            if (data.success) { showMsg('重命名成功'); await navigateLocalTo(localCurrentPath); await loadLocalDirectoryTree(); }
            else showMsg(data.error || '重命名失败');
        } catch (err) { showMsg(err.message); }
    }

    async function localDeleteItem(path) {
        if (!confirm(`确定删除 ${path} 吗？`)) return;
        try {
            const res = await fetch(`${API_FILES}/delete`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: 'local', path })
            });
            const data = await res.json();
            if (data.success) { showMsg('删除成功'); await navigateLocalTo(localCurrentPath); await loadLocalDirectoryTree(); }
            else showMsg(data.error || '删除失败');
        } catch (err) { showMsg(err.message); }
    }

    function localDownloadItem(path, type) {
        if (type === 'directory') window.open(`${API_FILES}/downloadZip?rootType=local&path=${encodeURIComponent(path)}`, '_blank');
        else window.open(`${API_FILES}/download?rootType=local&path=${encodeURIComponent(path)}`, '_blank');
    }

    async function localNewFolder() {
        const folderName = prompt('请输入目录名称');
        if (!folderName) return;
        try {
            const res = await fetch(`${API_FILES}/mkdir`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: 'local', parentPath: localCurrentPath, name: folderName })
            });
            const data = await res.json();
            if (data.success) { showMsg('目录创建成功'); await navigateLocalTo(localCurrentPath); await loadLocalDirectoryTree(); }
            else showMsg(data.error || '创建失败');
        } catch (err) { showMsg(err.message); }
    }

    async function localNewFile() {
        const fileName = prompt('请输入文件名（含扩展名）', 'newfile.txt');
        if (!fileName) return;
        try {
            const res = await fetch(`${API_FILES}/createFile`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: 'local', parentPath: localCurrentPath, fileName })
            });
            const data = await res.json();
            if (data.success) { showMsg('文件创建成功'); await navigateLocalTo(localCurrentPath); await loadLocalDirectoryTree(); }
            else showMsg(data.error || '创建失败');
        } catch (err) { showMsg(err.message); }
    }

    function localUploadFile() {
        const input = document.createElement('input');
        input.type = 'file';
        input.onchange = () => {
            const file = input.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = async (e) => {
                const bytes = new Uint8Array(e.target.result);
                let binary = '';
                const CHUNK = 0x8000;
                for (let i = 0; i < bytes.length; i += CHUNK) binary += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK));
                const base64 = btoa(binary);
                try {
                    const res = await fetch(`${API_FILES}/upload`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            rootType: 'local', parentPath: localCurrentPath,
                            fileName: file.name, content: base64
                        })
                    });
                    const data = await res.json();
                    if (data.success) { showMsg('上传成功'); await navigateLocalTo(localCurrentPath); await loadLocalDirectoryTree(); }
                    else showMsg(data.error || '上传失败');
                } catch (err) { showMsg(err.message); }
            };
            reader.readAsArrayBuffer(file);
        };
        input.click();
    }

    localNewFolderBtn.addEventListener('click', localNewFolder);
    localNewFileBtn.addEventListener('click', localNewFile);
    localUploadBtn.addEventListener('click', localUploadFile);
    localRefreshBtn.addEventListener('click', async () => {
        await loadLocalDirectoryTree();
        await navigateLocalTo(localCurrentPath);
    });
    localRefreshTreeBtn.addEventListener('click', loadLocalDirectoryTree);
    localCloseEditorBtn.addEventListener('click', () => {
        localEditorPanel.style.display = 'none';
        localFileListPanel.style.display = 'block';
        localCurrentEditPath = null;
    });
    localSaveFileBtn.addEventListener('click', saveLocalFile);

    // ==================== 启动 ====================
    loadDefaultConfig();
    initServerDirectoryTree();
    switchMode('server');
})();