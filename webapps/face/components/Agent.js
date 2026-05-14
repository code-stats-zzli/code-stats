import { apiUrl } from '../api.js';

export const Agent = {
    template: `
        <div class="card">
            <h2>🧠 AI Agent (DeepSeek + Ollama)</h2>
            <div style="display: flex; gap: 20px; flex-wrap: wrap;">
                <div style="flex:1; min-width: 280px;">
                    <h3>⚙️ 部署与配置</h3>
                    <div class="form-group">
                        <label>Ollama 状态</label>
                        <button class="btn-secondary" @click="checkStatus">检查状态</button>
                        <div v-if="statusMsg" style="margin-top: 8px;">{{ statusMsg }}</div>
                    </div>
                    <div class="form-group">
                        <label>模型名称</label>
                        <input type="text" v-model="modelName" placeholder="deepseek-r1:7b">
                        <button class="btn-secondary" @click="pullModel">📥 下载模型</button>
                        <button class="btn-secondary" @click="runModel">▶️ 运行模型</button>
                        <div v-if="pullLog" style="margin-top: 8px; white-space: pre-wrap; font-size:12px;">{{ pullLog }}</div>
                    </div>
                </div>
                <div style="flex:2;">
                    <h3>💬 对话</h3>
                    <div style="display: flex; gap: 12px; margin-bottom: 16px;">
                        <select v-model="selectedModel">
                            <option value="deepseek-r1:7b">DeepSeek-R1 7B</option>
                            <option value="deepseek-coder:6.7b">DeepSeek-Coder 6.7B</option>
                        </select>
                        <input type="number" v-model="temperature" step="0.1" min="0" max="2" placeholder="温度">
                        <select v-model="backend">
                            <option value="ollama">本地 Ollama</option>
                            <option value="deepseek">DeepSeek API</option>
                            <option value="custom">自定义 API</option>
                        </select>
                        <input v-if="backend === 'custom'" v-model="customUrl" placeholder="API URL">
                        <input v-if="backend === 'custom'" v-model="customKey" placeholder="API Key (可选)">
                    </div>
                    <div class="chat-messages" style="height: 300px; overflow-y: auto; background: white; border-radius: 16px; padding: 12px;">
                        <div v-for="(msg, idx) in messages" :key="idx" style="margin:8px 0;">
                            <strong>{{ msg.role === 'user' ? '👤 我' : '🤖 助手' }}：</strong>
                            <span>{{ msg.content }}</span>
                        </div>
                        <div v-if="loading">🤖 正在思考...</div>
                    </div>
                    <div style="margin-top: 12px; display: flex; gap: 8px;">
                        <textarea v-model="inputText" rows="2" style="flex:1;" placeholder="输入问题..."></textarea>
                        <button class="btn-primary" @click="sendMessage">发送</button>
                    </div>
                </div>
            </div>
        </div>
    `,
    data() {
        return {
            modelName: 'deepseek-r1:7b',
            statusMsg: '',
            pullLog: '',
            selectedModel: 'deepseek-r1:7b',
            temperature: 0.7,
            backend: 'ollama',
            customUrl: '',
            customKey: '',
            messages: [{ role: 'assistant', content: '你好！我是 DeepSeek AI 助手，请先检查 Ollama 状态并确保模型已下载。' }],
            inputText: '',
            loading: false
        };
    },
    methods: {
        async checkStatus() {
            try {
                const resp = await fetch(apiUrl('/agent/api/ollama/status'));
                const data = await resp.json();
                if (data.status === 'ok') {
                    this.statusMsg = `✅ 服务正常，版本: ${data.version}`;
                } else {
                    this.statusMsg = `❌ ${data.message}`;
                }
            } catch (err) {
                this.statusMsg = `❌ 连接失败: ${err.message}`;
            }
        },
        async pullModel() {
            if (!this.modelName) return;
            this.pullLog = '正在拉取模型，请稍候...';
            try {
                const resp = await fetch(apiUrl('/agent/api/ollama/pull'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ model: this.modelName })
                });
                const data = await resp.json();
                if (data.success) {
                    this.pullLog = '✅ 模型下载完成！';
                } else {
                    this.pullLog = `❌ 失败: ${data.message}`;
                }
            } catch (err) {
                this.pullLog = `❌ 请求异常: ${err.message}`;
            }
        },
        async runModel() {
            if (!this.modelName) return;
            try {
                const resp = await fetch(apiUrl('/agent/api/ollama/run'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ model: this.modelName })
                });
                const data = await resp.json();
                if (data.success) {
                    this.statusMsg = `✅ 模型已就绪: ${data.message}`;
                } else {
                    this.statusMsg = `❌ 启动失败: ${data.message}`;
                }
            } catch (err) {
                this.statusMsg = `❌ 请求异常: ${err.message}`;
            }
        },
        async sendMessage() {
            if (!this.inputText.trim()) return;
            const userMsg = { role: 'user', content: this.inputText };
            this.messages.push(userMsg);
            this.inputText = '';
            this.loading = true;
            try {
                const payload = {
                    model: this.selectedModel,
                    messages: this.messages.slice(), // 发送全部历史
                    temperature: parseFloat(this.temperature),
                    backend: this.backend
                };
                if (this.backend === 'custom') {
                    payload.customUrl = this.customUrl;
                    payload.customKey = this.customKey;
                }
                const resp = await fetch(apiUrl('/agent/api/chat'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const data = await resp.json();
                if (data.error) {
                    this.messages.push({ role: 'assistant', content: `❌ 错误: ${data.error}` });
                } else {
                    const reply = data.response || data.message || '无回复';
                    this.messages.push({ role: 'assistant', content: reply });
                }
            } catch (err) {
                this.messages.push({ role: 'assistant', content: `❌ 请求失败: ${err.message}` });
            } finally {
                this.loading = false;
            }
        }
    },
    mounted() {
        this.checkStatus();
    }
};