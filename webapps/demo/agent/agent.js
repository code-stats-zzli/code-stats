(function() {
    const API_BASE = '/demo/agent/api';
    let currentPullInterval = null;

    const checkStatusBtn = document.getElementById('checkStatusBtn');
    const statusResult = document.getElementById('statusResult');
    const modelNameInput = document.getElementById('modelName');
    const pullModelBtn = document.getElementById('pullModelBtn');
    const pullProgress = document.getElementById('pullProgress');
    const pullLog = document.getElementById('pullLog');
    const runModelBtn = document.getElementById('runModelBtn');
    const runResult = document.getElementById('runResult');

    const menuItems = document.querySelectorAll('.menu-item');
    const deployView = document.getElementById('deployView');
    const chatView = document.getElementById('chatView');

    function switchView(viewId) {
        deployView.classList.toggle('active', viewId === 'deploy');
        chatView.classList.toggle('active', viewId === 'chat');
        menuItems.forEach(item => {
            item.classList.toggle('active', item.dataset.view === viewId);
        });
    }

    menuItems.forEach(item => {
        item.addEventListener('click', () => switchView(item.dataset.view));
    });
    switchView('chat');

    function showStatus(container, message, isError = false) {
        container.innerHTML = `<span style="color: ${isError ? '#dc2626' : '#0066ff'}">${message}</span>`;
    }

    async function checkOllamaStatus() {
        showStatus(statusResult, '🔄 检查中...');
        try {
            const resp = await fetch(`${API_BASE}/ollama/status`);
            const data = await resp.json();
            if (data.status === 'ok') {
                showStatus(statusResult, `✅ Ollama 服务正常<br>版本: ${data.version || '未知'}<br>API地址: ${data.url || 'http://localhost:11434'}`);
            } else {
                showStatus(statusResult, `❌ Ollama 未运行或未安装<br>${data.message || '请先安装 Ollama 并启动服务'}`, true);
            }
        } catch (err) {
            showStatus(statusResult, `❌ 无法连接后端服务: ${err.message}`, true);
        }
    }
    if (checkStatusBtn) checkStatusBtn.addEventListener('click', checkOllamaStatus);

    async function pullModel() {
        const model = modelNameInput.value.trim();
        if (!model) { alert('请输入模型名称'); return; }
        pullProgress.style.display = 'block';
        pullLog.textContent = '🔄 正在拉取模型，请稍候...\n';
        document.querySelector('#pullProgress .progress-fill').style.width = '0%';
        try {
            const resp = await fetch(`${API_BASE}/ollama/pull`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ model: model })
            });
            const data = await resp.json();
            if (data.success) {
                pullLog.textContent += '✅ 模型下载完成！\n';
                document.querySelector('#pullProgress .progress-fill').style.width = '100%';
                showStatus(runResult, `模型 ${model} 已安装，可点击「运行模型」测试。`);
            } else {
                pullLog.textContent += `❌ 失败: ${data.message}\n`;
            }
        } catch (err) {
            pullLog.textContent += `❌ 请求异常: ${err.message}\n`;
        }
    }
    if (pullModelBtn) pullModelBtn.addEventListener('click', pullModel);

    async function runModel() {
        const model = modelNameInput.value.trim();
        if (!model) { alert('请先填写模型名称'); return; }
        showStatus(runResult, '🔄 正在启动模型服务...');
        try {
            const resp = await fetch(`${API_BASE}/ollama/run`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ model: model })
            });
            const data = await resp.json();
            if (data.success) {
                showStatus(runResult, `✅ ${data.message}<br>API 地址: http://localhost:11434/api/generate`);
            } else {
                showStatus(runResult, `❌ 启动失败: ${data.message}`, true);
            }
        } catch (err) {
            showStatus(runResult, `❌ 请求异常: ${err.message}`, true);
        }
    }
    if (runModelBtn) runModelBtn.addEventListener('click', runModel);

    // 对话模块
    const chatModelSelect = document.getElementById('chatModelSelect');
    const temperatureInput = document.getElementById('temperature');
    const newChatBtn = document.getElementById('newChatBtn');
    const chatMessages = document.getElementById('chatMessages');
    const userInput = document.getElementById('userInput');
    const sendBtn = document.getElementById('sendBtn');
    const backendTypeSelect = document.getElementById('backendType');
    const customUrlGroup = document.getElementById('customUrlGroup');
    const customApiUrl = document.getElementById('customApiUrl');
    const customApiKey = document.getElementById('customApiKey');

    let conversationHistory = [];

    backendTypeSelect.addEventListener('change', () => {
        customUrlGroup.style.display = backendTypeSelect.value === 'custom' ? 'inline-flex' : 'none';
    });

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

    async function sendMessage() {
        const message = userInput.value.trim();
        if (!message) return;
        userInput.value = '';
        appendMessage('user', message);
        conversationHistory.push({ role: 'user', content: message });
        showLoading();
        try {
            const model = chatModelSelect.value;
            const temperature = parseFloat(temperatureInput.value);
            const backend = backendTypeSelect.value;
            let requestBody = {
                model: model,
                messages: conversationHistory,
                temperature: temperature,
                stream: false,
                backend: backend
            };
            if (backend === 'custom') {
                requestBody.customUrl = customApiUrl.value.trim();
                requestBody.customKey = customApiKey.value.trim();
            }
            const response = await fetch('/demo/agent/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody)
            });
            const data = await response.json();
            hideLoading();
            if (data.error) {
                appendMessage('assistant', `❌ 错误: ${data.error}`);
            } else {
                const reply = data.response || data.message || '无回复';
                appendMessage('assistant', reply);
                conversationHistory.push({ role: 'assistant', content: reply });
                if (conversationHistory.length > 20) conversationHistory = conversationHistory.slice(-20);
            }
        } catch (err) {
            hideLoading();
            appendMessage('assistant', `❌ 请求失败: ${err.message}`);
        }
    }

    if (sendBtn) sendBtn.addEventListener('click', sendMessage);
    if (userInput) {
        userInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
    }
    if (newChatBtn) {
        newChatBtn.addEventListener('click', () => {
            conversationHistory = [];
            chatMessages.innerHTML = '';
            appendMessage('assistant', '你好！我是 DeepSeek AI 助手，基于 Ollama 本地部署。请问有什么可以帮助你的？');
        });
    }
})();