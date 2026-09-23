import { BaseElement, escapeHtml } from '../../core/BaseElement.js';

export class FormComponent extends BaseElement {
    static meta = {
        label: '表单',
        icon: '📋',
        category: 'component',
        defaultProps: {
            title: '用户信息',
            fields: '用户名,邮箱,密码',   // 逗号分隔
            x: 40, y: 40,
            width: 260
        }
    };
    createDOM() {
        const el = document.createElement('form');
        const fields = (this.props.fields || '').split(',').map(s => s.trim()).filter(Boolean);
        let html = this.props.title
            ? `<div style="font-weight:600;color:#0066ff;margin-bottom:12px;">${escapeHtml(this.props.title)}</div>`
            : '';
        fields.forEach(f => {
            html += `
                <div style="margin-bottom:10px;">
                    <label style="display:block;font-size:12px;color:#64748b;margin-bottom:4px;">${escapeHtml(f)}</label>
                    <input type="text" style="width:100%;padding:6px 10px;border:1px solid #cbd5e1;border-radius:8px;font-size:13px;box-sizing:border-box;">
                </div>`;
        });
        html += `<button type="button" style="width:100%;padding:8px;background:#0066ff;color:white;border:none;border-radius:8px;cursor:pointer;font-size:13px;">提交</button>`;
        el.innerHTML = html;
        el.style.background = '#ffffff';
        el.style.border = '1px solid #e2e8f0';
        el.style.borderRadius = '12px';
        el.style.padding = '16px';
        el.addEventListener('submit', e => e.preventDefault());
        return el;
    }
}