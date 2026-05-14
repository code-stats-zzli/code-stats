// webapps/vue/core/utils.js
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

let uid = 0;
export function nextUid() {
    return `v-${uid++}`;
}

export function deepClone(obj) {
    return JSON.parse(JSON.stringify(obj));
}

export function parseEventName(attrName) {
    if (attrName.startsWith('@')) return attrName.slice(1);
    return null;
}