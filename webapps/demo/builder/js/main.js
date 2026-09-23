/* ============ 应用入口 ============ */
import { ElementRegistry } from './core/ElementRegistry.js';
import { CanvasManager } from './core/CanvasManager.js';
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

/* ============ 注册所有元素 ============ */
[
    // 基础
    TextElement, HeadingElement, ButtonElement, ImageElement,
    InputElement, LinkElement, DivElement,
    // 组件
    CardComponent, AlertComponent, FormComponent, TableComponent, NavComponent
].forEach(cls => ElementRegistry.register(cls));

/* ============ 初始化画布与 UI ============ */
const canvasEl = document.getElementById('canvas');
const canvasManager = new CanvasManager(canvasEl);

new ElementPanel(document.getElementById('elementPanel'), canvasManager);
new PropertyPanel(document.getElementById('propertyPanel'), canvasManager);
new Toolbar(document.getElementById('toolbar'), canvasManager);

/* ============ 快捷键 ============ */
document.addEventListener('keydown', (e) => {
    // Delete / Backspace 删除选中元素
    if ((e.key === 'Delete' || e.key === 'Backspace')
        && canvasManager.selectedId
        && !isTypingInForm()) {
        canvasManager.remove(canvasManager.selectedId);
    }
    // Ctrl+D 复制（简化处理：复制到右下偏移 20px）
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
    return tag === 'input' || tag === 'textarea' || tag === 'select' || el.isContentEditable;
}

/* ============ 暴露到 window，便于调试 ============ */
window.__builder = { canvasManager, ElementRegistry };