/**
 * 系统日志模块
 *
 * 挂载到 SysCenter：
 *   - 文件切换下拉
 *   - 首屏 tail
 *   - 定时增量轮询（read?offset=）
 *   - 自动滚动 / 手动滚动暂停
 *   - 文件截断自动恢复（truncated=true → 重新 tail）
 *   - 可暂停 / 可清屏 / 可跳到末尾
 */
(function () {
    'use strict';

    SysCenter.register({
        id: 'log',
        name: '系统日志',
        icon: '📋',
        desc: '实时查看服务端日志文件内容（自动滚动，可暂停）',
        mount: mountLog
    });

    function mountLog(container) {
        container.innerHTML = `
            <div class="log-toolbar">
                <div class="log-toolbar-left">
                    <label class="log-label">文件</label>
                    <select id="logFileSelect" class="log-select"></select>
                    <button id="logRefreshFilesBtn" class="btn-secondary log-btn-sm"
                            title="刷新文件列表">🔄 刷新</button>
                </div>
                <div class="log-toolbar-right">
                    <label class="log-label">行数</label>
                    <select id="logLinesSelect" class="log-select log-select-sm">
                        <option value="200">200</option>
                        <option value="500" selected>500</option>
                        <option value="1000">1000</option>
                        <option value="2000">2000</option>
                    </select>
                    <label class="log-label">刷新</label>
                    <select id="logIntervalSelect" class="log-select log-select-sm">
                        <option value="1000">1s</option>
                        <option value="2000" selected>2s</option>
                        <option value="5000">5s</option>
                        <option value="0">手动</option>
                    </select>
                    <button id="logToggleBtn" class="btn-primary log-btn-sm">⏸ 暂停</button>
                    <button id="logClearBtn" class="btn-secondary log-btn-sm">🗑 清屏</button>
                    <button id="logScrollBottomBtn" class="btn-secondary log-btn-sm">⬇ 底部</button>
                </div>
            </div>
            <div class="log-meta">
                <span id="logMetaFile">未选择文件</span>
                <span id="logMetaOffset">偏移: 0</span>
                <span id="logMetaState">🟢 实时</span>
                <span id="logMetaUpdate">--</span>
            </div>
            <div class="log-viewer" id="logViewer"></div>
        `;

        const API = (typeof window.apiUrl === 'function')
            ? window.apiUrl('/sys/api/log')
            : '/sys/api/log';

        const fileSelect      = container.querySelector('#logFileSelect');
        const refreshFilesBtn = container.querySelector('#logRefreshFilesBtn');
        const linesSelect     = container.querySelector('#logLinesSelect');
        const intervalSelect  = container.querySelector('#logIntervalSelect');
        const toggleBtn       = container.querySelector('#logToggleBtn');
        const clearBtn        = container.querySelector('#logClearBtn');
        const scrollBottomBtn = container.querySelector('#logScrollBottomBtn');
        const viewer          = container.querySelector('#logViewer');
        const metaFile        = container.querySelector('#logMetaFile');
        const metaOffset      = container.querySelector('#logMetaOffset');
        const metaState       = container.querySelector('#logMetaState');
        const metaUpdate      = container.querySelector('#logMetaUpdate');

        const MAX_VIEW_CHARS = 1024 * 1024; // 1MB 内存保护

        const state = {
            fileName: null,
            offset: 0,
            running: true,
            autoScroll: true,
            intervalMs: 2000,
            timer: null,
            loading: false,
            disposed: false
        };

        // ==================== UI 辅助 ====================

        function updateMeta() {
            metaFile.textContent   = state.fileName ? `文件: ${state.fileName}` : '未选择文件';
            metaOffset.textContent = `偏移: ${state.offset}`;
            metaState.textContent  = state.running ? '🟢 实时' : '⏸ 暂停';
            metaUpdate.textContent = `更新: ${new Date().toLocaleTimeString()}`;
        }

        function formatSize(b) {
            if (b == null) return '-';
            if (b < 1024) return b + ' B';
            if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
            return (b / 1024 / 1024).toFixed(2) + ' MB';
        }

        function setContent(text) {
            viewer.textContent = text;
            if (state.autoScroll) viewer.scrollTop = viewer.scrollHeight;
        }

        function appendContent(text) {
            if (!text) return;
            viewer.textContent += text;
            if (viewer.textContent.length > MAX_VIEW_CHARS) {
                viewer.textContent = viewer.textContent.slice(-MAX_VIEW_CHARS);
            }
            if (state.autoScroll) viewer.scrollTop = viewer.scrollHeight;
        }

        // ==================== 数据加载 ====================

        async function loadFiles() {
            try {
                const resp = await fetch(API + '/files', { cache: 'no-store' });
                const data = await resp.json();
                if (!data.success) throw new Error(data.error || '加载失败');

                const files = data.files || [];
                if (!files.length) {
                    fileSelect.innerHTML = '<option value="">（目录下无 .log/.txt 文件）</option>';
                    fileSelect.disabled = true;
                    state.fileName = null;
                    setContent(
                        '📭 未找到日志文件。\n\n' +
                        `当前日志目录: ${data.dir || '(未知)'}\n` +
                        '请在 application.properties 中配置 sys.log.dir 指向日志目录，或检查 log-config.xml 中的 file 参数。\n'
                    );
                    return;
                }

                fileSelect.disabled = false;
                fileSelect.innerHTML = files.map(f =>
                    `<option value="${escapeAttr(f.name)}">${escapeHtml(f.name)} (${formatSize(f.size)})</option>`
                ).join('');

                if (state.fileName && files.some(f => f.name === state.fileName)) {
                    fileSelect.value = state.fileName;
                } else {
                    state.fileName = files[0].name;
                    fileSelect.value = state.fileName;
                    await reloadTail();
                }
            } catch (e) {
                fileSelect.innerHTML = `<option value="">加载失败：${escapeHtml(e.message)}</option>`;
                setContent(`❌ 加载文件列表失败：${e.message}\n`);
            }
        }

        async function reloadTail() {
            if (!state.fileName) return;
            const lines = parseInt(linesSelect.value, 10) || 500;
            try {
                const url = `${API}/tail?file=${encodeURIComponent(state.fileName)}&lines=${lines}`;
                const resp = await fetch(url, { cache: 'no-store' });
                const data = await resp.json();
                if (!data.success) throw new Error(data.error || '加载失败');

                const text = (data.lines || []).join('\n');
                setContent(text ? text + '\n' : '（文件为空）\n');
                state.offset = data.offset || 0;
                updateMeta();
            } catch (e) {
                setContent(`❌ 加载日志失败：${e.message}\n`);
            }
        }

        async function readIncrement() {
            if (!state.fileName || !state.running || state.loading || state.disposed) return;
            state.loading = true;
            try {
                const url = `${API}/read?file=${encodeURIComponent(state.fileName)}&offset=${state.offset}`;
                const resp = await fetch(url, { cache: 'no-store' });
                const data = await resp.json();
                if (!data.success) throw new Error(data.error || '读取失败');

                if (data.truncated) {
                    await reloadTail();
                } else if (data.content) {
                    appendContent(data.content);
                    state.offset = data.offset || state.offset;
                    updateMeta();
                }
            } catch (e) {
                console.warn('[SysLog] 增量读取失败', e);
            } finally {
                state.loading = false;
            }
        }

        // ==================== 定时器 ====================

        function stopTimer() {
            if (state.timer) {
                clearInterval(state.timer);
                state.timer = null;
            }
        }

        function startTimer() {
            stopTimer();
            if (state.disposed) return;
            if (!state.running) return;
            if (state.intervalMs <= 0) return;
            state.timer = setInterval(readIncrement, state.intervalMs);
        }

        // ==================== 事件绑定 ====================

        viewer.addEventListener('scroll', () => {
            const nearBottom =
                viewer.scrollHeight - viewer.scrollTop - viewer.clientHeight < 50;
            state.autoScroll = nearBottom;
        });

        fileSelect.addEventListener('change', async () => {
            state.fileName = fileSelect.value || null;
            state.offset = 0;
            state.autoScroll = true;
            await reloadTail();
            startTimer();
        });

        refreshFilesBtn.addEventListener('click', async () => {
            await loadFiles();
            startTimer();
        });

        linesSelect.addEventListener('change', reloadTail);

        intervalSelect.addEventListener('change', () => {
            state.intervalMs = parseInt(intervalSelect.value, 10) || 0;
            startTimer();
            updateMeta();
        });

        toggleBtn.addEventListener('click', () => {
            state.running = !state.running;
            toggleBtn.textContent = state.running ? '⏸ 暂停' : '▶ 继续';
            toggleBtn.classList.toggle('btn-primary', state.running);
            toggleBtn.classList.toggle('btn-secondary', !state.running);
            startTimer();
            updateMeta();
        });

        clearBtn.addEventListener('click', () => setContent(''));

        scrollBottomBtn.addEventListener('click', () => {
            viewer.scrollTop = viewer.scrollHeight;
            state.autoScroll = true;
        });

        // ==================== 生命周期 ====================

        const observer = new MutationObserver(() => {
            if (!document.body.contains(container)) {
                state.disposed = true;
                stopTimer();
                observer.disconnect();
            }
        });
        observer.observe(document.body, { childList: true, subtree: true });

        window.addEventListener('beforeunload', () => {
            state.disposed = true;
            stopTimer();
        });

        // ==================== 启动 ====================

        (async () => {
            await loadFiles();
            if (state.fileName) {
                await reloadTail();
            }
            updateMeta();
            startTimer();
        })();
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"']/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[m]));
    }
    function escapeAttr(s) { return escapeHtml(s); }
})();