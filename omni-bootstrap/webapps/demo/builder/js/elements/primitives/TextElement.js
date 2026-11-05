import { BaseElement, escapeHtml } from '../../core/BaseElement.js';

export class TextElement extends BaseElement {
    static meta = {
        label: '文本',
        icon: '📝',
        category: 'primitive',
        defaultProps: {
            text: '这是一段文本',
            x: 40, y: 40,
            fontSize: 14,
            color: '#1e293b',
            fontWeight: 'normal'
        }
    };
    createDOM() {
        const el = document.createElement('span');
        el.textContent = this.props.text;
        el.style.fontSize = this.props.fontSize + 'px';
        el.style.color = this.props.color;
        el.style.fontWeight = this.props.fontWeight;
        el.style.display = 'inline-block';
        return el;
    }
}