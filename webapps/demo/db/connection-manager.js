(function () {
    'use strict';

    const API = window.apiUrl('/connection/api');
    const DRIVER_API = window.apiUrl('/driver/api');
    const FILES_API = window.apiUrl('/files/api');

    const $ = id => document.getElementById(id);

    // ==================== 全局状态 ====================
    let currentTempId = null;
    let currentParsed = null;

    // 连接配置 tab 状态
    let currentVersions = [];
    let currentDriverClasses = [];
    let currentVersionDriverMap = {};

    // 资源关联 tab 状态
    let allDrivers = [];
    let selectedDriverKey = null;
    let driverFilterKeyword = '';

    // ==================== 工具 ====================
    function esc(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"]/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
        }[m]));
    }
    function showMsg(el, msg, type) {
        el.style.display = 'block';
        el.className = 'msg ' + (type || 'ok');
        el.textContent = msg;
    }
    function hideMsg(el) { el.style.display = 'none'; }

    const DB_TYPE_LABELS = {
        mysql: 'MySQL', oracle: 'Oracle', sqlserver: 'SQL Server', postgresql: 'PostgreSQL'
    };
    const DB_TYPE_ICONS = { mysql: '🐬', oracle: '🔴', sqlserver: '🟦', postgresql: '🐘' };
    const DB_TYPE_ORDER = ['mysql', 'oracle', 'sqlserver', 'postgresql'];
    const ALLOWED_DB_TYPES = DB_TYPE_ORDER;

    const VERSION_PATTERN = /(\d+(?:\.\d+){1,3})/;
    function detectDbTypeFromFileName(fileName) {
        if (!fileName) return null;
        const lower = fileName.toLowerCase();
        if (lower.includes('mysql')) return 'mysql';
        if (lower.includes('ojdbc') || lower.includes('oracle')) return 'oracle';
        if (lower.includes('postgresql') || lower.includes('postgres')
            || lower.includes('-pg-') || lower.endsWith('-pg.jar')) return 'postgresql';
        if (lower.includes('mssql') || lower.includes('sqljdbc') || lower.includes('sqlserver')) {
            return 'sqlserver';
        }
        return null;
    }
    function detectVersionFromFileName(fileName) {
        if (!fileName) return null;
        const m = fileName.match(VERSION_PATTERN);
        return m ? m[1] : null;
    }

    // ==================== ★ 左侧 Tab 切换 ====================
    const leftTabsBar = document.querySelector('.left-tabs-bar');
    const leftPanes = {
        resource: $('leftPaneResource'),
        sample: $('leftPaneSample')
    };
    function switchLeftTab(tabId) {
        leftTabsBar.querySelectorAll('.left-tab-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === tabId);
        });
        Object.keys(leftPanes).forEach(k => {
            leftPanes[k].classList.toggle('active', k === tabId);
        });
        if (tabId === 'resource' && allDrivers.length === 0) loadDrivers();
        if (tabId === 'sample' && sampleList.children.length <= 1) loadSamples();
    }
    leftTabsBar.addEventListener('click', (e) => {
        const btn = e.target.closest('.left-tab-btn');
        if (btn) switchLeftTab(btn.dataset.tab);
    });

    // ==================== 引导卡片 ====================
    const guideCard = $('guideCard');
    const guideHeader = $('guideHeader');
    const guideToggle = $('guideToggle');
    guideHeader.addEventListener('click', () => {
        const collapsed = guideCard.classList.toggle('collapsed');
        guideToggle.textContent = collapsed ? '▶' : '▼';
    });
    function highlightStep(step) {
        document.querySelectorAll('.guide-step').forEach(el => {
            const s = parseInt(el.getAttribute('data-step'), 10);
            el.classList.toggle('active', s === step);
        });
    }

    // ==================== 资源关联 Tab：驱动列表 ====================
    async function loadDrivers() {
        const listEl = $('driverList');
        if (!listEl) return;
        listEl.innerHTML = '<div class="loading">加载中...</div>';
        try {
            const resp = await fetch(DRIVER_API + '/entry/list');
            const data = await resp.json();
            if (!data.success) throw new Error(data.error || '加载失败');
            allDrivers = data.items || [];
            renderDriverList();
        } catch (e) {
            listEl.innerHTML = `<div class="loading" style="color:#c62828;">❌ ${esc(e.message)}</div>`;
        }
    }

    function renderDriverList() {
        const listEl = $('driverList');
        if (!listEl) return;

        const kw = (driverFilterKeyword || '').toLowerCase().trim();
        const filtered = allDrivers.filter(d => {
            if (!kw) return true;
            return (d.dbType || '').toLowerCase().includes(kw)
                || (d.version || '').toLowerCase().includes(kw)
                || (d.fileName || '').toLowerCase().includes(kw);
        });

        if (filtered.length === 0) {
            listEl.innerHTML = `<div class="loading">${kw ? '无匹配结果' : '暂无驱动，点击「上传驱动」'}</div>`;
            return;
        }

        const groups = {};
        DB_TYPE_ORDER.forEach(t => groups[t] = []);
        filtered.forEach(d => {
            if (!groups[d.dbType]) groups[d.dbType] = [];
            groups[d.dbType].push(d);
        });

        let html = '';
        DB_TYPE_ORDER.forEach(type => {
            const list = groups[type] || [];
            if (list.length === 0) return;
            list.sort((a, b) => compareVersion(b.version, a.version));
            html += `<div class="driver-group-title">
                ${DB_TYPE_ICONS[type] || '📦'} ${DB_TYPE_LABELS[type] || type} (${list.length})
            </div>`;
            for (const d of list) {
                const active = d.key === selectedDriverKey ? ' active' : '';
                const sourceCls = d.source === 'db' ? 'db' : d.source === 'dir' ? 'dir' : 'both';
                const sourceText = d.source === 'db' ? '数据库' : d.source === 'dir' ? '目录' : '双来源';
                html += `
                    <div class="driver-item${active}" data-key="${esc(d.key)}">
                        <span class="di-icon">${DB_TYPE_ICONS[d.dbType] || '📦'}</span>
                        <div class="di-info">
                            <div class="di-title">${esc(d.version)}</div>
                            <div class="di-meta">${esc(d.fileName)}</div>
                        </div>
                        <span class="di-source ${sourceCls}">${sourceText}</span>
                    </div>`;
            }
        });
        listEl.innerHTML = html;

        listEl.querySelectorAll('.driver-item').forEach(el => {
            el.addEventListener('click', () => selectDriver(el.dataset.key));
        });
    }

    function compareVersion(a, b) {
        const pa = (a || '').split('.'), pb = (b || '').split('.');
        const n = Math.max(pa.length, pb.length);
        for (let i = 0; i < n; i++) {
            const x = i < pa.length ? parseInt(pa[i], 10) || 0 : 0;
            const y = i < pb.length ? parseInt(pb[i], 10) || 0 : 0;
            if (x !== y) return x - y;
        }
        return 0;
    }

    function selectDriver(key) {
        selectedDriverKey = key;
        renderDriverList();
        renderDriverEditor();
    }

    // ==================== 资源关联 Tab：驱动编辑面板 ====================
    function renderDriverEditor() {
        const editorEl = $('driverEditor');
        if (!editorEl) return;
        const d = allDrivers.find(x => x.key === selectedDriverKey);
        if (!d) {
            editorEl.style.display = 'none';
            editorEl.innerHTML = '';
            return;
        }
        editorEl.style.display = 'block';
        editorEl.innerHTML = `
            <div class="editor-header">
                <div>
                    <div class="editor-title">${DB_TYPE_ICONS[d.dbType] || '📦'} ${esc(DB_TYPE_LABELS[d.dbType] || d.dbType)} 驱动</div>
                    <div class="editor-subtitle">📄 ${esc(d.fileName)}</div>
                </div>
                <button id="closeEditorBtn" class="btn-close-editor" title="收起编辑面板">✖</button>
            </div>
            <div id="editorMsg" class="editor-msg" style="display:none;"></div>
            <div class="editor-field">
                <label>驱动版本</label>
                <input type="text" id="editVersion" value="${esc(d.version || '')}" autocomplete="off">
                <div class="hint">修改后同步更新到 driver_jar 表 / 目录文件名</div>
            </div>
            <div class="editor-field">
                <label>驱动类</label>
                <input type="text" id="editDriverClass"
                       value="${esc(d.driverClass || '')}"
                       placeholder="如 com.mysql.cj.jdbc.Driver" autocomplete="off">
                <div class="hint">留空则按文件内容自动推导；填写后覆盖推导结果</div>
            </div>
            <div class="editor-field">
                <label>来源</label>
                <input type="text" value="${d.source === 'db' ? '数据库 driver_jar 表' : d.source === 'dir' ? 'resources/drivers 目录' : '数据库 + 目录'}" readonly style="background:#f8fafc;">
            </div>
            ${d.jarPath ? `
            <div class="editor-field">
                <label>物理路径</label>
                <input type="text" value="${esc(d.jarPath)}" readonly style="background:#f8fafc;font-size:11px;word-break:break-all;">
            </div>` : ''}
            <div class="editor-actions">
                <button id="saveEditBtn" class="btn-primary">💾 保存</button>
                <button id="resetEditBtn" class="btn-secondary">🔄 重置</button>
                <button id="deleteEditBtn" class="btn-secondary" style="color:#dc2626;">🗑️ 删除驱动</button>
            </div>
        `;

        $('closeEditorBtn').addEventListener('click', () => {
            selectedDriverKey = null;
            editorEl.style.display = 'none';
            editorEl.innerHTML = '';
            renderDriverList();
        });
        $('saveEditBtn').addEventListener('click', () => saveDriverEdit(d));
        $('resetEditBtn').addEventListener('click', () => {
            $('editVersion').value = d.version || '';
            $('editDriverClass').value = d.driverClass || '';
            hideEditorMsg();
        });
        $('deleteEditBtn').addEventListener('click', () => deleteDriver(d));
    }

    function showEditorMsg(msg, type) {
        const el = $('editorMsg');
        if (!el) return;
        el.style.display = 'block';
        el.className = 'editor-msg ' + (type || 'ok');
        el.textContent = msg;
    }
    function hideEditorMsg() {
        const el = $('editorMsg');
        if (el) el.style.display = 'none';
    }

    async function saveDriverEdit(d) {
        const newVersion = $('editVersion').value.trim();
        const newDriverClass = $('editDriverClass').value.trim();
        if (!newVersion) { showEditorMsg('版本号不能为空', 'bad'); return; }
        if (newVersion.indexOf('_') >= 0) { showEditorMsg('版本号不能包含下划线 _', 'bad'); return; }
        try {
            const resp = await fetch(DRIVER_API + '/entry/update', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    key: d.key,
                    dbType: d.dbType,
                    oldVersion: d.version,
                    newVersion: newVersion,
                    driverClass: newDriverClass
                })
            });
            const data = await resp.json();
            if (data.success) {
                showEditorMsg('✅ 已保存', 'ok');
                await loadDrivers();
                selectedDriverKey = data.key || d.key;
                renderDriverList();
                renderDriverEditor();
            } else {
                showEditorMsg('❌ ' + (data.error || '保存失败'), 'bad');
            }
        } catch (e) {
            showEditorMsg('❌ 网络错误: ' + e.message, 'bad');
        }
    }

    async function deleteDriver(d) {
        if (!confirm(`确定删除驱动 ${DB_TYPE_LABELS[d.dbType] || d.dbType} ${d.version} 吗？`)) return;
        try {
            const resp = await fetch(DRIVER_API + '/entry/delete', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    key: d.key,
                    dbType: d.dbType,
                    version: d.version
                })
            });
            const data = await resp.json();
            if (data.success) {
                selectedDriverKey = null;
                await loadDrivers();
                renderDriverEditor();
            } else {
                alert('删除失败：' + (data.error || '未知错误'));
            }
        } catch (e) {
            alert('删除失败：' + e.message);
        }
    }

    const driverFilter = $('driverFilter');
    if (driverFilter) {
        driverFilter.addEventListener('input', () => {
            driverFilterKeyword = driverFilter.value;
            renderDriverList();
        });
    }
    const refreshDriversBtn = $('refreshDriversBtn');
    if (refreshDriversBtn) {
        refreshDriversBtn.addEventListener('click', loadDrivers);
    }

    // ==================== 资源关联 Tab：上传驱动 ====================
    const uploadModal      = $('driverUploadModal');
    const closeUploadBtn   = $('closeDriverUploadModal');
    const jarFileInput     = $('driverJarFile');
    const filePicker       = $('driverFilePicker');
    const filePickerName   = $('driverFilePickerName');
    const detectBanner     = $('driverDetectBanner');
    const uploadDbType     = $('driverUploadDbType');
    const uploadVersion    = $('driverUploadVersion');
    const uploadError      = $('driverUploadError');
    const uploadConfirmBtn = $('driverUploadConfirmBtn');
    const uploadCancelBtn  = $('driverUploadCancelBtn');

    function setUploadBanner(kind, html) {
        if (!detectBanner) return;
        detectBanner.className = 'detect-banner ' + kind;
        detectBanner.innerHTML = html;
    }
    function setUploadError(msg) {
        if (uploadError) uploadError.textContent = msg || '';
    }

    function openUploadModal() {
        jarFileInput.value = '';
        filePicker.classList.remove('has-file');
        filePickerName.textContent = '点击选择 .jar 文件';
        uploadDbType.value = 'mysql';
        uploadVersion.value = '';
        setUploadBanner('info', '选择文件后自动识别数据库类型与版本');
        setUploadError('');
        uploadModal.style.display = 'flex';
    }
    function closeUploadModal() { uploadModal.style.display = 'none'; }

    filePicker.addEventListener('click', () => jarFileInput.click());
    jarFileInput.addEventListener('change', () => {
        const file = jarFileInput.files[0];
        if (!file) return;
        if (!file.name.toLowerCase().endsWith('.jar')) {
            setUploadError('只允许上传 .jar 文件');
            jarFileInput.value = '';
            filePicker.classList.remove('has-file');
            filePickerName.textContent = '点击选择 .jar 文件';
            return;
        }
        setUploadError('');
        filePicker.classList.add('has-file');
        filePickerName.textContent = file.name + ' (' + (file.size / 1024).toFixed(1) + ' KB)';

        const detectedType = detectDbTypeFromFileName(file.name);
        const detectedVersion = detectVersionFromFileName(file.name);
        if (detectedType) uploadDbType.value = detectedType;
        if (detectedVersion) uploadVersion.value = detectedVersion;

        const parts = [];
        if (detectedType) parts.push('<b>类型</b>：' + (DB_TYPE_LABELS[detectedType] || detectedType));
        if (detectedVersion) parts.push('<b>版本</b>：' + esc(detectedVersion));

        if (detectedType && detectedVersion) {
            setUploadBanner('success', '✅ 从文件名自动识别：' + parts.join(' · ') + '<br>请确认后提交');
        } else if (detectedType || detectedVersion) {
            setUploadBanner('warn', '⚠️ 部分识别：' + parts.join(' · ') + '<br>未识别的字段请手动填写');
        } else {
            setUploadBanner('warn', '⚠️ 未能识别类型与版本，请手动填写');
        }
    });

    async function doUpload() {
        setUploadError('');
        const file = jarFileInput.files[0];
        const dbType = uploadDbType.value;
        const version = uploadVersion.value.trim();

        if (!file) { setUploadError('请选择 Jar 文件'); return; }
        if (!file.name.toLowerCase().endsWith('.jar')) { setUploadError('只允许上传 .jar 文件'); return; }
        if (!ALLOWED_DB_TYPES.includes(dbType)) { setUploadError('不支持的数据库类型：' + dbType); return; }
        if (!version) { setUploadError('版本不能为空'); uploadVersion.focus(); return; }
        if (version.indexOf('_') >= 0) { setUploadError('版本不能包含下划线 _'); return; }

        uploadConfirmBtn.disabled = true;
        uploadConfirmBtn.textContent = '⏳ 上传中...';
        try {
            const fd = new FormData();
            fd.append('file', file);
            fd.append('type', 'drivers');
            fd.append('path', '');
            fd.append('dbType', dbType);
            fd.append('version', version);

            const resp = await fetch(FILES_API + '/resources/upload', { method: 'POST', body: fd });
            const data = await resp.json();
            if (data.success) {
                closeUploadModal();
                await loadDrivers();
            } else {
                setUploadError(data.error || '上传失败');
            }
        } catch (e) {
            setUploadError('网络错误：' + e.message);
        } finally {
            uploadConfirmBtn.disabled = false;
            uploadConfirmBtn.textContent = '保存';
        }
    }

    $('uploadDriverBtn').addEventListener('click', openUploadModal);
    closeUploadBtn.addEventListener('click', closeUploadModal);
    uploadCancelBtn.addEventListener('click', closeUploadModal);
    uploadConfirmBtn.addEventListener('click', doUpload);
    uploadModal.addEventListener('click', (e) => {
        if (e.target === uploadModal) closeUploadModal();
    });

    // ==================== 示例 Tab：加载示例连接串 ====================
    const sampleList = $('sampleList');

    async function loadSamples() {
        if (!sampleList) return;
        sampleList.innerHTML = '<div class="loading">加载中...</div>';
        try {
            const resp = await fetch(API + '/samples');
            const data = await resp.json();
            if (!data.success) throw new Error(data.error || '加载失败');
            const list = data.samples || [];
            if (!list.length) {
                sampleList.innerHTML = '<div class="loading">暂无示例</div>';
                return;
            }
            sampleList.innerHTML = list.map(s => `
                <div class="sample-item" data-idx="${esc(s.dbType)}">
                    <div class="sample-title">${esc(s.label || s.dbType)}</div>
                    <div class="sample-url">${esc(s.url)}</div>
                </div>
            `).join('');
            sampleList.querySelectorAll('.sample-item').forEach(el => {
                el.addEventListener('click', () => {
                    const s = list.find(x => x.dbType === el.dataset.idx);
                    if (!s) return;
                    connUrl.value = s.url || '';
                    connUsername.value = s.username || '';
                    connPassword.value = '';
                    connDbType.value = s.dbType || '';
                    loadComboboxOptions(connDbType.value, true);
                    doParse();
                    highlightStep(4);
                    // ★ 提示用户已填充（可选）
                    showMsg(statusMsg, '✅ 示例已填充到右侧，可直接测试连接', 'ok');
                    setTimeout(() => hideMsg(statusMsg), 2500);
                });
            });
        } catch (e) {
            sampleList.innerHTML = `<div class="loading" style="color:#c62828;">加载失败：${esc(e.message)}</div>`;
        }
    }

    // ==================== 连接配置表单（原逻辑保持不变） ====================
    const connUrl            = $('connUrl');
    const connUsername       = $('connUsername');
    const connPassword       = $('connPassword');
    const connDbType         = $('connDbType');
    const connDriverClass    = $('connDriverClass');
    const connVersion        = $('connVersion');
    const connVersionBtn     = $('connVersionBtn');
    const connVersionPanel   = $('connVersionPanel');
    const connDriverClassBtn = $('connDriverClassBtn');
    const connDriverClassPanel = $('connDriverClassPanel');
    const connStatus         = $('connStatus');
    const parseBtn           = $('parseBtn');
    const testBtn            = $('testBtn');
    const closeBtn           = $('closeBtn');
    const parseResult        = $('parseResult');
    const statusMsg          = $('statusMsg');
    const sqlSection         = $('sqlSection');
    const sqlInput           = $('sqlInput');
    const execSqlBtn         = $('execSqlBtn');
    const clearSqlBtn        = $('clearSqlBtn');
    const sqlResult          = $('sqlResult');
    const saveSection        = $('saveSection');
    const dsName             = $('dsName');
    const saveDsBtn          = $('saveDsBtn');

    function setConnected(connected) {
        if (connected) {
            connStatus.textContent = '✅ 已连接';
            connStatus.className = 'status-badge connected';
            closeBtn.disabled = false;
            highlightStep(5);
        } else {
            connStatus.textContent = '未连接';
            connStatus.className = 'status-badge disconnected';
            closeBtn.disabled = true;
            sqlSection.style.display = 'none';
            saveSection.style.display = 'none';
            sqlResult.innerHTML = '';
            currentTempId = null;
        }
    }

    // ---- 自定义下拉 ----
    function openComboPanel(inputEl, panelEl, options, showSubHint) {
        closeAllComboPanels(panelEl);
        if (!options || options.length === 0) {
            panelEl.innerHTML = '<div class="combo-option hint">（无可用选项，请手动输入）</div>';
            panelEl.classList.add('show');
            return;
        }
        const cur = (inputEl.value || '').trim();
        let html = '';
        for (const opt of options) {
            const selected = (opt === cur) ? ' selected' : '';
            let subHint = '';
            if (showSubHint) {
                const cls = currentVersionDriverMap[opt];
                if (cls) subHint = `<span class="sub-hint">→ ${esc(cls)}</span>`;
            }
            html += `<div class="combo-option${selected}" data-value="${esc(opt)}">${esc(opt)}${subHint}</div>`;
        }
        panelEl.innerHTML = html;
        panelEl.querySelectorAll('.combo-option').forEach(el => {
            el.addEventListener('mousedown', (e) => {
                e.preventDefault(); e.stopPropagation();
                const val = el.getAttribute('data-value');
                if (val != null) {
                    inputEl.value = val;
                    inputEl.dispatchEvent(new Event('input', { bubbles: true }));
                }
                panelEl.classList.remove('show');
            });
        });
        panelEl.classList.add('show');
    }
    function closeAllComboPanels(except) {
        [connVersionPanel, connDriverClassPanel].forEach(p => {
            if (p && p !== except) p.classList.remove('show');
        });
    }
    document.addEventListener('click', (e) => {
        if (e.target.closest('.combo-wrap')) return;
        closeAllComboPanels();
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeAllComboPanels();
    });

    connVersion.addEventListener('click', () => openComboPanel(connVersion, connVersionPanel, currentVersions, true));
    connVersionBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        openComboPanel(connVersion, connVersionPanel, currentVersions, true);
    });
    connDriverClass.addEventListener('click', () => openComboPanel(connDriverClass, connDriverClassPanel, currentDriverClasses, false));
    connDriverClassBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        openComboPanel(connDriverClass, connDriverClassPanel, currentDriverClasses, false);
    });

    function filterPanel(inputEl, panelEl, options) {
        if (!panelEl.classList.contains('show')) return;
        const kw = (inputEl.value || '').trim().toLowerCase();
        const filtered = kw ? options.filter(o => o.toLowerCase().includes(kw)) : options;
        if (filtered.length === 0) {
            panelEl.innerHTML = '<div class="combo-option hint">（无匹配项）</div>';
            return;
        }
        let html = '';
        for (const opt of filtered) {
            const cls = currentVersionDriverMap[opt];
            const subHint = (cls && panelEl === connVersionPanel)
                ? `<span class="sub-hint">→ ${esc(cls)}</span>` : '';
            html += `<div class="combo-option" data-value="${esc(opt)}">${esc(opt)}${subHint}</div>`;
        }
        panelEl.innerHTML = html;
        panelEl.querySelectorAll('.combo-option').forEach(el => {
            el.addEventListener('mousedown', (e) => {
                e.preventDefault(); e.stopPropagation();
                const val = el.getAttribute('data-value');
                if (val != null) {
                    inputEl.value = val;
                    inputEl.dispatchEvent(new Event('input', { bubbles: true }));
                }
                panelEl.classList.remove('show');
            });
        });
    }
    connVersion.addEventListener('input', () => {
        filterPanel(connVersion, connVersionPanel, currentVersions);
        syncDriverClassFromVersion();
    });
    connDriverClass.addEventListener('input', () => {
        filterPanel(connDriverClass, connDriverClassPanel, currentDriverClasses);
    });

    function syncDriverClassFromVersion() {
        const v = connVersion.value.trim();
        if (!v) return;
        if (currentVersionDriverMap[v]) {
            connDriverClass.value = currentVersionDriverMap[v];
            return;
        }
        const major = parseInt(v, 10);
        if (!isNaN(major)) {
            for (const ver of Object.keys(currentVersionDriverMap)) {
                if (parseInt(ver, 10) === major) {
                    connDriverClass.value = currentVersionDriverMap[ver];
                    return;
                }
            }
        }
        connDriverClass.value = inferDriverClassFallback(connDbType.value, v);
    }

    function inferDriverClassFallback(dbType, version) {
        if (!dbType) return connDriverClass.value || '';
        const t = dbType.toLowerCase();
        if (t === 'mysql') {
            const major = parseInt(version, 10);
            return (!isNaN(major) && major >= 8)
                ? 'com.mysql.cj.jdbc.Driver' : 'com.mysql.jdbc.Driver';
        }
        if (t === 'oracle') return 'oracle.jdbc.OracleDriver';
        if (t === 'postgresql' || t === 'pg') return 'org.postgresql.Driver';
        if (t === 'sqlserver') return 'com.microsoft.sqlserver.jdbc.SQLServerDriver';
        return connDriverClass.value || '';
    }

    async function loadComboboxOptions(dbType, forceReset) {
        currentVersions = [];
        currentDriverClasses = [];
        currentVersionDriverMap = {};
        if (!dbType) {
            if (forceReset) {
                connVersion.value = '';
                connDriverClass.value = '';
            }
            return;
        }
        try {
            const resp = await fetch(DRIVER_API + '/combobox?dbType=' + encodeURIComponent(dbType));
            const data = await resp.json();
            if (!data.success) return;
            currentVersions = data.versions || [];
            currentDriverClasses = data.driverClasses || [];
            currentVersionDriverMap = data.versionDriverMap || {};
            if (currentVersions.length > 0 && (forceReset || !connVersion.value)) {
                connVersion.value = currentVersions[0];
                const cls = currentVersionDriverMap[currentVersions[0]];
                if (cls) connDriverClass.value = cls;
            } else if (connVersion.value) {
                syncDriverClassFromVersion();
            }
        } catch (e) {
            console.warn('加载 combobox 失败:', e);
        }
    }

    connDbType.addEventListener('change', () => {
        loadComboboxOptions(connDbType.value, true);
        highlightStep(2);
    });
    loadComboboxOptions(connDbType.value, false);

    // ---- 解析 / 测试 / 执行 / 关闭 / 保存 ----
    async function doParse() {
        const url = connUrl.value.trim();
        if (!url) { showMsg(statusMsg, '请先输入连接串', 'bad'); return; }
        hideMsg(statusMsg);
        try {
            const resp = await fetch(API + '/parse', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url })
            });
            const data = await resp.json();
            const parsed = data.data || {};
            currentParsed = parsed;
            parseResult.style.display = 'block';
            if (!data.success) {
                parseResult.innerHTML = `<div class="bad">❌ 解析失败：${esc(data.error || parsed.message || '')}</div>`;
                return;
            }
            parseResult.innerHTML = `
                <div class="ok">✅ 解析成功</div>
                <div class="kv"><b>数据库类型</b><span>${esc(parsed.dbType || '-')}</span></div>
                <div class="kv"><b>主机</b><span>${esc(parsed.host || '-')}</span></div>
                <div class="kv"><b>端口</b><span>${esc(parsed.port || '-')}</span></div>
                <div class="kv"><b>数据库名</b><span>${esc(parsed.databaseName || '-')}</span></div>
            `;
            if (parsed.dbType && !connDbType.value) {
                connDbType.value = parsed.dbType;
                loadComboboxOptions(connDbType.value, true);
            }
        } catch (e) {
            showMsg(statusMsg, '解析异常：' + e.message, 'bad');
        }
    }

    async function doTest() {
        const url = connUrl.value.trim();
        if (!url) { showMsg(statusMsg, '请先输入连接串', 'bad'); return; }
        testBtn.disabled = true;
        testBtn.textContent = '⏳ 连接中...';
        hideMsg(statusMsg);
        try {
            const payload = {
                url,
                username: connUsername.value.trim(),
                password: connPassword.value,
                dbType: connDbType.value || null,
                driverClass: connDriverClass.value.trim() || null,
                version: connVersion.value.trim() || null
            };
            const resp = await fetch(API + '/test', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await resp.json();
            if (!data.success) {
                showMsg(statusMsg, '❌ ' + (data.error || '连接失败'), 'bad');
                setConnected(false);
                return;
            }
            currentTempId = data.tempId;
            setConnected(true);
            sqlSection.style.display = 'block';
            saveSection.style.display = 'block';
            showMsg(statusMsg, `✅ 连接成功！类型：${data.dbType}，临时 ID：${data.tempId}`, 'ok');
        } catch (e) {
            showMsg(statusMsg, '连接异常：' + e.message, 'bad');
            setConnected(false);
        } finally {
            testBtn.disabled = false;
            testBtn.textContent = '🔌 测试连接';
        }
    }

    async function doExecSql() {
        if (!currentTempId) { showMsg(statusMsg, '请先建立连接', 'bad'); return; }
        const sql = sqlInput.value.trim();
        if (!sql) { showMsg(statusMsg, '请输入 SQL 语句', 'bad'); return; }
        execSqlBtn.disabled = true;
        execSqlBtn.textContent = '⏳ 执行中...';
        sqlResult.innerHTML = '<div class="loading">执行中...</div>';
        try {
            const resp = await fetch(API + '/execute', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ tempId: currentTempId, sql })
            });
            const data = await resp.json();
            if (!data.success) {
                sqlResult.innerHTML = `<div class="msg bad">❌ ${esc(data.error || '执行失败')}</div>`;
                return;
            }
            const cols = data.columns || [];
            const rows = data.rows || [];
            if (!cols.length) {
                sqlResult.innerHTML = `<div class="msg ok">✅ 执行成功，返回 ${rows.length} 行</div>`;
                return;
            }
            let html = `<div class="sql-meta">共 ${data.total || rows.length} 行 · 耗时 ${data.durationMs || 0} ms</div>`;
            html += '<div style="max-height:420px;overflow:auto;border-radius:12px;">';
            html += '<table class="sql-result-table"><thead><tr>';
            html += cols.map(c => `<th>${esc(c)}</th>`).join('');
            html += '</tr></thead><tbody>';
            for (const row of rows) {
                html += '<tr>';
                for (const c of cols) {
                    const v = row[c];
                    const display = v == null ? '' : String(v);
                    html += `<td title="${esc(display)}">${esc(display)}</td>`;
                }
                html += '</tr>';
            }
            html += '</tbody></table></div>';
            sqlResult.innerHTML = html;
        } catch (e) {
            sqlResult.innerHTML = `<div class="msg bad">❌ 请求异常：${esc(e.message)}</div>`;
        } finally {
            execSqlBtn.disabled = false;
            execSqlBtn.textContent = '▶️ 执行 SQL';
        }
    }

    async function doClose() {
        if (!currentTempId) return;
        if (!confirm('确定关闭当前临时连接吗？')) return;
        try {
            await fetch(API + '/close', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ tempId: currentTempId })
            });
        } catch (e) { /* ignore */ }
        setConnected(false);
        showMsg(statusMsg, '已关闭连接', 'ok');
    }

    async function doSaveDs() {
        if (!currentTempId) { showMsg(statusMsg, '请先建立连接', 'bad'); return; }
        saveDsBtn.disabled = true;
        saveDsBtn.textContent = '⏳ 保存中...';
        try {
            const resp = await fetch(API + '/save', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ tempId: currentTempId, name: dsName.value.trim() || null })
            });
            const data = await resp.json();
            if (!data.success) {
                showMsg(statusMsg, '❌ 保存失败：' + (data.error || '未知错误'), 'bad');
                return;
            }
            showMsg(statusMsg, `✅ 已保存为正式数据源（ID: ${data.id}）`, 'ok');
            highlightStep(6);
        } catch (e) {
            showMsg(statusMsg, '保存异常：' + e.message, 'bad');
        } finally {
            saveDsBtn.disabled = false;
            saveDsBtn.textContent = '💾 保存';
        }
    }

    parseBtn.addEventListener('click', doParse);
    testBtn.addEventListener('click', doTest);
    closeBtn.addEventListener('click', doClose);
    execSqlBtn.addEventListener('click', doExecSql);
    saveDsBtn.addEventListener('click', doSaveDs);
    clearSqlBtn.addEventListener('click', () => {
        sqlInput.value = '';
        sqlResult.innerHTML = '';
    });

    let parseTimer = null;
    connUrl.addEventListener('input', () => {
        if (parseTimer) clearTimeout(parseTimer);
        parseTimer = setTimeout(() => {
            if (connUrl.value.trim()) doParse();
        }, 400);
    });

    window.addEventListener('beforeunload', () => {
        if (currentTempId) {
            try {
                navigator.sendBeacon(
                    API + '/close',
                    JSON.stringify({ tempId: currentTempId })
                );
            } catch (e) {}
        }
    });

    // ==================== 启动 ====================
    highlightStep(1);
    loadDrivers();      // 左侧默认 tab 是「资源关联」，加载驱动列表
    loadSamples();      // 预加载示例（切到示例 tab 时立即显示）

    // URL 参数：?url=xxx&username=yyy 自动填充
    const urlParams = new URLSearchParams(window.location.search);
    const presetUrl = urlParams.get('url');
    if (presetUrl) {
        connUrl.value = decodeURIComponent(presetUrl);
        const presetUser = urlParams.get('username');
        if (presetUser) connUsername.value = decodeURIComponent(presetUser);
        setTimeout(() => doParse(), 200);
    }
})();