/** 页面数据加载编排 */
export class DataLoader {
    constructor(canvasManager) {
        this.canvas = canvasManager;
        this.loadingIds = new Set();
    }

    async reloadAll() {
        const list = this.canvas.getAll().filter(el => el.dataBinding);
        for (const el of list) {
            await this.reloadElement(el);
        }
    }

    async reloadElement(element) {
        const binding = element.dataBinding;
        if (!binding) return;
        if (this.loadingIds.has(element.id)) return;
        this.loadingIds.add(element.id);

        try {
            const params = Object.assign(
                { page: 1, pageSize: 20 },
                binding.params || {}
            );
            let result;

            if (binding.cubeId) {
                // 走已持久化 cube
                const resp = await fetch(window.apiUrl('/cube/api/query'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ cubeId: binding.cubeId, params })
                });
                result = await resp.json();
            } else if (binding.datasourceId && binding.table) {
                // 临时 inline cube → preview
                const resp = await fetch(window.apiUrl('/cube/api/preview'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        type: 'TABLE',
                        config: {
                            datasourceId: binding.datasourceId,
                            database: binding.database,
                            table: binding.table,
                            columns: binding.columns || null
                        },
                        params
                    })
                });
                result = await resp.json();
            } else {
                return;
            }

            if (!result.success) {
                this.renderError(element, result.meta?.error || '加载失败');
                return;
            }
            element.runtimeData = result;
            element.applyData(result);
        } catch (e) {
            console.error('[DataLoader]', e);
            this.renderError(element, e.message);
        } finally {
            this.loadingIds.delete(element.id);
        }
    }

    renderError(element, msg) {
        if (!element.dom) return;
        const hint = element.dom.querySelector('.dc-error')
            || element.dom.querySelector('.dc-placeholder');
        const target = hint || element.dom;
        target.innerHTML = `<div class="dc-error" style="padding:16px;color:#dc2626;
            text-align:center;font-size:13px;">❌ ${msg}</div>`;
    }
}