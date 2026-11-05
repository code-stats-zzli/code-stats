/**
 * SSH 远程端口/服务探测 Tab。
 *
 * 对外 API：
 *   window.PortScanTab.mount(containerEl, connIdGetter)
 *   window.PortScanTab.clear()
 *
 * 依赖：window.apiUrl 由 global.js 提供（无 global.js 时降级用 CONTEXT_PATH）。
 */
(function () {
    'use strict';

    let container = null;
    let getConnectionId = null;
    let scanning = false;
    let lastResult = null;

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"']/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[m]));
    }
    function apiUrl(path) {
        return (typeof window.apiUrl === 'function') ? window.apiUrl(path) : path;
    }

    function injectStyle() {
        if (document.getElementById('ps-style')) return;
        const style = document.createElement('style');
        style.id = 'ps-style';
        style.textContent = `
            .ps-wrap { flex: 1; display: flex; flex-direction: column; gap: 12px;
                       padding: 14px 16px; min-height: 0; overflow: hidden; box-sizing: border-box; }
            .ps-toolbar { display: flex; align-items: center; gap: 12px;
                          flex-wrap: wrap; flex-shrink: 0; }
            .ps-toolbar .ps-hint { font-size: 12px; color: #94a3b8; }
            .ps-toolbar input.ps-filter { width: 220px; padding: 6px 12px;
                border: 1px solid rgba(0,102,255,0.3); border-radius: 10px;
                font-family: 'JetBrains Mono', monospace; font-size: 12px;
                background: white; outline: none; }
            .ps-toolbar input.ps-filter:focus { border-color: var(--accent-primary, #0066ff); }
            .ps-summary { flex-shrink: 0; background: rgba(0,102,255,0.04);
                          border: 1px solid rgba(0,102,255,0.15);
                          border-radius: 10px; padding: 10px 14px;
                          display: flex; flex-direction: column; gap: 6px; }
            .ps-summary-row { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
            .ps-summary-label { font-size: 12px; color: var(--text-muted, #64748b); }
            .ps-chip { display: inline-block; padding: 2px 10px;
                       border-radius: 12px; font-size: 11.5px;
                       background: rgba(0,102,255,0.1);
                       color: var(--accent-primary, #0066ff);
                       border: 1px solid rgba(0,102,255,0.2);
                       font-family: 'JetBrains Mono', monospace; }
            .ps-chip-primary { background: var(--accent-primary, #0066ff); color: #fff; }
            .ps-table-wrap { flex: 1; min-height: 0; overflow: auto;
                             border: 1px solid rgba(0,102,255,0.15);
                             border-radius: 10px; background: rgba(255,255,255,0.65); }
            .ps-table { width: 100%; border-collapse: collapse; font-size: 12.5px; }
            .ps-table th { position: sticky; top: 0; z-index: 2;
                           background: rgba(0,102,255,0.1); color: var(--accent-primary, #0066ff);
                           padding: 8px 12px; text-align: left; font-weight: 600;
                           white-space: nowrap; }
            .ps-table td { padding: 6px 12px;
                           border-bottom: 1px solid rgba(0,0,0,0.05);
                           font-family: 'JetBrains Mono', monospace;
                           font-size: 12px; vertical-align: middle; }
            .ps-table tr:hover td { background: rgba(0,102,255,0.04); }
            .ps-port { font-weight: 700; color: var(--accent-primary, #0066ff); }
            .ps-addr { color: #64748b; }
            .ps-cat { display: inline-block; padding: 1px 8px; border-radius: 10px;
                      font-size: 11px; background: rgba(124,58,237,0.1);
                      color: #7c3aed; border: 1px solid rgba(124,58,237,0.25); }
            .ps-error { color: #dc2626; padding: 20px; font-size: 13px; text-align: center; }
            .ps-table-wrap::-webkit-scrollbar { width: 6px; height: 6px; }
            .ps-table-wrap::-webkit-scrollbar-thumb {
                background: rgba(0,102,255,0.3); border-radius: 4px; }
        `;
        document.head.appendChild(style);
    }

    function render() {
        if (!container) return;
        container.innerHTML = `
            <div class="ps-wrap">
                <div class="ps-toolbar">
                    <button id="psScanBtn" class="btn-primary btn-sm">🔍 开始扫描</button>
                    <input id="psFilterInput" class="ps-filter"
                           placeholder="按端口/服务名过滤（如 3306 / mysql）">
                    <span class="ps-hint" id="psHint">
                        通过 SSH 在目标机执行命令采集监听端口，自动识别服务类型
                    </span>
                </div>
                <div id="psSummary" class="ps-summary" style="display:none;"></div>
                <div id="psTableWrap" class="ps-table-wrap">
                    <div class="empty-tip">选择左侧服务器后点击「开始扫描」</div>
                </div>
            </div>
        `;
        container.querySelector('#psScanBtn').addEventListener('click', doScan);
        container.querySelector('#psFilterInput').addEventListener('input', applyFilter);
    }

    async function doScan() {
        if (scanning) return;
        const connId = getConnectionId ? getConnectionId() : null;
        if (!connId) { alert('请先在左侧选择一台服务器'); return; }

        const btn = container.querySelector('#psScanBtn');
        const wrap = container.querySelector('#psTableWrap');
        const summary = container.querySelector('#psSummary');
        const hint = container.querySelector('#psHint');

        scanning = true;
        btn.disabled = true;
        btn.textContent = '⏳ 扫描中...';
        hint.textContent = '正在通过 SSH 采集目标机监听端口...';
        summary.style.display = 'none';
        wrap.innerHTML = '<div class="empty-tip">⏳ 正在扫描远程端口...</div>';

        try {
            const resp = await fetch(apiUrl('/sys/api/ssh/port-scan'), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                body: JSON.stringify({ connectionId: connId })
            });
            const data = await resp.json();
            if (!data.success) throw new Error(data.error || '扫描失败');
            lastResult = data.data;
            if (data.warning) hint.textContent = '⚠️ ' + data.warning;
            else hint.textContent = '扫描完成：' + (lastResult.ports || []).length + ' 个端口';
            renderResult();
        } catch (e) {
            wrap.innerHTML = `<div class="ps-error">❌ ${escapeHtml(e.message)}</div>`;
            hint.textContent = '扫描失败';
            lastResult = null;
        } finally {
            scanning = false;
            btn.disabled = false;
            btn.textContent = '🔍 开始扫描';
        }
    }

    function renderResult() {
        if (!lastResult) return;
        const wrap = container.querySelector('#psTableWrap');
        const summary = container.querySelector('#psSummary');
        const ports = lastResult.ports || [];

        if (ports.length === 0) {
            wrap.innerHTML = '<div class="empty-tip">未检测到监听端口</div>';
            summary.style.display = 'none';
            return;
        }

        const s = lastResult.summary || {};
        const byCat = Object.entries(s.byCategory || {});
        const bySvc = Object.entries(s.byService || {}).sort((a, b) => b[1] - a[1]).slice(0, 10);
        summary.style.display = 'flex';
        summary.innerHTML = `
            <div class="ps-summary-row">
                <span class="ps-chip ps-chip-primary">目标：${escapeHtml(lastResult.host || '')} (${escapeHtml(lastResult.osType || '')})</span>
                <span class="ps-chip">共 ${s.total || ports.length} 个</span>
                <span class="ps-chip">TCP ${s.tcpCount || 0}</span>
                <span class="ps-chip">UDP ${s.udpCount || 0}</span>
                <span class="ps-chip">耗时 ${lastResult.durationMs || 0}ms</span>
            </div>
            ${byCat.length ? `<div class="ps-summary-row">
                <span class="ps-summary-label">分类：</span>
                ${byCat.map(([k, v]) => `<span class="ps-chip">${escapeHtml(k)} ${v}</span>`).join('')}
            </div>` : ''}
            ${bySvc.length ? `<div class="ps-summary-row">
                <span class="ps-summary-label">服务：</span>
                ${bySvc.map(([k, v]) => `<span class="ps-chip">${escapeHtml(k)} ${v}</span>`).join('')}
            </div>` : ''}
        `;

        let html = '<table class="ps-table"><thead><tr>'
            + '<th>端口</th><th>协议</th><th>状态</th><th>监听地址</th>'
            + '<th>进程名</th><th>PID</th><th>服务</th><th>分类</th>'
            + '</tr></thead><tbody>';
        ports.forEach(p => {
            html += '<tr>'
                + `<td class="ps-port">${p.port}</td>`
                + `<td>${escapeHtml(p.protocol || '')}</td>`
                + `<td>${escapeHtml(p.state || '')}</td>`
                + `<td class="ps-addr">${escapeHtml(p.address || '')}</td>`
                + `<td>${escapeHtml(p.processName || '-')}</td>`
                + `<td>${p.processPid != null ? p.processPid : '-'}</td>`
                + `<td>${escapeHtml(p.serviceName || '未知')}</td>`
                + `<td><span class="ps-cat">${escapeHtml(p.serviceCategory || '其他')}</span></td>`
                + '</tr>';
        });
        html += '</tbody></table>';
        wrap.innerHTML = html;
        applyFilter();
    }

    function applyFilter() {
        if (!container) return;
        const kw = (container.querySelector('#psFilterInput').value || '').trim().toLowerCase();
        const rows = container.querySelectorAll('#psTableWrap .ps-table tbody tr');
        if (rows.length === 0) return;
        rows.forEach(tr => {
            if (!kw) { tr.style.display = ''; return; }
            tr.style.display = tr.textContent.toLowerCase().includes(kw) ? '' : 'none';
        });
    }

    window.PortScanTab = {
        mount(containerEl, connIdGetter) {
            if (!containerEl) { console.warn('[PortScanTab] container 为空，跳过挂载'); return; }
            container = containerEl;
            getConnectionId = connIdGetter || (() => null);
            injectStyle();
            render();
        },
        clear() {
            lastResult = null;
            if (!container) return;
            const summary = container.querySelector('#psSummary');
            const wrap = container.querySelector('#psTableWrap');
            if (summary) summary.style.display = 'none';
            if (wrap) wrap.innerHTML = '<div class="empty-tip">切换了服务器，请重新扫描</div>';
        }
    };
})();