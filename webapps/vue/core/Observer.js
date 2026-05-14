// webapps/vue/core/Observer.js
import { isObject } from './utils.js';
import { Dep } from './Dep.js';      // 新增独立 Dep 文件

export function observe(data) {
    if (!isObject(data)) return data;
    return new Observer(data);
}

export class Observer {
    constructor(data) {
        return this._createReactive(data);
    }

    _createReactive(obj) {
        const depsMap = new Map();   // 每个 key 对应一个 Dep

        const handler = {
            get(target, key, receiver) {
                if (Dep.target) {
                    let dep = depsMap.get(key);
                    if (!dep) {
                        dep = new Dep();
                        depsMap.set(key, dep);
                    }
                    dep.depend();
                }
                const value = Reflect.get(target, key, receiver);
                if (isObject(value)) {
                    return observe(value);
                }
                return value;
            },
            set(target, key, value, receiver) {
                const oldVal = target[key];
                const result = Reflect.set(target, key, value, receiver);
                if (oldVal !== value) {
                    let dep = depsMap.get(key);
                    if (dep) dep.notify();
                }
                return result;
            }
        };
        return new Proxy(obj, handler);
    }
}