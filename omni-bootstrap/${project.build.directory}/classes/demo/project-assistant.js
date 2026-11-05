// ==================== 独立项目助手（父页面唯一实例） ====================
(function() {
    let sessionId = sessionStorage.getItem('project_assistant_session');
    if (!sessionId) {
        sessionId = 'proj_' + Date.now() + '_' + Math.random().toString(36).substr(2, 8);
        sessionStorage.setItem('project_assistant_session', sessionId);
    }

    let messageHistory = [];
    let widget, fab, messagesContainer, inputText, sendBtn, statusSpan;
    let isWidgetVisible = false;

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

    async function sendMessageToAssistant(question) {
        if (!question || !question.trim()) return;
        appendMessage('user', question);
        messageHistory.push({ role: 'user', content: question });
        showLoading();
        setStatus('项目助手正在思考...');

        try {
            const response = await fetch(window.apiUrl('/agent/api/chat'), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    sessionId: sessionId,
                    messages: messageHistory,
                    backend: 'project'
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
        if (!isWidgetVisible) toggleWidget(true);
    }

    function clearMessages() {
        messagesContainer.innerHTML = '';
        messageHistory = [];
        fetch(window.apiUrl('/agent/api/close'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sessionId: sessionId })
        }).catch(e => console.warn('关闭会话失败', e));
        sessionId = 'proj_' + Date.now() + '_' + Math.random().toString(36).substr(2, 8);
        sessionStorage.setItem('project_assistant_session', sessionId);
        appendMessage('assistant', '你好！我是 CodeStats 项目助手，可以点击页面中的高亮关键词向我提问。');
    }

    function toggleWidget(show) {
        if (show === undefined) show = !isWidgetVisible;
        widget.style.display = show ? 'flex' : 'none';
        isWidgetVisible = show;
        if (show && messagesContainer.children.length === 0) {
            appendMessage('assistant', '你好！我是 CodeStats 项目助手，可以点击页面中的高亮关键词向我提问。');
        }
    }

    function initDOM() {
        if (document.querySelector('.assistant-fab')) return;

        const fabBtn = document.createElement('div');
        fabBtn.className = 'assistant-fab';
        fabBtn.innerHTML = '🤖';
        fabBtn.setAttribute('aria-label', '项目助手');
        document.body.appendChild(fabBtn);

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
                <button data-question="目录管理支持哪些操作？">📁 目录管理支持哪些操作？</button>
            </div>
            <div class="assistant-input-area">
                <textarea placeholder="询问关于 CodeStats 的问题..." rows="2"></textarea>
                <button>发送</button>
            </div>
            <div class="chat-status"></div>
        `;
        document.body.appendChild(chatWidget);

        fab = fabBtn;
        widget = chatWidget;
        messagesContainer = widget.querySelector('.assistant-messages');
        inputText = widget.querySelector('textarea');
        sendBtn = widget.querySelector('.assistant-input-area button');
        statusSpan = widget.querySelector('.chat-status');

        fab.addEventListener('click', () => toggleWidget(true));
        widget.querySelector('.close-btn').addEventListener('click', () => toggleWidget(false));
        sendBtn.addEventListener('click', () => sendMessageToAssistant(inputText.value));
        inputText.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessageToAssistant(inputText.value);
            }
        });
        const quickBtns = widget.querySelectorAll('.assistant-quick-buttons button');
        quickBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                const question = btn.getAttribute('data-question');
                if (question) sendMessageToAssistant(question);
            });
        });
    }

    // 暴露全局方法供 iframe 调用
    window.ProjectAssistant = {
        sendQuestion: function(question) {
            if (!widget) {
                initDOM();
                setTimeout(() => {
                    if (window.ProjectAssistant) window.ProjectAssistant.sendQuestion(question);
                }, 100);
                return;
            }
            if (!isWidgetVisible) toggleWidget(true);
            setTimeout(() => sendMessageToAssistant(question), 150);
        },
        showWidget: () => toggleWidget(true),
        hideWidget: () => toggleWidget(false)
    };

    // 监听来自 iframe 的消息（备用）
    window.addEventListener('message', (event) => {
        if (event.data && event.data.type === 'askProjectAssistant') {
            const question = event.data.question;
            if (question && window.ProjectAssistant) {
                window.ProjectAssistant.sendQuestion(question);
            }
        }
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initDOM);
    } else {
        initDOM();
    }
})();