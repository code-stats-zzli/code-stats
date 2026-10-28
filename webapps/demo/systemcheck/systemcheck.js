(function() {
    const API = window.apiUrl('/systemcheck/api');

    let allModules = [];
    let currentTaskId = null;
    let pollTimer = null;
    let timerTick = null;
    let taskStartTime = 0;
    let lastStatus = null;
    let collapsedGroups = new Set();
    let initialFieldValues = {};

    const DOM = {
        tabs:           document.querySelectorAll('.sys-tab'),
        panes: {
            config: document.getElementById('configPane'),
            result: document.getElementById('resultPane')
        },
        threadCount:    document.getElementById('threadCount'),
        moduleList:     document.getElementById('moduleList'),
        estimatedTotal: document.getElementById('estimatedTotal'),
        selectedCount:  document.getElementById('selectedCount'),
        runBtn:         document.getElementById('runBtn'),
        runningPanel:   document.getElementById('runningPanel'),
        runningTimer:   document.getElementById('runningTimer'),
        elapsedText:    document.getElementById('elapsedText'),
        progressText:   document.getElementById('progressText'),
        estimatedText:  document.getElementById('estimatedText'),
        progressFill:   document.getElementById('progressFill'),
        runningModules: document.getElementById('runningModules'),
        cancelBtn:      document.getElementById('cancelBtn'),
        resultPanel:    document.getElementById('resultPanel'),
        emptyState:     document.getElementById('emptyState'),
        summaryStats:   document.getElementById('summaryStats'),
        totalDuration:  document.getElementById('totalDuration'),
        resultBody:     document.getElementById('resultBody'),
        exportHtmlBtn:  document.getElementById('exportHtmlBtn'),
        exportCsvBtn:   document.getElementById('exportCsvBtn'),
        exportJsonBtn:  document.getElementById('exportJsonBtn'),
        backToConfigBtn: document.getElementById('backToConfigBtn')
    };

    function switchTab(id) {
        DOM.tabs.forEach(t => t.classList.toggle('active', t.dataset.tab === id));
        Object.keys(DOM.panes).forEach(k => DOM.panes[k].classList.toggle('active', k === id));
    }
    DOM.tabs.forEach(t => t.addEventListener('click', () => switchTab(t.dataset.tab)));

    // ==================== 序列化归一化 ====================
    function serializeFieldValue(value, type) {
        if (value == null) return '';
        if (type === 'number') {
            const n = Number(value);
            return isNaN(n) ? String(value) : String(n);
        }
        if (type === 'list') {
            if (Array.isArray(value)) return value.map(v => String(v).trim()).filter(v => v).join(',');
            return String(value).split(',').map(s => s.trim()).filter(s => s).join(',');
        }
        return String(value);
    }

    // ==================== 加载配置 ====================
    async function loadConfig() {
        if (DOM.moduleList) DOM.moduleList.innerHTML = '<div class="loading">加载配置中...</div>';
        try {
            const resp = await fetch(API + '/config', { cache: 'no-store' });
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            const cfg = await resp.json();
            if (cfg.error) throw new Error(cfg.error);
            if (!Array.isArray(cfg.checks)) throw new Error('配置格式错误');

            allModules = cfg.checks;
            if (DOM.threadCount) DOM.threadCount.value = cfg.threadCount || 4;

            initialFieldValues = {};
            allModules.forEach(m => {
                const map = {};
                (m.configFields || []).forEach(f => {
                    map[f.key] = serializeFieldValue(f.value, f.type);
                });
                initialFieldValues[m.id] = map;
            });

            renderModules();
            updateEstimate();
            console.log('[SystemCheck] 配置加载成功，模块数 =', allModules.length);
        } catch (e) {
            console.error('[SystemCheck] 加载配置失败', e);
            if (DOM.moduleList) {
                DOM.moduleList.innerHTML = `<div class="sys-error">❌ 加载配置失败：${escapeHtml(e.message)}<br><br>
                    <button class="btn-secondary" onclick="location.reload()">🔄 重试</button></div>`;
            }
        }
    }

    // ==================== 渲染模块列表 ====================
    function renderModules() {
        if (!DOM.moduleList) return;
        if (!allModules.length) {
            DOM.moduleList.innerHTML = '<div class="sys-empty">未配置任何检查模块</div>';
            return;
        }
        let html = '';
        allModules.forEach(m => {
            const hasConfig = Array.isArray(m.configFields) && m.configFields.length > 0;
            const fieldsHtml = hasConfig
                ? m.configFields.map(f => renderConfigField(m.id, f)).join('')
                : '<div class="config-field"><span style="color:#94a3b8;font-size:12px;">该模块无额外配置项</span></div>';

            html += `
                <div class="module-item ${m.enabled ? '' : 'disabled'}" data-module-id="${escapeHtml(m.id)}">
                    <div class="module-header">
                        <input type="checkbox" class="check-box" data-id="${escapeHtml(m.id)}" ${m.enabled ? 'checked' : ''}>
                        <span class="icon">${m.icon || '📦'}</span>
                        <span class="name">${escapeHtml(m.name || m.id)}</span>
                        <span class="id">${escapeHtml(m.id)}</span>
                        <span class="estimated">⏱ ~${m.estimatedSeconds || 5}s</span>
                        <button class="expand-btn ${hasConfig ? '' : 'no-config'}" title="展开/收起配置">${hasConfig ? '▶' : '·'}</button>
                    </div>
                    <div class="module-config">${fieldsHtml}</div>
                </div>`;
        });
        DOM.moduleList.innerHTML = html;

        DOM.moduleList.querySelectorAll('.module-header .check-box').forEach(cb => cb.addEventListener('change', updateEstimate));
        DOM.moduleList.querySelectorAll('.expand-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const item = btn.closest('.module-item');
                const config = item.querySelector('.module-config');
                if (!config) return;
                const open = config.classList.toggle('open');
                btn.textContent = open ? '▼' : '▶';
            });
        });
    }

    function renderConfigField(moduleId, f) {
        const value = f.value;
        const hint = f.hint ? `<div class="field-hint">💡 ${escapeHtml(f.hint)}</div>` : '';
        const attrs = `data-module="${escapeHtml(moduleId)}" data-field="${escapeHtml(f.key)}" data-type="${escapeHtml(f.type || 'text')}"`;

        if (f.type === 'list') {
            const display = Array.isArray(value) ? value.join(', ') : String(value || '');
            return `<div class="config-field"><label>${escapeHtml(f.label)}</label>
                <input type="text" class="field-input" ${attrs} value="${escapeHtml(display)}" placeholder="逗号分隔">${hint}</div>`;
        }
        if (f.type === 'number') {
            return `<div class="config-field"><label>${escapeHtml(f.label)}</label>
                <input type="number" class="field-input" ${attrs} value="${value != null ? value : ''}">${hint}</div>`;
        }
        if (f.type === 'textarea') {
            return `<div class="config-field"><label>${escapeHtml(f.label)}</label>
                <textarea class="field-input" ${attrs} rows="4">${escapeHtml(value != null ? String(value) : '')}</textarea>${hint}</div>`;
        }
        return `<div class="config-field"><label>${escapeHtml(f.label)}</label>
            <input type="text" class="field-input" ${attrs} value="${escapeHtml(value != null ? String(value) : '')}">${hint}</div>`;
    }

    function updateEstimate() {
        let total = 0, count = 0;
        DOM.moduleList.querySelectorAll('.module-item').forEach(item => {
            const cb = item.querySelector('.check-box');
            if (cb && cb.checked) {
                const m = allModules.find(x => x.id === item.dataset.moduleId);
                if (m) { total += m.estimatedSeconds || 5; count++; item.classList.remove('disabled'); }
            } else {
                item.classList.add('disabled');
            }
        });
        if (DOM.estimatedTotal) DOM.estimatedTotal.textContent = total;
        if (DOM.selectedCount) DOM.selectedCount.textContent = count;
    }

    function collectConfig() {
        const checks = [];
        const moduleConfig = {};
        DOM.moduleList.querySelectorAll('.module-item').forEach(item => {
            const cb = item.querySelector('.check-box');
            if (!cb || !cb.checked) return;
            const moduleId = item.dataset.moduleId;
            checks.push(moduleId);

            const fields = {};
            item.querySelectorAll('.field-input').forEach(input => {
                const fieldKey = input.dataset.field;
                const type = input.dataset.type || 'text';
                const raw = input.value;
                let currentSerialized, parsedValue;

                if (type === 'number') {
                    if (raw === '' || isNaN(parseFloat(raw))) { currentSerialized = raw; parsedValue = undefined; }
                    else { const n = parseFloat(raw); currentSerialized = String(n); parsedValue = n; }
                } else if (type === 'list') {
                    const arr = raw.split(',').map(s => s.trim()).filter(s => s);
                    currentSerialized = arr.join(',');
                    parsedValue = arr;
                } else {
                    currentSerialized = raw;
                    parsedValue = raw;
                }

                const initialSerialized = (initialFieldValues[moduleId] || {})[fieldKey];
                if (parsedValue !== undefined && currentSerialized !== initialSerialized) {
                    fields[fieldKey] = parsedValue;
                }
            });
            if (Object.keys(fields).length) moduleConfig[moduleId] = fields;
        });
        return { checks, threadCount: parseInt(DOM.threadCount.value) || 4, moduleConfig };
    }

    // ==================== 开始检查 ====================
    async function startCheck() {
        let payload = collectConfig();
        if (payload.checks.length === 0) {
            DOM.moduleList.querySelectorAll('.check-box').forEach(cb => cb.checked = true);
            updateEstimate();
            payload = collectConfig();
            if (payload.checks.length === 0) {
                alert('没有可执行的检查模块');
                return;
            }
        }

        DOM.runBtn.disabled = true;
        DOM.runBtn.textContent = '⏳ 启动中...';

        switchTab('result');
        DOM.emptyState.style.display = 'none';
        DOM.resultPanel.style.display = 'none';
        DOM.runningPanel.style.display = 'block';
        DOM.progressFill.style.width = '0%';
        DOM.runningTimer.textContent = '00:00';
        DOM.elapsedText.textContent = '0.0s';
        DOM.progressText.textContent = `0 / ${payload.checks.length}`;
        DOM.cancelBtn.disabled = true;
        DOM.cancelBtn.textContent = '⏳ 正在启动...';

        const selectedModules = allModules.filter(m => payload.checks.includes(m.id));
        DOM.runningModules.innerHTML = selectedModules.map(m => `
            <div class="run-module state-PENDING">
                <div class="mod-row">
                    <span class="state-icon">⚪</span>
                    <span class="mod-name">${escapeHtml(m.name || m.id)}</span>
                    <span class="mod-time">等待中</span>
                </div>
            </div>
        `).join('');

        try {
            const resp = await fetch(API + '/run', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await resp.json();
            if (!data.success) throw new Error(data.error || '启动失败');

            currentTaskId = data.taskId;
            taskStartTime = Date.now();
            lastStatus = null;
            DOM.cancelBtn.disabled = false;
            DOM.cancelBtn.textContent = '⏹ 停止检查';

            startTimer();
            startPolling();
        } catch (e) {
            DOM.runningModules.innerHTML = `<div class="sys-error" style="padding:20px;">
                ❌ 启动失败：${escapeHtml(e.message)}<br>
                <button class="btn-secondary" style="margin-top:15px;" onclick="location.reload()">🔄 重新开始</button>
            </div>`;
            DOM.cancelBtn.disabled = true;
        } finally {
            DOM.runBtn.disabled = false;
            DOM.runBtn.textContent = '🚀 开始检查';
        }
    }

    // ==================== 计时器 ====================
    function startTimer() {
        stopTimer();
        timerTick = setInterval(() => {
            if (!taskStartTime) return;
            const elapsed = (Date.now() - taskStartTime) / 1000;
            const mm = String(Math.floor(elapsed / 60)).padStart(2, '0');
            const ss = String(Math.floor(elapsed % 60)).padStart(2, '0');
            if (DOM.runningTimer) DOM.runningTimer.textContent = `${mm}:${ss}`;
            if (DOM.elapsedText) DOM.elapsedText.textContent = elapsed.toFixed(1) + 's';
        }, 200);
    }
    function stopTimer() {
        if (timerTick) { clearInterval(timerTick); timerTick = null; }
    }

    // ==================== 轮询 ====================
    let pollErrorCount = 0;
    function startPolling() {
        stopPolling();
        pollErrorCount = 0;
        pollTimer = setInterval(pollStatus, 1000);
        pollStatus();
    }
    function stopPolling() {
        if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    }

    async function pollStatus() {
        if (!currentTaskId) return;
        try {
            const resp = await fetch(API + '/status?taskId=' + encodeURIComponent(currentTaskId));
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            const data = await resp.json();

            if (!data.success) {
                stopPolling(); stopTimer();
                DOM.runningModules.innerHTML = `<div class="sys-error" style="padding:20px;">
                    ❌ 状态获取失败：${escapeHtml(data.error || '任务丢失')}<br>
                    <button class="btn-secondary" style="margin-top:15px;" onclick="location.reload()">🔄 刷新页面</button>
                </div>`;
                return;
            }
            pollErrorCount = 0;
            lastStatus = data;
            renderRunning(data);

            if (data.state !== 'RUNNING') {
                stopPolling(); stopTimer();
                handleFinalState(data);
            }
        } catch (e) {
            pollErrorCount++;
            console.error('轮询异常，尝试次数:', pollErrorCount, e);
            if (pollErrorCount >= 3) {
                stopPolling(); stopTimer();
                DOM.runningModules.innerHTML = `<div class="sys-error" style="padding:20px;color:#f59e0b;">
                    ⚠️ 与后端失去连接 (${escapeHtml(e.message)})<br>
                    <button class="btn-secondary" style="margin-top:15px;" onclick="location.reload()">🔄 刷新页面</button>
                </div>`;
            }
        }
    }

    // ==================== 渲染运行状态（★ 本次核心改动） ====================
    function renderRunning(s) {
        if (DOM.progressText) DOM.progressText.textContent = `${s.completedModules} / ${s.totalModules}`;
        if (DOM.estimatedText) DOM.estimatedText.textContent = s.estimatedTotalSeconds || 0;

        const pct = s.totalModules > 0 ? (s.completedModules / s.totalModules) * 100 : 0;
        if (DOM.progressFill) DOM.progressFill.style.width = pct + '%';

        const modules = s.modules || [];
        if (DOM.runningModules && modules.length > 0) {
            DOM.runningModules.innerHTML = modules.map(m => {
                let icon = '⏳', timeText = '';
                if (m.state === 'PENDING') icon = '⚪';
                else if (m.state === 'RUNNING') {
                    icon = '🔄';
                    timeText = ((Date.now() - m.startTime) / 1000).toFixed(1) + 's';
                } else if (m.state === 'SUCCESS') { icon = '✅'; timeText = (m.durationMs / 1000).toFixed(2) + 's'; }
                else if (m.state === 'FAILED') { icon = '❌'; timeText = (m.durationMs / 1000).toFixed(2) + 's'; }
                else if (m.state === 'CANCELLED') { icon = '⏹'; timeText = (m.durationMs / 1000).toFixed(2) + 's'; }

                // ★ 新增：正在检查的文件（仅 RUNNING 且有 currentFile 时显示）
                let fileHtml = '';
                if (m.state === 'RUNNING' && m.currentFile) {
                    const display = m.currentFile.length > 60
                        ? '…' + m.currentFile.slice(-57) : m.currentFile;
                    fileHtml = `<div class="mod-file" title="${escapeHtml(m.currentFile)}">📄 ${escapeHtml(display)}</div>`;
                }

                return `
                    <div class="run-module state-${m.state}">
                        <div class="mod-row">
                            <span class="state-icon">${icon}</span>
                            <span class="mod-name">${escapeHtml(m.checkName)}</span>
                            <span class="mod-time">${timeText}</span>
                        </div>
                        ${fileHtml}
                    </div>`;
            }).join('');
        }
    }

    // ==================== 结束处理 ====================
    function handleFinalState(s) {
        DOM.runningPanel.style.display = 'none';
        DOM.resultPanel.style.display = 'flex';

        if (s.state === 'CANCELLED') {
            DOM.resultBody.innerHTML = `
                <div class="sys-empty">
                    <div style="font-size:48px;margin-bottom:16px;">⏹</div>
                    <div>检查已取消</div>
                    <div style="font-size:12px;margin-top:8px;">已用时间：${(s.elapsedSeconds || 0).toFixed(2)}s</div>
                    <button class="btn-secondary" style="margin-top:20px;" onclick="document.querySelector('.sys-tab[data-tab=config]').click()">返回配置</button>
                </div>`;
            DOM.summaryStats.innerHTML = '';
            DOM.totalDuration.textContent = (s.elapsedSeconds || 0).toFixed(2) + 's';
            return;
        }

        if (s.state === 'ERROR') {
            DOM.resultBody.innerHTML = `<div class="sys-error">❌ 检查执行错误：${escapeHtml(s.errorMessage || '未知')}</div>`;
            return;
        }

        renderResult(s);
        DOM.exportHtmlBtn.disabled = false;
        DOM.exportCsvBtn.disabled = false;
        DOM.exportJsonBtn.disabled = false;
    }

    function renderResult(data) {
        const results = data.results || [];
        const summary = data.summary || { ok:0, bad:0, warn:0, info:0 };
        const elapsedSeconds = data.elapsedSeconds || 0;

        DOM.summaryStats.innerHTML = `
            <div class="item ok"><span>合规</span><span class="num">${summary.ok}</span></div>
            <div class="item bad"><span>不合规</span><span class="num">${summary.bad}</span></div>
            <div class="item warn"><span>警告</span><span class="num">${summary.warn}</span></div>
            <div class="item info"><span>信息</span><span class="num">${summary.info}</span></div>
            <div class="item time"><span>实际耗时</span><span class="num">${elapsedSeconds.toFixed(2)}s</span></div>
        `;
        DOM.totalDuration.textContent = elapsedSeconds.toFixed(2) + 's';

        let html = `
            <div style="margin-bottom:12px;padding:10px 16px;background:rgba(0,102,255,0.06);
                        border-radius:12px;font-size:13px;display:flex;gap:24px;flex-wrap:wrap;">
                <span>✅ <strong>实际总耗时</strong>：${elapsedSeconds.toFixed(2)} 秒</span>
                <span>📊 <strong>执行模块</strong>：${results.length} 个</span>
            </div>`;

        for (const r of results) {
            const findings = r.findings || [];
            const isCollapsed = collapsedGroups.has(r.checkId);
            const groupCls = isCollapsed ? 'collapsed' : '';
            const headerCls = isCollapsed ? 'collapsed' : '';

            html += `<div class="sys-check-group">
                <div class="sys-group-header ${headerCls}" data-toggle="${escapeHtml(r.checkId)}">
                    <div class="left">
                        <span class="arrow">▼</span>
                        <span>${escapeHtml(r.checkName || r.checkId)}</span>
                    </div>
                    <div class="timing">
                        <span class="actual">✅ ${(r.durationMs/1000).toFixed(2)}s</span>
                        <span>📋 ${findings.length} 条</span>
                    </div>
                </div>
                <div class="sys-group-body ${groupCls}">`;

            if (!r.success) {
                html += `<div class="sys-error">❌ 执行失败：${escapeHtml(r.errorMessage || '未知')}</div>`;
            } else if (findings.length === 0) {
                html += `<div class="sys-empty">无数据</div>`;
            } else {
                for (const f of findings) {
                    html += `
                        <div class="sys-finding-row">
                            <span class="status status-${escapeHtml(f.status || '信息')}">${escapeHtml(f.status)}</span>
                            <div class="body">
                                <div class="item-name">${escapeHtml(f.item)}</div>
                                <div class="detail">${escapeHtml(f.detail)}</div>
                                ${f.suggestion ? `<div class="suggestion">💡 ${escapeHtml(f.suggestion)}</div>` : ''}
                            </div>
                        </div>`;
                }
            }
            html += `</div></div>`;
        }
        DOM.resultBody.innerHTML = html;

        DOM.resultBody.querySelectorAll('[data-toggle]').forEach(el => {
            el.addEventListener('click', () => {
                const gid = el.dataset.toggle;
                const body = el.nextElementSibling;
                if (!body) return;
                const isCollapsed = body.classList.toggle('collapsed');
                el.classList.toggle('collapsed', isCollapsed);
                if (isCollapsed) collapsedGroups.add(gid);
                else collapsedGroups.delete(gid);
            });
        });
    }

    async function cancelCheck() {
        if (!currentTaskId) return;
        if (!confirm('确定停止检查吗？')) return;
        DOM.cancelBtn.disabled = true;
        DOM.cancelBtn.textContent = '⏳ 停止中...';
        try {
            const resp = await fetch(API + '/cancel?taskId=' + encodeURIComponent(currentTaskId), { method: 'POST' });
            const data = await resp.json();
            if (!data.success) alert('取消失败：' + (data.message || '未知'));
        } catch (e) {
            alert('取消请求失败：' + e.message);
        }
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"]/g, m => ({
            '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;'
        }[m]));
    }

    // ==================== 事件绑定 ====================
    DOM.runBtn.addEventListener('click', startCheck);
    DOM.cancelBtn.addEventListener('click', cancelCheck);
    DOM.backToConfigBtn.addEventListener('click', () => {
        DOM.resultPanel.style.display = 'none';
        DOM.emptyState.style.display = 'block';
        switchTab('config');
    });
    DOM.exportHtmlBtn.addEventListener('click', () => window.open(API + '/export/html', '_blank'));
    DOM.exportCsvBtn.addEventListener('click',  () => window.open(API + '/export/csv',  '_blank'));
    DOM.exportJsonBtn.addEventListener('click', () => window.open(API + '/export/json', '_blank'));

    window.addEventListener('beforeunload', () => { stopPolling(); stopTimer(); });

    console.log('[SystemCheck] 页面脚本已加载, API =', API);
    loadConfig();
})();