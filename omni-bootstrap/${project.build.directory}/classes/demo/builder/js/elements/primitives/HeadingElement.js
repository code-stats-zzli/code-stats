import { BaseElement } from '../../core/BaseElement.js';

export class HeadingElement extends BaseElement {
    static meta = {
        label: '标题',
        icon: '🅷',
        category: 'primitive',
        defaultProps: {
            text: '标题文字',
            level: 2,
            x: 40, y: 40,
            color: '#0066ff'
        }
    };
    createDOM() {
        const el = document.createElement('h' + (this.props.level || 2));
        el.textContent = this.props.text;
        el.style.color = this.props.color;
        el.style.margin = '0';
        return el;
    }
}