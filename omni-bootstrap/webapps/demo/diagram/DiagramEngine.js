/**
 * 顶层引擎：把 core 各模块组装起来，对外暴露一个门面 API。
 * 分层：基础工具 → 模型 → 视图 → 命令 → 交互 → 插件（节点类型）→ 应用。
 */
import { EventBus } from './core/base.js';
import { Diagram, serialize, deserialize, validate } from './core/model.js';
import { SvgRenderer } from './core/view.js';
import { CommandManager, RemoveNodesCommand, UpdateNodeCommand } from './core/command.js';
import { InteractionController, Selection } from './core/interaction.js';
import { NodeTypeList } from './core/nodetypes.js';

export class DiagramEngine extends EventBus {
    constructor(svgElement) {
        super();
        this.svg = svgElement;
        this.renderer = new SvgRenderer(svgElement);
        this.diagram = new Diagram();
        this.commands = new CommandManager();
        this.selection = new Selection();
        this.interaction = new InteractionController(this, svgElement);
        this.tool = 'select';

        // 命令执行后统一重绘、广播
        this.commands.onChange = (diagram) => {
            this.render();
            this.emit('change', this.diagram);
            this.emit('selection:change');
        };
    }

    /** 渲染整张图 */
    render() {
        this.renderer.render(this.diagram, this.selection);
        this.emit('render');
    }

    /** 切换工具：select / pan / rect / ellipse / line / arrow / text / pen */
    setTool(name) {
        this.tool = name;
        this.svg.style.cursor =
            name === 'select' ? 'default' :
            name === 'pan' ? 'grab' :
            'crosshair';
        this.emit('tool:change', name);
    }

    /* ============ 序列化 ============ */

    serialize() {
        return serialize(this.diagram);
    }

    load(json) {
        if (!validate(json)) {
            console.warn('[diagram] invalid json, reset to empty');
            json = { version: 1, nodes: [] };
        }
        this.diagram = deserialize(json);
        this.selection.clear();
        this.commands.clear();
        this.render();
        this.emit('change', this.diagram);
        this.emit('selection:change');
    }

    clear() {
        this.load({ version: 1, nodes: [] });
    }

    /* ============ 撤销 / 重做 ============ */

    undo() {
        const d = this.commands.undo(this.diagram);
        if (d) {
            this.render();
            this.emit('change', this.diagram);
            this.emit('selection:change');
        }
    }

    redo() {
        const d = this.commands.redo(this.diagram);
        if (d) {
            this.render();
            this.emit('change', this.diagram);
            this.emit('selection:change');
        }
    }

    /* ============ 选中操作 ============ */

    deleteSelected() {
        if (this.selection.size === 0) return;
        this.commands.dispatch(
            new RemoveNodesCommand(this.selection.ids()),
            this.diagram
        );
        this.selection.clear();
        this.emit('selection:change');
        this.render();
    }

    updateSelected(patch) {
        for (const id of this.selection.ids()) {
            this.commands.dispatch(
                new UpdateNodeCommand(id, patch),
                this.diagram
            );
        }
    }

    selectAll() {
        this.selection.replaceAll(this.diagram.allNodes().map(n => n.id));
        this.emit('selection:change');
        this.render();
    }

    /* ============ 视图 ============ */

    zoomToFit() {
        const rect = this.svg.getBoundingClientRect();
        const bounds = this.diagram.getBounds();
        this.renderer.viewport.zoomToFit(rect.width, rect.height, bounds);
        this.render();
    }

    /* ============ 导出 ============ */

    exportSvg() {
        const bounds = this.diagram.getBounds();
        const padding = 20;
        const w = Math.max(100, Math.ceil(bounds.w + padding * 2));
        const h = Math.max(100, Math.ceil(bounds.h + padding * 2));

        const clone = this.svg.cloneNode(true);
        // 清空临时层
        clone.querySelectorAll('[data-layer="overlay"]').forEach(el => el.innerHTML = '');
        // 移除选中框
        clone.querySelectorAll('[data-selection="1"]').forEach(el => el.remove());

        const worldLayer = clone.querySelector('[data-layer="world"]');
        if (worldLayer) {
            worldLayer.setAttribute('transform',
                `translate(${padding - bounds.x},${padding - bounds.y})`);
        }
        // 移除编辑器产生的辅助属性
        clone.querySelectorAll('[data-node-id]').forEach(el => el.removeAttribute('data-node-id'));
        clone.removeAttribute('style');
        clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
        clone.setAttribute('width', w);
        clone.setAttribute('height', h);
        clone.setAttribute('viewBox', `0 0 ${w} ${h}`);

        return '<?xml version="1.0" encoding="UTF-8"?>\n'
            + new XMLSerializer().serializeToString(clone);
    }

    async exportPng() {
        const svgStr = this.exportSvg();
        const blob = new Blob([svgStr], { type: 'image/svg+xml;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        return new Promise((resolve, reject) => {
            const img = new Image();
            img.onload = () => {
                try {
                    const canvas = document.createElement('canvas');
                    canvas.width = img.naturalWidth || img.width || 800;
                    canvas.height = img.naturalHeight || img.height || 600;
                    const ctx = canvas.getContext('2d');
                    ctx.fillStyle = '#ffffff';
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                    ctx.drawImage(img, 0, 0);
                    URL.revokeObjectURL(url);
                    resolve(canvas.toDataURL('image/png'));
                } catch (e) {
                    URL.revokeObjectURL(url);
                    reject(e);
                }
            };
            img.onerror = () => {
                URL.revokeObjectURL(url);
                reject(new Error('svg load failed'));
            };
            img.src = url;
        });
    }

    /** 供 UI 使用：所有已注册的节点类型 */
    getNodeTypes() {
        return NodeTypeList;
    }
}