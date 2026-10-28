(function() {
    const API = window.apiUrl('/systemcheck/api');

    let allModules = [];
    let currentTaskId = null;
    let pollTimer = null;
    let timerTick = null;
    let taskStartTime = 0;
    let lastStatus = null;
    let collapsedGroups = new Set();

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
        // 运行中
        runningPanel:   document.getElementById('runningPanel'),
        runningTimer:   document.getElementById('runningTimer'),
        elapsedText:    document.getElementById('elapsedText'),
        progressText:   document.getElementById('progressText'),
        estimatedText:  document.getElementById('estimatedText'),
        progressFill:   document.getElementById('progressFill'),
        runningModules: document.getElementById('runningModules'),
        cancelBtn:      document.getElementById('cancelBtn'),
        // 结果
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

    // ==================== Tab 切换 ====================
    function switchTab(id) {
        DOM.tabs.forEach(t => t.classList.toggle('active', t.dataset.tab === id));
        Object.keys(DOM.panes).forEach(k => DOM.panes[k].classList.toggle('active', k === id));
    }
    DOM.tabs.forEach(t => t.addEventListener('click', () => switchTab(t.dataset.tab)));

    // ==================== 加载配置 ====================
    async function loadConfig() {
        try {
            const resp = await fetch(API + '/config');
            const cfg = await resp.json();
            allModules = cfg.checks || [];
            DOM.threadCount.value = cfg.threadCount || 4;
            renderModules();
            updateEstimate();
        } catch (e) {
            DOM.moduleList.innerHTML = `<div class="sys-error">❌ 加载配置失败：${escapeHtml(e.message)}</div>`;
        }
    }

    // ==================== 渲染模块 ====================
    function renderModules() {
        if (!allModules.length) {
            DOM.moduleList.innerHTML = '<div class="sys-empty">未配置任何检查模块</div>';
            return;
        }
        DOM.moduleList.innerHTML = allModules.map(renderModule).join('');
        DOM.moduleList.querySelectorAll('.module-header .check-box')
            .forEach(cb => cb.addEventListener('change', updateEstimate));
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

    function renderModule(m) {
        const hasConfig = Array.isArray(m.configFields) && m.configFields.length > 0;
        const fieldsHtml = hasConfig
            ? m.configFields.map(f => renderConfigField(m.id, f)).join('')
            : '<div class="config-field"><span style="color:#94a3b8;font-size:12px;">该模块无额外配置项</span></div>';

        return `
            <div class="module-item ${m.enabled ? '' : 'disabled'}" data-module-id="${escapeHtml(m.id)}">
                <div class="module-header">
                    <input type="checkbox" class="check-box" data-id="${escapeHtml(m.id)}" ${m.enabled ? 'checked' : ''}>
                    <span class="icon">${m.icon || '📦'}</span>
                    <span class="name">${escapeHtml(m.name)}</span>
                    <span class="id">${escapeHtml(m.id)}</span>
                    <span class="estimated">⏱ ~${m.estimatedSeconds || 5}s</span>
                    <button class="expand-btn ${hasConfig ? '' : 'no-config'}" title="展开/收起配置">
                        ${hasConfig ? '▶' : '·'}
                    </button>
                </div>
                <div class="module-config">${fieldsHtml}</div>
            </div>`;
    }

    function renderConfigField(moduleId, f) {
        const value = f.value;
        const hint = f.hint ? `<div class="field-hint">💡 ${escapeHtml(f.hint)}</div>` : '';
        const attrs = `data-module="${escapeHtml(moduleId)}" data-field="${escapeHtml(f.key)}"`;

        if (f.type === 'list') {
            const display = Array.isArray(value) ? value.join(', ') : String(value || '');
            return `
                <div class="config-field">
                    <label>${escapeHtml(f.label)}</label>
                    <input type="text" class="field-input" ${attrs}
                           value="${escapeHtml(display)}" placeholder="逗号分隔">
                    ${hint}
                </div>`;
        }
        if (f.type === 'number') {
            return `
                <div class="config-field">
                    <label>${escapeHtml(f.label)}</label>
                    <input type="number" class="field-input" ${attrs}
                           value="${value != null ? value : ''}">
                    ${hint}
                </div>`;
        }
        if (f.type === 'textarea') {
            return `
                <div class="config-field">
                    <label>${escapeHtml(f.label)}</label>
                    <textarea class="field-input" ${attrs} rows="4">${escapeHtml(value != null ? String(value) : '')}</textarea>
                    ${hint}
                </div>`;
        }
        return `
            <div class="config-field">
                <label>${escapeHtml(f.label)}</label>
                <input type="text" class="field-input" ${attrs}
                       value="${escapeHtml(value != null ? String(value) : '')}">
                ${hint}
            </div>`;
    }

    // ==================== 预估总耗时 ====================
    function updateEstimate() {
        let total = 0, count = 0;
        DOM.moduleList.querySelectorAll('.module-item').forEach(item => {
            const cb = item.querySelector('.check-box');
            if (cb && cb.checked) {
                const m = allModules.find(x => x.id === item.dataset.moduleId);
                if (m) {
                    total += m.estimatedSeconds || 5;
                    count++;
                    item.classList.remove('disabled');
                }
            } else {
                item.classList.add('disabled');
            }
        });
        DOM.estimatedTotal.textContent = total;
        DOM.selectedCount.textContent = count;
    }

    // ==================== 收集配置 ====================
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
                const m = allModules.find(x => x.id === moduleId);
                const fieldDef = m && m.configFields && m.configFields.find(f => f.key === fieldKey);
                const type = fieldDef ? fieldDef.type : 'text';
                const raw = input.value;
                if (type === 'number') {
                    if (raw !== '' && !isNaN(parseFloat(raw))) fields[fieldKey] = parseFloat(raw);
                } else if (type === 'list') {
                    fields[fieldKey] = raw.split(',').map(s => s.trim()).filter(s => s);
                } else {
                    fields[fieldKey] = raw;
                }
            });
            if (Object.keys(fields).length) moduleConfig[moduleId] = fields;
        });
        return {
            checks,
            threadCount: parseInt(DOM.threadCount.value) || 4,
            moduleConfig
        };
    }

    // ==================== 开始检查（异步） ====================
    async function startCheck() {
        const payload = collectConfig();
        if (payload.checks.length === 0) {
            alert('请至少勾选一个检查模块');
            return;
        }

        DOM.runBtn.disabled = true;
        DOM.runBtn.textContent = '⏳ 启动中...';

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

            // 切到结果 Tab 显示运行面板
            switchTab('result');
            DOM.emptyState.style.display = 'none';
            DOM.resultPanel.style.display = 'none';
            DOM.runningPanel.style.display = 'block';
            DOM.runningModules.innerHTML = '';
            DOM.progressFill.style.width = '0%';
            DOM.runningTimer.textContent = '00:00';
            DOM.cancelBtn.disabled = false;
            DOM.cancelBtn.textContent = '⏹ 停止检查';

            startPolling();
            startTimer();
        } catch (e) {
            alert('启动失败：' + e.message);
        } finally {
            DOM.runBtn.disabled = false;
            DOM.runBtn.textContent = '🚀 开始检查';
        }
    }

    // ==================== 计时器 ====================
    function startTimer() {
        stopTimer();
        timerTick = setInterval(() => {
            if (!currentTaskId) return;
            const elapsed = (Date.now() - taskStartTime) / 1000;
            const mm = String(Math.floor(elapsed / 60)).padStart(2, '0');
            const ss = String(Math.floor(elapsed % 60)).padStart(2, '0');
            DOM.runningTimer.textContent = `${mm}:${ss}`;
            DOM.elapsedText.textContent = elapsed.toFixed(1) + 's';
        }, 200);
    }
    function stopTimer() {
        if (timerTick) { clearInterval(timerTick); timerTick = null; }
    }

    // ==================== 轮询状态 ====================
    function startPolling() {
        stopPolling();
        pollTimer = setInterval(pollStatus, 500);
        pollStatus();
    }
    function stopPolling() {
        if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    }

    async function pollStatus() {
        if (!currentTaskId) return;
        try {
            const resp = await fetch(API + '/status?taskId=' + encodeURIComponent(currentTaskId));
            const data = await resp.json();
            if (!data.success) {
                // 任务过期
                stopPolling();
                stopTimer();
                return;
            }
            lastStatus = data;
            renderRunning(data);

            if (data.state !== 'RUNNING') {
                stopPolling();
                stopTimer();
                handleFinalState(data);
            }
        } catch (e) {
            console.error('轮询失败', e);
        }
    }

    // ==================== 渲染运行状态 ====================
    function renderRunning(s) {
        const elapsed = (s.elapsedMs || 0) / 1000;
        const mm = String(Math.floor(elapsed / 60)).padStart(2, '0');
        const ss = String(Math.floor(elapsed % 60)).padStart(2, '0');
        DOM.runningTimer.textContent = `${mm}:${ss}`;
        DOM.elapsedText.textContent = elapsed.toFixed(1) + 's';
        DOM.progressText.textContent = `${s.completedModules} / ${s.totalModules}`;
        DOM.estimatedText.textContent = s.estimatedTotalSeconds || 0;

        const pct = s.totalModules > 0 ? (s.completedModules / s.totalModules) * 100 : 0;
        DOM.progressFill.style.width = pct + '%';

        DOM.runningModules.innerHTML = (s.modules || []).map(m => {
            let icon = '⏳', timeText = '';
            if (m.state === 'PENDING') icon = '⚪';
            else if (m.state === 'RUNNING') {
                icon = '🔄';
                timeText = ((Date.now() - m.startTime) / 1000).toFixed(1) + 's';
            } else if (m.state === 'SUCCESS') {
                icon = '✅';
                timeText = (m.durationMs / 1000).toFixed(2) + 's';
            } else if (m.state === 'FAILED') {
                icon = '❌';
                timeText = (m.durationMs / 1000).toFixed(2) + 's';
            } else if (m.state === 'CANCELLED') {
                icon = '⏹';
                timeText = (m.durationMs / 1000).toFixed(2) + 's';
            }
            return `
                <div class="run-module state-${m.state}">
                    <span class="state-icon">${icon}</span>
                    <span class="mod-name">${escapeHtml(m.checkName)}</span>
                    <span class="mod-time">${timeText}</span>
                </div>`;
        }).join('');
    }

    // ==================== 结束状态处理 ====================
    function handleFinalState(s) {
        DOM.runningPanel.style.display = 'none';
        DOM.resultPanel.style.display = 'flex';

        if (s.state === 'CANCELLED') {
            DOM.resultBody.innerHTML = `
                <div class="sys-empty">
                    <div style="font-size:48px;margin-bottom:16px;">⏹</div>
                    <div>检查已取消</div>
                    <div style="font-size:12px;margin-top:8px;">
                        已用时间：${(s.elapsedSeconds || 0).toFixed(2)}s · 完成 ${s.completedModules}/${s.totalModules} 个模块
                    </div>
                    <button class="btn-secondary" style="margin-top:20px;" onclick="document.querySelector('.sys-tab[data-tab=config]').click()">返回配置</button>
                </div>`;
            DOM.summaryStats.innerHTML = '';
            DOM.totalDuration.textContent = (s.elapsedSeconds || 0).toFixed(2) + 's';
            DOM.exportHtmlBtn.disabled = true;
            DOM.exportCsvBtn.disabled = true;
            DOM.exportJsonBtn.disabled = true;
            return;
        }

        if (s.state === 'ERROR') {
            DOM.resultBody.innerHTML = `<div class="sys-error">❌ 检查执行错误：${escapeHtml(s.errorMessage || '未知')}</div>`;
            return;
        }

        // COMPLETED
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

        if (!results.length) {
            DOM.resultBody.innerHTML = '<div class="sys-empty">未返回任何结果</div>';
            return;
        }

        const topSummary = `
            <div style="margin-bottom:12px;padding:10px 16px;background:rgba(0,102,255,0.06);
                        border-radius:12px;font-size:13px;display:flex;gap:24px;flex-wrap:wrap;">
                <span>⏱ <strong>预计总耗时</strong>：约 ${data.estimatedTotalSeconds || 0} 秒</span>
                <span>✅ <strong>实际总耗时</strong>：${elapsedSeconds.toFixed(2)} 秒</span>
                <span>📊 <strong>执行模块</strong>：${results.length} 个</span>
            </div>`;

        let html = topSummary;
        for (const r of results) {
            const findings = r.findings || [];
            const isCollapsed = collapsedGroups.has(r.checkId);
            const groupCls = isCollapsed ? 'collapsed' : '';
            const headerCls = isCollapsed ? 'collapsed' : '';
            const actualSec = (r.durationMs || 0) / 1000;
            const estSec = r.estimatedSeconds || 0;
            const durationIcon = actualSec > estSec * 1.5 ? '🐢' : (actualSec < estSec * 0.5 ? '⚡' : '✅');

            html += `<div class="sys-check-group">
                <div class="sys-group-header ${headerCls}" data-toggle="${escapeHtml(r.checkId)}">
                    <div class="left">
                        <span class="arrow">▼</span>
                        <span>${escapeHtml(r.checkName || r.checkId)}</span>
                    </div>
                    <div class="timing">
                        <span class="actual">${durationIcon} 实际 ${actualSec.toFixed(2)}s</span>
                        <span class="est">⏱ 预估 ~${estSec}s</span>
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

    // ==================== 取消 ====================
    async function cancelCheck() {
        if (!currentTaskId) return;
        if (!confirm('确定停止检查吗？已完成的模块结果会保留。')) return;
        DOM.cancelBtn.disabled = true;
        DOM.cancelBtn.textContent = '⏳ 停止中...';
        try {
            const resp = await fetch(API + '/cancel?taskId=' + encodeURIComponent(currentTaskId), { method: 'POST' });
            const data = await resp.json();
            if (!data.success) {
                alert('取消失败：' + (data.message || '未知'));
                DOM.cancelBtn.disabled = false;
                DOM.cancelBtn.textContent = '⏹ 停止检查';
            }
        } catch (e) {
            alert('取消请求失败：' + e.message);
            DOM.cancelBtn.disabled = false;
            DOM.cancelBtn.textContent = '⏹ 停止检查';
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

    // 页面卸载时清理定时器
    window.addEventListener('beforeunload', () => {
        stopPolling();
        stopTimer();
    });

    // 启动
    loadConfig();
})();