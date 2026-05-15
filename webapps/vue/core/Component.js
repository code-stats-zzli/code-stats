// webapps/vue/core/Component.js
import { observe } from './Observer.js';
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

        // 获取初始数据并使其响应式
        const rawData = options.data ? options.data() : {};
        this.$data = observe(rawData);

        // 代理所有数据属性到 this 实例
        this._proxyData();

        // 绑定方法到 this
        this._bindMethods();

        if (this.$style) {
            StyleManager.inject(this.$id, this.$style);
        }
    }

    _proxyData() {
        const keys = Object.keys(this.$data);
        for (let i = 0; i < keys.length; i++) {
            const key = keys[i];
            Object.defineProperty(this, key, {
                enumerable: true,
                configurable: true,
                get: () => this.$data[key],
                set: (val) => { this.$data[key] = val; }
            });
        }
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