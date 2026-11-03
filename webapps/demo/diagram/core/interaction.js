/**
 * 交互层：把鼠标事件翻译成"用户想干嘛"，再交给对应手势处理。
 * 手势各自独立，新增工具只需新增一个 Gesture。
 */
import { genId } from './base.js';
import { createNode, nodeBounds } from './model.js';
import { AddNodeCommand, MoveNodesCommand } from './command.js';
import { NodeTypes } from './nodetypes.js';

/* ============ 选中集 ============ */
export class Selection {
    constructor() { this._ids = new Set(); }
    get size() { return this._ids.size; }
    has(id) { return this._ids.has(id); }
    ids() { return Array.from(this._ids); }
    add(id) { this._ids.add(id); }
    toggle(id) {
        this._ids.has(id) ? this._ids.delete(id) : this._ids.add(id);
    }
    clear() { this._ids.clear(); }
    replaceAll(ids) { this._ids = new Set(ids); }
}

/* ============ 交互控制器 ============ */
export class InteractionController {
    constructor(engine, svg) {
        this.engine = engine;
        this.svg = svg;
        this.gesture = null;
        this._bind();
    }

    _bind() {
        this.svg.addEventListener('pointerdown', this._onDown.bind(this));
        window.addEventListener('pointermove', this._onMove.bind(this));
        window.addEventListener('pointerup', this._onUp.bind(this));
        this.svg.addEventListener('wheel', this._onWheel.bind(this), { passive: false });
        this.svg.addEventListener('contextmenu', e => e.preventDefault());
    }

    _toWorld(e) {
        const rect = this.svg.getBoundingClientRect();
        const sp = { x: e.clientX - rect.left, y: e.clientY - rect.top };
        return this.engine.renderer.viewport.screenToWorld(sp);
    }

    _onDown(e) {
        // 右键 / 中键 → 平移
        if (e.button === 1 || e.button === 2) {
            this.gesture = new PanGesture(this.engine, e);
            this.gesture.start();
            return;
        }
        if (e.button !== 0) return;

        const tool = this.engine.tool;
        const startWorld = this._toWorld(e);

        if (tool === 'select') {
            const hitEl = e.target.closest('[data-node-id]');
            if (hitEl) {
                const id = hitEl.dataset.nodeId;
                if (e.shiftKey) {
                    this.engine.selection.toggle(id);
                } else if (!this.engine.selection.has(id)) {
                    this.engine.selection.replaceAll([id]);
                }
                this.engine.emit('selection:change');
                this.engine.render();

                this.gesture = new DragGesture(this.engine, e, startWorld);
                this.gesture.start();
            } else {
                if (!e.shiftKey) {
                    this.engine.selection.clear();
                    this.engine.emit('selection:change');
                    this.engine.render();
                }
                this.gesture = new MarqueeGesture(this.engine, e, startWorld);
                this.gesture.start();
            }
        } else {
            this.gesture = new DrawGesture(this.engine, tool, startWorld);
            this.gesture.start();
        }
    }

    _onMove(e) {
        if (!this.gesture) return;
        this.gesture.move(this._toWorld(e), e);
    }

    _onUp(e) {
        if (!this.gesture) return;
        const g = this.gesture;
        this.gesture = null;
        g.end(this._toWorld(e), e);
    }

    _onWheel(e) {
        e.preventDefault();
        const rect = this.svg.getBoundingClientRect();
        const sp = { x: e.clientX - rect.left, y: e.clientY - rect.top };
        const factor = e.deltaY > 0 ? 0.9 : 1.1;
        this.engine.renderer.viewport.zoomAt(sp, factor);
        this.engine.render();
    }
}

/* ============================================================
 * 手势：拖拽选中节点
 * 拖拽期间直接修改模型坐标，但 pointerup 时先回滚再 dispatch，
 * 保证 undo 栈只记录一次净位移。
 * ============================================================ */
class DragGesture {
    constructor(engine, e, startWorld) {
        this.engine = engine;
        this.startScreen = { x: e.clientX, y: e.clientY };
        this.moved = false;
        this.origins = [];
        for (const id of engine.selection.ids()) {
            const n = engine.diagram.getNode(id);
            if (n) this.origins.push({ id, x: n.x, y: n.y });
        }
    }
    start() {}

