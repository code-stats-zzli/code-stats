// webapps/vue/core/Observer.js
import { isObject } from './utils.js';
import { Dep } from './Dep.js';

export function observe(data) {
    if (!isObject(data)) return data;
    if (data.__ob__) return data;
    new Observer(data);   // 只实例化，不返回 Observer
    return data;          // ✅ 返回经过响应式处理的原始对象
}

export class Observer {
    constructor(data) {
        this.data = data;
        data.__ob__ = this;
        this.walk(data);
    }

    walk(obj) {
        const keys = Object.keys(obj);
        for (let i = 0; i < keys.length; i++) {
            const key = keys[i];
            if (key === '__ob__') continue;
            defineReactive(obj, key, obj[key]);
        }
    }
}

function defineReactive(obj, key, val) {
    const childOb = observe(val);
    const dep = new Dep();

    Object.defineProperty(obj, key, {
        enumerable: true,
        configurable: true,
        get() {
            if (Dep.target) {
                dep.depend();
                // 子对象依赖收集（简化）
            }
            return val;
        },
        set(newVal) {
            if (newVal === val) return;
            val = newVal;
            observe(newVal);
            dep.notify();
        }
    });
}