import { ElementRegistry } from '../core/ElementRegistry.js';

export class Exporter {

    /** 生成干净的 HTML 字符串 */
    static toHTML(canvasManager, title = '导出的页面') {
        const instances = canvasManager.getAll();
        const bodyContent = instances.map(el => {
            const clone = el.dom.cloneNode(true);
            // 去掉编辑期的标记
            clone.classList.remove('builder-element', 'builder-selected');
            clone.removeAttribute('data-element-id');
            clone.removeAttribute('data-element-type');
            // 保留定位样式（已在 dom.style 上），去掉 outline 等
            clone.style.outline = '';
            clone.style.boxShadow = '';
            clone.style.zIndex = '';
            return '  ' + clone.outerHTML;
        }).join('\n');

        return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${title}</title>
<style>
    * { box-sizing: border-box; }
    body {
        margin: 0;
        padding: 0;
        min-height: 100vh;
        position: relative;
        font-family: system-ui, -apple-system, 'Segoe UI', 'PingFang SC', sans-serif;
        color: #1e293b;
    }
</style>
</head>
<body>
${bodyContent}
</body>
</html>`;
    }

    /** 下载 HTML 文件 */
    static downloadHTML(canvasManager) {
        const html = this.toHTML(canvasManager);
        this._download(html, 'page.html', 'text/html;charset=utf-8');
    }

    /** 新窗口预览 */
    static preview(canvasManager) {
        const html = this.toHTML(canvasManager, '预览');
        const win = window.open('', '_blank');
        if (win) {
            win.document.write(html);
            win.document.close();
        }
    }

    /** 导出为 JSON */
    static toJSON(canvasManager) {
        return {
            version: '1.0',
            exportedAt: new Date().toISOString(),
            elements: canvasManager.getAll().map(el => el.toJSON())
        };
    }

    static downloadJSON(canvasManager) {
        const json = JSON.stringify(this.toJSON(canvasManager), null, 2);
        this._download(json, 'page.json', 'application/json;charset=utf-8');
    }

    /** 从 JSON 加载到画布 */
    static loadJSON(canvasManager, json) {
        canvasManager.clear();
        if (!json || !Array.isArray(json.elements)) return;
        json.elements.forEach(item => {
            const ElementClass = ElementRegistry.get(item.type);
            if (!ElementClass) {
                console.warn('未注册的元素类型，跳过:', item.type);
                return;
            }
            const element = new ElementClass(item.props);
            const dom = element.render();
            dom.style.zIndex = canvasManager.zIndexCounter++;
            canvasManager.canvas.appendChild(dom);
            canvasManager.instances.set(element.id, element);
            canvasManager._bindElementEvents(dom, element);
        });
        canvasManager._notifyChange();
    }

    /** 通过文件选择器导入 JSON */
    static importJSONFromFile(canvasManager) {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.json,application/json';
        input.onchange = () => {
            const file = input.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = (e) => {
                try {
                    const json = JSON.parse(e.target.result);
                    this.loadJSON(canvasManager, json);
                } catch (err) {
                    alert('JSON 解析失败: ' + err.message);
                }
            };
            reader.readAsText(file);
        };
        input.click();
    }

    /* ============ 内部工具 ============ */
    static _download(content, filename, mime) {
        const blob = new Blob([content], { type: mime });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
}