    move(worldPt, e) {
        const dx = e.clientX - this.startScreen.x;
        const dy = e.clientY - this.startScreen.y;
        if (Math.abs(dx) + Math.abs(dy) < 3) return;
        this.moved = true;
        const scale = this.engine.renderer.viewport.scale;
        const wdx = dx / scale;
        const wdy = dy / scale;
        for (const o of this.origins) {
            const n = this.engine.diagram.getNode(o.id);
            if (n) { n.x = o.x + wdx; n.y = o.y + wdy; }
        }
        this.engine.render();
    }

    end(worldPt, e) {
        if (!this.moved) return;
        const dx = (e.clientX - this.startScreen.x) / this.engine.renderer.viewport.scale;
        const dy = (e.clientY - this.startScreen.y) / this.engine.renderer.viewport.scale;
        if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) {
            // 位移太小，视为点击
            for (const o of this.origins) {
                const n = this.engine.diagram.getNode(o.id);
                if (n) { n.x = o.x; n.y = o.y; }
            }
            this.engine.render();
            return;
        }
        // 回滚到起点，让命令记录净位移
        for (const o of this.origins) {
            const n = this.engine.diagram.getNode(o.id);
            if (n) { n.x = o.x; n.y = o.y; }
        }
        const moves = this.origins.map(o => ({ id: o.id, dx, dy }));
        this.engine.commands.dispatch(new MoveNodesCommand(moves), this.engine.diagram);
    }
}

/* ============================================================
 * 手势：平移画布（右键 / 中键）
 * ============================================================ */
class PanGesture {
    constructor(engine, e) {
        this.engine = engine;
        this.startX = e.clientX;
        this.startY = e.clientY;
        const vp = engine.renderer.viewport;
        this.originX = vp.x;
        this.originY = vp.y;
    }
    start() { this.engine.svg.style.cursor = 'grabbing'; }
    move(worldPt, e) {
        const vp = this.engine.renderer.viewport;
        vp.x = this.originX + (e.clientX - this.startX);
        vp.y = this.originY + (e.clientY - this.startY);
        this.engine.render();
    }
    end() { this.engine.svg.style.cursor = ''; }
}

/* ============================================================
 * 手势：框选（点击空白拖拽）
 * ============================================================ */
class MarqueeGesture {
    constructor(engine, e, startWorld) {
        this.engine = engine;
        this.startWorld = startWorld;
        this.baseSelection = e.shiftKey ? engine.selection.ids() : [];
    }
    start() {}

    move(worldPt) {
        const r = this._norm(this.startWorld, worldPt);
        const overlay = this.engine.renderer.layers.overlay;
        overlay.innerHTML = '';
        const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        rect.setAttribute('x', r.x);
        rect.setAttribute('y', r.y);
        rect.setAttribute('width', r.w);
        rect.setAttribute('height', r.h);
        rect.setAttribute('fill', 'rgba(0,102,255,0.1)');
        rect.setAttribute('stroke', '#0066ff');
        rect.setAttribute('stroke-dasharray', '4 3');
        rect.setAttribute('pointer-events', 'none');
        overlay.appendChild(rect);
    }

    end(worldPt) {
        const r = this._norm(this.startWorld, worldPt);
        const sel = new Set(this.baseSelection);
        for (const n of this.engine.diagram.allNodes()) {
            const b = nodeBounds(n);
            if (b.x >= r.x && b.y >= r.y
                && b.x + b.w <= r.x + r.w
                && b.y + b.h <= r.y + r.h) {
                sel.add(n.id);
            }
        }
        this.engine.selection.replaceAll(Array.from(sel));
        this.engine.emit('selection:change');
        this.engine.renderer.layers.overlay.innerHTML = '';
        this.engine.render();
    }

    _norm(a, b) {
        return {
            x: Math.min(a.x, b.x),
            y: Math.min(a.y, b.y),
            w: Math.abs(b.x - a.x),
            h: Math.abs(b.y - a.y)
        };
    }
}

/* ============================================================
 * 手势：拖拽绘制新元素（矩形/椭圆/线/箭头/文本/自由笔）
 * ============================================================ */
