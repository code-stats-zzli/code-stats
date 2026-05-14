// js/simple-vue/Observer.js
import { isObject } from './utils.js';

export class Observer {
    constructor(data) {
        this.dep = new Dep();
        return this.walk(data);
    }

    walk(data) {
        if (!isObject(data)) return data;
        return new Proxy(data, {
            get: (target, key, receiver) => {
                if (Dep.target) {
                    this.dep.depend();
                }
                const val = Reflect.get(target, key, receiver);
                // 深层响应
                if (isObject(val)) {
                    return new Observer(val);
                }
                return val;
            },
            set: (target, key, value, receiver) => {
                const oldVal = target[key];
                const result = Reflect.set(target, key, value, receiver);
                if (oldVal !== value) {
                    this.dep.notify();
                }
                return result;
            }
        });
    }
}

// 依赖管理
export class Dep {
    constructor() {
        this.subscribers = new Set();
    }
    depend() {
        if (Dep.target) {
            this.subscribers.add(Dep.target);
        }
    }
    notify() {
        this.subscribers.forEach(sub => sub.update());
    }
}

Dep.target = null;