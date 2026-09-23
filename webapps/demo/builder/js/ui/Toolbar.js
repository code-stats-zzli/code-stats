import { Exporter } from '../io/Exporter.js';

/**
 * 顶部工具栏：清空、预览、导出 HTML、导出/导入 JSON
 */
export class Toolbar {
    constructor(rootEl, canvasManager) {
        this.root = rootEl;
        this.canvasManager = canvasManager;
        this._render();
    }

    _render() {
        this.root.innerHTML = `
            <div class="toolbar-brand">🧱 可视化页面搭建器</div>
            <div class="toolbar-actions">
                <button class="toolbar-btn" id="btnClear">🗑️ 清空</button>
                <button class="toolbar-btn" id="btnExportJson">⬇️ 导出JSON</button>
                <button class="toolbar-btn" id="btnImportJson">⬆️ 导入JSON</button>
                <button class="toolbar-btn" id="btnPreview">👁️ 预览</button>
                <button class="toolbar-btn primary" id="btnExport">📦 导出HTML</button>
            </div>
        `;

        this.root.querySelector('#btnClear').addEventListener('click', () => {
            if (confirm('确定清空画布吗？')) this.canvasManager.clear();
        });

        this.root.querySelector('#btnExport').addEventListener('click', () => {
            Exporter.downloadHTML(this.canvasManager);
        });

        this.root.querySelector('#btnPreview').addEventListener('click', () => {
            Exporter.preview(this.canvasManager);
        });

        this.root.querySelector('#btnExportJson').addEventListener('click', () => {
            Exporter.downloadJSON(this.canvasManager);
        });

        this.root.querySelector('#btnImportJson').addEventListener('click', () => {
            Exporter.importJSONFromFile(this.canvasManager);
        });
    }
}