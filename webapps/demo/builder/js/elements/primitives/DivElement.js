import { BaseElement } from '../../core/BaseElement.js';

export class DivElement extends BaseElement {
    static meta = {
        label: '容器',
        icon: '⬛',
        category: 'primitive',
        defaultProps: {
            x: 40, y: 40,
            width: 200,
            height: 100,
            bgColor: 'rgba(0,102,255,0.1)',
            border: '1px dashed #0066ff',
            borderRadius: '8px'
        }
    };
    createDOM() {
        const el = document.createElement('div');
        Object.assign(el.style, {
            background: this.props.bgColor,
            border: this.props.border,
            borderRadius: this.props.borderRadius
        });
        return el;
    }
}