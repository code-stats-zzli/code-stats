import { ElementRegistry } from '../core/ElementRegistry.js';

export class Exporter {

    static toHTML(canvasManager, title = '导出的页面') {
        const instances = canvasManager.getAll();
        const bodyContent = instances.map(el => {
            const clone = el.dom.cloneNode(true);
            clone.classList.remove('builder-element', 'builder-selected');
            clone.removeAttribute('data-element-id');
            clone.removeAttribute('data-element-type');
            clone.style.outline = '';
            clone.style.boxShadow = '';
            clone.style.zIndex = '';
            return '  ' + clone.outerHTML;
        }).join('\n');

        return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>${title}</title>
<style>
    * { box-sizing: border-box; }
    body { margin:0; padding:0; min-height:100vh; position:relative;
           font-family: system-ui, -apple-system, 'PingFang SC', sans-serif; }
</style>
</head>
<body>
${bodyContent}
</body>
</html>`;
    }

    static downloadHTML(canvasManager) {
        this._download(this.toHTML(canvasManager), 'page.html', 'text/html;charset=utf-8');
    }

    static preview(canvasManager) {
        const win = window.open('', '_blank');
        if (win) { win.document.write(this.toHTML(canvasManager, '预览')); win.document.close(); }
    }

    static toJSON(canvasManager) {
        return {
            version: '1.1',
            exportedAt: new Date().toISOString(),
            elements: canvasManager.getAll().map(el => el.toJSON())
        };
    }

    static downloadJSON(canvasManager) {
        const json = JSON.stringify(this.toJSON(canvasManager), null, 2);
        this._download(json, 'page.json', 'application/json;charset=utf-8');
    }

    /** 从 JSON 恢复页面 + 自动加载数据 */
    static loadJSON(canvasManager, json) {
        canvasManager.clear();
        if (!json || !Array.isArray(json.elements)) return;

        json.elements.forEach(item => {
            const ElementClass = ElementRegistry.get(item.type);
            if (!ElementClass) { console.warn('未注册类型，跳过:', item.type); return; }
            const element = new ElementClass(item.props || {});
            if (item.dataBinding) element.dataBinding = item.dataBinding;
            const dom = element.render();
            dom.style.zIndex = canvasManager.zIndexCounter++;
            canvasManager.canvas.appendChild(dom);
            canvasManager.instances.set(element.id, element);
            canvasManager._bindElementEvents(dom, element);
        });
        canvasManager._notifyChange();

        // ★ 加载完成后自动触发数据加载
        if (window.__builder && window.__builder.dataLoader) {
            window.__builder.dataLoader.reloadAll();
        }
    }

    static importJSONFromFile(canvasManager) {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.json,application/json';
        input.onchange = () => {
            const file = input.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = (e) => {
                try { this.loadJSON(canvasManager, JSON.parse(e.target.result)); }
                catch (err) { alert('JSON 解析失败: ' + err.message); }
            };
            reader.readAsText(file);
        };
        input.click();
    }

    static _download(content, filename, mime) {
        const blob = new Blob([content], { type: mime });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = filename;
        document.body.appendChild(a); a.click(); document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
}