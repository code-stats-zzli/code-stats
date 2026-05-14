// js/simple-vue/utils.js
export function isObject(obj) {
    return obj !== null && typeof obj === 'object';
}

export function isFunction(fn) {
    return typeof fn === 'function';
}

export function isString(str) {
    return typeof str === 'string';
}

export function isElement(node) {
    return node && node.nodeType === 1;
}

// 生成唯一 ID
let uid = 0;
export function nextUid() {
    return `v-${uid++}`;
}

// 简单深拷贝
export function deepClone(obj) {
    return JSON.parse(JSON.stringify(obj));
}

// 解析事件名（如 @click -> click）
export function parseEventName(attrName) {
    if (attrName.startsWith('@')) return attrName.slice(1);
    return null;
}