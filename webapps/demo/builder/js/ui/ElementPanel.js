import { ElementRegistry } from '../core/ElementRegistry.js';

/**
 * 左侧元素抽屉 —— 动态分类渲染
 * 分类顺序：primitive → component → data → 其他（按注册顺序追加）
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
        // 已知分类的中文标题 + 排序权重
        const CATEGORY_META = {
            primitive: { title: '🧱 基础元素', order: 1 },
            component: { title: '🧩 组件元素', order: 2 },
            data:      { title: '🔌 数据元素', order: 3 }
        };

        // ★ 从 Registry 动态收集所有出现过的 category
        const all = ElementRegistry.getAll();
        const categories = [...new Set(
            all.map(c => (c.meta && c.meta.category) || 'primitive')
        )];

        // 按已知顺序排，未知分类追加到末尾
        categories.sort((a, b) => {
            const oa = CATEGORY_META[a]?.order ?? 999;
            const ob = CATEGORY_META[b]?.order ?? 999;
            return oa - ob;
        });

        let html = '';
        categories.forEach(cat => {
            const list = ElementRegistry.getByCategory(cat);
            if (!list.length) return;
            const title = CATEGORY_META[cat]?.title || cat;
            html += `<div class="element-group-title">${title}</div>`;
            list.forEach(Cls => {
                const meta = Cls.meta || {};
                html += `
                    <div class="element-item"
                         draggable="true"
                         data-type="${Cls.name}">
                        <span class="icon">${meta.icon || '📦'}</span>
                        <span>${meta.label || Cls.name}</span>
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