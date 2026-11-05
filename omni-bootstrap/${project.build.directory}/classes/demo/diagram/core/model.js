/**
 * 模型层：纯数据 + 校验 + 序列化。
 * 模型里不允许出现 DOM 引用。
 */
import { genId } from './base.js';

/** 创建一个节点 */
export function createNode({ type, x, y, w, h, props = {} }) {
    return { id: genId(type), type, x, y, w, h, props };
}

/** 文档对象 */
export class Diagram {
    constructor() {
        this.nodes = new Map();
        this.version = 1;
    }
    addNode(node) { this.nodes.set(node.id, node); return node; }
    removeNode(id) { this.nodes.delete(id); }
    getNode(id) { return this.nodes.get(id); }
    allNodes() { return Array.from(this.nodes.values()); }
    clear() { this.nodes.clear(); }

    /** 全图包围盒（世界坐标），空图返回一个默认大小 */
    getBounds() {
        if (this.nodes.size === 0) return { x: 0, y: 0, w: 800, h: 600 };
        let x1 = Infinity, y1 = Infinity, x2 = -Infinity, y2 = -Infinity;
        for (const n of this.nodes.values()) {
            const r = nodeBounds(n);
            if (r.x < x1) x1 = r.x;
            if (r.y < y1) y1 = r.y;
            if (r.x + r.w > x2) x2 = r.x + r.w;
            if (r.y + r.h > y2) y2 = r.y + r.h;
        }
        return { x: x1, y: y1, w: x2 - x1, h: y2 - y1 };
    }
}

/** 节点的世界坐标包围盒（兼容 w/h 为负） */
export function nodeBounds(n) {
    return {
        x: Math.min(n.x, n.x + n.w),
        y: Math.min(n.y, n.y + n.h),
        w: Math.abs(n.w),
        h: Math.abs(n.h)
    };
}

/** 序列化为可 JSON 化的对象 */
export function serialize(diagram) {
    return { version: diagram.version, nodes: diagram.allNodes() };
}

/** 反序列化为 Diagram 实例（深拷贝，避免外部引用污染） */
export function deserialize(json) {
    const d = new Diagram();
    if (json && Array.isArray(json.nodes)) {
        for (const n of json.nodes) {
            d.addNode(JSON.parse(JSON.stringify(n)));
        }
    }
    return d;
}

/** 简单校验：id 唯一、type 存在 */
export function validate(json) {
    if (!json || !Array.isArray(json.nodes)) return false;
    const ids = new Set();
    for (const n of json.nodes) {
        if (!n || !n.id || !n.type) return false;
        if (ids.has(n.id)) return false;
        ids.add(n.id);
    }
    return true;
}