import { ElementRegistry } from './ElementRegistry.js';

/**
 * 画布管理器
 * 职责：
 *  - 监听 drop 事件，创建元素实例
 *  - 管理所有实例（增删改查）
 *  - 处理选中、拖拽移动
 *  - 通知 UI 更新
 */
export class CanvasManager {
    constructor(canvasEl) {
        this.canvas = canvasEl;
        this.instances = new Map();     // id -> BaseElement
        this.selectedId = null;
        this.gridSize = 5;              // 吸附网格大小（px）
        this.snapEnabled = true;
        this.zIndexCounter = 1;

        this.onSelect = null;           // 外部回调
        this.onChange = null;

        this._bindCanvasDrop();
        this._bindCanvasClick();
    }

    /* ============ 拖入 ============ */
    _bindCanvasDrop() {
        this.canvas.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'copy';
        });

        this.canvas.addEventListener('drop', (e) => {
            e.preventDefault();
            const typeName = e.dataTransfer.getData('application/x-builder-element');
            if (!typeName) return;
            const ElementClass = ElementRegistry.get(typeName);
            if (!ElementClass) {
                console.warn('未知元素类型:', typeName);
                return;
            }
            const rect = this.canvas.getBoundingClientRect();
            // 让鼠标位置对应元素的左上角（略微偏移使体验更好）
            const x = e.clientX - rect.left + this.canvas.scrollLeft - 10;
            const y = e.clientY - rect.top + this.canvas.scrollTop - 10;
            this.add(ElementClass, x, y);
        });
    }

    /* ============ 点击空白取消选中 ============ */
    _bindCanvasClick() {
        this.canvas.addEventListener('mousedown', (e) => {
            if (e.target === this.canvas || e.target.classList.contains('canvas-hint')) {
                this.select(null);
            }
        });
    }

    /* ============ 添加元素实例 ============ */
    add(ElementClass, x, y) {
        // 吸附网格
        if (this.snapEnabled) {
            x = Math.round(x / this.gridSize) * this.gridSize;
            y = Math.round(y / this.gridSize) * this.gridSize;
        }
        const element = new ElementClass({ x, y });
        const dom = element.render();
        dom.style.zIndex = this.zIndexCounter++;

        this.canvas.appendChild(dom);
        this.instances.set(element.id, element);

        this._bindElementEvents(dom, element);
        this.select(element.id);
        this._notifyChange();
        return element;
    }

    /* ============ 元素事件绑定 ============ */
    _bindElementEvents(dom, element) {
        // mousedown 用于选中 + 拖动
        const onMouseDown = (e) => {
            if (e.button !== 0) return;
            // 拖动时的起点
            e.stopPropagation();
            this.select(element.id);
            this._startDrag(e, element);
        };

        dom.addEventListener('mousedown', onMouseDown);
        // 把 handler 挂到 element 上，避免重复绑定
        element._boundMouseDown = onMouseDown;
    }

    /* ============ 拖拽 ============ */
    _startDrag(e, element) {
        const startClientX = e.clientX;
        const startClientY = e.clientY;
        const startLeft = element.props.x || 0;
        const startTop = element.props.y || 0;

        const onMove = (ev) => {
            let nx = startLeft + (ev.clientX - startClientX);
            let ny = startTop + (ev.clientY - startClientY);
            if (this.snapEnabled) {
                nx = Math.round(nx / this.gridSize) * this.gridSize;
                ny = Math.round(ny / this.gridSize) * this.gridSize;
            }
            element.setPosition(nx, ny);
        };

        const onUp = () => {
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('mouseup', onUp);
            this._notifyChange();
        };

        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup', onUp);
    }

    /* ============ 选中 ============ */
    select(id) {
        if (this.selectedId === id) return;
        // 取消旧选中
        if (this.selectedId) {
            const prev = this.instances.get(this.selectedId);
            if (prev && prev.dom) prev.dom.classList.remove('builder-selected');
        }
        this.selectedId = id;
        // 高亮新选中
        if (id) {
            const cur = this.instances.get(id);
            if (cur && cur.dom) {
                cur.dom.classList.add('builder-selected');
                cur.dom.style.zIndex = this.zIndexCounter++;
            }
        }
        if (typeof this.onSelect === 'function') {
            this.onSelect(id ? this.instances.get(id) : null);
        }
    }

    /* ============ 更新属性 ============ */
    updateProps(id, newProps) {
        const el = this.instances.get(id);
        if (!el) return;
        el.updateProps(newProps);
        // 重建 DOM 后要重新绑定事件
        this._bindElementEvents(el.dom, el);
        this._notifyChange();
    }

    /* ============ 删除 ============ */
    remove(id) {
        const el = this.instances.get(id);
        if (!el) return;
        if (el.dom) el.dom.remove();
        this.instances.delete(id);
        if (this.selectedId === id) this.select(null);
        this._notifyChange();
    }

    /* ============ 清空 ============ */
    clear() {
        this.instances.forEach(el => el.dom && el.dom.remove());
        this.instances.clear();
        this.select(null);
        this._notifyChange();
    }

    /* ============ 获取全部 ============ */
    getAll() {
        return Array.from(this.instances.values());
    }

    /* ============ 通知变化 ============ */
    _notifyChange() {
        // 隐藏提示
        const hint = this.canvas.querySelector('.canvas-hint');
        if (hint) hint.style.display = this.instances.size > 0 ? 'none' : 'block';

        if (typeof this.onChange === 'function') {
            this.onChange(this.getAll());
        }
    }
}