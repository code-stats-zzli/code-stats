import { BaseElement, escapeHtml } from '../../core/BaseElement.js';

/** 数据表格 — 消费 Cube 数据 */
export class DataTableElement extends BaseElement {
    static meta = {
        label: '数据表格',
        icon: '📋',
        category: 'data',
        defaultProps: {
            x: 40, y: 40,
            width: 620,
            height: 300,
            fontSize: 13,
            stripe: true,
            borderColor: '#e2e8f0',
            headerBg: '#f0f4fa',
            headerColor: '#0066ff'
        }
    };

    createDOM() {
        const el = document.createElement('div');
        el.className = 'dc-table-wrapper';
        el.style.cssText = `
            overflow: auto;
            background: #fff;
            border: 1px solid ${this.props.borderColor};
            border-radius: 8px;
            font-size: ${this.props.fontSize}px;
            font-family: 'Inter','Segoe UI',sans-serif;
        `;
        if (!this.dataBinding) {
            el.innerHTML = `<div class="dc-placeholder" style="padding:24px;
                text-align:center;color:#94a3b8;font-size:12px;">
                📋 未绑定数据源<br><span style="font-size:11px;">在右侧「数据」面板中选择数据源</span>
            </div>`;
        } else if (!this.runtimeData) {
            el.innerHTML = `<div class="dc-placeholder" style="padding:24px;
                text-align:center;color:#94a3b8;font-size:12px;">⏳ 加载中...</div>`;
        }
        return el;
    }

    applyData(result) {
        if (!this.dom) return;
        if (!result || !result.columns || !result.columns.length) {
            this.dom.innerHTML = `<div class="dc-placeholder" style="padding:24px;
                text-align:center;color:#94a3b8;font-size:12px;">📭 无数据</div>`;
            return;
        }
        const fm = (this.dataBinding && this.dataBinding.fieldMapping) || {};
        const cols = result.columns;
        const rows = result.rows || [];
        let html = '<table style="width:100%;border-collapse:collapse;">';
        html += '<thead><tr>';
        for (const c of cols) {
            html += `<th style="position:sticky;top:0;background:${this.props.headerBg};
                color:${this.props.headerColor};padding:8px 12px;text-align:left;
                font-weight:600;border-bottom:2px solid ${this.props.borderColor};
                white-space:nowrap;">${escapeHtml(fm[c] || c)}</th>`;
        }
        html += '</tr></thead><tbody>';
        rows.forEach((row, idx) => {
            const bg = this.props.stripe && idx % 2 === 1 ? '#fafbfc' : '#fff';
            html += `<tr style="background:${bg}">`;
            for (const cell of row) {
                html += `<td style="padding:6px 12px;border-bottom:1px solid ${this.props.borderColor};
                    white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:320px;">${
                    cell == null ? '' : escapeHtml(String(cell))
                }</td>`;
            }
            html += '</tr>';
        });
        html += '</tbody></table>';
        if (result.meta && result.meta.total != null) {
            html += `<div style="padding:6px 12px;background:#f8fafc;color:#64748b;
                font-size:11px;border-top:1px solid ${this.props.borderColor};">
                共 ${result.meta.total} 行 · 耗时 ${result.meta.durationMs || 0} ms
            </div>`;
        }
        this.dom.innerHTML = html;
    }
}