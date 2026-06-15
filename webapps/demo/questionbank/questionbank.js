(function() {
    try {
        document.body.style.height = '100%';
        document.documentElement.style.height = '100%';

        const API_BASE = window.apiUrl('/questionbank/api');
        let currentMergedFile = null;      // 当前合并文件路径
        let currentKeyword = '';            // 当前搜索关键词
        let currentPage = 1;                // 当前页码
        let pageSize = 20;                  // 每页条数
        let totalRecords = 0;               // 总记录数

        // DOM 元素
        const rootDirInput = document.getElementById('rootDir');
        const keywordInput = document.getElementById('keyword');
        const namePrefixesInput = document.getElementById('namePrefixes');
        const outputFileInput = document.getElementById('outputFile');
        const mergeBtn = document.getElementById('mergeBtn');
        const resetBtn = document.getElementById('resetBtn');
        const progressArea = document.getElementById('mergeProgress');
        const progressFill = document.querySelector('#mergeProgress .progress-fill');
        const progressText = document.querySelector('#mergeProgress .progress-text');
        const mergeResultCard = document.getElementById('mergeResultCard');
        const mergeSummary = document.getElementById('mergeSummary');
        const fileListUl = document.getElementById('fileList');
        const downloadMergedBtn = document.getElementById('downloadMergedBtn');
        const searchKeyword = document.getElementById('searchKeyword');
        const searchBtn = document.getElementById('searchBtn');
        const searchResultDiv = document.getElementById('searchResult');
        const tableSearchInput = document.getElementById('tableSearchInput');
        const searchTableBtn = document.getElementById('searchTableBtn');
        const resetTableBtn = document.getElementById('resetTableBtn');
        const previewTableHead = document.getElementById('previewTableHead');
        const previewTableBody = document.getElementById('previewTableBody');
        const previewPagination = document.getElementById('previewPagination');
        const tabBtns = document.querySelectorAll('.tab-btn');
        const tabPanes = document.querySelectorAll('.tab-pane');
        const resultPanePlaceholder = document.getElementById('resultPanePlaceholder');

        // ---------- Tab 切换 ----------
        function activateTab(tabId) {
            tabBtns.forEach(btn => {
                btn.classList.toggle('active', btn.dataset.tab === tabId);
            });
            tabPanes.forEach(pane => {
                pane.classList.toggle('active', pane.id === `tab${tabId.charAt(0).toUpperCase() + tabId.slice(1)}Pane`);
            });
            // 切换到预览时重新加载数据（保留当前搜索关键词和页码）
            if (tabId === 'preview' && currentMergedFile) {
                loadPreviewData(currentKeyword, currentPage);
            }
        }

        tabBtns.forEach(btn => {
            btn.addEventListener('click', () => activateTab(btn.dataset.tab));
        });

        // 将合并结果卡片内容移动到结果Tab
        function showMergeResultInTab() {
            if (resultPanePlaceholder && mergeResultCard) {
                resultPanePlaceholder.innerHTML = mergeResultCard.innerHTML;
                const newDownloadBtn = resultPanePlaceholder.querySelector('#downloadMergedBtn');
                if (newDownloadBtn) {
                    newDownloadBtn.onclick = () => {
                        if (!currentMergedFile) return;
                        const url = window.apiUrl(`/questionbank/api/download?file=${encodeURIComponent(currentMergedFile)}&t=${Date.now()}`);
                        window.open(url, '_blank');
                    };
                }
                mergeResultCard.style.display = 'none';
            }
        }

        // 加载默认配置
        async function loadDefaultConfig() {
            try {
                const resp = await fetch(API_BASE + '/config');
                if (resp.ok) {
                    const config = await resp.json();
                    if (config.rootDir) rootDirInput.value = config.rootDir;
                    if (config.outputFile) outputFileInput.value = config.outputFile;
                    if (config.keyword !== undefined) keywordInput.value = config.keyword;
                    if (config.namePrefixes !== undefined) namePrefixesInput.value = config.namePrefixes;
                } else {
                    rootDirInput.value = 'D:/题库';
                    outputFileInput.value = './merged_' + Date.now() + '.txt';
                }
            } catch (err) {
                console.warn('加载默认配置失败', err);
                rootDirInput.value = 'D:/题库';
                outputFileInput.value = './merged_' + Date.now() + '.txt';
            }
        }

        function showMsg(msg, isError = false) {
            const toast = document.createElement('div');
            toast.style.position = 'fixed';
            toast.style.bottom = '20px';
            toast.style.right = '20px';
            toast.style.backgroundColor = isError ? '#dc2626' : '#22c55e';
            toast.style.color = 'white';
            toast.style.padding = '10px 20px';
            toast.style.borderRadius = '40px';
            toast.style.zIndex = '9999';
            toast.textContent = msg;
            document.body.appendChild(toast);
            setTimeout(() => toast.remove(), 3000);
        }

        function setProgress(percent, text) {
            progressArea.style.display = 'block';
            progressFill.style.width = percent + '%';
            progressText.textContent = text;
            if (percent >= 100) {
                setTimeout(() => {
                    progressArea.style.display = 'none';
                }, 1500);
            }
        }

        // ========== 加载预览数据（调用后端 /preview 接口） ==========
        async function loadPreviewData(keyword, page) {
            if (!currentMergedFile) {
                showMsg('请先执行合并操作', true);
                return;
            }
            const url = `${API_BASE}/preview?keyword=${encodeURIComponent(keyword || '')}&page=${page}&pageSize=${pageSize}`;
            try {
                const resp = await fetch(url);
                const data = await resp.json();
                if (data.success) {
                    totalRecords = data.total;
                    currentPage = data.page;
                    currentKeyword = keyword || '';
                    renderPreviewTable(data.columns, data.rows);
                    renderPagination(data.total, data.page);
                } else {
                    showMsg(data.error || '加载预览数据失败', true);
                    previewTableHead.innerHTML = '';
                    previewTableBody.innerHTML = '<tr><td colspan="10" style="text-align:center;">加载失败</td></tr>';
                    if (previewPagination) previewPagination.innerHTML = '';
                }
            } catch (err) {
                console.error('预览数据加载失败', err);
                showMsg('预览数据加载失败: ' + err.message, true);
            }
        }

        function renderPreviewTable(columns, rows) {
            if (!columns || columns.length === 0) {
                previewTableHead.innerHTML = '';
                previewTableBody.innerHTML = '<tr><td colspan="10" style="text-align:center;">无数据</td></tr>';
                return;
            }
            // 表头
            previewTableHead.innerHTML = '<tr>' + columns.map(col => `<th>${escapeHtml(col)}</th>`).join('') + '</tr>';
            // 表体
            if (!rows || rows.length === 0) {
                previewTableBody.innerHTML = '<tr><td colspan="' + columns.length + '" style="text-align:center;">暂无匹配数据</td></tr>';
                return;
            }
            let bodyHtml = '';
            for (const row of rows) {
                bodyHtml += '<tr>';
                for (const col of columns) {
                    let val = row[col];
                    if (val === undefined || val === null) val = '';
                    bodyHtml += `<td title="${escapeHtml(String(val))}">${escapeHtml(String(val).substring(0, 100))}</td>`;
                }
                bodyHtml += '</tr>';
            }
            previewTableBody.innerHTML = bodyHtml;
        }

        function renderPagination(total, currentPageNum) {
            if (!previewPagination) return;
            const totalPages = Math.ceil(total / pageSize);
            if (totalPages <= 1) {
                previewPagination.innerHTML = '';
                return;
            }
            let html = '';
            // 上一页
            if (currentPageNum > 1) {
                html += `<button data-page="${currentPageNum - 1}">上一页</button>`;
            }
            // 页码（简单显示前后3页）
            let startPage = Math.max(1, currentPageNum - 2);
            let endPage = Math.min(totalPages, currentPageNum + 2);
            for (let i = startPage; i <= endPage; i++) {
                if (i === currentPageNum) {
                    html += `<button class="active" disabled>${i}</button>`;
                } else {
                    html += `<button data-page="${i}">${i}</button>`;
                }
            }
            // 下一页
            if (currentPageNum < totalPages) {
                html += `<button data-page="${currentPageNum + 1}">下一页</button>`;
            }
            previewPagination.innerHTML = html;

            // 绑定事件
            previewPagination.querySelectorAll('button[data-page]').forEach(btn => {
                btn.addEventListener('click', () => {
                    const newPage = parseInt(btn.dataset.page);
                    if (!isNaN(newPage) && newPage !== currentPageNum) {
                        loadPreviewData(currentKeyword, newPage);
                    }
                });
            });
        }

        // ========== 合并文件逻辑 ==========
        async function mergeFiles() {
            const rootDir = rootDirInput.value.trim();
            const keyword = keywordInput.value.trim();
            const namePrefixes = namePrefixesInput.value.trim();
            let outputFile = outputFileInput.value.trim();
            if (!rootDir) {
                showMsg('请输入根目录', true);
                return;
            }
            if (!outputFile) {
                outputFile = `./merged_${Date.now()}.txt`;
                outputFileInput.value = outputFile;
            }
            setProgress(10, '正在扫描 Excel 文件...');
            try {
                const resp = await fetch(API_BASE + '/merge', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ rootDir, keyword, namePrefixes, outputFile })
                });
                const data = await resp.json();
                if (data.success) {
                    setProgress(100, '合并完成！');
                    showMsg(`合并成功！共 ${data.fileCount} 个文件，${data.totalLines} 行`);
                    currentMergedFile = data.mergedFile;
                    displayMergeResult(data);
                    mergeResultCard.style.display = 'block';
                    showMergeResultInTab();
                    // 重置预览状态
                    currentKeyword = '';
                    currentPage = 1;
                    totalRecords = 0;
                    // 自动切换到预览 Tab 并加载第一页数据
                    activateTab('preview');
                } else {
                    setProgress(0, '合并失败');
                    showMsg(data.error, true);
                }
            } catch (err) {
                setProgress(0, '请求异常');
                showMsg(err.message, true);
            }
        }

        function displayMergeResult(data) {
            mergeSummary.innerHTML = `
                <p><strong>合并文件：</strong> ${data.mergedFile}</p>
                <p><strong>文件数：</strong> ${data.fileCount}</p>
                <p><strong>总行数（含头部）：</strong> ${data.totalLines}</p>
            `;
            fileListUl.innerHTML = data.files.map(f => `<li>${escapeHtml(f)}</li>`).join('');
            downloadMergedBtn.onclick = () => {
                if (!currentMergedFile) return;
                const url = window.apiUrl(`/questionbank/api/download?file=${encodeURIComponent(currentMergedFile)}&t=${Date.now()}`);
                window.open(url, '_blank');
            };
        }

        // 按题目关键词搜索（基于后端 /search 接口）
        async function search() {
            const keyword = searchKeyword.value.trim();
            if (!keyword) {
                searchResultDiv.innerHTML = '<div class="empty-tip">请输入题目关键词</div>';
                return;
            }
            searchResultDiv.innerHTML = '<div class="empty-tip">搜索中...</div>';
            try {
                const resp = await fetch(API_BASE + `/search?keyword=${encodeURIComponent(keyword)}`);
                const data = await resp.json();
                if (data.success && data.matches.length) {
                    let html = '';
                    data.matches.forEach(m => {
                        html += `<div class="match-item"><strong>行 ${m.lineNumber}:</strong> ${escapeHtml(m.content)}</div>`;
                    });
                    searchResultDiv.innerHTML = html;
                } else {
                    searchResultDiv.innerHTML = '<div class="empty-tip">未找到匹配的题目</div>';
                }
            } catch (err) {
                searchResultDiv.innerHTML = `<div class="empty-tip">搜索失败: ${err.message}</div>`;
            }
        }

        // 重置所有表单
        function resetForm() {
            rootDirInput.value = 'D:/题库';
            keywordInput.value = '';
            namePrefixesInput.value = '';
            outputFileInput.value = '';
            mergeResultCard.style.display = 'none';
            if (resultPanePlaceholder) resultPanePlaceholder.innerHTML = '请先执行合并操作';
            searchResultDiv.innerHTML = '';
            searchKeyword.value = '';
            currentMergedFile = null;
            currentKeyword = '';
            currentPage = 1;
            totalRecords = 0;
            tableSearchInput.value = '';
            previewTableHead.innerHTML = '';
            previewTableBody.innerHTML = '';
            if (previewPagination) previewPagination.innerHTML = '';
            activateTab('result');
        }

        // 浏览根目录
        function selectDirectory(callback) {
            const input = document.createElement('input');
            input.type = 'file';
            input.webkitdirectory = true;
            input.directory = true;
            input.onchange = (e) => {
                const files = e.target.files;
                if (files.length) {
                    const path = files[0].webkitRelativePath.split('/')[0];
                    callback(path);
                }
            };
            input.click();
        }

        function selectOutputFile() {
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = '.txt';
            input.onchange = (e) => {
                const file = e.target.files[0];
                if (file) {
                    outputFileInput.value = file.name;
                }
            };
            input.click();
        }

        // 搜索预览（触发后端过滤）
        function searchPreview() {
            const kw = tableSearchInput.value.trim();
            loadPreviewData(kw, 1);
        }

        function resetPreview() {
            tableSearchInput.value = '';
            loadPreviewData('', 1);
        }

        // 事件绑定
        document.getElementById('browseBtn').addEventListener('click', () => {
            selectDirectory((dir) => { rootDirInput.value = dir; });
        });
        document.getElementById('selectOutputBtn').addEventListener('click', selectOutputFile);
        mergeBtn.addEventListener('click', mergeFiles);
        resetBtn.addEventListener('click', resetForm);
        searchBtn.addEventListener('click', search);
        searchKeyword.addEventListener('keypress', (e) => { if (e.key === 'Enter') search(); });
        searchTableBtn.addEventListener('click', searchPreview);
        resetTableBtn.addEventListener('click', resetPreview);
        tableSearchInput.addEventListener('keypress', (e) => { if (e.key === 'Enter') searchPreview(); });

        function escapeHtml(str) {
            if (!str) return '';
            return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
        }

        // 初始化
        loadDefaultConfig();
        activateTab('result');
    } catch(e) {
        console.error('QuestionBank page error:', e);
        document.body.innerHTML = '<div style="color:red;padding:20px;">页面加载失败，请查看控制台</div>';
    }
})();