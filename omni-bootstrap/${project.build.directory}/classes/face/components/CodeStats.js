// webapps/face/components/CodeStats.js
import { apiUrl } from '../api.js';

export const CodeStats = {
    template: `
        <div class="card">
            <h2>📊 代码分析</h2>
            <div class="config-form" style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
                <div><label>根目录</label><input v-model="rootDir" placeholder="D:/work/space/CodeStats"></div>
                <div><label>扩展名</label><input v-model="extensions" placeholder="java,js,py"></div>
                <div><label>排除路径</label><input v-model="excludePaths" placeholder="test,target"></div>
                <div><label>排除注释</label><input type="checkbox" v-model="excludeComments"></div>
                <div><label>线程数</label><input type="number" v-model="threadCount"></div>
                <div><label>排序方式</label><select v-model="sortBy"><option>lines</option><option>name</option><option>language</option></select></div>
                <div><label>额外路径</label><input v-model="extraPaths" placeholder="D:/other"></div>
                <div><label>合并文件输出</label><input v-model="mergeOutputFile" placeholder="合并后保存路径"></div>
            </div>
            <div style="margin-top: 20px;">
                <button class="btn-primary" @click="analyze">开始分析</button>
                <button class="btn-secondary" @click="resetConfig">重置</button>
            </div>
            <div v-if="analyzing" class="progress-bar" style="margin-top: 20px;">
                <div class="progress-fill" :style="'width:' + progressPercent + '%'"></div>
                <div style="text-align:center;">{{ progressText }}</div>
            </div>
            <div v-if="resultData" style="margin-top: 24px;">
                <div class="result-tabs" style="display: flex; gap: 12px;">
                    <button class="btn-secondary" @click="showFilesTab">文件列表</button>
                    <button class="btn-secondary" @click="showSummaryTab">摘要统计</button>
                </div>
                <div id="filesPanel" :style="showFiles ? 'display:block' : 'display:none'"></div>
                <div id="summaryPanel" :style="showFiles ? 'display:none' : 'display:block'"></div>
                <div v-if="resultData && resultData.mergeOutput">
                    <button class="btn-secondary" @click="downloadMergeFile(resultData.mergeOutput)">📥 下载合并文件</button>
                </div>
            </div>
        </div>
    `,
    data() {
        return {
            rootDir: '',
            extensions: 'java',
            excludePaths: '',
            extraPaths: '',
            excludeComments: true,
            threadCount: 4,
            sortBy: 'lines',
            mergeOutputFile: '',
            analyzing: false,
            progressPercent: 0,
            progressText: '',
            resultData: null,      // 初始为 null，避免访问属性时报错
            showFiles: true,
            showSummary: false
        };
    },
    methods: {
        async fetchDefaultConfig() {
            try {
                const resp = await fetch(apiUrl('/code/api/config'));
                if (resp.ok) {
                    const cfg = await resp.json();
                    this.rootDir = cfg.rootDir || '';
                    this.extensions = cfg.extensions || 'java';
                    this.excludePaths = cfg.excludePaths || '';
                    this.extraPaths = cfg.extraPaths || '';
                    this.excludeComments = cfg.excludeComments !== false;
                    this.threadCount = cfg.threadCount || 4;
                    this.sortBy = cfg.sortBy || 'lines';
                    this.mergeOutputFile = cfg.mergeOutputFile || '';
                }
            } catch (e) {
                console.error('加载配置失败', e);
            }
        },
        async analyze() {
            this.analyzing = true;
            this.progressPercent = 10;
            this.progressText = '正在分析...';
            try {
                const params = new URLSearchParams({
                    rootDir: this.rootDir,
                    extensions: this.extensions,
                    excludePaths: this.excludePaths,
                    extraPaths: this.extraPaths,
                    excludeComments: this.excludeComments,
                    threadCount: this.threadCount,
                    sortBy: this.sortBy,
                    mergeEnabled: true,
                    mergeOutputFile: this.mergeOutputFile
                });
                const resp = await fetch(apiUrl(`/code/api/analyze?${params.toString()}`));
                this.progressPercent = 80;
                const data = await resp.json();
                if (data.error) throw new Error(data.error);
                this.resultData = data;
                this.renderFiles();
                this.renderSummary();
                this.progressPercent = 100;
                this.progressText = '分析完成';
                setTimeout(() => { this.analyzing = false; }, 1000);
            } catch (err) {
                alert('分析失败: ' + err.message);
                this.analyzing = false;
            }
        },
        renderFiles() {
            const container = document.getElementById('filesPanel');
            if (!container || !this.resultData) return;
            const files = this.resultData.files || [];
            let html = '<table class="data-table"><thead><tr><th>文件路径</th><th>总行数</th><th>代码行</th><th>注释行</th><th>空行</th><th>语言</th><th>复杂度</th></tr></thead><tbody>';
            for (const f of files) {
                html += `<tr>
                    <td title="${this.escapeHtml(f.path)}">${this.truncatePath(f.path)}</td>
                    <td>${f.lines}</td>
                    <td>${f.codeLines}</td>
                    <td>${f.commentLines}</td>
                    <td>${f.blankLines}</td>
                    <td>${this.escapeHtml(f.language)}</td>
                    <td>${f.complexity}</td>
                </tr>`;
            }
            html += '</tbody></table>';
            container.innerHTML = html;
        },
        renderSummary() {
            const container = document.getElementById('summaryPanel');
            if (!container || !this.resultData) return;
            const summary = this.resultData.summary || {};
            const langs = this.resultData.languages || {};
            let html = `<h3>总体统计</h3>
                <p>总文件数: ${summary.totalFiles || 0}, 总行数: ${summary.totalLines || 0}</p>
                <p>代码行: ${summary.totalCodeLines || 0}, 注释行: ${summary.totalCommentLines || 0}, 空行: ${summary.totalBlankLines || 0}</p>
                <p>耗时: ${summary.durationMs || 0} ms</p>
                <h3>语言分布</h3>`;
            for (const [lang, stat] of Object.entries(langs)) {
                html += `<div><strong>${this.escapeHtml(lang)}</strong>: 文件 ${stat.files}, 行数 ${stat.lines}, 代码 ${stat.code}, 注释 ${stat.comments}, 空行 ${stat.blanks}</div>`;
            }
            container.innerHTML = html;
        },
        resetConfig() {
            this.fetchDefaultConfig();
        },
        async downloadMergeFile(filePath) {
            const encoded = btoa(encodeURIComponent(filePath));
            const url = apiUrl(`/code/api/download?filePath=${encoded}`);
            window.open(url, '_blank');
        },
        escapeHtml(str) {
            if (!str) return '';
            return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
        },
        truncatePath(path) {
            return path.length > 60 ? '...' + path.slice(-57) : path;
        },
        showFilesTab() {
            this.showFiles = true;
            this.showSummary = false;
        },
        showSummaryTab() {
            this.showFiles = false;
            this.showSummary = true;
        }
    },
    mounted() {
        this.fetchDefaultConfig();
    }
};