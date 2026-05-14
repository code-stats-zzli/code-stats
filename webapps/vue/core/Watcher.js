// webapps/vue/core/Watcher.js
import { Dep } from './Dep.js';

export class Watcher {
    constructor(vm, expOrFn, callback) {
        this.vm = vm;
        this.getter = typeof expOrFn === 'function' ? expOrFn : this.parseExpression(expOrFn);
        this.callback = callback;
        this.value = this.get();
    }

    parseExpression(exp) {
        return () => {
            return new Function('vm', `with(vm) { return ${exp} }`)(this.vm);
        };
    }

    get() {
        Dep.target = this;
        const value = this.getter.call(this.vm, this.vm);
        Dep.target = null;
        return value;
    }

    update() {
        const oldVal = this.value;
        const newVal = this.get();
        if (oldVal !== newVal) {
            this.value = newVal;
            this.callback(newVal, oldVal);
        }
    }
}