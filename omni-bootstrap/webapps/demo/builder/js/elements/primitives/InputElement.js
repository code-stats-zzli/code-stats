import { BaseElement } from '../../core/BaseElement.js';

export class InputElement extends BaseElement {
    static meta = {
        label: '输入框',
        icon: '⌨️',
        category: 'primitive',
        defaultProps: {
            placeholder: '请输入...',
            x: 40, y: 40,
            width: 200,
            padding: '8px 12px',
            borderRadius: '8px'
        }
    };
    createDOM() {
        const el = document.createElement('input');
        el.type = 'text';
        el.placeholder = this.props.placeholder;
        Object.assign(el.style, {
            padding: this.props.padding,
            border: '1px solid #cbd5e1',
            borderRadius: this.props.borderRadius,
            fontSize: '14px',
            fontFamily: 'inherit',
            outline: 'none'
        });
        el.addEventListener('focus', () => el.style.borderColor = '#0066ff');
        el.addEventListener('blur', () => el.style.borderColor = '#cbd5e1');
        return el;
    }
}