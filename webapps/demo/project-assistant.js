// 项目助手独立聊天模块
(function() {
    let sessionId = sessionStorage.getItem('project_assistant_session');
    if (!sessionId) {
        sessionId = 'proj_' + Date.now() + '_' + Math.random().toString(36).substr(2, 8);
        sessionStorage.setItem('project_assistant_session', sessionId);
    }

    // 缓存消息历史（仅用于前端展示，后端也维护一份）
    let messageHistory = [];

    // 获取 DOM 元素（在初始化时创建）
    let widget, fab, messagesContainer, inputText, sendBtn, statusSpan;

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    function appendMessage(role, content) {
        const msgDiv = document.createElement('div');
        msgDiv.className = `message ${role}`;
        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = role === 'user' ? '🧑' : '📘';
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        contentDiv.innerHTML = escapeHtml(content).replace(/\n/g, '<br>');
        msgDiv.appendChild(avatar);
        msgDiv.appendChild(contentDiv);
        messagesContainer.appendChild(msgDiv);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    function setStatus(text, isError = false) {
        if (statusSpan) {
            statusSpan.textContent = text;
            statusSpan.style.color = isError ? '#dc2626' : 'var(--accent-primary)';
            setTimeout(() => {
                if (statusSpan.textContent === text) statusSpan.textContent = '';
            }, 3000);
        }
    }

    function showLoading() {
        const loadingDiv = document.createElement('div');
        loadingDiv.className = 'message assistant';
        loadingDiv.id = 'assistant-loading';
        loadingDiv.innerHTML = `<div class="message-avatar">📘</div><div class="message-content loading-dots">思考中</div>`;
        messagesContainer.appendChild(loadingDiv);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    function hideLoading() {
        const loading = document.getElementById('assistant-loading');
        if (loading) loading.remove();
    }

    async function sendMessage() {
        const question = inputText.value.trim();
        if (!question) return;
        inputText.value = '';
        appendMessage('user', question);
        messageHistory.push({ role: 'user', content: question });
        showLoading();
        setStatus('项目助手正在思考...');

        try {
            // 调用现有接口，指定 backend = "project"
            const response = await fetch(window.apiUrl('/agent/api/chat'), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    sessionId: sessionId,
                    messages: messageHistory,    // 后端会基于 sessionId 维护历史，这里也传递便于后端扩展
                    backend: 'project',
                    // 下面几个字段对于 project 后端无影响，但保留
                    model: '',
                    temperature: 0.7
                })
            });
            const data = await response.json();
            hideLoading();
            if (data.success) {
                const reply = data.response || '（无回复）';
                appendMessage('assistant', reply);
                messageHistory.push({ role: 'assistant', content: reply });
                setStatus('就绪');
            } else {
                const errMsg = data.error || '未知错误';
                appendMessage('assistant', `❌ 后端错误：${errMsg}`);
                setStatus(errMsg, true);
            }
        } catch (err) {
            hideLoading();
            appendMessage('assistant', `❌ 网络异常：${err.message}`);
            setStatus('连接失败，请检查后端服务', true);
        }
    }

    function clearMessages() {
        messagesContainer.innerHTML = '';
        messageHistory = [];
        // 可选：向后端发送关闭会话请求（释放后端内存）
        fetch(window.apiUrl('/agent/api/close'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sessionId: sessionId })
        }).catch(e => console.warn('关闭会话失败', e));
        // 重新生成 sessionId
        sessionId = 'proj_' + Date.now() + '_' + Math.random().toString(36).substr(2, 8);
        sessionStorage.setItem('project_assistant_session', sessionId);
        // 显示欢迎消息
        appendMessage('assistant', '你好！我是 CodeStats 项目助手，你可以问我关于框架架构、模块功能等问题。');
    }

    function toggleWidget(show) {
        widget.style.display = show ? 'flex' : 'none';
    }

    function initDOM() {
        // 创建浮动按钮
        const fabBtn = document.createElement('div');
        fabBtn.className = 'assistant-fab';
        fabBtn.innerHTML = '🤖';
        fabBtn.setAttribute('aria-label', '项目助手');
        document.body.appendChild(fabBtn);

        // 创建聊天窗口
        const chatWidget = document.createElement('div');
        chatWidget.className = 'assistant-chat-widget';
        chatWidget.style.display = 'none';
        chatWidget.innerHTML = `
        <div class="widget-header">
            <span>📖 CodeStats 项目助手</span>
            <button class="close-btn">✖</button>
        </div>
        <div class="assistant-messages"></div>
        <div class="assistant-quick-buttons">
            <button data-question="什么是 CodeStats？">📌 什么是 CodeStats？</button>
            <button data-question="如何启动项目？">🚀 如何启动项目？</button>
            <button data-question="框架核心组件有哪些？">⚙️ 框架核心组件有哪些？</button>
            <button data-question="怎么配置数据库连接池？">🗄️ 怎么配置数据库连接池？</button>
            <button data-question="代码分析模块如何使用？">📊 代码分析模块如何使用？</button>
        </div>
        <div class="assistant-input-area">
            <textarea placeholder="询问关于 CodeStats 的问题，例如：什么是 CodeStats？" rows="2"></textarea>
            <button>发送</button>
        </div>
        <div class="chat-status"></div>
    `;
        document.body.appendChild(chatWidget);

        // 绑定全局引用
        fab = fabBtn;
        widget = chatWidget;
        messagesContainer = widget.querySelector('.assistant-messages');
        inputText = widget.querySelector('textarea');
        sendBtn = widget.querySelector('.assistant-input-area button');
        statusSpan = widget.querySelector('.chat-status');

        // 添加欢迎消息
        appendMessage('assistant', '你好！我是 CodeStats 项目助手，你可以问我关于框架架构、模块功能等问题。');

        // 事件绑定
        fab.addEventListener('click', () => toggleWidget(true));
        widget.querySelector('.close-btn').addEventListener('click', () => toggleWidget(false));
        sendBtn.addEventListener('click', sendMessage);
        inputText.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
        const quickBtns = widget.querySelectorAll('.assistant-quick-buttons button');
        quickBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                const question = btn.getAttribute('data-question');
                if (question) {
                    inputText.value = question;
                    sendMessage();
                }
            });
        });

        // 点击窗口外部不关闭（符合预期），如要点击遮罩关闭可自行扩展
    }

    // 页面加载完成后初始化
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initDOM);
    } else {
        initDOM();
    }
})();