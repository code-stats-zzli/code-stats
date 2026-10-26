(function() {
    // ==================== 全局状态 ====================
    const API_CODE = window.apiUrl('/code/api');
    const API_FILES = window.apiUrl('/files/api');

    // 分析相关
    let dependencyData = null;
    let classInfoMap = null;
    let currentMergeFilePath = null;

    // 本地目录管理状态
    let localCurrentPath = '';
    let localCurrentEditPath = null;

    // ==================== DOM 引用 ====================
    const modeList = document.getElementById('modeList');
    const serverWorkspace = document.getElementById('serverWorkspace');
    const localWorkspace = document.getElementById('localWorkspace');
    const spiderWorkspace = document.getElementById('spiderWorkspace');
    const qbWorkspace = document.getElementById('qbWorkspace');

    // 服务器分析
    const serverDirTree = document.getElementById('serverDirTree');
    const serverRefreshTreeBtn = document.getElementById('serverRefreshTreeBtn');
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
    const analyzeBtn = document.getElementById('analyzeBtn');
    const resetBtn = document.getElementById('resetBtn');
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

    // 本地目录管理
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

    // ==================== 工具函数 ====================
    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>"]/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
        }[m]));
    }
    function truncatePath(path) { return path.length > 60 ? '...' + path.slice(-57) : path; }
    function formatSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }
    function showMsg(msg, isError = false) { alert(msg); }

    // ==================== 模式切换 ====================
    function switchMode(mode) {
        document.querySelectorAll('.mode-item').forEach(li => li.classList.remove('active'));
        const target = document.querySelector(`.mode-item[data-mode="${mode}"]`);
        if (target) target.classList.add('active');

        serverWorkspace.style.display = 'none';
        localWorkspace.style.display = 'none';
        spiderWorkspace.style.display = 'none';
        qbWorkspace.style.display = 'none';

        if (mode === 'server') {
            serverWorkspace.style.display = 'flex';
        } else if (mode === 'local') {
            localWorkspace.style.display = 'flex';
            initLocalFileManager();
        } else if (mode === 'webspider') {
            spiderWorkspace.style.display = 'flex';
        } else if (mode === 'questionbank') {
            qbWorkspace.style.display = 'flex';
        }
    }

    modeList.addEventListener('click', (e) => {
        const item = e.target.closest('.mode-item');
        if (!item) return;
        const mode = item.dataset.mode;
        if (mode) switchMode(mode);
    });

    // ==================== 通用目录树渲染 ====================
    /**
     * 渲染目录树
     * @param {HTMLElement} container 树容器
     * @param {Object} rootNode 根节点
     * @param {Function} onDirectoryClick 目录点击回调 (node, fullPath)
     * @param {Function} onFileClick 文件点击回调 (node, fullPath)
     * @param {Object} pathContext { absolutePath: string } 用于拼接完整路径
     */
    function renderDirectoryTree(container, rootNode, onDirectoryClick, onFileClick, pathContext) {
        container.innerHTML = '';
        if (!rootNode) return;

        const rootLi = createTreeNode(rootNode, 0, onDirectoryClick, onFileClick, pathContext);
        container.appendChild(rootLi);

        // 自动展开根
        const rootToggle = rootLi.querySelector(':scope > .tree-item > .toggle');
        if (rootToggle && rootToggle.textContent === '▶') rootToggle.click();
    }

    function createTreeNode(node, level, onDirectoryClick, onFileClick, pathContext) {
        const li = document.createElement('li');
        li.className = 'tree-node';

        const div = document.createElement('div');
        div.className = 'tree-item';
        div.style.paddingLeft = (level * 16 + 8) + 'px';

        const isDir = node.type === 'directory';
        const hasChildren = node.hasChildren === true;

        const toggle = document.createElement('span');
        toggle.className = 'toggle';
        if (isDir && hasChildren) {
            toggle.textContent = node.expanded ? '▼' : '▶';
        } else {
            toggle.textContent = ' ';
            toggle.style.opacity = '0';
        }

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

        // 子节点容器（懒加载）
        let childrenUl = null;
        if (isDir && hasChildren) {
            childrenUl = document.createElement('ul');
            childrenUl.className = 'tree-children';
            if (node.expanded) childrenUl.classList.add('open');
            li.appendChild(childrenUl);
        }

        // 点击事件
        div.addEventListener('click', async (e) => {
            e.stopPropagation();
            if (isDir) {
                if (onDirectoryClick) onDirectoryClick(node, pathContext);
                // 展开/收起
                if (hasChildren && toggle.textContent !== ' ') {
                    const isOpen = childrenUl.classList.contains('open');
                    if (!isOpen && childrenUl.children.length === 0) {
                        // 懒加载
                        try {
                            const url = `${API_FILES}/tree?rootType=local&path=${encodeURIComponent(node.path)}&withChildren=true`;
                            const res = await fetch(url);
                            if (res.ok) {
                                const data = await res.json();
                                node.children = data.children || [];
                                node.hasChildren = data.hasChildren;
                                childrenUl.innerHTML = '';
                                node.children.forEach(child =>
                                    childrenUl.appendChild(createTreeNode(child, level + 1, onDirectoryClick, onFileClick, pathContext))
                                );
                            }
                        } catch (err) { console.error('懒加载失败', err); }
                    }
                    childrenUl.classList.toggle('open');
                    toggle.textContent = childrenUl.classList.contains('open') ? '▼' : '▶';
                    node.expanded = childrenUl.classList.contains('open');
                }
            } else {
                if (onFileClick) onFileClick(node, pathContext);
            }
        });

        // 预渲染子节点（如果已有）
        if (node.children && node.children.length && childrenUl) {
            node.children.forEach(child =>
                childrenUl.appendChild(createTreeNode(child, level + 1, onDirectoryClick, onFileClick, pathContext))
            );
        }

        return li;
    }

    // ==================== 服务器目录树 ====================
    async function loadServerDirectoryTree() {
        serverDirTree.innerHTML = '<div class="loading">加载中...</div>';
        try {
            // 先获取根信息，拿到绝对路径
            const rootRes = await fetch(`${API_FILES}/root?rootType=local`);
            const rootInfo = await rootRes.json();
            const absolutePath = rootInfo.absolutePath || '';

            const res = await fetch(`${API_FILES}/tree?rootType=local&path=&withChildren=true`);
            if (!res.ok) throw new Error('获取目录树失败');
            const rootNode = await res.json();

            const pathContext = { absolutePath };
            renderDirectoryTree(
                serverDirTree,
                rootNode,
                // 点击目录：填充 rootDir
                (node, ctx) => {
                    const fullPath = node.path
                        ? ctx.absolutePath + '/' + node.path
                        : ctx.absolutePath;
                    rootDirInput.value = fullPath;
                    // 高亮选中
                    serverDirTree.querySelectorAll('.tree-item.selected').forEach(el => el.classList.remove('selected'));
                    const item = serverDirTree.querySelector(`.tree-item[data-path="${CSS.escape(node.path || '')}"]`);
                    if (item) item.classList.add('selected');
                },
                // 点击文件：不处理
                null,
                pathContext
            );

            // 给每个 tree-item 加上 data-path
            serverDirTree.querySelectorAll('.tree-item').forEach(el => {
                const nameEl = el.querySelector('.name');
                if (nameEl) {
                    // 通过位置找节点比较复杂，这里简化：点击时通过 name 反查不方便
                    // 所以高亮方式改为：直接给当前选中的 div 添加 selected 类
                    // 由于我们在渲染时没有保存 node.path 到 DOM，改用另一种方式
                }
            });
        } catch (err) {
            serverDirTree.innerHTML = `<div class="error-tip">❌ ${escapeHtml(err.message)}</div>`;
        }
    }

    // 修正：改进目录树点击回调，直接操作 DOM 高亮
    function renderDirectoryTreeWithHighlight(container, rootNode, onDirectoryClick, onFileClick, pathContext) {
        container.innerHTML = '';
        if (!rootNode) return;

        const rootLi = createTreeNodeWithHighlight(rootNode, 0, onDirectoryClick, onFileClick, pathContext);
        container.appendChild(rootLi);

        const rootToggle = rootLi.querySelector(':scope > .tree-item > .toggle');
        if (rootToggle && rootToggle.textContent === '▶') rootToggle.click();
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
        if (isDir && hasChildren) {
            toggle.textContent = node.expanded ? '▼' : '▶';
        } else {
            toggle.textContent = ' ';
            toggle.style.opacity = '0';
        }

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
            if (node.expanded) childrenUl.classList.add('open');
            li.appendChild(childrenUl);
        }

        div.addEventListener('click', async (e) => {
            e.stopPropagation();
            if (isDir) {
                // 高亮
                containerHighlight(container, div);
                if (onDirectoryClick) onDirectoryClick(node, pathContext);
                // 展开/收起
                if (hasChildren && toggle.textContent !== ' ') {
                    const isOpen = childrenUl.classList.contains('open');
                    if (!isOpen && childrenUl.children.length === 0) {
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
                    childrenUl.classList.toggle('open');
                    toggle.textContent = childrenUl.classList.contains('open') ? '▼' : '▶';
                    node.expanded = childrenUl.classList.contains('open');
                }
            } else {
                if (onFileClick) onFileClick(node, pathContext);
            }
        });

        if (node.children && node.children.length && childrenUl) {
            node.children.forEach(child =>
                childrenUl.appendChild(createTreeNodeWithHighlight(child, level + 1, onDirectoryClick, onFileClick, pathContext))
            );
        }

        return li;
    }

    // 高亮工具（按容器查当前选中的 div）
    function containerHighlight(container, targetDiv) {
        container.querySelectorAll('.tree-item.selected').forEach(el => el.classList.remove('selected'));
        if (targetDiv) targetDiv.classList.add('selected');
    }

    // 重新实现服务器目录树加载
    async function initServerDirectoryTree() {
        serverDirTree.innerHTML = '<div class="loading">加载中...</div>';
        try {
            const rootRes = await fetch(`${API_FILES}/root?rootType=local`);
            const rootInfo = await rootRes.json();
            const absolutePath = rootInfo.absolutePath || '';

            const res = await fetch(`${API_FILES}/tree?rootType=local&path=&withChildren=true`);
            if (!res.ok) throw new Error('获取目录树失败');
            const rootNode = await res.json();

            renderDirectoryTreeWithHighlight(
                serverDirTree,
                rootNode,
                (node, ctx) => {
                    const fullPath = node.path
                        ? ctx.absolutePath + '/' + node.path
                        : ctx.absolutePath;
                    rootDirInput.value = fullPath;
                },
                null,
                { absolutePath }
            );
        } catch (err) {
            serverDirTree.innerHTML = `<div class="error-tip">❌ ${escapeHtml(err.message)}</div>`;
        }
    }

    // ==================== 服务器分析逻辑 ====================

    async function loadDefaultConfig() {
        try {
            const resp = await fetch(API_CODE + '/config');
            if (resp.ok) {
                const config = await resp.json();
                rootDirInput.value = config.rootDir || '';
                extensionsInput.value = config.extensions || 'java';
                excludePathsInput.value = config.excludePaths || '';
                extraPathsInput.value = config.extraPaths || '';
                excludeCommentsCheck.checked = config.excludeComments !== false;
                threadCountInput.value = config.threadCount || 4;
                sortBySelect.value = config.sortBy || 'lines';
                mergeEnabledCheck.checked = config.mergeEnabled !== false;
                mergeOutputFileInput.value = config.mergeOutputFile || '';
                toggleMergeRow();
            }
        } catch (e) { console.error('加载默认配置失败', e); }
    }

    function toggleMergeRow() {
        mergeRow.style.display = mergeEnabledCheck.checked ? 'flex' : 'none';
    }
    mergeEnabledCheck.addEventListener('change', toggleMergeRow);

    function utf8ToBase64(str) {
        const utf8Bytes = new TextEncoder().encode(str);
        let binary = '';
        for (let i = 0; i < utf8Bytes.length; i++) binary += String.fromCharCode(utf8Bytes[i]);
        return btoa(binary);
    }

    async function analyze() {
        if (downloadMergeBtn) downloadMergeBtn.disabled = true;
        currentMergeFilePath = null;
        dependencyData = null;

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

        progressBar.style.display = 'block';
        progressFill.style.width = '10%';
        progressText.textContent = '正在分析...';

        try {
            const resp = await fetch(API_CODE + '/analyze?' + params.toString());
            progressFill.style.width = '80%';
            const data = await resp.json();
            if (data.error) { alert('分析失败：' + data.error); return; }
            renderResults(data);
            if (data.mergeOutput && data.mergeOutput.trim() !== '') {
                currentMergeFilePath = data.mergeOutput;
                if (downloadMergeBtn) downloadMergeBtn.disabled = false;
            }
            await loadDependencyData(params);
            progressFill.style.width = '100%';
            progressText.textContent = '分析完成';
            setTimeout(() => progressBar.style.display = 'none', 2000);
        } catch (e) {
            alert('请求失败：' + e.message);
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

    async function loadDependencyData(params) {
        const depParams = new URLSearchParams();
        depParams.append('rootDir', rootDirInput.value);
        depParams.append('extensions', extensionsInput.value);
        depParams.append('excludePaths', excludePathsInput.value);
        depParams.append('extraPaths', extraPathsInput.value);
        depParams.append('threadCount', threadCountInput.value);
        depParams.append('excludeComments', excludeCommentsCheck.checked);
        try {
            const resp = await fetch(API_CODE + '/dependencies?' + depParams.toString());
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            const data = await resp.json();
            if (data.error) { console.warn('依赖分析失败：', data.error); return; }
            dependencyData = data;
            classInfoMap = data.classInfoMap || {};
            renderDependencyStats(data);
            populateClassSelect(classInfoMap);
        } catch (err) { console.error('依赖分析请求失败', err); }
    }

    function renderDependencyStats(data) {
        const depStatsDiv = document.getElementById('depStats');
        if (!depStatsDiv) return;
        const cycles = data.cycles || [];
        depStatsDiv.innerHTML = `
            <h3>📊 依赖统计</h3>
            <div style="display: flex; gap: 20px; flex-wrap: wrap;">
                <div><strong>总类数:</strong> ${data.totalClasses || 0}</div>
                <div><strong>平均出向依赖数:</strong> ${(data.averageOutgoingDependencies || 0).toFixed(2)}</div>
                <div><strong>最大出向依赖数:</strong> ${data.maxOutgoingDependencies || 0} (类: ${(data.maxOutgoingClasses || []).join(', ') || '无'})</div>
                <div><strong>循环依赖组数:</strong> ${cycles.length}</div>
            </div>
            ${cycles.length ? `<details><summary>🔍 查看循环依赖组</summary><pre>${JSON.stringify(cycles, null, 2)}</pre></details>` : ''}
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
            if (!dependencyData && rootDirInput.value) {
                const params = new URLSearchParams();
                params.append('rootDir', rootDirInput.value);
                params.append('extensions', extensionsInput.value);
                params.append('excludePaths', excludePathsInput.value);
                params.append('extraPaths', extraPathsInput.value);
                params.append('threadCount', threadCountInput.value);
                loadDependencyData(params);
            } else if (dependencyData && classInfoMap && Object.keys(classInfoMap).length > 0 &&
                document.getElementById('classSelect')?.options.length <= 1) {
                populateClassSelect(classInfoMap);
            }
        }
        resultTabs.forEach(btn => btn.classList.toggle('active', btn.dataset.tab === tabName));
    }
    resultTabs.forEach(btn => btn.addEventListener('click', () => switchTab(btn.dataset.tab)));

    function exportToCsv() {
        const rows = document.querySelectorAll('#fileTableBody tr');
        if (!rows.length) { alert('没有数据可导出，请先执行分析'); return; }
        const headers = [];
        document.querySelector('#filesTable table thead tr')?.querySelectorAll('th').forEach(th => headers.push(th.innerText.trim()));
        const csvRows = [headers.join(',')];
        for (let row of rows) {
            const cells = row.querySelectorAll('td');
            const rowData = [];
            for (let cell of cells) {
                let text = cell.innerText.trim();
                if (text.includes(',') || text.includes('"') || text.includes('\n')) {
                    text = `"${text.replace(/"/g, '""')}"`;
                }
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

    async function downloadMergeFile() {
        if (!currentMergeFilePath) { alert('没有可用的合并文件'); return; }
        let encodedPath;
        try {
            encodedPath = utf8ToBase64(currentMergeFilePath);
        } catch (e) { alert('文件路径编码失败'); return; }
        const downloadUrl = window.apiUrl(`/code/api/download?filePath=${encodedPath}`);
        try {
            const response = await fetch(downloadUrl);
            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || '下载失败');
            }
            const blob = await response.blob();
            const link = document.createElement('a');
            const url = URL.createObjectURL(blob);
            let filename = currentMergeFilePath.split(/[\\/]/).pop() || 'merged.txt';
            link.href = url;
            link.setAttribute('download', filename);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);
        } catch (err) { alert('下载合并文件失败：' + err.message); }
    }

    analyzeBtn.addEventListener('click', analyze);
    resetBtn.addEventListener('click', loadDefaultConfig);
    serverRefreshTreeBtn.addEventListener('click', initServerDirectoryTree);
    if (exportCsvBtn) exportCsvBtn.addEventListener('click', exportToCsv);
    if (downloadMergeBtn) downloadMergeBtn.addEventListener('click', downloadMergeFile);

    // ==================== 本地目录管理（完整功能） ====================

    let localInited = false;

    async function initLocalFileManager() {
        if (localInited) {
            // 已初始化，直接刷新
            await loadLocalDirectoryTree();
            await loadLocalFileList('');
            return;
        }
        localInited = true;
        await loadLocalDirectoryTree();
        await loadLocalFileList('');
    }

    async function loadLocalDirectoryTree() {
        localDirTree.innerHTML = '<div class="loading">加载中...</div>';
        try {
            const res = await fetch(`${API_FILES}/tree?rootType=local&path=&withChildren=true`);
            if (!res.ok) throw new Error('获取目录树失败');
            const rootNode = await res.json();

            renderDirectoryTreeWithHighlight(
                localDirTree,
                rootNode,
                // 点击目录：切换当前路径
                (node) => {
                    navigateLocalTo(node.path || '');
                },
                // 点击文件：打开编辑器
                (node) => {
                    openLocalEditor(node.path);
                },
                {}
            );
        } catch (err) {
            localDirTree.innerHTML = `<div class="error-tip">❌ ${escapeHtml(err.message)}</div>`;
        }
    }

    async function navigateLocalTo(path) {
        localCurrentPath = path;
        localCurrentPathSpan.textContent = path === '' ? '/' : '/' + path;
        try {
            const list = await loadLocalFileList(path);
            renderLocalFileList(list);
            localEditorPanel.style.display = 'none';
            localFileListPanel.style.display = 'block';
        } catch (err) { showMsg(err.message, true); }
    }

    async function loadLocalFileList(path) {
        const url = `${API_FILES}/list?rootType=local&path=${encodeURIComponent(path)}`;
        const res = await fetch(url);
        if (!res.ok) throw new Error('获取文件列表失败');
        return await res.json();
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
        } catch (err) { showMsg(err.message, true); }
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
            if (data.success) {
                showMsg('保存成功');
                navigateLocalTo(localCurrentPath);
                loadLocalDirectoryTree();
            } else { showMsg(data.error || '保存失败', true); }
        } catch (err) { showMsg(err.message, true); }
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
            if (data.success) {
                showMsg('重命名成功');
                await navigateLocalTo(localCurrentPath);
                await loadLocalDirectoryTree();
            } else { showMsg(data.error || '重命名失败', true); }
        } catch (err) { showMsg(err.message, true); }
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
            if (data.success) {
                showMsg('删除成功');
                await navigateLocalTo(localCurrentPath);
                await loadLocalDirectoryTree();
            } else { showMsg(data.error || '删除失败', true); }
        } catch (err) { showMsg(err.message, true); }
    }

    function localDownloadItem(path, type) {
        if (type === 'directory') {
            window.open(`${API_FILES}/downloadZip?rootType=local&path=${encodeURIComponent(path)}`, '_blank');
        } else {
            window.open(`${API_FILES}/download?rootType=local&path=${encodeURIComponent(path)}`, '_blank');
        }
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
            if (data.success) {
                showMsg('目录创建成功');
                await navigateLocalTo(localCurrentPath);
                await loadLocalDirectoryTree();
            } else { showMsg(data.error || '创建失败', true); }
        } catch (err) { showMsg(err.message, true); }
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
            if (data.success) {
                showMsg('文件创建成功');
                await navigateLocalTo(localCurrentPath);
                await loadLocalDirectoryTree();
            } else { showMsg(data.error || '创建失败', true); }
        } catch (err) { showMsg(err.message, true); }
    }

    /**
     * 本地文件上传：用 ArrayBuffer 读取，避免二进制损坏
     */
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
                for (let i = 0; i < bytes.length; i += CHUNK) {
                    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK));
                }
                const base64 = btoa(binary);
                try {
                    const res = await fetch(`${API_FILES}/upload`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            rootType: 'local',
                            parentPath: localCurrentPath,
                            fileName: file.name,
                            content: base64
                        })
                    });
                    const data = await res.json();
                    if (data.success) {
                        showMsg('上传成功');
                        await navigateLocalTo(localCurrentPath);
                        await loadLocalDirectoryTree();
                    } else { showMsg(data.error || '上传失败', true); }
                } catch (err) { showMsg(err.message, true); }
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