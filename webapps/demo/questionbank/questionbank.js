(function() {
    try {
        document.body.style.height = '100%';
        document.documentElement.style.height = '100%';

        const API_BASE = window.apiUrl('/questionbank/api');
        let currentMergedFile = null;

        const rootDirInput = document.getElementById('rootDir');
        const keywordInput = document.getElementById('keyword');
        const outputFileInput = document.getElementById('outputFile');
        const mergeBtn = document.getElementById('mergeBtn');
        const resetBtn = document.getElementById('resetBtn');
        const progressArea = document.getElementById('mergeProgress');
        const progressFill = document.querySelector('#mergeProgress .progress-fill');
        const progressText = document.querySelector('#mergeProgress .progress-text');
        const resultCard = document.querySelector('.qb-result');
        const mergeSummary = document.getElementById('mergeSummary');
        const fileListUl = document.getElementById('fileList');
        const downloadMergedBtn = document.getElementById('downloadMergedBtn');
        const searchKeyword = document.getElementById('searchKeyword');
        const searchBtn = document.getElementById('searchBtn');
        const searchResultDiv = document.getElementById('searchResult');

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

        async function mergeFiles() {
            const rootDir = rootDirInput.value.trim();
            const keyword = keywordInput.value.trim();
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
                    body: JSON.stringify({ rootDir, keyword, outputFile })
                });
                const data = await resp.json();
                if (data.success) {
                    setProgress(100, '合并完成！');
                    showMsg(`合并成功！共 ${data.fileCount} 个文件，${data.totalLines} 行`);
                    currentMergedFile = data.mergedFile;
                    displayMergeResult(data);
                    resultCard.style.display = 'block';
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
            outputFileInput.value = '';
            resultCard.style.display = 'none';
            searchResultDiv.innerHTML = '';
            searchKeyword.value = '';
            currentMergedFile = null;
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

        function escapeHtml(str) {
            if (!str) return '';
            return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
        }
    } catch(e) {
        console.error('QuestionBank page error:', e);
        document.body.innerHTML = '<div style="color:red;padding:20px;">页面加载失败，请查看控制台</div>';
    }
})();