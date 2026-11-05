// webapps/vue/core/Watcher.js
import { Dep } from './Dep.js';

export class Watcher {
    constructor(vm, expOrFn, callback) {
        this.vm = vm;
        this.callback = callback;
        if (typeof expOrFn === 'function') {
            this.getter = expOrFn;
        } else {
            this.getter = this.parseExpression(expOrFn);
        }
        this.value = this.get();
    }

    parseExpression(exp) {
        try {
            return new Function('vm', `with(vm) { return ${exp} }`);
        } catch (e) {
            console.warn('表达式解析失败:', exp, e);
            return () => undefined;
        }
    }

    get() {
        Dep.target = this;
        let value;
        try {
            value = this.getter(this.vm);
        } catch (e) {
            console.warn('Watcher求值错误:', e);
            value = undefined;
        }
        Dep.target = null;
        return value;
    }

    update() {
        const newVal = this.get();
        const oldVal = this.value;
        if (newVal !== oldVal) {
            this.value = newVal;
            this.callback(newVal, oldVal);
        }
    }
}