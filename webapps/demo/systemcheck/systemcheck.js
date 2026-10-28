(function() {
    const API = window.apiUrl('/systemcheck/api');

    let allModules = [];       // 后端返回的模块列表（含 configFields）
    let lastResult = null;
    let collapsedGroups = new Set();

    const DOM = {
        tabs:          document.querySelectorAll('.sys-tab'),
        panes: {
            config: document.getElementById('configPane'),
            result: document.getElementById('resultPane')
        },
        threadCount:    document.getElementById('threadCount'),
        moduleList:     document.getElementById('moduleList'),
        estimatedTotal: document.getElementById('estimatedTotal'),
        selectedCount:  document.getElementById('selectedCount'),
        runBtn:         document.getElementById('runBtn'),
        summaryStats:   document.getElementById('summaryStats'),
        totalDuration:  document.getElementById('totalDuration'),
        resultBody:     document.getElementById('resultBody'),
        exportHtmlBtn:  document.getElementById('exportHtmlBtn'),
        exportCsvBtn:   document.getElementById('exportCsvBtn'),
        exportJsonBtn:  document.getElementById('exportJsonBtn')
    };

    // ==================== Tab 切换 ====================
    function switchTab(tabId) {
        DOM.tabs.forEach(t => t.classList.toggle('active', t.dataset.tab === tabId));
        Object.keys(DOM.panes).forEach(k => {
            DOM.panes[k].classList.toggle('active', k === tabId);
        });
    }
    DOM.tabs.forEach(t => {
        t.addEventListener('click', () => switchTab(t.dataset.tab));
    });

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

    // ==================== 渲染模块列表 ====================
    function renderModules() {
        if (!allModules.length) {
            DOM.moduleList.innerHTML = '<div class="sys-empty">未配置任何检查模块</div>';
            return;
        }
        DOM.moduleList.innerHTML = allModules.map(m => renderModule(m)).join('');

        // 绑定事件
        DOM.moduleList.querySelectorAll('.module-header .check-box').forEach(cb => {
            cb.addEventListener('change', updateEstimate);
        });
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
            : '<div class="config-field no-config-hint">该模块无额外配置项</div>';

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
            </div>
        `;
    }

    function renderConfigField(moduleId, f) {
        const value = f.value;
        const hint = f.hint ? `<div class="field-hint">💡 ${escapeHtml(f.hint)}</div>` : '';
        const commonAttrs = `data-module="${escapeHtml(moduleId)}" data-field="${escapeHtml(f.key)}"`;

        if (f.type === 'list') {
            const display = Array.isArray(value) ? value.join(', ') : String(value || '');
            return `
                <div class="config-field">
                    <label>${escapeHtml(f.label)}</label>
                    <input type="text" class="field-input" ${commonAttrs}
                           value="${escapeHtml(display)}"
                           placeholder="逗号分隔">
                    ${hint}
                </div>`;
        }
        if (f.type === 'number') {
            return `
                <div class="config-field">
                    <label>${escapeHtml(f.label)}</label>
                    <input type="number" class="field-input" ${commonAttrs}
                           value="${value != null ? value : ''}">
                    ${hint}
                </div>`;
        }
        // 兜底：text
        return `
            <div class="config-field">
                <label>${escapeHtml(f.label)}</label>
                <input type="text" class="field-input" ${commonAttrs}
                       value="${escapeHtml(value != null ? String(value) : '')}">
                ${hint}
            </div>`;
    }

    // ==================== 实时更新预估总耗时 ====================
    function updateEstimate() {
        let total = 0;
        let count = 0;
        DOM.moduleList.querySelectorAll('.module-item').forEach(item => {
            const cb = item.querySelector('.check-box');
            if (cb && cb.checked) {
                const moduleId = item.dataset.moduleId;
                const m = allModules.find(x => x.id === moduleId);
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

    // ==================== 收集用户配置 ====================
    function collectConfig() {
        const checks = [];
        const moduleConfig = {};

        DOM.moduleList.querySelectorAll('.module-item').forEach(item => {
            const cb = item.querySelector('.check-box');
            const moduleId = item.dataset.moduleId;
            if (!cb || !cb.checked) return;
            checks.push(moduleId);

            const fields = {};
            item.querySelectorAll('.field-input').forEach(input => {
                const fieldKey = input.dataset.field;
                const type = input.type === 'number' ? 'number' : 'text';
                const raw = input.value;
                if (type === 'number') {
                    if (raw !== '' && !isNaN(parseFloat(raw))) fields[fieldKey] = parseFloat(raw);
                } else {
                    const m = allModules.find(x => x.id === moduleId);
                    const fieldDef = m && m.configFields && m.configFields.find(f => f.key === fieldKey);
                    if (fieldDef && fieldDef.type === 'list') {
                        fields[fieldKey] = raw.split(',').map(s => s.trim()).filter(s => s);
                    } else {
                        fields[fieldKey] = raw;
                    }
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

    // ==================== 执行检查 ====================
    async function runCheck() {
        const payload = collectConfig();
        if (payload.checks.length === 0) {
            alert('请至少勾选一个检查模块');
            return;
        }

        // 禁用按钮、切换到结果 Tab
        DOM.runBtn.disabled = true;
        DOM.runBtn.textContent = '⏳ 检查中...';
        DOM.exportHtmlBtn.disabled = true;
        DOM.exportCsvBtn.disabled = true;
        DOM.exportJsonBtn.disabled = true;
        switchTab('result');

        const estTotal = allModules
            .filter(m => payload.checks.includes(m.id))
            .reduce((s, m) => s + (m.estimatedSeconds || 5), 0);

        DOM.resultBody.innerHTML = `
            <div class="sys-loading">
                <div class="sys-spinner"></div>
                <div>正在执行系统检查，共 ${payload.checks.length} 项</div>
                <div style="font-size:12px;color:#94a3b8;margin-top:8px;">
                    预计耗时约 ${estTotal} 秒 · ${payload.threadCount} 线程并发
                </div>
                <div style="font-size:12px;color:#94a3b8;margin-top:4px;">
                    明文扫描 / 依赖漏洞扫描可能较慢，请耐心等待...
                </div>
            </div>`;

        try {
            const resp = await fetch(API + '/run', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await resp.json();
            if (!data.success) throw new Error(data.error || '未知错误');
            lastResult = data;
            renderResult(data);
            enableExportButtons();
        } catch (e) {
            DOM.resultBody.innerHTML = `<div class="sys-error">❌ 检查失败：${escapeHtml(e.message)}</div>`;
        } finally {
            DOM.runBtn.disabled = false;
            DOM.runBtn.textContent = '🚀 开始检查';
        }
    }

    // ==================== 渲染结果 ====================
    function renderResult(data) {
        const results = data.results || [];
        const summary = data.summary || { ok:0, bad:0, warn:0, info:0 };
        const totalDurationMs = data.totalDurationMs || 0;
        const estimatedTotalSeconds = data.estimatedTotalSeconds || 0;

        DOM.summaryStats.innerHTML = `
            <div class="item ok"><span>合规</span><span class="num">${summary.ok}</span></div>
            <div class="item bad"><span>不合规</span><span class="num">${summary.bad}</span></div>
            <div class="item warn"><span>警告</span><span class="num">${summary.warn}</span></div>
            <div class="item info"><span>信息</span><span class="num">${summary.info}</span></div>
            <div class="item time"><span>实际耗时</span><span class="num" id="totalDuration">${(totalDurationMs / 1000).toFixed(2)}s</span></div>
        `;

        if (!results.length) {
            DOM.resultBody.innerHTML = '<div class="sys-empty">未返回任何结果</div>';
            return;
        }

        // 顶部汇总时间
        const timeSummary = `
            <div style="margin-bottom:12px;padding:10px 16px;background:rgba(0,102,255,0.06);
                        border-radius:12px;font-size:13px;display:flex;gap:24px;flex-wrap:wrap;">
                <span>⏱ <strong>预计耗时</strong>：约 ${estimatedTotalSeconds} 秒</span>
                <span>✅ <strong>实际耗时</strong>：${(totalDurationMs / 1000).toFixed(2)} 秒</span>
                <span>📊 <strong>执行模块</strong>：${results.length} 个</span>
            </div>`;

        let html = timeSummary;
        for (const r of results) {
            const findings = r.findings || [];
            const isCollapsed = collapsedGroups.has(r.checkId);
            const groupCls = isCollapsed ? 'collapsed' : '';
            const headerCls = isCollapsed ? 'collapsed' : '';

            html += `<div class="sys-check-group" data-group="${escapeHtml(r.checkId)}">
                <div class="sys-group-header ${headerCls}" data-group-toggle="${escapeHtml(r.checkId)}">
                    <div class="left">
                        <span class="arrow">▼</span>
                        <span>${escapeHtml(r.checkName || r.checkId)}</span>
                    </div>
                    <div class="timing">
                        <span class="actual">✅ 实际 ${(r.durationMs || 0) / 1000}s</span>
                        <span class="est">⏱ 预估 ~${r.estimatedSeconds || 0}s</span>
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

        // 折叠/展开
        DOM.resultBody.querySelectorAll('[data-group-toggle]').forEach(el => {
            el.addEventListener('click', () => {
                const gid = el.dataset.groupToggle;
                const body = el.nextElementSibling;
                if (!body) return;
                const isCollapsed = body.classList.toggle('collapsed');
                el.classList.toggle('collapsed', isCollapsed);
                if (isCollapsed) collapsedGroups.add(gid);
                else collapsedGroups.delete(gid);
            });
        });
    }

    function enableExportButtons() {
        DOM.exportHtmlBtn.disabled = false;
        DOM.exportCsvBtn.disabled = false;
        DOM.exportJsonBtn.disabled = false;
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"]/g, m => ({
            '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;'
        }[m]));
    }

    // ==================== 事件绑定 ====================
    DOM.runBtn.addEventListener('click', runCheck);
    DOM.exportHtmlBtn.addEventListener('click', () => window.open(API + '/export/html', '_blank'));
    DOM.exportCsvBtn.addEventListener('click',  () => window.open(API + '/export/csv',  '_blank'));
    DOM.exportJsonBtn.addEventListener('click', () => window.open(API + '/export/json', '_blank'));

    // ==================== 启动 ====================
    loadConfig();
})();