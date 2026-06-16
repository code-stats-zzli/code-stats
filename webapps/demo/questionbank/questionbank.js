// ======== webapps/demo/questionbank/questionbank.js ========
(function() {
    try {
        document.body.style.height = '100%';
        document.documentElement.style.height = '100%';

        const API_BASE = window.apiUrl('/questionbank/api');
        let currentMergedFile = null;
        let currentKeyword = '';
        let currentPage = 1;
        let pageSize = 20;
        let totalRecords = 0;

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
        const exportExcelBtn = document.getElementById('exportExcelBtn');

        // ========== 浮窗相关 DOM ==========
        const ocrFab = document.getElementById('screenOcrFab');
        const ocrWidget = document.getElementById('screenOcrWidget');
        const closeOcrWidgetBtn = document.getElementById('closeOcrWidgetBtn');
        const startOcrSelectBtn = document.getElementById('startOcrSelectBtn');
        const ocrStatus = document.getElementById('ocrStatus');
        const ocrResultDiv = document.getElementById('ocrResult');
        const ocrRecognizedTextSpan = document.getElementById('ocrRecognizedText');
        const ocrSearchBtn = document.getElementById('ocrSearchBtn');

        let currentRecognizedText = '';
        let lastCroppedCanvas = null;  // ★ 新增：保存最近一次裁剪的 Canvas

        function showOcrWidget() { ocrWidget.style.display = 'block'; }
        function hideOcrWidget() {
            ocrWidget.style.display = 'none';
            ocrStatus.innerHTML = '';
            ocrResultDiv.style.display = 'none';
            currentRecognizedText = '';
            lastCroppedCanvas = null;
        }
        if (ocrFab) ocrFab.addEventListener('click', showOcrWidget);
        if (closeOcrWidgetBtn) closeOcrWidgetBtn.addEventListener('click', hideOcrWidget);

        // ========== 屏幕识别核心逻辑（拖拽后直接识别，无确认按钮） ==========
        let isSelecting = false;
        let startX, startY, overlay = null, rectDiv = null;

        function cleanupSelection() {
            if (overlay) overlay.remove();
            if (rectDiv) rectDiv.remove();
            document.removeEventListener('mousedown', onMouseDown);
            document.removeEventListener('mousemove', onMouseMove);
            document.removeEventListener('mouseup', onMouseUp);
            const handler = window.__ocrEscHandler;
            if (handler) {
                document.removeEventListener('keydown', handler);
                window.__ocrEscHandler = null;
            }
        }

        function onMouseDown(e) {
            isSelecting = true;
            startX = e.clientX;
            startY = e.clientY;
            if (rectDiv) rectDiv.remove();
            rectDiv = document.createElement('div');
            rectDiv.className = 'selection-rect';
            rectDiv.style.left = startX + 'px';
            rectDiv.style.top = startY + 'px';
            rectDiv.style.width = '0px';
            rectDiv.style.height = '0px';
            document.body.appendChild(rectDiv);
        }

        function onMouseMove(e) {
            if (!isSelecting) return;
            const currentX = e.clientX;
            const currentY = e.clientY;
            const left = Math.min(startX, currentX);
            const top = Math.min(startY, currentY);
            const width = Math.abs(currentX - startX);
            const height = Math.abs(currentY - startY);
            rectDiv.style.left = left + 'px';
            rectDiv.style.top = top + 'px';
            rectDiv.style.width = width + 'px';
            rectDiv.style.height = height + 'px';
        }

        async function onMouseUp(e) {
            if (!isSelecting) return;
            isSelecting = false;
            const endX = e.clientX;
            const endY = e.clientY;
            const left = Math.min(startX, endX);
            const top = Math.min(startY, endY);
            const width = Math.abs(endX - startX);
            const height = Math.abs(endY - startY);

            if (width < 5 || height < 5) {
                cleanupSelection();
                ocrStatus.innerHTML = '<span style="color:#ef4444;">❌ 拖拽距离过小，请按住鼠标左键并拖拽出一个矩形区域</span>';
                return;
            }

            cleanupSelection();
            await captureAndOCR(left, top, width, height);
        }

        function startSelection() {
            if (overlay) cleanupSelection();
            ocrStatus.innerHTML = '🔍 请拖拽鼠标选择要识别的区域（拖拽完成后自动识别）...';
            overlay = document.createElement('div');
            overlay.className = 'screen-select-overlay';
            document.body.appendChild(overlay);

            document.addEventListener('mousedown', onMouseDown);
            document.addEventListener('mousemove', onMouseMove);
            document.addEventListener('mouseup', onMouseUp);

            const escHandler = (e) => {
                if (e.key === 'Escape') {
                    cleanupSelection();
                    ocrStatus.innerHTML = '已取消选择';
                    document.removeEventListener('keydown', escHandler);
                    window.__ocrEscHandler = null;
                }
            };
            window.__ocrEscHandler = escHandler;
            document.addEventListener('keydown', escHandler);
        }

        async function captureAndOCR(left, top, width, height) {
            const loadingMask = document.createElement('div');
            loadingMask.className = 'loading-mask';
            loadingMask.innerHTML = '<div>⏳ 正在识别文字，请稍候...</div>';
            document.body.appendChild(loadingMask);
            ocrStatus.innerHTML = '⏳ 正在识别...';

            try {
                const canvas = await html2canvas(document.body, {
                    scale: 2,
                    logging: false,
                    useCORS: true,
                    windowWidth: document.documentElement.scrollWidth,
                    windowHeight: document.documentElement.scrollHeight
                });
                const croppedCanvas = document.createElement('canvas');
                const ctx = croppedCanvas.getContext('2d');
                croppedCanvas.width = width;
                croppedCanvas.height = height;

                const scrollX = window.scrollX || window.pageXOffset;
                const scrollY = window.scrollY || window.pageYOffset;
                const docLeft = left + scrollX;
                const docTop = top + scrollY;

                ctx.drawImage(canvas, docLeft, docTop, width, height, 0, 0, width, height);

                // ★ 保存裁剪后的 Canvas 供下载使用
                lastCroppedCanvas = croppedCanvas;

                const blob = await new Promise(resolve => croppedCanvas.toBlob(resolve, 'image/png'));
                const formData = new FormData();
                formData.append('image', blob, 'screenshot.png');
                const resp = await fetch(API_BASE + '/ocr', { method: 'POST', body: formData });
                const result = await resp.json();
                loadingMask.remove();
                if (result.error) {
                    ocrStatus.innerHTML = `<span style="color:#ef4444;">❌ 识别失败: ${result.error}</span>`;
                    return;
                }
                const recognizedText = result.text || '';
                if (!recognizedText.trim()) {
                    ocrStatus.innerHTML = '<span style="color:#ef4444;">⚠️ 未识别到任何文字，请重试</span>';
                    return;
                }
                currentRecognizedText = recognizedText;
                ocrRecognizedTextSpan.innerText = recognizedText;
                ocrResultDiv.style.display = 'block';
                ocrStatus.innerHTML = '✅ 识别成功！可点击“立即搜索”或修改后搜索';

                // ★ 添加“保存截图”按钮
                let saveBtn = document.getElementById('saveScreenshotBtn');
                if (!saveBtn) {
                    saveBtn = document.createElement('button');
                    saveBtn.id = 'saveScreenshotBtn';
                    saveBtn.className = 'btn-secondary';
                    saveBtn.textContent = '💾 保存截图';
                    saveBtn.style.marginTop = '8px';
                    ocrResultDiv.appendChild(saveBtn);
                }
                saveBtn.onclick = () => {
                    if (!lastCroppedCanvas) return;
                    lastCroppedCanvas.toBlob(function(blob) {
                        const url = URL.createObjectURL(blob);
                        const a = document.createElement('a');
                        a.href = url;
                        a.download = `screenshot_${Date.now()}.png`;
                        a.click();
                        URL.revokeObjectURL(url);
                    }, 'image/png');
                };
            } catch (err) {
                loadingMask.remove();
                ocrStatus.innerHTML = `<span style="color:#ef4444;">❌ 截图或识别失败: ${err.message}</span>`;
                console.error(err);
            }
        }

        function searchWithRecognizedText() {
            if (!currentRecognizedText.trim()) {
                alert('没有可用的识别结果');
                return;
            }
            searchKeyword.value = currentRecognizedText;
            activateTab('result');
            searchBtn.click();
        }

        if (startOcrSelectBtn) startOcrSelectBtn.addEventListener('click', startSelection);
        if (ocrSearchBtn) ocrSearchBtn.addEventListener('click', searchWithRecognizedText);

        // ========== 原有题库逻辑 ==========
        function highlightKeyword(text, keyword) {
            if (!text || !keyword) return escapeHtml(text);
            const regex = new RegExp(`(${escapeRegex(keyword)})`, 'gi');
            return escapeHtml(text).replace(regex, '<mark style="background: #ffeb3b; color: #1e293b; padding: 0 2px; border-radius: 4px;">$1</mark>');
        }

        function escapeRegex(str) {
            return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        }

        function activateTab(tabId) {
            tabBtns.forEach(btn => {
                btn.classList.toggle('active', btn.dataset.tab === tabId);
            });
            tabPanes.forEach(pane => {
                pane.classList.toggle('active', pane.id === `tab${tabId.charAt(0).toUpperCase() + tabId.slice(1)}Pane`);
            });
            if (tabId === 'preview' && currentMergedFile) {
                loadPreviewData(currentKeyword, currentPage);
            } else if (tabId === 'preview' && !currentMergedFile) {
                previewTableHead.innerHTML = '';
                previewTableBody.innerHTML = '<tr><td colspan="10" style="text-align:center;">暂无数据，请先执行合并</td></tr>';
                if (previewPagination) previewPagination.innerHTML = '';
            }
        }

        tabBtns.forEach(btn => {
            btn.addEventListener('click', () => activateTab(btn.dataset.tab));
        });

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
            previewTableHead.innerHTML = '<tr>' + columns.map(col => `<th>${escapeHtml(col)}</th>`).join('') + '</tr>';
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
                    let fullText = escapeHtml(String(val));
                    let displayText = fullText;
                    if (displayText.length > 100) {
                        displayText = displayText.substring(0, 97) + '...';
                    }
                    bodyHtml += `<td title="${fullText.replace(/"/g, '&quot;')}">${displayText}</td>`;
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
            if (currentPageNum > 1) {
                html += `<button data-page="${currentPageNum - 1}">上一页</button>`;
            }
            let startPage = Math.max(1, currentPageNum - 2);
            let endPage = Math.min(totalPages, currentPageNum + 2);
            for (let i = startPage; i <= endPage; i++) {
                if (i === currentPageNum) {
                    html += `<button class="active" disabled>${i}</button>`;
                } else {
                    html += `<button data-page="${i}">${i}</button>`;
                }
            }
            if (currentPageNum < totalPages) {
                html += `<button data-page="${currentPageNum + 1}">下一页</button>`;
            }
            previewPagination.innerHTML = html;

            previewPagination.querySelectorAll('button[data-page]').forEach(btn => {
                btn.addEventListener('click', () => {
                    const newPage = parseInt(btn.dataset.page);
                    if (!isNaN(newPage) && newPage !== currentPageNum) {
                        loadPreviewData(currentKeyword, newPage);
                    }
                });
            });
        }

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
            setProgress(10, '正在扫描文件...');
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
                    currentKeyword = '';
                    currentPage = 1;
                    totalRecords = 0;
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

        async function search() {
            const keyword = searchKeyword.value.trim();
            if (!keyword) {
                searchResultDiv.innerHTML = '<div class="empty-tip">请输入题目关键词</div>';
                return;
            }
            if (!currentMergedFile) {
                searchResultDiv.innerHTML = '<div class="empty-tip">⚠️ 请先执行合并操作，再搜索题目</div>';
                return;
            }
            searchResultDiv.innerHTML = '<div class="empty-tip">🔍 搜索中...</div>';
            try {
                const resp = await fetch(API_BASE + `/search?keyword=${encodeURIComponent(keyword)}`);
                const data = await resp.json();
                if (data.success && data.row) {
                    const columns = data.columns || [];
                    const row = data.row;
                    let html = '<div class="search-result-card">';
                    html += '<div class="card-header">📌 匹配结果</div>';
                    html += '<div class="card-body">';
                    for (const col of columns) {
                        let value = row[col] || '';
                        if (value !== '') {
                            const highlightedValue = highlightKeyword(value, keyword);
                            html += `
                                <div class="result-field">
                                    <span class="field-label">${escapeHtml(col)}：</span>
                                    <span class="field-value">${highlightedValue}</span>
                                </div>
                            `;
                        }
                    }
                    html += '</div></div>';
                    searchResultDiv.innerHTML = html;
                } else {
                    searchResultDiv.innerHTML = '<div class="empty-tip">😞 未找到匹配的题目，请尝试其他关键词</div>';
                }
            } catch (err) {
                console.error('搜索失败', err);
                searchResultDiv.innerHTML = `<div class="empty-tip">❌ 搜索失败: ${err.message}</div>`;
            }
        }

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

        function searchPreview() {
            const kw = tableSearchInput.value.trim();
            loadPreviewData(kw, 1);
        }

        function resetPreview() {
            tableSearchInput.value = '';
            loadPreviewData('', 1);
        }

        if (exportExcelBtn) {
            exportExcelBtn.addEventListener('click', async () => {
                const keyword = tableSearchInput ? tableSearchInput.value.trim() : '';
                const url = API_BASE + '/exportExcel?keyword=' + encodeURIComponent(keyword);
                try {
                    const response = await fetch(url);
                    if (!response.ok) {
                        const text = await response.text();
                        throw new Error(text || `HTTP ${response.status}`);
                    }
                    const blob = await response.blob();
                    let filename = '题库数据.xlsx';
                    const disposition = response.headers.get('Content-Disposition');
                    if (disposition && disposition.includes('filename=')) {
                        const match = disposition.match(/filename\*=UTF-8''(.+)/);
                        if (match && match[1]) {
                            filename = decodeURIComponent(match[1]);
                        } else {
                            const simpleMatch = disposition.match(/filename="(.+)"/);
                            if (simpleMatch && simpleMatch[1]) filename = simpleMatch[1];
                        }
                    }
                    const downloadUrl = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = downloadUrl;
                    a.download = filename;
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    URL.revokeObjectURL(downloadUrl);
                } catch (err) {
                    alert('导出失败：' + err.message);
                }
            });
        }

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

        loadDefaultConfig();
        activateTab('result');
    } catch(e) {
        console.error('QuestionBank page error:', e);
        document.body.innerHTML = '<div style="color:red;padding:20px;">页面加载失败，请查看控制台</div>';
    }
})();