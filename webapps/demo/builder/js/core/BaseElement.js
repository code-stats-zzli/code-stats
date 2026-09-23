/**
 * 元素抽象基类
 * 所有基础元素/组件元素都继承此类
 */
let _idCounter = 0;
function genId() {
    _idCounter++;
    return 'el_' + Date.now().toString(36) + '_' + _idCounter;
}

export class BaseElement {
    /**
     * 子类必须覆盖此静态属性：
     * static meta = {
     *   label: '文本',
     *   icon: '📝',
     *   category: 'primitive' | 'component',
     *   defaultProps: { ... }
     * }
     */
    static meta = {
        label: '未定义元素',
        icon: '📦',
        category: 'primitive',
        defaultProps: {}
    };

    constructor(props = {}) {
        this.id = props.id || genId();
        this.type = this.constructor.name;
        // 深拷贝默认 props，避免共享引用
        const defaults = JSON.parse(JSON.stringify(this.constructor.meta.defaultProps || {}));
        this.props = Object.assign(defaults, props);
        this.dom = null;
    }

    /**
     * 子类必须实现：返回真实 DOM 节点
     */
    createDOM() {
        throw new Error(`[${this.type}] 必须实现 createDOM()`);
    }

    /**
     * 渲染：创建 DOM 并应用基础样式
     */
    render() {
        const dom = this.createDOM();
        dom.dataset.elementId = this.id;
        dom.dataset.elementType = this.type;
        dom.classList.add('builder-element');

        // 绝对定位
        dom.style.position = 'absolute';
        dom.style.left = (this.props.x || 0) + 'px';
        dom.style.top = (this.props.y || 0) + 'px';

        // 尺寸
        if (this.props.width != null) {
            dom.style.width = typeof this.props.width === 'number'
                ? this.props.width + 'px' : this.props.width;
        }
        if (this.props.height != null) {
            dom.style.height = typeof this.props.height === 'number'
                ? this.props.height + 'px' : this.props.height;
        }

        this.dom = dom;
        return dom;
    }

    /**
     * 更新位置
     */
    setPosition(x, y) {
        this.props.x = x;
        this.props.y = y;
        if (this.dom) {
            this.dom.style.left = x + 'px';
            this.dom.style.top = y + 'px';
        }
    }

    /**
     * 更新属性：重建 DOM（简单策略）
     */
    updateProps(newProps) {
        Object.assign(this.props, newProps);
        const oldDom = this.dom;
        if (!oldDom) return null;

        const newDom = this.createDOM();
        newDom.dataset.elementId = this.id;
        newDom.dataset.elementType = this.type;
        newDom.classList.add('builder-element');
        newDom.style.position = 'absolute';
        newDom.style.left = (this.props.x || 0) + 'px';
        newDom.style.top = (this.props.y || 0) + 'px';
        if (this.props.width != null) {
            newDom.style.width = typeof this.props.width === 'number'
                ? this.props.width + 'px' : this.props.width;
        }
        if (this.props.height != null) {
            newDom.style.height = typeof this.props.height === 'number'
                ? this.props.height + 'px' : this.props.height;
        }
        if (oldDom.classList.contains('builder-selected')) {
            newDom.classList.add('builder-selected');
        }

        oldDom.parentNode.replaceChild(newDom, oldDom);
        this.dom = newDom;
        return newDom;
    }

    /**
     * 序列化
     */
    toJSON() {
        return {
            type: this.type,
            props: JSON.parse(JSON.stringify(this.props))
        };
    }
}

// 工具：HTML 转义
export function escapeHtml(str) {
    if (str == null) return '';
    return String(str).replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[m]));
}