// ======== D:\work\git\CodeStats\webapps\demo\spider\spider.js ========
(function() {
    let spiderTaskId = null;
    let pollInterval = null;
    let articleTableBody = null;
    let failedTableBody = null;
    let processedUrls = new Set();
    let latestAuthorInfo = null;  // 存储最新的作者信息，供详情弹窗使用

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
    const authorDetailsDiv = document.querySelector('#authorInfo .author-details');
    const downloadMergeBtn = document.getElementById('downloadMergeBtn');
    const showAuthorDetailBtn = document.getElementById('showAuthorDetailBtn');
    const successCountSpan = document.getElementById('successCount');
    const failedCountSpan = document.getElementById('failedCount');
    const currentStepSpan = document.getElementById('currentStep');
    const currentUrlSpan = document.getElementById('currentUrl');
    const summaryInfoDiv = document.getElementById('summaryInfo');

    const tabSuccessBtn = document.getElementById('tabSuccessBtn');
    const tabFailedBtn = document.getElementById('tabFailedBtn');
    const successPanel = document.getElementById('successPanel');
    const failedPanel = document.getElementById('failedPanel');

    // 弹窗元素
    const authorDetailModal = document.getElementById('authorDetailModal');
    const authorDetailBody = document.getElementById('authorDetailBody');
    const closeAuthorModalBtn = document.getElementById('closeAuthorModalBtn');
    const closeModalFooterBtn = document.getElementById('closeModalFooterBtn');

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
        if (failedTableBody) failedTableBody.innerHTML = '';
        if (summaryInfoDiv) summaryInfoDiv.innerHTML = '';
        if (authorDetailsDiv) authorDetailsDiv.innerHTML = '';
        downloadMergeBtn.style.display = 'none';
        showAuthorDetailBtn.style.display = 'none';
        successCountSpan.innerText = '0';
        failedCountSpan.innerText = '0';
        tabSuccessBtn.classList.add('active');
        tabFailedBtn.classList.remove('active');
        successPanel.style.display = 'block';
        failedPanel.style.display = 'none';
        latestAuthorInfo = null;
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
                const maxArticles = parseInt(maxArticlesInput.value) || 100;
                const percent = status.state === 'COMPLETED' ? 100 :
                    (status.processedCount && maxArticles ? Math.min(95, (status.processedCount / maxArticles) * 100) : 10);
                progressFill.style.width = percent + '%';
                progressText.innerText = status.state === 'RUNNING' ?
                    `📥 已成功 ${status.successCount || 0} 篇，失败 ${status.failedCount || 0} 篇，${status.currentStep || '处理中'}...` :
                    (status.state === 'COMPLETED' ? `✅ 爬取完成！成功 ${status.successCount} 篇，失败 ${status.failedCount} 篇` : `❌ 失败: ${status.errorMessage}`);

                if (currentStepSpan) currentStepSpan.innerText = status.currentStep || '-';
                if (currentUrlSpan) currentUrlSpan.innerText = status.currentUrl || '-';

                if (status.articles && status.articles.length) {
                    appendNewArticles(status.articles);
                }
                if (status.failedArticles && status.failedArticles.length) {
                    appendFailedArticles(status.failedArticles);
                }
                updateSummaryInfo(status);

                if (status.state === 'COMPLETED') {
                    clearInterval(pollInterval);
                    resultArea.style.display = 'block';
                    latestAuthorInfo = status.authorInfo || {};
                    renderAuthorBrief(latestAuthorInfo, status.successCount, status.failedCount);
                    if (status.mergedFilePath) {
                        downloadMergeBtn.style.display = 'inline-block';
                        downloadMergeBtn.onclick = () => {
                            window.open(window.apiUrl(`/spider/api/download?taskId=${spiderTaskId}`), '_blank');
                        };
                    } else {
                        downloadMergeBtn.style.display = 'none';
                    }
                    // 只有当作者信息中包含详细统计数据（如 totalVisits）时才显示详情按钮
                    if (latestAuthorInfo.totalVisits || latestAuthorInfo.originalCount || (latestAuthorInfo.columns && latestAuthorInfo.columns.length)) {
                        showAuthorDetailBtn.style.display = 'inline-block';
                    } else {
                        showAuthorDetailBtn.style.display = 'none';
                    }
                    successCountSpan.innerText = status.successCount || 0;
                    failedCountSpan.innerText = status.failedCount || 0;
                } else if (status.state === 'ERROR') {
                    clearInterval(pollInterval);
                    resultArea.style.display = 'block';
                    authorDetailsDiv.innerHTML = `<div class="error">爬取失败: ${status.errorMessage}</div>`;
                    downloadMergeBtn.style.display = 'none';
                    showAuthorDetailBtn.style.display = 'none';
                }
            } catch (err) {
                console.error(err);
            }
        }, 1500);
    }

    function renderAuthorBrief(author, successCount, failedCount) {
        const nickname = author.nickname || author.name || usernameInput.value;
        const description = author.description || 'CSDN 博主';
        let statsHtml = `
            <div>
                <h3>${escapeHtml(nickname)}</h3>
                <p>${escapeHtml(description)}</p>
                <div class="stats-mini">
                    <span class="stat-badge">📄 成功 ${successCount}</span>
                    <span class="stat-badge">❌ 失败 ${failedCount}</span>
                    <span class="stat-badge">⭐ 影响力 ${Math.min(100, successCount * 5)}</span>
        `;
        if (author.totalVisits) statsHtml += `<span class="stat-badge">👀 总访问 ${author.totalVisits}</span>`;
        if (author.originalCount) statsHtml += `<span class="stat-badge">✍️ 原创 ${author.originalCount}</span>`;
        if (author.fansCount) statsHtml += `<span class="stat-badge">👥 粉丝 ${author.fansCount}</span>`;
        statsHtml += `</div></div>`;
        authorDetailsDiv.innerHTML = statsHtml;
    }

    function showAuthorDetailModal() {
        if (!latestAuthorInfo) return;
        const author = latestAuthorInfo;
        let html = `<div style="font-size: 14px; line-height: 1.6;">`;
        html += `<p><strong>昵称：</strong> ${escapeHtml(author.nickname || author.name || '-')}</p>`;
        html += `<p><strong>简介：</strong> ${escapeHtml(author.description || '-')}</p>`;
        html += `<hr><h4>📊 统计信息</h4>`;
        html += `<ul style="columns: 2;">`;
        if (author.totalVisits) html += `<li>总访问量：${author.totalVisits}</li>`;
        if (author.originalCount) html += `<li>原创文章：${author.originalCount}</li>`;
        if (author.fansCount) html += `<li>粉丝数：${author.fansCount}</li>`;
        if (author.followsCount) html += `<li>关注数：${author.followsCount}</li>`;
        if (author.likesCount) html += `<li>点赞数：${author.likesCount}</li>`;
        if (author.commentsCount) html += `<li>评论数：${author.commentsCount}</li>`;
        if (author.collectionsCount) html += `<li>收藏数：${author.collectionsCount}</li>`;
        if (author.rank) html += `<li>博客排名：${author.rank}</li>`;
        html += `</ul>`;
        if (author.columns && author.columns.length) {
            html += `<hr><h4>📚 专栏列表</h4><ul>`;
            author.columns.forEach(col => {
                html += `<li>${escapeHtml(col.name)} (${col.articleCount || 0} 篇文章)</li>`;
            });
            html += `</ul>`;
        } else {
            html += `<hr><p>暂无专栏信息</p>`;
        }
        html += `</div>`;
        authorDetailBody.innerHTML = html;
        authorDetailModal.style.display = 'flex';
    }

    function closeModal() {
        authorDetailModal.style.display = 'none';
    }

    if (showAuthorDetailBtn) showAuthorDetailBtn.addEventListener('click', showAuthorDetailModal);
    if (closeAuthorModalBtn) closeAuthorModalBtn.addEventListener('click', closeModal);
    if (closeModalFooterBtn) closeModalFooterBtn.addEventListener('click', closeModal);
    window.addEventListener('click', (e) => {
        if (e.target === authorDetailModal) closeModal();
    });

    function appendNewArticles(articles) {
        if (!articleTableBody) {
            const wrapper = document.getElementById('articleTableWrapper');
            wrapper.innerHTML = `<table class="data-table" id="articleTable">
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
        successCountSpan.innerText = articleTableBody.rows.length;
    }

    function appendFailedArticles(failedArticles) {
        if (!failedTableBody) {
            failedTableBody = document.getElementById('failedTableBody');
        }
        for (let fail of failedArticles) {
            const existing = Array.from(failedTableBody.rows).some(row => row.cells[0].innerText === fail.url);
            if (existing) continue;
            const row = failedTableBody.insertRow();
            row.insertCell(0).innerHTML = `<a href="${escapeAttr(fail.url)}" target="_blank">${escapeHtml(fail.url)}</a>`;
            row.insertCell(1).innerText = fail.reason || '未知错误';
        }
        failedCountSpan.innerText = failedTableBody.rows.length;
    }

    function updateSummaryInfo(status) {
        if (!summaryInfoDiv) return;
        const success = status.successCount || 0;
        const failed = status.failedCount || 0;
        const total = success + failed;
        const percent = total > 0 ? (success / total) * 100 : 0;
        const angle = (percent / 100) * 360;
        summaryInfoDiv.innerHTML = `
            <div class="summary-stats">
                <span>📄 总文章数: ${total}</span>
                <span>✅ 成功: ${success}</span>
                <span>❌ 失败: ${failed}</span>
                <div class="progress-ring" style="background: conic-gradient(#22c55e 0deg ${angle}deg, #e2e8f0 ${angle}deg 360deg);"></div>
            </div>
        `;
    }

    function switchToSuccessTab() {
        tabSuccessBtn.classList.add('active');
        tabFailedBtn.classList.remove('active');
        successPanel.style.display = 'block';
        failedPanel.style.display = 'none';
    }
    function switchToFailedTab() {
        tabFailedBtn.classList.add('active');
        tabSuccessBtn.classList.remove('active');
        successPanel.style.display = 'none';
        failedPanel.style.display = 'block';
    }
    tabSuccessBtn.addEventListener('click', switchToSuccessTab);
    tabFailedBtn.addEventListener('click', switchToFailedTab);

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }
    function escapeAttr(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
})();