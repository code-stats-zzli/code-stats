// js/simple-vue/Component.js
import { Observer } from './Observer.js';
import { Compiler } from './Compiler.js';
import { StyleManager } from './StyleManager.js';
import { nextUid } from './utils.js';

export class Component {
    constructor(options) {
        this.$options = options;
        this.$data = options.data ? options.data() : {};
        this.$methods = options.methods || {};
        this.$template = options.template;
        this.$style = options.style;
        this.$id = nextUid();
        this.$el = null;

        // 代理 data 到 this
        this._proxyData();
        // 响应式
        this.$data = new Observer(this.$data);
        // 注入样式
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
        Object.keys(this.$methods).forEach(key => {
            this[key] = this.$methods[key].bind(this);
        });
    }

    mount(selector) {
        const container = typeof selector === 'string' ? document.querySelector(selector) : selector;
        if (!container) throw new Error('容器不存在');
        // 渲染
        this.$el = this.render();
        container.appendChild(this.$el);
        // 编译
        new Compiler(this);
        return this;
    }

    render() {
        // 将模板字符串转换为 DOM 元素
        const wrapper = document.createElement('div');
        wrapper.innerHTML = this.$template.trim();
        const dom = wrapper.firstChild;
        // 添加作用域属性
        dom.setAttribute('data-v', this.$id);
        return dom;
    }

    update() {
        // 简单重新渲染（实际应有 diff）
        const oldEl = this.$el;
        const newEl = this.render();
        oldEl.parentNode.replaceChild(newEl, oldEl);
        this.$el = newEl;
        new Compiler(this);
    }
}