import { BaseElement, escapeHtml } from '../../core/BaseElement.js';

export class TableComponent extends BaseElement {
    static meta = {
        label: '表格',
        icon: '📊',
        category: 'component',
        defaultProps: {
            columns: '姓名,年龄,城市',
            rows: '张三,25,北京;李四,30,上海;王五,28,广州',
            x: 40, y: 40
        }
    };
    createDOM() {
        const el = document.createElement('table');
        const cols = (this.props.columns || '').split(',').map(s => s.trim());
        const rows = (this.props.rows || '').split(';').map(r => r.split(',').map(s => s.trim()));
        let html = '<thead><tr>';
        cols.forEach(c => {
            html += `<th style="background:rgba(0,102,255,0.1);color:#0066ff;padding:8px 12px;text-align:left;font-size:13px;">${escapeHtml(c)}</th>`;
        });
        html += '</tr></thead><tbody>';
        rows.forEach(r => {
            html += '<tr>';
            cols.forEach((_, i) => {
                html += `<td style="padding:8px 12px;border-bottom:1px solid #e2e8f0;font-size:13px;">${escapeHtml(r[i] || '')}</td>`;
            });
            html += '</tr>';
        });
        html += '</tbody>';
        el.innerHTML = html;
        el.style.borderCollapse = 'collapse';
        el.style.background = 'white';
        el.style.borderRadius = '8px';
        el.style.overflow = 'hidden';
        el.style.boxShadow = '0 1px 4px rgba(0,0,0,0.05)';
        return el;
    }
}