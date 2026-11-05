import { BaseElement, escapeHtml } from '../../core/BaseElement.js';

export class AlertComponent extends BaseElement {
    static meta = {
        label: '提示条',
        icon: '⚠️',
        category: 'component',
        defaultProps: {
            text: '这是一条提示信息',
            type: 'info',       // info | success | warning | error
            x: 40, y: 40,
            width: 300,
            borderRadius: '8px'
        }
    };
    createDOM() {
        const colors = {
            info:    { bg: 'rgba(0,102,255,0.1)',  border: '#0066ff', icon: 'ℹ️', color: '#0066ff' },
            success: { bg: 'rgba(34,197,94,0.1)',  border: '#22c55e', icon: '✅', color: '#15803d' },
            warning: { bg: 'rgba(245,158,11,0.1)', border: '#f59e0b', icon: '⚠️', color: '#b45309' },
            error:   { bg: 'rgba(239,68,68,0.1)',  border: '#ef4444', icon: '❌', color: '#b91c1c' }
        };
        const c = colors[this.props.type] || colors.info;
        const el = document.createElement('div');
        el.innerHTML = `<span style="margin-right:8px;">${c.icon}</span>${escapeHtml(this.props.text)}`;
        Object.assign(el.style, {
            background: c.bg,
            borderLeft: `4px solid ${c.border}`,
            color: c.color,
            padding: '10px 14px',
            borderRadius: this.props.borderRadius,
            fontSize: '13px'
        });
        return el;
    }
}