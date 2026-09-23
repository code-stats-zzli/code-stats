import { BaseElement, escapeHtml } from '../../core/BaseElement.js';

export class NavComponent extends BaseElement {
    static meta = {
        label: '导航栏',
        icon: '🧭',
        category: 'component',
        defaultProps: {
            brand: 'Brand',
            items: '首页,产品,关于,联系',
            x: 40, y: 40,
            width: 600
        }
    };
    createDOM() {
        const el = document.createElement('nav');
        const items = (this.props.items || '').split(',').map(s => s.trim());
        let html = `<div style="font-weight:700;color:#0066ff;margin-right:24px;">${escapeHtml(this.props.brand)}</div>`;
        items.forEach(item => {
            html += `<a href="#" style="color:#1e293b;text-decoration:none;margin:0 12px;font-size:13px;">${escapeHtml(item)}</a>`;
        });
        el.innerHTML = html;
        Object.assign(el.style, {
            display: 'flex',
            alignItems: 'center',
            background: 'white',
            padding: '12px 20px',
            borderRadius: '12px',
            boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
            boxSizing: 'border-box'
        });
        el.querySelectorAll('a').forEach(a => {
            a.addEventListener('mouseenter', () => a.style.color = '#0066ff');
            a.addEventListener('mouseleave', () => a.style.color = '#1e293b');
        });
        return el;
    }
}