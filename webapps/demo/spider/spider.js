(function() {
    let spiderTaskId = null;
    let pollInterval = null;
    let articleTableBody = null;
    let processedUrls = new Set();

    const usernameInput = document.getElementById('username');
    const maxConcurrentInput = document.getElementById('maxConcurrent');
    const intervalMsInput = document.getElementById('intervalMs');
    const maxArticlesInput = document.getElementById('maxArticles');
    const maxTimeSecondsInput = document.getElementById('maxTimeSeconds');
    const mergeEnabledCheck = document.getElementById('mergeEnabled');
    const mergeFileNameRow = document.getElementById('mergeFileNameRow');
    const mergeFileNameInput = document.getElementById('mergeFileName');
    const startBtn = document.getElementById('startBtn');
    const resetBtn = document.getElementById('resetBtn');
    const progressArea = document.getElementById('progressArea');
    const progressFill = document.getElementById('progressFill');
    const progressText = document.getElementById('progressText');
    const resultArea = document.getElementById('resultArea');
    const authorInfoDiv = document.getElementById('authorInfo');
    const articleWrapper = document.getElementById('articleTableWrapper');
    const downloadMergeBtn = document.getElementById('downloadMergeBtn');

    // 实时显示元素
    const currentStepSpan = document.getElementById('currentStep');
    const currentUrlSpan = document.getElementById('currentUrl');
    const summaryInfoDiv = document.getElementById('summaryInfo');

    function toggleMergeFileName() {
        mergeFileNameRow.style.display = mergeEnabledCheck.checked ? 'flex' : 'none';
    }
    mergeEnabledCheck.addEventListener('change', toggleMergeFileName);
    toggleMergeFileName();

    resetBtn.addEventListener('click', () => {
        usernameInput.value = 'qq_41652036';
        maxConcurrentInput.value = '3';
        intervalMsInput.value = '1500';
        maxArticlesInput.value = '100';
        maxTimeSecondsInput.value = '0';
        mergeEnabledCheck.checked = true;
        toggleMergeFileName();
        mergeFileNameInput.value = 'merged.txt';
        if (pollInterval) clearInterval(pollInterval);
        spiderTaskId = null;
        progressArea.style.display = 'none';
        resultArea.style.display = 'none';
        processedUrls.clear();
        if (articleTableBody) articleTableBody.innerHTML = '';
        if (summaryInfoDiv) summaryInfoDiv.innerHTML = '';
        authorInfoDiv.innerHTML = '';
    });

    startBtn.addEventListener('click', async () => {
        const username = usernameInput.value.trim();
        if (!username) {
            alert('请输入 CSDN 用户名');
            return;
        }
        const maxConcurrent = parseInt(maxConcurrentInput.value);
        const intervalMs = parseInt(intervalMsInput.value);
        const mergeEnabled = mergeEnabledCheck.checked;
        const mergeFileName = mergeFileNameInput.value.trim() || 'merged.txt';
        const maxArticles = parseInt(maxArticlesInput.value);
        const maxTimeSeconds = parseInt(maxTimeSecondsInput.value);

        progressArea.style.display = 'block';
        progressFill.style.width = '5%';
        progressText.innerText = '正在启动爬虫...';
        resultArea.style.display = 'none';
        processedUrls.clear();

        try {
            const resp = await fetch(window.apiUrl('/spider/api/start'), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username,
                    maxConcurrent,
                    intervalMs,
                    mergeEnabled,
                    mergeFileName,
                    maxArticles,
                    maxTimeSeconds
                })
            });
            const data = await resp.json();
            if (data.taskId) {
                spiderTaskId = data.taskId;
                pollStatus();
            } else {
                alert('启动失败：' + (data.error || '未知错误'));
                progressArea.style.display = 'none';
            }
        } catch (err) {
            alert('请求异常：' + err.message);
            progressArea.style.display = 'none';
        }
    });

    function pollStatus() {
        if (pollInterval) clearInterval(pollInterval);
        pollInterval = setInterval(async () => {
            if (!spiderTaskId) {
                clearInterval(pollInterval);
                return;
            }
            try {
                const resp = await fetch(window.apiUrl(`/spider/api/status?taskId=${spiderTaskId}`));
                const status = await resp.json();
                // 更新进度条
                const maxArticles = parseInt(maxArticlesInput.value) || 100;
                const percent = status.state === 'COMPLETED' ? 100 :
                    (status.processedCount && maxArticles ? Math.min(95, (status.processedCount / maxArticles) * 100) : 10);
                progressFill.style.width = percent + '%';
                progressText.innerText = status.state === 'RUNNING' ?
                    `📥 已获取 ${status.processedCount || 0} 篇文章，${status.currentStep || '处理中'}...` :
                    (status.state === 'COMPLETED' ? `✅ 爬取完成！共 ${status.totalArticles} 篇文章` : `❌ 失败: ${status.errorMessage}`);

                // 更新实时步骤
                if (currentStepSpan) currentStepSpan.innerText = status.currentStep || '-';
                if (currentUrlSpan) currentUrlSpan.innerText = status.currentUrl || '-';

                // 实时更新文章列表（增量追加）
                if (status.articles && status.articles.length) {
                    appendNewArticles(status.articles);
                    updateSummaryInfo(status.articles, maxArticles);
                }

                if (status.state === 'COMPLETED') {
                    clearInterval(pollInterval);
                    resultArea.style.display = 'block';
                    // 显示作者信息（无头像，纯文字）
                    const author = status.authorInfo || {};
                    authorInfoDiv.innerHTML = `
                        <div class="author-details">
                            <h3>${escapeHtml(author.nickname || author.name || usernameInput.value)}</h3>
                            <p>${escapeHtml(author.description || 'CSDN 博主')}</p>
                            <div class="stats-mini">
                                <span class="stat-badge">📄 文章 ${status.totalArticles}</span>
                                <span class="stat-badge">⭐ 影响力 ${Math.min(100, status.totalArticles * 5)}</span>
                            </div>
                        </div>
                    `;
                    if (status.mergedFilePath) {
                        downloadMergeBtn.style.display = 'inline-block';
                        downloadMergeBtn.onclick = () => {
                            window.open(window.apiUrl(`/spider/api/download?taskId=${spiderTaskId}`), '_blank');
                        };
                    } else {
                        downloadMergeBtn.style.display = 'none';
                    }
                } else if (status.state === 'ERROR') {
                    clearInterval(pollInterval);
                    resultArea.style.display = 'block';
                    authorInfoDiv.innerHTML = `<div class="error">爬取失败: ${status.errorMessage}</div>`;
                }
            } catch (err) {
                console.error(err);
            }
        }, 1500);
    }

    // 增量添加新文章到表格
    function appendNewArticles(articles) {
        if (!articleTableBody) {
            articleWrapper.innerHTML = `<table class="data-table" id="articleTable">
                <thead><tr><th>标题</th><th>阅读数</th><th>操作</th></tr></thead>
                <tbody id="articleTableBody"></tbody>
            </table>`;
            articleTableBody = document.getElementById('articleTableBody');
        }
        for (let art of articles) {
            if (processedUrls.has(art.url)) continue;
            processedUrls.add(art.url);
            const row = articleTableBody.insertRow();
            row.insertCell(0).innerHTML = `<a href="${escapeAttr(art.url)}" target="_blank">${escapeHtml(art.title)}</a>`;
            row.insertCell(1).innerHTML = art.viewCount || 0;
            const btn = document.createElement('button');
            btn.className = 'download-single';
            btn.textContent = '⬇️ 下载';
            btn.dataset.url = art.url;
            btn.dataset.title = art.title;
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                const url = btn.dataset.url;
                const title = btn.dataset.title;
                try {
                    const res = await fetch(window.apiUrl(`/spider/api/article?taskId=${spiderTaskId}&url=${encodeURIComponent(url)}`));
                    if (!res.ok) throw new Error('下载失败');
                    const blob = await res.blob();
                    const a = document.createElement('a');
                    const objectUrl = URL.createObjectURL(blob);
                    a.href = objectUrl;
                    a.download = `${title.replace(/[\\/:*?\"<>|]/g, '_')}.txt`;
                    a.click();
                    URL.revokeObjectURL(objectUrl);
                } catch (err) {
                    alert('下载失败：' + err.message);
                }
            });
            row.insertCell(2).appendChild(btn);
        }
    }

    // 实时计算并显示摘要信息（带进度环图表）
    function updateSummaryInfo(articles, maxArticles) {
        if (!summaryInfoDiv) return;
        let totalChars = 0;
        let totalViews = 0;
        for (let art of articles) {
            if (art.content) totalChars += art.content.length;
            if (art.viewCount) totalViews += art.viewCount;
        }
        const count = articles.length;
        const avgViews = count > 0 ? (totalViews / count).toFixed(0) : 0;
        const percent = Math.min(100, (count / maxArticles) * 100);
        const angle = (percent / 100) * 360;
        summaryInfoDiv.innerHTML = `
            <div class="summary-stats">
                <span>📄 已获取: ${count}</span>
                <span>📝 总字数: ${totalChars.toLocaleString()}</span>
                <span>👀 平均阅读: ${avgViews}</span>
                <div class="progress-ring" style="background: conic-gradient(#0066ff 0deg ${angle}deg, #e2e8f0 ${angle}deg 360deg);"></div>
            </div>
        `;
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }
    function escapeAttr(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
})();