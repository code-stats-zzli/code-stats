/**
 * 系统管理 · 系统参数模块。
 *
 * 存储：<user.dir>/resources/others/system-params.json
 * 读取优先级：JSON > classpath application.properties
 *
 * 导出：omni-pack（默认，可被「代码分析 → 代码导入」还原）/ file-block / merge-file / plain-json
 * 导入：自动识别上述 4 种格式，只应用可修改列表中的 Key
 */
(function () {
    'use strict';

    SysCenter.register({
        id: 'params',
        name: '系统参数',
        icon: '⚙️',
        desc: '查看 / 修改 resources/others/system-params.json；可修改 Key 只读、值可编辑；支持 omni-pack / file-block / merge-file / 纯 JSON 导出与导入',
        mount: mountParams
    });

    function mountParams(container) {
        const API = (typeof window.apiUrl === 'function')
            ? window.apiUrl('/sys/api/params')
            : '/sys/api/params';

        container.innerHTML = `
            <div class="params-wrap">
                <div class="params-toolbar">
                    <div class="params-tabs">
                        <button class="params-tab active" data-tab="modifiable">可修改配置</button>
                        <button class="params-tab" data-tab="immutable">不可修改配置</button>
                    </div>
                    <input id="paramsSearch" class="params-search" placeholder="搜索 Key / 描述 / 涉及文件...">
                    <button id="paramsRefreshBtn" class="btn-secondary params-btn-sm">🔄 刷新</button>
                    <button id="paramsReloadBtn" class="btn-secondary params-btn-sm" title="从 JSON 文件重新加载到内存">♻ 重新加载</button>
                    <select id="paramsExportFormat" class="params-format-select" title="导出格式">
                        <option value="omni-pack">omni-pack（可导入项目）</option>
                        <option value="file-block">file-block</option>
                        <option value="merge-file">merge-file</option>
                        <option value="plain-json">纯 JSON</option>
                    </select>
                    <button id="paramsExportBtn" class="btn-secondary params-btn-sm">⬇ 导出</button>
                    <button id="paramsImportBtn" class="btn-secondary params-btn-sm">⬆ 导入</button>
                </div>
                <div id="paramsMeta" class="params-meta"></div>
                <div id="paramsTableWrap" class="params-table-wrap">
                    <div class="params-empty">加载中...</div>
                </div>
            </div>

            <!-- 导入弹窗 -->
            <div id="paramsImportModal" class="params-modal" style="display:none;">
                <div class="params-modal-content">
                    <div class="params-modal-header">
                        <span>⬆ 导入系统参数</span>
                        <span class="params-modal-close" id="paramsImportClose">✖</span>
                    </div>
                    <div class="params-modal-body">
                        <div class="params-import-tabs">
                            <button class="params-import-tab active" data-pane="file">📂 上传文件</button>
                            <button class="params-import-tab" data-pane="paste">📋 粘贴内容</button>
                        </div>

                        <div class="params-import-pane active" id="paramsImportPaneFile">
                            <div class="params-import-file-drop" id="paramsImportFileDrop">
                                <div style="font-size:36px;">📄</div>
                                <div class="params-import-file-name" id="paramsImportFileName">点击选择文件（支持 .json / .md / .txt / .log）</div>
                                <div style="font-size:11px;color:#94a3b8;margin-top:4px;">文件内容将按所选格式解析，自动识别 system-params.json</div>
                            </div>
                            <input type="file" id="paramsImportFile" accept=".json,.md,.txt,.log" style="display:none;">
                        </div>

                        <div class="params-import-pane" id="paramsImportPanePaste">
                            <textarea id="paramsImportText" class="params-import-textarea"
                                placeholder="粘贴系统参数内容，支持四种格式：&#10;  1) omni-pack 围栏 + file 围栏（resources/others/system-params.json）&#10;  2) file 围栏（resources/others/system-params.json）&#10;  3) // ======== resources/others/system-params.json ========&#10;  4) 纯 JSON {&quot;server.port&quot;:&quot;28080&quot;}"></textarea>
                        </div>

                        <div class="params-import-options">
                            <label>格式：
                                <select id="paramsImportFormat">
                                    <option value="auto">自动识别</option>
                                    <option value="omni-pack">omni-pack</option>
                                    <option value="file-block">file-block</option>
                                    <option value="merge-file">merge-file</option>
                                    <option value="plain-json">纯 JSON</option>
                                </select>
                            </label>
                            <button id="paramsImportParseBtn" class="btn-secondary params-btn-sm">🔍 解析预览</button>
                        </div>

                        <div id="paramsImportPreview" class="params-import-preview" style="display:none;">
                            <div class="params-preview-header">
                                <span>📋 解析结果</span>
                                <span id="paramsImportDetectedFormat" style="font-weight:400;font-size:12px;"></span>
                            </div>
                            <div class="params-preview-body" id="paramsImportPreviewBody"></div>
                        </div>
                    </div>
                    <div class="params-modal-footer">
                        <button id="paramsImportApplyBtn" class="btn-primary params-btn-sm" disabled>✅ 确认导入</button>
                        <button id="paramsImportCancelBtn" class="btn-secondary params-btn-sm">取消</button>
                    </div>
                </div>
            </div>
        `;

        const $ = id => container.querySelector('#' + id);
        const searchInput = $('paramsSearch');
        const metaEl = $('paramsMeta');
        const tableWrap = $('paramsTableWrap');
        const refreshBtn = $('paramsRefreshBtn');
        const reloadBtn = $('paramsReloadBtn');
        const exportFormatSel = $('paramsExportFormat');
        const exportBtn = $('paramsExportBtn');
        const importBtn = $('paramsImportBtn');

        // 导入弹窗
        const importModal = $('paramsImportModal');
        const importClose = $('paramsImportClose');
        const importCancelBtn = $('paramsImportCancelBtn');
        const importApplyBtn = $('paramsImportApplyBtn');
        const importTabs = container.querySelectorAll('.params-import-tab');
        const importPaneFile = $('paramsImportPaneFile');
        const importPanePaste = $('paramsImportPanePaste');
        const importFileInput = $('paramsImportFile');
        const importFileDrop = $('paramsImportFileDrop');
        const importFileName = $('paramsImportFileName');
        const importText = $('paramsImportText');
        const importFormatSel = $('paramsImportFormat');
        const importParseBtn = $('paramsImportParseBtn');
        const importPreview = $('paramsImportPreview');
        const importDetectedFormat = $('paramsImportDetectedFormat');
        const importPreviewBody = $('paramsImportPreviewBody');

        let data = { modifiable: [], immutable: [], jsonFile: '', jsonFileExists: false, paramsRelPath: '' };
        let activeTab = 'modifiable';
        let keyword = '';
        let importMode = 'file';
        let currentImportFile = null;
        let currentParseResult = null;

        function escapeHtml(s) {
            if (s == null) return '';
            return String(s).replace(/[&<>"']/g, m => ({
                '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
            }[m]));
        }
        function cssEscape(s) {
            if (window.CSS && CSS.escape) return CSS.escape(s);
            return String(s).replace(/["\\]/g, '\\$&');
        }
        function toast(msg, isErr) {
            let el = document.querySelector('.params-toast');
            if (!el) {
                el = document.createElement('div');
                el.className = 'params-toast';
                document.body.appendChild(el);
            }
            el.textContent = msg;
            el.className = 'params-toast' + (isErr ? ' err' : '');
            clearTimeout(el.__timer);
            el.__timer = setTimeout(() => el.remove(), 2800);
        }

        async function load() {
            tableWrap.innerHTML = '<div class="params-empty">加载中...</div>';
            try {
                const resp = await fetch(API + '/list', { cache: 'no-store' });
                const json = await resp.json();
                if (!json.success) throw new Error(json.error || '加载失败');
                data = {
                    modifiable: json.modifiable || [],
                    immutable: json.immutable || [],
                    jsonFile: json.jsonFile || '',
                    jsonFileExists: !!json.jsonFileExists,
                    paramsRelPath: json.paramsRelPath || 'resources/others/system-params.json'
                };
                metaEl.innerHTML =
                    '📄 存储文件：<b>' + escapeHtml(data.jsonFile) + '</b>'
                    + (data.jsonFileExists ? ' ✅' : ' ⚠ 尚未生成（保存后自动创建）')
                    + ' &nbsp;·&nbsp; 读取优先级：JSON &gt; classpath application.properties'
                    + ' &nbsp;·&nbsp; 导出 omni-pack 可被「代码分析 → 代码导入」还原到 <b>'
                    + escapeHtml(data.paramsRelPath) + '</b>';
                render();
            } catch (e) {
                tableWrap.innerHTML = `<div class="params-error">❌ 加载失败：${escapeHtml(e.message)}</div>`;
            }
        }

        function render() {
            container.querySelectorAll('.params-tab').forEach(btn => {
                btn.classList.toggle('active', btn.dataset.tab === activeTab);
            });
            const list = activeTab === 'modifiable' ? data.modifiable : data.immutable;
            const kw = keyword.trim().toLowerCase();
            const filtered = kw
                ? list.filter(it =>
                    (it.key || '').toLowerCase().includes(kw)
                    || (it.description || '').toLowerCase().includes(kw)
                    || (it.file || '').toLowerCase().includes(kw))
                : list;

            if (!filtered.length) {
                tableWrap.innerHTML = '<div class="params-empty">暂无匹配配置</div>';
                return;
            }

            const editable = activeTab === 'modifiable';
            let html = '<table class="params-table"><thead><tr>';
            html += '<th style="min-width:220px;">Key</th>';
            html += '<th style="min-width:240px;">值</th>';
            html += '<th style="min-width:220px;">描述</th>';
            html += '<th style="min-width:220px;">涉及文件</th>';
            if (editable) html += '<th style="min-width:90px;">操作</th>';
            html += '</tr></thead><tbody>';

            filtered.forEach(item => {
                html += '<tr data-key="' + escapeHtml(item.key) + '">';
                html += '<td class="params-key">' + escapeHtml(item.key) + renderBadges(item) + '</td>';
                html += '<td>' + renderValueCell(item, editable) + '</td>';
                html += `<td class="params-desc">${escapeHtml(item.description || '')}</td>`;
                html += `<td class="params-file">${escapeHtml(item.file || '')}</td>`;
                if (editable) {
                    html += `<td class="params-actions">
                        <button class="params-save-btn" data-save="${escapeHtml(item.key)}">保存</button>
                    </td>`;
                }
                html += '</tr>';
            });
            html += '</tbody></table>';
            tableWrap.innerHTML = html;

            if (editable) {
                tableWrap.querySelectorAll('[data-save]').forEach(btn => {
                    btn.addEventListener('click', () => saveOne(btn.dataset.save, btn));
                });
            }
        }

        function renderBadges(item) {
            let html = '';
            if (item.sensitive) html += '<span class="params-badge sensitive">敏感</span>';
            if (item.needRestart) html += '<span class="params-badge restart">需重启</span>';
            else html += '<span class="params-badge live">实时生效</span>';
            return html;
        }

        function renderValueCell(item, editable) {
            if (!editable) {
                if (item.sensitive && item.value) {
                    return '<span class="params-value-readonly">******</span>';
                }
                return `<span class="params-value-readonly">${escapeHtml(item.value || '')}</span>`;
            }

            const type = item.type || 'text';
            if (type === 'boolean') {
                const v = String(item.value || '').toLowerCase();
                return `<select class="params-value-select" data-value="${escapeHtml(item.key)}">
                    <option value="true" ${v === 'true' ? 'selected' : ''}>true</option>
                    <option value="false" ${v === 'false' ? 'selected' : ''}>false</option>
                </select>`;
            }
            if (type === 'select' && Array.isArray(item.options) && item.options.length) {
                let opts = '<option value="">-- 请选择 --</option>';
                item.options.forEach(o => {
                    opts += `<option value="${escapeHtml(o)}" ${String(item.value || '') === String(o) ? 'selected' : ''}>${escapeHtml(o)}</option>`;
                });
                return `<select class="params-value-select" data-value="${escapeHtml(item.key)}">${opts}</select>`;
            }
            const placeholder = item.sensitive ? '留空表示不修改' : '';
            const value = item.sensitive ? '' : (item.value || '');
            return `<input class="params-value-input"
                           data-value="${escapeHtml(item.key)}"
                           type="${type === 'number' ? 'number' : 'text'}"
                           value="${escapeHtml(value)}"
                           placeholder="${escapeHtml(placeholder)}">`;
        }

        function getValue(key) {
            const el = tableWrap.querySelector(`[data-value="${cssEscape(key)}"]`);
            return el ? el.value : '';
        }

        async function saveOne(key, btn) {
            const value = getValue(key);
            const item = data.modifiable.find(x => x.key === key);
            if (item && item.sensitive && (!value || value.trim() === '')) {
                toast('敏感参数留空表示不修改', true);
                return;
            }
            if (btn) {
                btn.disabled = true;
                btn.textContent = '保存中...';
            }
            try {
                const resp = await fetch(API + '/update', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                    body: JSON.stringify({ key: key, value: value })
                });
                const json = await resp.json();
                if (!json.success) throw new Error(json.error || '保存失败');
                toast(json.message || '保存成功');
                await load();
            } catch (e) {
                toast('保存失败：' + e.message, true);
            } finally {
                if (btn) {
                    btn.disabled = false;
                    btn.textContent = '保存';
                }
            }
        }

        async function doExport() {
            const format = exportFormatSel.value || 'omni-pack';
            try {
                const url = API + '/export?format=' + encodeURIComponent(format);
                const resp = await fetch(url, { cache: 'no-store' });
                if (!resp.ok) throw new Error('HTTP ' + resp.status);
                const text = await resp.text();

                if (format === 'plain-json') {
                    JSON.parse(text);
                }

                const ext = format === 'plain-json' ? 'json' : 'md';
                const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
                const fileName = 'system-params-' + format + '-' + ts + '.' + ext;

                const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
                const url2 = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url2;
                a.download = fileName;
                a.click();
                setTimeout(() => URL.revokeObjectURL(url2), 1000);

                if (format === 'omni-pack') {
                    toast('✅ 已导出 omni-pack，可粘贴到「代码分析 → 代码导入」还原到 resources/others/system-params.json');
                } else {
                    toast('✅ 已导出 ' + format + ' 格式：' + fileName);
                }
            } catch (e) {
                toast('导出失败：' + e.message, true);
            }
        }

        function openImportModal() {
            importModal.style.display = 'flex';
            switchImportPane('file');
            importFileInput.value = '';
            currentImportFile = null;
            importFileName.textContent = '点击选择文件（支持 .json / .md / .txt / .log）';
            importText.value = '';
            importFormatSel.value = 'auto';
            importPreview.style.display = 'none';
            importDetectedFormat.textContent = '';
            importPreviewBody.innerHTML = '';
            currentParseResult = null;
            importApplyBtn.disabled = true;
        }

        function closeImportModal() {
            importModal.style.display = 'none';
        }

        function switchImportPane(pane) {
            importMode = pane;
            importTabs.forEach(t => t.classList.toggle('active', t.dataset.pane === pane));
            importPaneFile.classList.toggle('active', pane === 'file');
            importPanePaste.classList.toggle('active', pane === 'paste');
        }

        function resetImportPreview() {
            currentParseResult = null;
            importApplyBtn.disabled = true;
            importPreview.style.display = 'none';
            importDetectedFormat.textContent = '';
            importPreviewBody.innerHTML = '';
        }

        async function doParsePreview() {
            let content = '';
            if (importMode === 'file') {
                if (!currentImportFile) {
                    toast('请选择文件', true);
                    return;
                }
                try {
                    content = await currentImportFile.text();
                } catch (e) {
                    toast('读取文件失败：' + e.message, true);
                    return;
                }
            } else {
                content = importText.value;
                if (!content || !content.trim()) {
                    toast('请粘贴内容', true);
                    return;
                }
            }

            const format = importFormatSel.value || 'auto';
            importParseBtn.disabled = true;
            importParseBtn.textContent = '解析中...';
            try {
                const resp = await fetch(API + '/import', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                    body: JSON.stringify({ json: content, format: format })
                });
                const r = await resp.json();
                renderPreview(r);
            } catch (e) {
                importPreview.style.display = 'block';
                importDetectedFormat.textContent = '';
                importPreviewBody.innerHTML = `<div class="preview-error">❌ 解析失败：${escapeHtml(e.message)}</div>`;
                importApplyBtn.disabled = true;
                currentParseResult = null;
            } finally {
                importParseBtn.disabled = false;
                importParseBtn.textContent = '🔍 解析预览';
            }
        }

        function renderPreview(r) {
            importPreview.style.display = 'block';
            importDetectedFormat.textContent = r.detectedFormat ? '格式：' + r.detectedFormat : '';

            if (!r.success) {
                importPreviewBody.innerHTML =
                    `<div class="preview-error">❌ ${escapeHtml(r.error || '解析失败')}</div>`;
                importApplyBtn.disabled = true;
                currentParseResult = null;
                return;
            }

            const applied = r.applied || [];
            const skipped = r.skipped || [];
            let html = '';

            if (applied.length) {
                html += '<div class="params-preview-note">' +
                    '✅ 将应用 ' + applied.length + ' 个参数（已解析到 system-params.json）' +
                    '</div>';
                applied.forEach(k => {
                    html += `<div class="preview-item">
                        <span class="tag tag-apply">应用</span>${escapeHtml(k)}
                    </div>`;
                });
            }

            if (skipped.length) {
                html += '<div class="params-preview-note" style="margin-top:8px;">' +
                    '⚠ 跳过 ' + skipped.length + ' 项（不在可修改列表 / 敏感值留空）' +
                    '</div>';
                skipped.forEach(k => {
                    html += `<div class="preview-item">
                        <span class="tag tag-skip">跳过</span>${escapeHtml(k)}
                    </div>`;
                });
            }

            if (!html) {
                html = '<div class="preview-error">❌ 未识别到任何可应用参数</div>';
                importApplyBtn.disabled = true;
                currentParseResult = null;
            } else {
                importApplyBtn.disabled = false;
                currentParseResult = r;
            }
            importPreviewBody.innerHTML = html;
        }

        async function doApply() {
            if (!currentParseResult) return;
            let content = '';
            if (importMode === 'file' && currentImportFile) {
                content = await currentImportFile.text();
            } else {
                content = importText.value;
            }
            const format = importFormatSel.value || 'auto';

            importApplyBtn.disabled = true;
            importApplyBtn.textContent = '导入中...';
            try {
                const resp = await fetch(API + '/import', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                    body: JSON.stringify({ json: content, format: format })
                });
                const r = await resp.json();
                if (!r.success) {
                    toast('导入失败：' + (r.error || '未知'), true);
                    return;
                }
                const applied = (r.applied || []).length;
                const skipped = (r.skipped || []).length;
                toast('✅ 导入完成：应用 ' + applied + ' 项' + (skipped ? '，跳过 ' + skipped + ' 项' : ''));
                closeImportModal();
                await load();
            } catch (e) {
                toast('导入失败：' + e.message, true);
            } finally {
                importApplyBtn.disabled = false;
                importApplyBtn.textContent = '✅ 确认导入';
            }
        }

        container.querySelectorAll('.params-tab').forEach(btn => {
            btn.addEventListener('click', () => {
                activeTab = btn.dataset.tab;
                render();
            });
        });
        searchInput.addEventListener('input', () => {
            keyword = searchInput.value;
            render();
        });
        refreshBtn.addEventListener('click', load);
        reloadBtn.addEventListener('click', async () => {
            try {
                const r = await (await fetch(API + '/reload', { method: 'POST' })).json();
                toast(r.success ? (r.message || '已重新加载') : (r.error || '重新加载失败'), !r.success);
                if (r.success) await load();
            } catch (e) {
                toast('重新加载失败：' + e.message, true);
            }
        });
        exportBtn.addEventListener('click', doExport);
        importBtn.addEventListener('click', openImportModal);

        importClose.addEventListener('click', closeImportModal);
        importCancelBtn.addEventListener('click', closeImportModal);
        importModal.addEventListener('click', (e) => {
            if (e.target === importModal) closeImportModal();
        });

        importTabs.forEach(t => {
            t.addEventListener('click', () => {
                switchImportPane(t.dataset.pane);
                resetImportPreview();
            });
        });

        importFileDrop.addEventListener('click', () => importFileInput.click());
        importFileInput.addEventListener('change', () => {
            const f = importFileInput.files && importFileInput.files[0];
            if (!f) return;
            currentImportFile = f;
            importFileName.textContent = f.name + ' (' + (f.size / 1024).toFixed(1) + ' KB)';
            resetImportPreview();
        });

        importText.addEventListener('input', resetImportPreview);
        importFormatSel.addEventListener('change', resetImportPreview);
        importParseBtn.addEventListener('click', doParsePreview);
        importApplyBtn.addEventListener('click', doApply);

        load();
    }
})();