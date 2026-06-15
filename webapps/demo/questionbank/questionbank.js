(function() {
    try {
        document.body.style.height = '100%';
        document.documentElement.style.height = '100%';

        const API_BASE = window.apiUrl('/questionbank/api');
        let currentMergedFile = null;
        let fullTableData = [];      // 存储解析后的全部数据行（对象数组）
        let tableColumns = [];       // 列名数组

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
        const resultCard = document.querySelector('.qb-result');
        const previewCard = document.getElementById('previewCard');
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
                    // 降级默认值
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

        // 解析 CSV 行（支持引号包裹）
        function parseCSVLine(line) {
            const result = [];
            let inQuote = false;
            let current = '';
            for (let i = 0; i < line.length; i++) {
                const ch = line[i];
                if (ch === '"') {
                    inQuote = !inQuote;
                } else if (ch === ',' && !inQuote) {
                    result.push(current);
                    current = '';
                } else {
                    current += ch;
                }
            }
            result.push(current);
            return result.map(cell => cell.trim());
        }

        // 从合并文件内容解析表格数据
        async function loadAndDisplayTable(mergedFilePath) {
            try {
                const resp = await fetch(mergedFilePath);
                if (!resp.ok) throw new Error('获取合并文件失败');
                const content = await resp.text();
                const lines = content.split(/\r?\n/);
                // 过滤掉以 // 开头的注释行
                const dataLines = lines.filter(line => !line.trim().startsWith('//') && line.trim().length > 0);
                if (dataLines.length === 0) {
                    showMsg('合并文件中无有效数据行', true);
                    return;
                }
                // 第一行作为表头
                const headerRow = dataLines[0];
                const columns = parseCSVLine(headerRow);
                tableColumns = columns;
                // 解析数据行
                const rows = [];
                for (let i = 1; i < dataLines.length; i++) {
                    const values = parseCSVLine(dataLines[i]);
                    if (values.length === 0) continue;
                    const rowObj = {};
                    columns.forEach((col, idx) => {
                        rowObj[col] = idx < values.length ? values[idx] : '';
                    });
                    rows.push(rowObj);
                }
                fullTableData = rows;
                renderTable(rows);
                previewCard.style.display = 'block';
            } catch (err) {
                console.error('解析表格失败', err);
                showMsg('预览表格失败: ' + err.message, true);
            }
        }

        // 渲染表格
        function renderTable(dataRows) {
            if (!previewTableHead || !previewTableBody) return;
            if (!tableColumns.length) {
                previewCard.style.display = 'none';
                return;
            }
            // 渲染表头
            previewTableHead.innerHTML = '<tr>' + tableColumns.map(col => `<th>${escapeHtml(col)}</th>`).join('') + '</tr>';
            // 渲染数据行
            if (dataRows.length === 0) {
                previewTableBody.innerHTML = '<tr><td colspan="' + tableColumns.length + '" style="text-align:center;">无匹配数据</td></tr>';
                return;
            }
            previewTableBody.innerHTML = dataRows.map(row => {
                return '<tr>' + tableColumns.map(col => `<td>${escapeHtml(row[col] || '')}</td>`).join('') + '</tr>';
            }).join('');
        }

        // 模糊搜索表格（在所有列中匹配）
        function filterTable() {
            const keyword = tableSearchInput.value.trim().toLowerCase();
            if (!keyword) {
                renderTable(fullTableData);
                return;
            }
            const filtered = fullTableData.filter(row => {
                return Object.values(row).some(cell => String(cell).toLowerCase().includes(keyword));
            });
            renderTable(filtered);
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
                    resultCard.style.display = 'block';
                    // 加载并展示表格
                    if (currentMergedFile) {
                        await loadAndDisplayTable(currentMergedFile);
                    }
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
                window.open(window.apiUrl(`/questionbank/api/download?file=${encodeURIComponent(currentMergedFile)}`), '_blank');
            };
        }

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

        function resetForm() {
            rootDirInput.value = 'D:/题库';
            keywordInput.value = '';
            namePrefixesInput.value = '';
            outputFileInput.value = '';
            resultCard.style.display = 'none';
            previewCard.style.display = 'none';
            searchResultDiv.innerHTML = '';
            searchKeyword.value = '';
            currentMergedFile = null;
            fullTableData = [];
            tableColumns = [];
            tableSearchInput.value = '';
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

        // 事件绑定
        document.getElementById('browseBtn').addEventListener('click', () => {
            selectDirectory((dir) => {
                rootDirInput.value = dir;
            });
        });
        document.getElementById('selectOutputBtn').addEventListener('click', selectOutputFile);
        mergeBtn.addEventListener('click', mergeFiles);
        resetBtn.addEventListener('click', resetForm);
        searchBtn.addEventListener('click', search);
        searchKeyword.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') search();
        });
        searchTableBtn.addEventListener('click', filterTable);
        resetTableBtn.addEventListener('click', () => {
            tableSearchInput.value = '';
            renderTable(fullTableData);
        });
        tableSearchInput.addEventListener('keyup', (e) => {
            if (e.key === 'Enter') filterTable();
        });

        function escapeHtml(str) {
            if (!str) return '';
            return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
        }

        // 初始化加载默认配置
        loadDefaultConfig();
    } catch(e) {
        console.error('QuestionBank page error:', e);
        document.body.innerHTML = '<div style="color:red;padding:20px;">页面加载失败，请查看控制台</div>';
    }
})();