import { BaseElement, escapeHtml } from '../../core/BaseElement.js';

export class CardComponent extends BaseElement {
    static meta = {
        label: '卡片',
        icon: '🗂️',
        category: 'component',
        defaultProps: {
            title: '卡片标题',
            content: '这里是一段卡片内容，可以放任意文本。',
            x: 40, y: 40,
            width: 240,
            bgColor: '#ffffff',
            borderColor: '#e2e8f0',
            borderRadius: '12px',
            padding: '16px'
        }
    };
    createDOM() {
        const el = document.createElement('div');
        el.innerHTML = `
            <div style="font-weight:600;color:#0066ff;margin-bottom:8px;">${escapeHtml(this.props.title)}</div>
            <div style="font-size:13px;color:#5b6e8c;line-height:1.5;">${escapeHtml(this.props.content)}</div>
        `;
        Object.assign(el.style, {
            background: this.props.bgColor,
            border: '1px solid ' + this.props.borderColor,
            borderRadius: this.props.borderRadius,
            padding: this.props.padding,
            boxShadow: '0 2px 8px rgba(0,0,0,0.05)'
        });
        return el;
    }
}