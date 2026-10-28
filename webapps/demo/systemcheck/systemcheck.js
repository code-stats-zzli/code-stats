(function() {
    const API = window.apiUrl('/systemcheck/api');

    let allChecks = [];       // [{id, name, icon, enabled}]
    let lastResult = null;
    let collapsedGroups = new Set();

    const checkListEl     = document.getElementById('checkList');
    const threadCountEl   = document.getElementById('threadCount');
    const runBtn          = document.getElementById('runBtn');
    const resetBtn        = document.getElementById('resetBtn');
    const resultBody      = document.getElementById('resultBody');
    const summaryStats    = document.getElementById('summaryStats');
    const exportHtmlBtn   = document.getElementById('exportHtmlBtn');
    const exportCsvBtn    = document.getElementById('exportCsvBtn');
    const exportJsonBtn   = document.getElementById('exportJsonBtn');

    // ---------- 加载默认配置 ----------
    async function loadConfig() {
        try {
            const resp = await fetch(API + '/config');
            const cfg = await resp.json();
            allChecks = cfg.checks || [];
            threadCountEl.value = cfg.threadCount || 4;
            renderCheckList();
        } catch (e) {
            checkListEl.innerHTML = `<div class="sys-error">❌ 加载配置失败：${e.message}</div>`;
        }
    }

    function renderCheckList() {
        checkListEl.innerHTML = allChecks.map(c => `
            <label class="sys-check-item">
                <input type="checkbox" data-id="${c.id}" ${c.enabled ? 'checked' : ''}>
                <span class="icon">${c.icon}</span>
                <span class="name">${c.name}</span>
            </label>
        `).join('');
    }

    // ---------- 执行检查 ----------
    async function runCheck() {
        const selected = [];
        document.querySelectorAll('#checkList input[type="checkbox"]:checked')
            .forEach(cb => selected.push(cb.dataset.id));
        if (selected.length === 0) {
            alert('请至少勾选一个检查项');
            return;
        }
        const threadCount = parseInt(threadCountEl.value) || 4;

        runBtn.disabled = true;
        runBtn.textContent = '⏳ 检查中...';
        exportHtmlBtn.disabled = true;
        exportCsvBtn.disabled = true;
        exportJsonBtn.disabled = true;

        resultBody.innerHTML = `
            <div class="sys-loading">
                <div class="sys-spinner"></div>
                <div>正在执行系统检查，共 ${selected.length} 项，${threadCount} 线程并发...</div>
                <div style="font-size:12px; color:#94a3b8; margin-top:8px;">
                    依赖漏洞扫描和明文密码扫描可能需要较长时间
                </div>
            </div>`;

        try {
            const resp = await fetch(API + '/run', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ checks: selected, threadCount })
            });
            const data = await resp.json();
            if (!data.success) throw new Error(data.error || '未知错误');
            lastResult = data;
            renderResult(data);
            enableExportButtons();
        } catch (e) {
            resultBody.innerHTML = `<div class="sys-error">❌ 检查失败：${e.message}</div>`;
        } finally {
            runBtn.disabled = false;
            runBtn.textContent = '🚀 开始检查';
        }
    }

    // ---------- 渲染结果 ----------
    function renderResult(data) {
        const results = data.results || [];
        const summary = data.summary || { ok:0, bad:0, warn:0, info:0 };

        // 汇总
        summaryStats.innerHTML = `
            <div class="item ok"><span>合规</span><span class="num">${summary.ok}</span></div>
            <div class="item bad"><span>不合规</span><span class="num">${summary.bad}</span></div>
            <div class="item warn"><span>警告</span><span class="num">${summary.warn}</span></div>
            <div class="item info"><span>信息</span><span class="num">${summary.info}</span></div>
        `;

        if (!results.length) {
            resultBody.innerHTML = '<div class="sys-empty">未返回任何结果</div>';
            return;
        }

        let html = '';
        for (const r of results) {
            const findings = r.findings || [];
            const isCollapsed = collapsedGroups.has(r.checkId);
            const groupCls = isCollapsed ? 'collapsed' : '';
            const arrow = isCollapsed ? '▶' : '▼';

            html += `<div class="sys-check-group" data-group="${r.checkId}">
                <div class="sys-group-header" data-group-toggle="${r.checkId}">
                    <span>${arrow} ${escapeHtml(r.checkName || r.checkId)}</span>
                    <span class="count">${findings.length} 条 · ${r.durationMs || 0} ms</span>
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
        resultBody.innerHTML = html;

        // 绑定折叠/展开
        resultBody.querySelectorAll('[data-group-toggle]').forEach(el => {
            el.addEventListener('click', () => {
                const gid = el.dataset.groupToggle;
                const body = el.nextElementSibling;
                if (!body) return;
                if (collapsedGroups.has(gid)) {
                    collapsedGroups.delete(gid);
                    body.classList.remove('collapsed');
                    el.firstElementChild.textContent = '▼ ' + el.firstElementChild.textContent.substring(2);
                } else {
                    collapsedGroups.add(gid);
                    body.classList.add('collapsed');
                    el.firstElementChild.textContent = '▶ ' + el.firstElementChild.textContent.substring(2);
                }
            });
        });
    }

    function enableExportButtons() {
        exportHtmlBtn.disabled = false;
        exportCsvBtn.disabled = false;
        exportJsonBtn.disabled = false;
    }

    function resetConfig() {
        loadConfig();
        lastResult = null;
        collapsedGroups.clear();
        resultBody.innerHTML = '<div class="sys-empty">点击「开始检查」执行系统安全检查</div>';
        summaryStats.innerHTML = `
            <div class="item ok"><span>合规</span><span class="num">0</span></div>
            <div class="item bad"><span>不合规</span><span class="num">0</span></div>
            <div class="item warn"><span>警告</span><span class="num">0</span></div>
            <div class="item info"><span>信息</span><span class="num">0</span></div>`;
        exportHtmlBtn.disabled = true;
        exportCsvBtn.disabled = true;
        exportJsonBtn.disabled = true;
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"]/g, m => ({
            '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;'
        }[m]));
    }

    // ---------- 事件绑定 ----------
    runBtn.addEventListener('click', runCheck);
    resetBtn.addEventListener('click', resetConfig);
    exportHtmlBtn.addEventListener('click', () => window.open(API + '/export/html', '_blank'));
    exportCsvBtn.addEventListener('click', () => window.open(API + '/export/csv', '_blank'));
    exportJsonBtn.addEventListener('click', () => window.open(API + '/export/json', '_blank'));

    // 启动
    loadConfig();
})();