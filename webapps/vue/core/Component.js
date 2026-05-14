// webapps/vue/core/Component.js
import { Observer } from './Observer.js';
import { Compiler } from './Compiler.js';
import { StyleManager } from './StyleManager.js';
import { nextUid } from './utils.js';

export class Component {
    constructor(options) {
        this.$options = options;
        this.$methods = options.methods || {};
        this.$template = options.template;
        this.$style = options.style;
        this.$id = nextUid();

        // 1. 获取原始数据
        const rawData = options.data ? options.data() : {};
        // 2. 先变为响应式对象
        this.$data = new Observer(rawData);
        // 3. 代理响应式数据到 this
        this._proxyData();
        // 4. 绑定方法到 this
        this._bindMethods();

        if (this.$style) {
            StyleManager.inject(this.$id, this.$style);
        }
    }

    _proxyData() {
        Object.keys(this.$data).forEach(key => {
            Object.defineProperty(this, key, {
                enumerable: true,
                configurable: true,
                get: () => this.$data[key],
                set: (val) => { this.$data[key] = val; }
            });
        });
    }

    _bindMethods() {
        Object.keys(this.$methods).forEach(key => {
            this[key] = this.$methods[key].bind(this);
        });
    }

    mount(selector) {
        const container = typeof selector === 'string' ? document.querySelector(selector) : selector;
        if (!container) throw new Error('容器不存在');
        this.$el = this.render();
        container.appendChild(this.$el);
        new Compiler(this);
        // 调用 mounted 钩子
        if (this.$options.mounted) {
            this.$options.mounted.call(this);
        }
        return this;
    }

    render() {
        const wrapper = document.createElement('div');
        wrapper.innerHTML = this.$template.trim();
        const dom = wrapper.firstChild;
        dom.setAttribute('data-v', this.$id);
        return dom;
    }
}