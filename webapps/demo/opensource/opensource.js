(function() {
    // 会话ID（保存到 sessionStorage 以便刷新后继续同一对话）
    let sessionId = sessionStorage.getItem('deepseek_session_id');
    if (!sessionId) {
        sessionId = 'web_' + Date.now() + '_' + Math.random().toString(36).substr(2, 8);
        sessionStorage.setItem('deepseek_session_id', sessionId);
    }

    const chatMessages = document.getElementById('chatMessages');
    const userInput = document.getElementById('userInput');
    const sendBtn = document.getElementById('sendBtn');
    const chatStatus = document.getElementById('chatStatus');

    // 添加消息到界面
    function appendMessage(role, content) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${role}`;
        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = role === 'user' ? '🧑‍🚀' : '🧠';
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        contentDiv.textContent = content;
        messageDiv.appendChild(avatar);
        messageDiv.appendChild(contentDiv);
        chatMessages.appendChild(messageDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    // 显示“正在输入”状态
    function showLoading() {
        const loadingDiv = document.createElement('div');
        loadingDiv.className = 'message assistant';
        loadingDiv.id = 'loadingMessage';
        loadingDiv.innerHTML = `<div class="message-avatar">🧠</div><div class="message-content loading-dots">思考中</div>`;
        chatMessages.appendChild(loadingDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function hideLoading() {
        const loading = document.getElementById('loadingMessage');
        if (loading) loading.remove();
    }

    function setStatus(text, isError = false) {
        chatStatus.textContent = text;
        chatStatus.style.color = isError ? '#dc2626' : 'var(--accent-primary)';
        if (!isError) {
            setTimeout(() => { if (chatStatus.textContent === text) chatStatus.textContent = ''; }, 3000);
        }
    }

    async function sendMessage() {
        const question = userInput.value.trim();
        if (!question) return;
        userInput.value = '';
        appendMessage('user', question);
        showLoading();
        setStatus('正在思考中...');

        try {
            const resp = await fetch(window.apiUrl('/deepseek/api/chat'), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    sessionId: sessionId,
                    question: question,
                    timeout: 45
                })
            });
            const data = await resp.json();
            hideLoading();
            if (data.success) {
                appendMessage('assistant', data.reply);
                setStatus('回复完成');
            } else {
                appendMessage('assistant', `❌ 后端错误：${data.error || '未知错误'}`);
                setStatus(data.error || '请求失败', true);
            }
        } catch (err) {
            hideLoading();
            appendMessage('assistant', `❌ 网络或服务异常：${err.message}`);
            setStatus('连接失败，请确保后端服务已启动', true);
        }
    }

    sendBtn.addEventListener('click', sendMessage);
    userInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    // 可选：在页面关闭前释放后端会话（非必须，后端会超时回收）
    window.addEventListener('beforeunload', () => {
        navigator.sendBeacon(window.apiUrl('/deepseek/api/close'), JSON.stringify({ sessionId }));
    });
})();