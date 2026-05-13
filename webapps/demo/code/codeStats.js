(function() {
    const API_BASE = '/demo/code/api';

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

    // 新增按钮 DOM
    const exportCsvBtn = document.getElementById('exportCsvBtn');
    const downloadMergeBtn = document.getElementById('downloadMergeBtn');

    // 当前模式 & 合并文件路径
    let currentMode = 'server';
    let currentMergeFilePath = null;

    // 加载默认配置（包含 extraPaths）
    async function loadDefaultConfig() {
        try {
            const resp = await fetch(`${API_BASE}/config`);
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

    // 合并输出行显示/隐藏
    function toggleMergeRow() {
        mergeRow.style.display = mergeEnabledCheck.checked ? 'flex' : 'none';
    }
    mergeEnabledCheck.addEventListener('change', toggleMergeRow);

    // 模式切换
    modeList.addEventListener('click', (e) => {
        const item = e.target.closest('.mode-item');
        if (!item || item.classList.contains('disabled')) return;
        modeList.querySelectorAll('.mode-item').forEach(li => li.classList.remove('active'));
        item.classList.add('active');
        currentMode = item.dataset.mode;
    });

    // 执行分析
    analyzeBtn.addEventListener('click', async () => {
        if (currentMode !== 'server') {
            alert('目前仅支持服务器分析模式');
            return;
        }
        // 禁用下载按钮（新分析完成前不可用）
        if (downloadMergeBtn) downloadMergeBtn.disabled = true;
        currentMergeFilePath = null;

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

        // 显示进度条
        progressBar.style.display = 'block';
        progressFill.style.width = '10%';
        progressText.textContent = '正在分析...';

        try {
            const resp = await fetch(`${API_BASE}/analyze?${params.toString()}`);
            progressFill.style.width = '80%';
            const data = await resp.json();
            if (data.error) {
                alert('分析失败：' + data.error);
                return;
            }
            renderResults(data);

            // 处理合并文件路径（若后端返回）
            if (data.mergeOutput && data.mergeOutput.trim() !== '') {
                currentMergeFilePath = data.mergeOutput;
                if (downloadMergeBtn) downloadMergeBtn.disabled = false;
            } else {
                currentMergeFilePath = null;
                if (downloadMergeBtn) downloadMergeBtn.disabled = true;
            }

            progressFill.style.width = '100%';
            progressText.textContent = '分析完成';
            setTimeout(() => progressBar.style.display = 'none', 2000);
        } catch (e) {
            alert('请求失败：' + e.message);
            progressBar.style.display = 'none';
        }
    });

    // 重置配置
    resetBtn.addEventListener('click', loadDefaultConfig);

    // 渲染结果（文件列表 + 摘要 + 语言）
    function renderResults(data) {
        // 文件列表表格
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
            <tr>
        `).join('');

        // 摘要统计
        const summary = data.summary || {};
        summaryInfo.innerHTML = `
            <h3>总体统计</h3>
            <p>总文件数: ${summary.totalFiles || 0}, 总行数: ${summary.totalLines || 0}</p>
            <p>代码行: ${summary.totalCodeLines || 0}, 注释行: ${summary.totalCommentLines || 0}, 空行: ${summary.totalBlankLines || 0}</p>
            <p>耗时: ${summary.durationMs || 0} ms</p>
        `;

        // 语言分布
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

        // 切换到文件列表标签页
        tabButtons.forEach(btn => btn.classList.remove('active'));
        tabButtons[0].classList.add('active');
        filesTable.style.display = 'block';
        summaryTab.style.display = 'none';
    }

    // 辅助函数：路径截断
    function truncatePath(path) {
        return path.length > 60 ? '...' + path.slice(-57) : path;
    }

    // 辅助函数：防XSS
    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, function(m) {
            if (m === '&') return '&amp;';
            if (m === '<') return '&lt;';
            if (m === '>') return '&gt;';
            return m;
        });
    }

    // ================= 导出 CSV 功能 =================
    function exportToCsv() {
        const rows = document.querySelectorAll('#fileTableBody tr');
        if (!rows.length) {
            alert('没有数据可导出，请先执行分析');
            return;
        }

        // 获取表头（从页面表格 thead 中提取）
        const headers = [];
        const thList = document.querySelector('#filesTable table thead tr')?.querySelectorAll('th');
        if (thList) {
            thList.forEach(th => headers.push(th.innerText.trim()));
        } else {
            // fallback
            headers.push('文件路径', '总行数', '代码行', '注释行', '空行', '语言', '复杂度');
        }

        const csvRows = [];
        csvRows.push(headers.join(','));

        for (let row of rows) {
            const cells = row.querySelectorAll('td');
            const rowData = [];
            for (let cell of cells) {
                let text = cell.innerText.trim();
                // CSV 转义：如果包含逗号、换行或双引号，则用双引号包裹并转义内部双引号
                if (text.includes(',') || text.includes('"') || text.includes('\n')) {
                    text = `"${text.replace(/"/g, '""')}"`;
                }
                rowData.push(text);
            }
            csvRows.push(rowData.join(','));
        }

        // 添加 BOM 以支持中文
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

    // ================= 下载合并文件功能 =================
    async function downloadMergeFile() {
        if (!currentMergeFilePath) {
            alert('没有可用的合并文件，请启用“文件合并”并重新分析。');
            return;
        }

        // 对文件路径进行 Base64 编码 + URL 编码（与后端约定一致）
        let encodedPath;
        try {
            // 先 URL 编码，再 Base64
            const urlEncoded = encodeURIComponent(currentMergeFilePath);
            encodedPath = btoa(urlEncoded);
        } catch (e) {
            console.error('编码失败', e);
            alert('文件路径编码失败');
            return;
        }

        const downloadUrl = `${API_BASE}/download?filePath=${encodedPath}`;
        try {
            const response = await fetch(downloadUrl);
            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || '下载失败');
            }

            const blob = await response.blob();
            const link = document.createElement('a');
            const url = URL.createObjectURL(blob);
            // 从响应头 Content-Disposition 或路径中提取文件名
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

    // 绑定新增按钮事件（如果按钮存在）
    if (exportCsvBtn) exportCsvBtn.addEventListener('click', exportToCsv);
    if (downloadMergeBtn) downloadMergeBtn.addEventListener('click', downloadMergeFile);

    // 结果标签页切换
    resultTabs.addEventListener('click', (e) => {
        const tab = e.target.closest('.tab');
        if (!tab) return;
        tabButtons.forEach(btn => btn.classList.remove('active'));
        tab.classList.add('active');
        const target = tab.dataset.tab;
        filesTable.style.display = target === 'files' ? 'block' : 'none';
        summaryTab.style.display = target === 'summary' ? 'block' : 'none';
    });

    // 初始化
    loadDefaultConfig();
})();