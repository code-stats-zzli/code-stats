/**
 * 基础设施层：ID / 事件总线 / 几何工具。
 * 这一层不依赖 DOM、不依赖模型，可被任何上层复用。
 */

/** 生成唯一 ID */
export function genId(prefix = 'n') {
    return prefix + '_' + Date.now().toString(36) + '_'
        + Math.random().toString(36).slice(2, 7);
}

/** 极简发布订阅 */
export class EventBus {
    constructor() { this._m = new Map(); }
    on(name, fn) {
        if (!this._m.has(name)) this._m.set(name, new Set());
        this._m.get(name).add(fn);
        return () => this.off(name, fn);
    }
    off(name, fn) { this._m.get(name)?.delete(fn); }
    emit(name, payload) {
        this._m.get(name)?.forEach(fn => {
            try { fn(payload); } catch (e) { console.error(e); }
        });
    }
}

/** 数值夹取 */
export function clamp(v, min, max) {
    return v < min ? min : v > max ? max : v;
}

/** 由两点得到规范化矩形（左上 + 宽高为正） */
export function normRect(x1, y1, x2, y2) {
    const x = Math.min(x1, x2), y = Math.min(y1, y2);
    return { x, y, w: Math.abs(x2 - x1), h: Math.abs(y2 - y1) };
}

/** 点是否在矩形内 */
export function pointInRect(p, r) {
    return p.x >= r.x && p.x <= r.x + r.w
        && p.y >= r.y && p.y <= r.y + r.h;
}