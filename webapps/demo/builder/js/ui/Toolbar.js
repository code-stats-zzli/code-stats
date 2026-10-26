import { Exporter } from '../io/Exporter.js';

/**
 * 顶部工具栏：清空、预览、导出 HTML、导出/导入 JSON、保存到资源库
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
                <!-- ★ 新增：保存到资源库 -->
                <button class="toolbar-btn primary" id="btnSaveToResource">💾 保存到资源库</button>
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

        // ★ 新增：保存到资源库
        this.root.querySelector('#btnSaveToResource').addEventListener('click', () => {
            this._saveToResource();
        });
    }

    async _saveToResource() {
        const instances = this.canvasManager.getAll();
        if (!instances || instances.length === 0) {
            alert('画布为空，无法保存');
            return;
        }

        const name = prompt('请输入页面名称（无需扩展名）', 'my_page');
        if (!name || !name.trim()) return;

        const safeName = name.trim().replace(/[\\/:*?"<>|]/g, '_');
        const html = Exporter.toHTML(this.canvasManager, safeName);
        const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
        const file = new File([blob], safeName + '.html', { type: 'text/html' });

        const fd = new FormData();
        fd.append('file', file);
        fd.append('type', 'pages');
        fd.append('path', '');

        try {
            const apiUrl = (typeof window.apiUrl === 'function')
                ? window.apiUrl('/files/api/resources/upload')
                : '/files/api/resources/upload';
            const resp = await fetch(apiUrl, { method: 'POST', body: fd });
            const data = await resp.json();
            if (data.success) {
                const displayName = data.data && data.data.displayName ? data.data.displayName : safeName + '.html';
                alert('✅ 已保存到资源库（导出页面）：' + displayName);
            } else {
                alert('❌ 保存失败：' + (data.error || '未知错误'));
            }
        } catch (e) {
            alert('❌ 网络错误：' + e.message);
        }
    }
}