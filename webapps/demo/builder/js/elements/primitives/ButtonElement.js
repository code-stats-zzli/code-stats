import { BaseElement } from '../../core/BaseElement.js';

export class ButtonElement extends BaseElement {
    static meta = {
        label: '按钮',
        icon: '🔘',
        category: 'primitive',
        defaultProps: {
            text: '按钮',
            x: 40, y: 40,
            bgColor: '#0066ff',
            color: '#ffffff',
            fontSize: 14,
            padding: '8px 20px',
            borderRadius: '8px'
        }
    };
    createDOM() {
        const el = document.createElement('button');
        el.textContent = this.props.text;
        Object.assign(el.style, {
            background: this.props.bgColor,
            color: this.props.color,
            fontSize: this.props.fontSize + 'px',
            padding: this.props.padding,
            border: 'none',
            borderRadius: this.props.borderRadius,
            cursor: 'pointer',
            fontFamily: 'inherit'
        });
        return el;
    }
}