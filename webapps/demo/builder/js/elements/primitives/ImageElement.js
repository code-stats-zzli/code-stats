import { BaseElement } from '../../core/BaseElement.js';

export class ImageElement extends BaseElement {
    static meta = {
        label: '图片',
        icon: '🖼️',
        category: 'primitive',
        defaultProps: {
            src: 'https://via.placeholder.com/200x120/0066ff/ffffff?text=Image',
            alt: '图片',
            x: 40, y: 40,
            width: 200,
            height: 'auto',
            borderRadius: '8px'
        }
    };
    createDOM() {
        const el = document.createElement('img');
        el.src = this.props.src;
        el.alt = this.props.alt;
        el.style.borderRadius = this.props.borderRadius;
        el.style.display = 'block';
        el.draggable = false;
        return el;
    }
}