class DrawGesture {
    constructor(engine, tool, startWorld) {
        this.engine = engine;
        this.tool = tool;
        this.startWorld = startWorld;
        this.penPoints = [{ x: 0, y: 0 }];
        this.moved = false;
    }
    start() {}

    move(worldPt) {
        if (Math.abs(worldPt.x - this.startWorld.x)
            + Math.abs(worldPt.y - this.startWorld.y) > 3) {
            this.moved = true;
        }

        let draft;
        if (this.tool === 'pen') {
            const last = this.penPoints[this.penPoints.length - 1];
            const rel = {
                x: worldPt.x - this.startWorld.x,
                y: worldPt.y - this.startWorld.y
            };
            if (Math.hypot(rel.x - last.x, rel.y - last.y) > 2) {
                this.penPoints.push(rel);
            }
            draft = {
                type: 'pen',
                x: this.startWorld.x,
                y: this.startWorld.y,
                w: 0, h: 0,
                props: { points: this.penPoints.slice(), stroke: '#1e293b', strokeWidth: 2 }
            };
        } else if (this.tool === 'line' || this.tool === 'arrow') {
            draft = {
                type: this.tool,
                x: this.startWorld.x,
                y: this.startWorld.y,
                w: worldPt.x - this.startWorld.x,
                h: worldPt.y - this.startWorld.y,
                props: { stroke: '#1e293b', strokeWidth: 2 }
            };
        } else {
            const x1 = Math.min(this.startWorld.x, worldPt.x);
            const y1 = Math.min(this.startWorld.y, worldPt.y);
            draft = {
                type: this.tool,
                x: x1,
                y: y1,
                w: Math.abs(worldPt.x - this.startWorld.x),
                h: Math.abs(worldPt.y - this.startWorld.y),
                props: {}
            };
        }
        this.engine.renderer.drawPreview(null, draft);
    }

    end(worldPt) {
        this.engine.renderer.clearPreview();
        const typeDef = NodeTypes[this.tool];
        if (!typeDef) return;

        let node;

        if (this.tool === 'pen') {
            if (this.penPoints.length < 2) return;
            let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
            for (const p of this.penPoints) {
                if (p.x < minX) minX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.x > maxX) maxX = p.x;
                if (p.y > maxY) maxY = p.y;
            }
            const shifted = this.penPoints.map(p => ({ x: p.x - minX, y: p.y - minY }));
            node = createNode({
                type: 'pen',
                x: this.startWorld.x + minX,
                y: this.startWorld.y + minY,
                w: Math.max(1, maxX - minX),
                h: Math.max(1, maxY - minY),
                props: Object.assign({}, typeDef.defaultProps, { points: shifted })
            });
        } else if (this.tool === 'line' || this.tool === 'arrow') {
            const w = worldPt.x - this.startWorld.x;
            const h = worldPt.y - this.startWorld.y;
            if (Math.abs(w) < 3 && Math.abs(h) < 3) return;
            node = createNode({
                type: this.tool,
                x: this.startWorld.x,
                y: this.startWorld.y,
                w, h,
                props: Object.assign({}, typeDef.defaultProps)
            });
        } else {
            // 矩形 / 椭圆 / 文本
            if (!this.moved) {
                // 单击 = 使用默认尺寸
                const size = typeDef.defaultSize;
                node = createNode({
                    type: this.tool,
                    x: this.startWorld.x,
                    y: this.startWorld.y,
                    w: size.w,
                    h: size.h,
                    props: Object.assign({}, typeDef.defaultProps)
                });
            } else {
                const x1 = Math.min(this.startWorld.x, worldPt.x);
                const y1 = Math.min(this.startWorld.y, worldPt.y);
                node = createNode({
                    type: this.tool,
                    x: x1,
                    y: y1,
                    w: Math.max(4, Math.abs(worldPt.x - this.startWorld.x)),
                    h: Math.max(4, Math.abs(worldPt.y - this.startWorld.y)),
                    props: Object.assign({}, typeDef.defaultProps)
                });
            }
        }
        this.engine.commands.dispatch(new AddNodeCommand(node), this.engine.diagram);
    }
}