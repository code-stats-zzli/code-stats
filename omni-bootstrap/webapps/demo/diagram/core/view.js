/**
 * 视图层：视口变换 + SVG 渲染。
 * 只负责"把模型画成 DOM"，不监听用户事件。
 */
import { nodeBounds } from './model.js';
import { NodeTypes } from './nodetypes.js';

const NS = 'http://www.w3.org/2000/svg';

/* ============================================================
 * Viewport —— 世界坐标 / 屏幕坐标 的相互转换
 * ============================================================ */
export class Viewport {
    constructor() {
        this.x = 0;
        this.y = 0;
        this.scale = 1;
    }

    screenToWorld(p) {
        return { x: (p.x - this.x) / this.scale, y: (p.y - this.y) / this.scale };
    }

    worldToScreen(p) {
        return { x: p.x * this.scale + this.x, y: p.y * this.scale + this.y };
    }

    getTransform() {
        return `translate(${this.x},${this.y}) scale(${this.scale})`;
    }

    /** 以屏幕点为锚点缩放，保证该点世界坐标不变 */
    zoomAt(screenPt, factor) {
        const worldPt = this.screenToWorld(screenPt);
        this.scale = Math.max(0.1, Math.min(5, this.scale * factor));
        this.x = screenPt.x - worldPt.x * this.scale;
        this.y = screenPt.y - worldPt.y * this.scale;
    }

    /** 让世界矩形适配屏幕 */
    zoomToFit(screenW, screenH, worldRect, padding = 40) {
        if (!worldRect || worldRect.w <= 0 || worldRect.h <= 0) return;
        const sx = (screenW - padding * 2) / worldRect.w;
        const sy = (screenH - padding * 2) / worldRect.h;
        this.scale = Math.max(0.1, Math.min(2, Math.min(sx, sy)));
        this.x = (screenW - worldRect.w * this.scale) / 2 - worldRect.x * this.scale;
        this.y = (screenH - worldRect.h * this.scale) / 2 - worldRect.y * this.scale;
    }
}

/* ============================================================
 * SvgRenderer —— 把 Diagram 画到 <svg>
 * ============================================================ */
export class SvgRenderer {
    constructor(svg) {
        this.svg = svg;
        this.viewport = new Viewport();
        this.layers = {};
        this._initDefs();
        this._initLayers();
    }

    _initDefs() {
        const defs = document.createElementNS(NS, 'defs');
        defs.innerHTML = `
            <marker id="dg-arrow" viewBox="0 0 10 10" refX="9" refY="5"
                    markerWidth="8" markerHeight="8" orient="auto-start-reverse">
                <path d="M0,0 L10,5 L0,10 z" fill="#1e293b"/>
            </marker>
            <marker id="dg-arrow-selected" viewBox="0 0 10 10" refX="9" refY="5"
                    markerWidth="8" markerHeight="8" orient="auto-start-reverse">
                <path d="M0,0 L10,5 L0,10 z" fill="#0066ff"/>
            </marker>
        `;
        this.svg.appendChild(defs);
    }

    _initLayers() {
        // world 容器：承载所有世界坐标下的内容（nodes / overlay）
        const world = document.createElementNS(NS, 'g');
        world.setAttribute('data-layer', 'world');
        this.svg.appendChild(world);

        for (const name of ['edges', 'nodes', 'overlay']) {
            const g = document.createElementNS(NS, 'g');
            g.setAttribute('data-layer', name);
            world.appendChild(g);
            this.layers[name] = g;
        }
    }

    applyViewport() {
        const world = this.svg.querySelector('[data-layer="world"]');
        if (world) world.setAttribute('transform', this.viewport.getTransform());
    }

    /** 全量渲染。为保证正确性，先做全量；后续可加增量。 */
    render(diagram, selection) {
        this.layers.nodes.innerHTML = '';
        this.layers.edges.innerHTML = '';
        for (const node of diagram.allNodes()) {
            this._renderNode(node, selection);
        }
        this.applyViewport();
    }

    _renderNode(node, selection) {
        const typeDef = NodeTypes[node.type];
        if (!typeDef) return;

        const g = document.createElementNS(NS, 'g');
        g.setAttribute('data-node-id', node.id);
        g.setAttribute('transform', `translate(${node.x},${node.y})`);

        const inner = typeDef.render(node);
        if (inner) g.appendChild(inner);

        // 选中框（标记 data-selection，导出时会移除）
        if (selection.has(node.id)) {
            const b = nodeBounds(node);
            const sel = document.createElementNS(NS, 'rect');
            sel.setAttribute('x', b.x - node.x - 2);
            sel.setAttribute('y', b.y - node.y - 2);
            sel.setAttribute('width', b.w + 4);
            sel.setAttribute('height', b.h + 4);
            sel.setAttribute('fill', 'none');
            sel.setAttribute('stroke', '#0066ff');
            sel.setAttribute('stroke-width', '1.5');
            sel.setAttribute('stroke-dasharray', '4 3');
            sel.setAttribute('pointer-events', 'none');
            sel.setAttribute('data-selection', '1');
            g.appendChild(sel);
        }

        this.layers.nodes.appendChild(g);
    }

    /** DrawGesture 使用：在 overlay 层画草稿 */
    drawPreview(_, draft) {
        this.layers.overlay.innerHTML = '';
        if (!draft) return;
        const typeDef = NodeTypes[draft.type];
        if (!typeDef) return;
        const g = document.createElementNS(NS, 'g');
        g.setAttribute('opacity', '0.6');
        g.setAttribute('pointer-events', 'none');
        g.setAttribute('transform', `translate(${draft.x},${draft.y})`);
        const inner = typeDef.render(draft);
        if (inner) g.appendChild(inner);
        this.layers.overlay.appendChild(g);
    }

    clearPreview() {
        this.layers.overlay.innerHTML = '';
    }
}