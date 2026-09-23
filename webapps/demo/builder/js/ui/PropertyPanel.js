/**
 * 右侧属性面板
 * 选中元素后展示其 props，可编辑
 */
export class PropertyPanel {
    constructor(rootEl, canvasManager) {
        this.root = rootEl;
        this.canvasManager = canvasManager;
        this.currentId = null;

        this.canvasManager.onSelect = (el) => this.render(el);
        this.render(null);
    }

    render(element) {
        if (!element) {
            this.currentId = null;
            this.root.innerHTML = '<div class="empty-tip">👈 选中元素后可编辑属性</div>';
            return;
        }
        this.currentId = element.id;
        const meta = element.constructor.meta;
        const props = element.props;

        let html = `<h3>${meta.icon} ${meta.label} 属性</h3>`;

        // 位置
        html += this._row('位置 X', 'x', props.x, 'number');
        html += this._row('位置 Y', 'y', props.y, 'number');

        // 遍历其他 props
        for (const key of Object.keys(props)) {
            if (key === 'x' || key === 'y') continue;
            const value = props[key];
            // 简单类型判断：数字 / 字符串
            const type = (typeof value === 'number') ? 'number' : 'text';
            html += this._row(key, key, value, type);
        }

        html += `<button class="btn-danger" id="deleteElBtn">🗑️ 删除元素</button>`;
        this.root.innerHTML = html;

        // 绑定编辑事件（用 change，避免每次输入都重建 DOM 导致失焦）
        this.root.querySelectorAll('input[data-key]').forEach(input => {
            input.addEventListener('change', (e) => {
                const key = e.target.dataset.key;
                let val = e.target.value;
                if (e.target.type === 'number') val = parseFloat(val) || 0;
                this.canvasManager.updateProps(this.currentId, { [key]: val });
                // 重新渲染属性面板（保证显示同步）
                const el = this.canvasManager.instances.get(this.currentId);
                if (el) this.render(el);
            });
        });

        // 删除
        const delBtn = this.root.querySelector('#deleteElBtn');
        if (delBtn) {
            delBtn.addEventListener('click', () => {
                this.canvasManager.remove(this.currentId);
            });
        }
    }

    _row(label, key, value, type) {
        const safeVal = (value == null) ? '' : String(value).replace(/"/g, '&quot;');
        return `
            <div class="prop-group">
                <label>${label}</label>
                <input type="${type}" data-key="${key}" value="${safeVal}">
            </div>`;
    }
}