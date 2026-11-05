import { BaseElement } from '../../core/BaseElement.js';

export class LinkElement extends BaseElement {
    static meta = {
        label: '链接',
        icon: '🔗',
        category: 'primitive',
        defaultProps: {
            text: '点击跳转',
            href: '#',
            x: 40, y: 40,
            color: '#0066ff',
            fontSize: 14
        }
    };
    createDOM() {
        const el = document.createElement('a');
        el.textContent = this.props.text;
        el.href = this.props.href;
        el.style.color = this.props.color;
        el.style.fontSize = this.props.fontSize + 'px';
        el.style.textDecoration = 'underline';
        el.draggable = false;
        return el;
    }
}