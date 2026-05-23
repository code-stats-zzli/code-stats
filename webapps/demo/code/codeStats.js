(function() {
    // 使用全局 apiUrl
    const API_BASE = window.apiUrl('/code/api');

    // DOM 元素
    const modeList = document.getElementById('modeList');
    const configPanel = document.getElementById('configPanel');
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
    const resultTabs = document.querySelector('.result-tabs');
    const tabButtons = resultTabs.querySelectorAll('.tab');
    const filesTable = document.getElementById('filesTable');
    const summaryTab = document.getElementById('summaryContent');
    const dependencyTab = document.getElementById('dependencyContent');
    const depStatsDiv = document.getElementById('depStats');
    const classSelect = document.getElementById('classSelect');
    const outgoingList = document.getElementById('outgoingList');
    const incomingList = document.getElementById('incomingList');

    const exportCsvBtn = document.getElementById('exportCsvBtn');
    const downloadMergeBtn = document.getElementById('downloadMergeBtn');

    let currentMode = 'server';
    let currentMergeFilePath = null;
    let dependencyData = null;          // 存储依赖分析结果
    let classInfoMap = null;            // 存储类名映射

    // ========== 原有代码统计功能 ==========

    async function loadDefaultConfig() {
        try {
            const resp = await fetch(API_BASE + '/config');
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
        } catch (e) {
            console.error('加载默认配置失败', e);
        }
    }

    function toggleMergeRow() {
        mergeRow.style.display = mergeEnabledCheck.checked ? 'flex' : 'none';
    }
    mergeEnabledCheck.addEventListener('change', toggleMergeRow);

    modeList.addEventListener('click', (e) => {
        const item = e.target.closest('.mode-item');
        if (!item || item.classList.contains('disabled')) return;
        modeList.querySelectorAll('.mode-item').forEach(li => li.classList.remove('active'));
        item.classList.add('active');
        currentMode = item.dataset.mode;
    });

    analyzeBtn.addEventListener('click', async () => {
        if (currentMode !== 'server') {
            alert('目前仅支持服务器分析模式');
            return;
        }
        if (downloadMergeBtn) downloadMergeBtn.disabled = true;
        currentMergeFilePath = null;
        dependencyData = null;   // 清空旧的依赖数据

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
            // 1. 执行代码行数统计
            const resp = await fetch(API_BASE + '/analyze?' + params.toString());
            progressFill.style.width = '80%';
            const data = await resp.json();
            if (data.error) {
                alert('分析失败：' + data.error);
                return;
            }
            renderResults(data);

            if (data.mergeOutput && data.mergeOutput.trim() !== '') {
                currentMergeFilePath = data.mergeOutput;
                if (downloadMergeBtn) downloadMergeBtn.disabled = false;
            } else {
                currentMergeFilePath = null;
                if (downloadMergeBtn) downloadMergeBtn.disabled = true;
            }

            // 2. 执行依赖分析（异步，不阻塞界面）
            await loadDependencyData(params);

            progressFill.style.width = '100%';
            progressText.textContent = '分析完成';
            setTimeout(() => progressBar.style.display = 'none', 2000);
        } catch (e) {
            alert('请求失败：' + e.message);
            progressBar.style.display = 'none';
        }
    });

    resetBtn.addEventListener('click', loadDefaultConfig);

    function renderResults(data) {
        const files = data.files || [];
        fileTableBody.innerHTML = files.map(f => `
            <tr>
                <td title="${escapeHtml(f.path)}">${truncatePath(f.path)}</td>
                <td>${f.lines}</td>
                <td>${f.codeLines}</td>
                <td>${f.commentLines}</td>
                <td>${f.blankLines}</td>
                <td>${f.language}</td>
                <td>${f.complexity}</td>
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
        languageInfo.innerHTML = `
            <h3>语言分布</h3>
            ${Object.entries(langs).map(([lang, stat]) => `
                <div class="language-item">
                    <strong>${escapeHtml(lang)}</strong>:
                    文件 ${stat.files}, 行数 ${stat.lines}, 代码 ${stat.code}, 注释 ${stat.comments}, 空行 ${stat.blanks}
                </div>
            `).join('')}
        `;

        // 默认激活文件列表标签页
        tabButtons.forEach(btn => btn.classList.remove('active'));
        tabButtons[0].classList.add('active');
        filesTable.style.display = 'block';
        summaryTab.style.display = 'none';
        if (dependencyTab) dependencyTab.style.display = 'none';
    }

    // ========== 依赖分析相关功能 ==========

    async function loadDependencyData(params) {
        // 提取依赖分析所需的参数（与行数统计类似）
        const depParams = new URLSearchParams();
        depParams.append('rootDir', rootDirInput.value);
        depParams.append('extensions', extensionsInput.value);
        depParams.append('excludePaths', excludePathsInput.value);
        depParams.append('extraPaths', extraPathsInput.value);
        depParams.append('threadCount', threadCountInput.value);
        // 排除注释参数对依赖分析无影响，但保留一致性
        depParams.append('excludeComments', excludeCommentsCheck.checked);

        try {
            const resp = await fetch(API_BASE + '/dependencies?' + depParams.toString());
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            const data = await resp.json();
            if (data.error) {
                console.warn('依赖分析失败：', data.error);
                if (depStatsDiv) depStatsDiv.innerHTML = `<div class="error-tip">依赖分析失败: ${data.error}</div>`;
                return;
            }
            dependencyData = data;
            classInfoMap = data.classInfoMap || {};
            renderDependencyStats(data);
            populateClassSelect(classInfoMap);
            // 如果当前激活的是依赖标签页，则刷新下拉框和依赖详情
            if (dependencyTab && dependencyTab.style.display !== 'none') {
                bindClassSelectEvent();
                // 如果有默认选中的类，触发一次
                if (classSelect && classSelect.value) {
                    loadDependenciesForClass(classSelect.value);
                }
            }
        } catch (err) {
            console.error('依赖分析请求失败', err);
            if (depStatsDiv) depStatsDiv.innerHTML = `<div class="error-tip">依赖分析请求失败: ${err.message}</div>`;
        }
    }

    function renderDependencyStats(data) {
        if (!depStatsDiv) return;
        const cycles = data.cycles || [];
        depStatsDiv.innerHTML = `
            <h3>📊 依赖统计</h3>
            <div style="display: flex; gap: 20px; flex-wrap: wrap;">
                <div><strong>总类数:</strong> ${data.totalClasses || 0}</div>
                <div><strong>平均出向依赖数:</strong> ${(data.averageOutgoingDependencies || 0).toFixed(2)}</div>
                <div><strong>最大出向依赖数:</strong> ${data.maxOutgoingDependencies || 0} 
                    (类: ${(data.maxOutgoingClasses || []).join(', ') || '无'})</div>
                <div><strong>循环依赖组数:</strong> ${cycles.length}</div>
            </div>
            ${cycles.length ? `
                <details style="margin-top: 12px;">
                    <summary>🔍 查看循环依赖组</summary>
                    <pre style="background: #f1f5f9; padding: 10px; border-radius: 12px; overflow-x: auto;">${JSON.stringify(cycles, null, 2)}</pre>
                </details>
            ` : ''}
            <hr style="margin: 16px 0; border-color: rgba(0,102,255,0.2);">
        `;
    }

    function populateClassSelect(classMap) {
        if (!classSelect) return;
        const classNames = Object.keys(classMap).sort();
        if (classNames.length === 0) {
            classSelect.innerHTML = '<option value="">无类信息</option>';
            return;
        }
        let options = '<option value="">请选择类</option>';
        for (const fqName of classNames) {
            const info = classMap[fqName];
            const display = `${info.simpleName} (${info.packageName || 'default'})`;
            options += `<option value="${escapeHtml(fqName)}">${escapeHtml(display)}</option>`;
        }
        classSelect.innerHTML = options;
        // 绑定 change 事件（避免重复绑定）
        classSelect.removeEventListener('change', classSelectChangeHandler);
        classSelect.addEventListener('change', classSelectChangeHandler);
    }

    function classSelectChangeHandler(e) {
        const className = e.target.value;
        if (className) {
            loadDependenciesForClass(className);
        } else {
            if (outgoingList) outgoingList.innerHTML = '<li>请选择一个类</li>';
            if (incomingList) incomingList.innerHTML = '<li>请选择一个类</li>';
        }
    }

    function bindClassSelectEvent() {
        // 确保已经绑定了事件，在 populateClassSelect 中已做，此函数可留空或用于手动触发
        if (classSelect && classSelect.value) {
            loadDependenciesForClass(classSelect.value);
        }
    }

    async function loadDependenciesForClass(className) {
        if (!className) return;
        try {
            const [outgoingResp, incomingResp] = await Promise.all([
                fetch(API_BASE + `/dependencies/outgoing?className=${encodeURIComponent(className)}`),
                fetch(API_BASE + `/dependencies/incoming?className=${encodeURIComponent(className)}`)
            ]);
            const outgoingData = await outgoingResp.json();
            const incomingData = await incomingResp.json();
            renderDependencyDetails(outgoingData.outgoing || [], incomingData.incoming || []);
        } catch (err) {
            console.error('加载类依赖详情失败', err);
            if (outgoingList) outgoingList.innerHTML = '<li>加载失败</li>';
            if (incomingList) incomingList.innerHTML = '<li>加载失败</li>';
        }
    }

    function renderDependencyDetails(outgoing, incoming) {
        if (outgoingList) {
            outgoingList.innerHTML = outgoing.length ? outgoing.map(dep => `<li>${escapeHtml(dep)}</li>`).join('') : '<li>无</li>';
        }
        if (incomingList) {
            incomingList.innerHTML = incoming.length ? incoming.map(dep => `<li>${escapeHtml(dep)}</li>`).join('') : '<li>无</li>';
        }
    }

    // ========== 标签页切换逻辑（增加依赖标签页） ==========
    function switchTab(tabName) {
        // 隐藏所有内容
        filesTable.style.display = 'none';
        summaryTab.style.display = 'none';
        if (dependencyTab) dependencyTab.style.display = 'none';

        if (tabName === 'files') {
            filesTable.style.display = 'block';
        } else if (tabName === 'summary') {
            summaryTab.style.display = 'block';
        } else if (tabName === 'dependency') {
            if (dependencyTab) dependencyTab.style.display = 'block';
            // 如果依赖数据尚未加载，但已经有分析参数，尝试加载（通常在 analyze 后已有数据）
            if (!dependencyData && rootDirInput.value) {
                const params = new URLSearchParams();
                params.append('rootDir', rootDirInput.value);
                params.append('extensions', extensionsInput.value);
                params.append('excludePaths', excludePathsInput.value);
                params.append('extraPaths', extraPathsInput.value);
                params.append('threadCount', threadCountInput.value);
                loadDependencyData(params);
            } else if (dependencyData && classInfoMap && Object.keys(classInfoMap).length > 0 && classSelect.options.length <= 1) {
                populateClassSelect(classInfoMap);
            }
        }
        // 高亮当前 tab
        tabButtons.forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === tabName);
        });
    }

    // 绑定标签页点击事件
    tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            const target = btn.dataset.tab;
            if (target) switchTab(target);
        });
    });

    // ========== 原有辅助函数 ==========
    function truncatePath(path) {
        return path.length > 60 ? '...' + path.slice(-57) : path;
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, function(m) {
            if (m === '&') return '&amp;';
            if (m === '<') return '&lt;';
            if (m === '>') return '&gt;';
            return m;
        });
    }

    function exportToCsv() {
        const rows = document.querySelectorAll('#fileTableBody tr');
        if (!rows.length) {
            alert('没有数据可导出，请先执行分析');
            return;
        }
        const headers = [];
        const thList = document.querySelector('#filesTable table thead tr')?.querySelectorAll('th');
        if (thList) {
            thList.forEach(th => headers.push(th.innerText.trim()));
        } else {
            headers.push('文件路径', '总行数', '代码行', '注释行', '空行', '语言', '复杂度');
        }
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
        link.setAttribute('download', `code_stats_${new Date().toISOString().slice(0,19).replace(/:/g, '-')}.csv`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    async function downloadMergeFile() {
        if (!currentMergeFilePath) {
            alert('没有可用的合并文件，请启用“文件合并”并重新分析。');
            return;
        }
        let encodedPath;
        try {
            const urlEncoded = encodeURIComponent(currentMergeFilePath);
            encodedPath = btoa(urlEncoded);
        } catch (e) {
            console.error('编码失败', e);
            alert('文件路径编码失败');
            return;
        }
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
            const disposition = response.headers.get('Content-Disposition');
            if (disposition && disposition.includes('filename=')) {
                const match = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
                if (match && match[1]) {
                    filename = match[1].replace(/['"]/g, '');
                }
            }
            link.href = url;
            link.setAttribute('download', filename);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);
        } catch (err) {
            alert('下载合并文件失败：' + err.message);
        }
    }

    if (exportCsvBtn) exportCsvBtn.addEventListener('click', exportToCsv);
    if (downloadMergeBtn) downloadMergeBtn.addEventListener('click', downloadMergeFile);

    // 初始化加载默认配置
    loadDefaultConfig();
})();