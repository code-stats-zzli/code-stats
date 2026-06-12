(function() {
    let spiderTaskId = null;
    let pollInterval = null;

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
        progressFill.style.width = '10%';
        progressText.innerText = '正在启动爬虫...';
        resultArea.style.display = 'none';

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
                if (status.state === 'COMPLETED') {
                    clearInterval(pollInterval);
                    progressFill.style.width = '100%';
                    progressText.innerText = `爬取完成！共获取 ${status.totalArticles} 篇文章`;
                    setTimeout(() => {
                        progressArea.style.display = 'none';
                        resultArea.style.display = 'block';
                    }, 1000);
                    renderResult(status);
                } else if (status.state === 'ERROR') {
                    clearInterval(pollInterval);
                    progressArea.style.display = 'none';
                    alert('爬取失败：' + status.errorMessage);
                } else {
                    const percent = Math.min(80, 10 + (status.totalArticles || 0) / 2);
                    progressFill.style.width = percent + '%';
                    progressText.innerText = `已获取 ${status.totalArticles || 0} 篇文章...`;
                }
            } catch (err) {
                console.error(err);
            }
        }, 2000);
    }

    function renderResult(status) {
        const author = status.authorInfo || {};
        authorInfoDiv.innerHTML = `
            <div class="author-avatar">👤</div>
            <div class="author-details">
                <h3>${escapeHtml(author.nickname || author.name || status.username)}</h3>
                <p>${escapeHtml(author.description || 'CSDN 博主')}</p>
                <p>📄 总文章数：${status.totalArticles}</p>
            </div>
        `;

        const articles = status.articles || [];
        if (articles.length === 0) {
            articleWrapper.innerHTML = '<p>未获取到文章</p>';
            return;
        }
        let html = `<table class="data-table"><thead><tr><th>标题</th><th>阅读数</th><th>操作</th></tr></thead><tbody>`;
        articles.forEach(art => {
            html += `<tr>
                <td><a href="${art.url}" target="_blank">${escapeHtml(art.title)}</a></td>
                <td>${art.viewCount || 0}</td>
                <td><button class="download-single" data-url="${art.url}" data-title="${escapeHtml(art.title)}">⬇️ 下载</button></td>
            </tr>`;
        });
        html += `</tbody></table>`;
        articleWrapper.innerHTML = html;

        if (status.mergedFilePath) {
            downloadMergeBtn.style.display = 'inline-block';
            downloadMergeBtn.onclick = () => {
                window.open(window.apiUrl(`/spider/api/download?taskId=${spiderTaskId}`), '_blank');
            };
        } else {
            downloadMergeBtn.style.display = 'none';
        }

        document.querySelectorAll('.download-single').forEach(btn => {
            btn.addEventListener('click', async (e) => {
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
        });
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }
})();