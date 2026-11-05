/** 元素抽象基类 —— 新增 dataBinding 支持 */
let _idCounter = 0;
function genId() {
    _idCounter++;
    return 'el_' + Date.now().toString(36) + '_' + _idCounter;
}

export class BaseElement {
    static meta = {
        label: '未定义元素',
        icon: '📦',
        category: 'primitive',
        defaultProps: {}
    };

    constructor(props = {}) {
        this.id = props.id || genId();
        this.type = this.constructor.name;
        const defaults = JSON.parse(JSON.stringify(this.constructor.meta.defaultProps || {}));
        this.props = Object.assign(defaults, props);
        // ★ 数据绑定（组件选数据源时填充）
        this.dataBinding = props.dataBinding || null;
        // 运行时数据（由 DataLoader 注入，不参与序列化）
        this.runtimeData = null;
        this.dom = null;
    }

    createDOM() {
        throw new Error(`[${this.type}] 必须实现 createDOM()`);
    }

    render() {
        const dom = this.createDOM();
        dom.dataset.elementId = this.id;
        dom.dataset.elementType = this.type;
        dom.classList.add('builder-element');
        dom.style.position = 'absolute';
        dom.style.left = (this.props.x || 0) + 'px';
        dom.style.top = (this.props.y || 0) + 'px';
        if (this.props.width != null) {
            dom.style.width = typeof this.props.width === 'number'
                ? this.props.width + 'px' : this.props.width;
        }
        if (this.props.height != null) {
            dom.style.height = typeof this.props.height === 'number'
                ? this.props.height + 'px' : this.props.height;
        }
        this.dom = dom;
        // 若已有运行时数据，立即渲染
        if (this.runtimeData && typeof this.applyData === 'function') {
            try { this.applyData(this.runtimeData); } catch (e) { /* ignore */ }
        }
        return dom;
    }

    setPosition(x, y) {
        this.props.x = x;
        this.props.y = y;
        if (this.dom) {
            this.dom.style.left = x + 'px';
            this.dom.style.top = y + 'px';
        }
    }

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
        // 重建后重放数据
        if (this.runtimeData && typeof this.applyData === 'function') {
            try { this.applyData(this.runtimeData); } catch (e) { /* ignore */ }
        }
        return newDom;
    }

    /** 子类覆盖：接收到查询结果后如何渲染 */
    applyData(result) { /* no-op */ }

    /** 元素是否支持数据绑定 */
    supportsDataBinding() {
        return typeof this.applyData === 'function' && this.applyData !== BaseElement.prototype.applyData;
    }

    toJSON() {
        return {
            type: this.type,
            props: JSON.parse(JSON.stringify(this.props)),
            dataBinding: this.dataBinding
                ? JSON.parse(JSON.stringify(this.dataBinding)) : null
        };
    }
}

export function escapeHtml(str) {
    if (str == null) return '';
    return String(str).replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[m]));
}