import { ElementRegistry } from '../core/ElementRegistry.js';

/**
 * 左侧元素抽屉
 * 由 + 号控制打开，展示所有已注册的元素（分组：基础 / 组件）
 */
export class ElementPanel {
    constructor(rootEl, canvasManager) {
        this.root = rootEl;
        this.canvasManager = canvasManager;

        this.fab = document.getElementById('addFab');
        this.drawer = document.querySelector('.builder-left');
        this.closeBtn = document.getElementById('drawerClose');

        this._render();
        this._bindToggle();
    }

    _render() {
        const groups = [
            { key: 'primitive', title: '🧱 基础元素' },
            { key: 'component', title: '🧩 组件元素' }
        ];
        let html = '';
        groups.forEach(g => {
            const list = ElementRegistry.getByCategory(g.key);
            if (!list.length) return;
            html += `<div class="element-group-title">${g.title}</div>`;
            list.forEach(Cls => {
                const meta = Cls.meta;
                html += `
                    <div class="element-item"
                         draggable="true"
                         data-type="${Cls.name}">
                        <span class="icon">${meta.icon}</span>
                        <span>${meta.label}</span>
                    </div>
                `;
            });
        });
        this.root.innerHTML = html;

        // 绑定拖拽
        this.root.querySelectorAll('.element-item').forEach(item => {
            item.addEventListener('dragstart', (e) => {
                e.dataTransfer.setData('application/x-builder-element', item.dataset.type);
                e.dataTransfer.effectAllowed = 'copy';
            });
        });
    }

    _bindToggle() {
        this.fab.addEventListener('click', () => {
            this.drawer.classList.toggle('drawer-open');
        });
        this.closeBtn.addEventListener('click', () => {
            this.drawer.classList.remove('drawer-open');
        });
    }
}