/* ============ 应用入口 ============ */
import { ElementRegistry } from './core/ElementRegistry.js';
import { CanvasManager } from './core/CanvasManager.js';
import { DataLoader } from './core/DataLoader.js';
import { ElementPanel } from './ui/ElementPanel.js';
import { PropertyPanel } from './ui/PropertyPanel.js';
import { Toolbar } from './ui/Toolbar.js';

// 基础元素
import { TextElement } from './elements/primitives/TextElement.js';
import { HeadingElement } from './elements/primitives/HeadingElement.js';
import { ButtonElement } from './elements/primitives/ButtonElement.js';
import { ImageElement } from './elements/primitives/ImageElement.js';
import { InputElement } from './elements/primitives/InputElement.js';
import { LinkElement } from './elements/primitives/LinkElement.js';
import { DivElement } from './elements/primitives/DivElement.js';

// 组件元素
import { CardComponent } from './elements/components/CardComponent.js';
import { AlertComponent } from './elements/components/AlertComponent.js';
import { FormComponent } from './elements/components/FormComponent.js';
import { TableComponent } from './elements/components/TableComponent.js';
import { NavComponent } from './elements/components/NavComponent.js';

// ★ 数据元素
import { DataTableElement } from './elements/data/DataTableElement.js';

/* ============================================================
 * ★ 兜底：如果 global.js 因任何原因未加载，这里自行注入 apiUrl
 *   （正常情况下 index.html 已经 <script src="../global.js"> 加载了）
 * ============================================================ */
if (typeof window.apiUrl !== 'function') {
    (function () {
        const parts = window.location.pathname.split('/');
        const ctx = (parts.length > 1 && parts[1]) ? '/' + parts[1] : '';
        window.CONTEXT_PATH = ctx;
        window.apiUrl = function (path) {
            return ctx + (path.startsWith('/') ? path : '/' + path);
        };
        console.warn('[builder] global.js 未加载，已注入兜底 apiUrl，CONTEXT_PATH =', ctx);
    })();
} else {
    console.log('[builder] global.js 已加载，CONTEXT_PATH =', window.CONTEXT_PATH);
}

/* ============ 注册所有元素 ============ */
[
    // 基础
    TextElement, HeadingElement, ButtonElement, ImageElement,
    InputElement, LinkElement, DivElement,
    // 组件
    CardComponent, AlertComponent, FormComponent, TableComponent, NavComponent,
    // ★ 数据元素
    DataTableElement
].forEach(cls => ElementRegistry.register(cls));

/* ============ 初始化画布与 UI ============ */
const canvasEl = document.getElementById('canvas');
const canvasManager = new CanvasManager(canvasEl);
const dataLoader = new DataLoader(canvasManager);

new ElementPanel(document.getElementById('elementPanel'), canvasManager);
new PropertyPanel(document.getElementById('propertyPanel'), canvasManager, dataLoader);
new Toolbar(document.getElementById('toolbar'), canvasManager);

/* ============ 快捷键 ============ */
document.addEventListener('keydown', (e) => {
    // Delete / Backspace 删除选中元素（不在表单输入时）
    if ((e.key === 'Delete' || e.key === 'Backspace')
        && canvasManager.selectedId
        && !isTypingInForm()) {
        canvasManager.remove(canvasManager.selectedId);
    }
    // Ctrl+D 复制选中元素
    if (e.ctrlKey && e.key === 'd' && canvasManager.selectedId) {
        e.preventDefault();
        const el = canvasManager.instances.get(canvasManager.selectedId);
        if (el) {
            canvasManager.add(
                el.constructor,
                (el.props.x || 0) + 20,
                (el.props.y || 0) + 20
            );
        }
    }
});

function isTypingInForm() {
    const el = document.activeElement;
    if (!el) return false;
    const tag = el.tagName.toLowerCase();
    return tag === 'input' || tag === 'textarea'
        || tag === 'select' || el.isContentEditable;
}

/* ============ 暴露到 window，方便调试 ============ */
window.__builder = { canvasManager, dataLoader, ElementRegistry };
console.log('[builder] 初始化完成');