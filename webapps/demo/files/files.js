(function () {
    'use strict';

    const API_BASE = window.apiUrl ? window.apiUrl('/files/api') : '/files/api';

    // ==================== 常量 ====================
    const ALLOWED_DB_TYPES = ['mysql', 'oracle', 'sqlserver', 'postgresql'];
    const DB_TYPE_LABELS = {
        mysql: 'MySQL',
        oracle: 'Oracle',
        sqlserver: 'SQL Server',
        postgresql: 'PostgreSQL'
    };
    // 与后端 DriverFileNameParser.VERSION_PATTERN 完全一致
    const VERSION_PATTERN = /(\d+(?:\.\d+){1,3})/;

    const TYPE_ICONS = {
        images: '🖼️',
        pages: '📄',
        sql: '🗃️',
        attachments: '📎',
        drivers: '🔌',
        others: '📦'
    };
    function getTypeIcon(type) { return TYPE_ICONS[type] || '📦'; }

    // ==================== 状态 ====================
    let currentType = 'images';
    let currentSubPath = '';
    let resourceTypes = {};

    // ==================== DOM 引用（全部可能为 null，逐一 null 检查） ====================
    const resourceTypeList    = document.getElementById('resourceTypeList');
    const resourceTitle       = document.getElementById('resourceTitle');
    const breadcrumb          = document.getElementById('breadcrumb');
    const resourceListPanel   = document.getElementById('resourceListPanel');
    const uploadResourceBtn   = document.getElementById('uploadResourceBtn');
    const refreshResourcesBtn = document.getElementById('refreshResourcesBtn');
    const newSubDirBtn        = document.getElementById('newSubDirBtn');
    const resourceFileInput   = document.getElementById('resourceFileInput');

    // 驱动弹窗
    const driverUploadModal       = document.getElementById('driverUploadModal');
    const closeDriverUploadModal  = document.getElementById('closeDriverUploadModal');
    const driverJarFile           = document.getElementById('driverJarFile');
    const driverFilePicker        = document.getElementById('driverFilePicker');
    const driverFilePickerName    = document.getElementById('driverFilePickerName');
    const driverDetectBanner      = document.getElementById('driverDetectBanner');
    const driverUploadDbType      = document.getElementById('driverUploadDbType');
    const driverUploadVersion     = document.getElementById('driverUploadVersion');
    const driverUploadError       = document.getElementById('driverUploadError');
    const driverUploadConfirmBtn  = document.getElementById('driverUploadConfirmBtn');
    const driverUploadCancelBtn   = document.getElementById('driverUploadCancelBtn');

    const driverModalReady = !!(
        driverUploadModal && driverJarFile && driverFilePicker &&
        driverUploadDbType && driverUploadVersion && driverUploadConfirmBtn
    );

    // ==================== 工具 ====================
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str).replace(/[&<>"]/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
        }[m]));
    }
    function formatSize(bytes) {
        if (bytes == null || bytes < 0) return '0 B';
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }
    function showMsg(msg) { alert(msg); }

    // 文件名识别
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

    // ==================== 加载类型列表 ====================
    async function loadResourceTypes() {
        if (!resourceTypeList) {
            console.warn('[files.js] #resourceTypeList 不存在');
            return;
        }
        try {
            const [typesResp, countsResp] = await Promise.all([
                fetch(`${API_BASE}/resources/types`),
                fetch(`${API_BASE}/resources/count`)
            ]);
            resourceTypes = await typesResp.json();
            const counts = await countsResp.json();

            let html = '';
            for (const [key, label] of Object.entries(resourceTypes)) {
                const count = counts[key] || 0;
                html += `
                    <div class="resource-type-item" data-type="${key}">
                        <span class="icon">${getTypeIcon(key)}</span>
                        <span class="name">${escapeHtml(label)}</span>
                        <span class="count">${count}</span>
                    </div>
                `;
            }
            resourceTypeList.innerHTML = html;

            resourceTypeList.querySelectorAll('.resource-type-item').forEach(el => {
                el.addEventListener('click', () => switchType(el.dataset.type));
            });

            if (currentType && resourceTypes[currentType]) {
                highlightType(currentType);
            } else {
                const firstType = Object.keys(resourceTypes)[0] || 'images';
                switchType(firstType);
            }
        } catch (err) {
            console.error('[files.js] 加载资源类型失败', err);
            resourceTypeList.innerHTML = `<div class="error-tip">❌ 加载失败: ${escapeHtml(err.message)}</div>`;
        }
    }

    function highlightType(type) {
        currentType = type;
        if (resourceTypeList) {
            resourceTypeList.querySelectorAll('.resource-type-item').forEach(el => {
                el.classList.toggle('active', el.dataset.type === type);
            });
        }
        if (resourceTitle) {
            const label = resourceTypes[type] || type;
            resourceTitle.textContent = `${getTypeIcon(type)} ${label} (资源管理)`;
        }
    }

    function switchType(type) {
        highlightType(type);
        currentSubPath = '';
        refreshList();
    }

    // ==================== 面包屑 ====================
    function renderBreadcrumb() {
        if (!breadcrumb) return;
        const typeLabel = resourceTypes[currentType] || currentType;
        const parts = currentSubPath ? currentSubPath.split('/') : [];
        let html = `<span class="bc-item bc-type" data-nav="">${getTypeIcon(currentType)} ${escapeHtml(typeLabel)}</span>`;
        let accum = '';
        for (let i = 0; i < parts.length; i++) {
            accum = accum ? accum + '/' + parts[i] : parts[i];
            html += `<span class="bc-sep">/</span><span class="bc-item" data-nav="${escapeHtml(accum)}">${escapeHtml(parts[i])}</span>`;
        }
        breadcrumb.innerHTML = html;
        breadcrumb.querySelectorAll('.bc-item').forEach(el => {
            el.addEventListener('click', () => {
                currentSubPath = el.dataset.nav || '';
                refreshList();
            });
        });
    }

    // ==================== 列表 ====================
    async function refreshList() {
        if (!resourceListPanel) return;
        renderBreadcrumb();
        resourceListPanel.innerHTML = '<div class="loading">加载中...</div>';
        try {
            const url = `${API_BASE}/resources/list?type=${encodeURIComponent(currentType)}&path=${encodeURIComponent(currentSubPath)}`;
            const resp = await fetch(url);
            const list = await resp.json();
            if (!Array.isArray(list)) {
                resourceListPanel.innerHTML = `<div class="error-tip">❌ ${escapeHtml(list.error || '加载失败')}</div>`;
                return;
            }
            if (list.length === 0) {
                resourceListPanel.innerHTML = `<div class="empty-tip">📭 此目录为空，可「上传资源」或「新建子目录」</div>`;
                return;
            }
            renderResourceList(list);
        } catch (err) {
            resourceListPanel.innerHTML = `<div class="error-tip">❌ ${escapeHtml(err.message)}</div>`;
        }
    }

    function renderResourceList(list) {
        let html = '<div class="resource-grid">';
        for (const item of list) {
            const displayName = item.displayName || item.name;
            const isDir = item.isDirectory;
            const fullPath = item.fullRelPath || item.name;

            let thumbHtml;
            if (isDir) {
                thumbHtml = `<div class="res-thumb-fallback folder-icon">📁</div>`;
            } else if (currentType === 'images') {
                const previewUrl = `${API_BASE}/resources/preview?type=${encodeURIComponent(currentType)}&path=${encodeURIComponent(fullPath)}`;
                thumbHtml = `<img class="res-thumb" src="${previewUrl}&t=${Date.now()}"
                                  alt="${escapeHtml(displayName)}"
                                  onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                             <div class="res-thumb-fallback" style="display:none;">🖼️</div>`;
            } else if (currentType === 'sql') {
                thumbHtml = `<div class="res-thumb-fallback sql-icon">🗃️</div>`;
            } else if (currentType === 'pages') {
                thumbHtml = `<div class="res-thumb-fallback page-icon">📄</div>`;
            } else if (currentType === 'drivers') {
                thumbHtml = `<div class="res-thumb-fallback" style="color:#7c3aed;">🔌</div>`;
            } else {
                thumbHtml = `<div class="res-thumb-fallback">${getTypeIcon(currentType)}</div>`;
            }

            let metaLine;
            if (currentType === 'drivers' && !isDir && item.dbType) {
                const typeLabel = DB_TYPE_LABELS[item.dbType] || item.dbType;
                metaLine = `<span style="color:#0066ff;font-weight:600;">${escapeHtml(typeLabel)}</span> · `
                    + `<span style="font-family:monospace;">${escapeHtml(item.version || '')}</span> · `
                    + `${formatSize(item.size)}`;
            } else {
                metaLine = isDir ? '目录' : formatSize(item.size) + ' · ' + (item.modified || '');
            }

            html += `
                <div class="resource-card ${isDir ? 'is-dir' : 'is-file'}"
                     data-name="${escapeHtml(displayName)}"
                     data-full-path="${escapeHtml(fullPath)}"
                     data-is-dir="${isDir}"
                     title="${isDir ? '双击进入目录' : '双击打开 / 下载'}">
                    <div class="res-thumb-wrap">${thumbHtml}</div>
                    <div class="res-info">
                        <div class="res-name">${escapeHtml(displayName)}</div>
                        <div class="res-meta">${metaLine}</div>
                    </div>
                    <div class="res-actions">
                        ${isDir ? '' : `<button class="res-action-btn" data-action="download" title="下载">⬇️</button>`}
                        <button class="res-action-btn res-delete" data-action="delete" title="删除">🗑️</button>
                    </div>
                </div>
            `;
        }
        html += '</div>';
        resourceListPanel.innerHTML = html;

        // 双击
        resourceListPanel.querySelectorAll('.resource-card').forEach(card => {
            card.addEventListener('dblclick', (e) => {
                if (e.target.closest('.res-action-btn')) return;
                const isDir = card.dataset.isDir === 'true';
                const fullPath = card.dataset.fullPath;
                if (isDir) {
                    currentSubPath = fullPath;
                    refreshList();
                } else {
                    handleFileDblClick(fullPath);
                }
            });
        });

        // 操作按钮
        resourceListPanel.querySelectorAll('.res-action-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const card = btn.closest('.resource-card');
                const fullPath = card.dataset.fullPath;
                const action = btn.dataset.action;
                if (action === 'download') downloadResource(fullPath);
                else if (action === 'delete') deleteResource(fullPath);
            });
        });
    }

    function getFileExt(name) {
        const i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase() : '';
    }

    function handleFileDblClick(fullPath) {
        const name = fullPath.split('/').pop();
        const ext = getFileExt(name);

        if (ext === 'sql') {
            const url = window.apiUrl
                ? window.apiUrl('/demo/db/dbclient.html')
                : '/demo/db/dbclient.html';
            window.open(
                url + '?loadSql=' + encodeURIComponent(fullPath)
                + '&loadSqlType=' + encodeURIComponent(currentType),
                '_blank'
            );
            return;
        }
        if (['html', 'htm', 'png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'bmp', 'ico', 'pdf'].includes(ext)) {
            window.open(
                `${API_BASE}/resources/preview?type=${encodeURIComponent(currentType)}&path=${encodeURIComponent(fullPath)}`,
                '_blank'
            );
            return;
        }
        downloadResource(fullPath);
    }

    function downloadResource(fullPath) {
        window.open(
            `${API_BASE}/resources/download?type=${encodeURIComponent(currentType)}&path=${encodeURIComponent(fullPath)}`,
            '_blank'
        );
    }

    async function deleteResource(fullPath) {
        const name = fullPath.split('/').pop();
        if (!confirm(`确定删除「${name}」吗？如果是目录会递归删除其内所有文件。`)) return;
        try {
            const resp = await fetch(`${API_BASE}/resources/delete`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ type: currentType, path: fullPath })
            });
            const data = await resp.json();
            if (data.success) {
                showMsg('删除成功');
                await refreshList();
                await loadResourceTypes();
                highlightType(currentType);
            } else {
                showMsg(data.error || '删除失败');
            }
        } catch (err) { showMsg(err.message); }
    }

    async function createSubDirectory() {
        const name = prompt('请输入子目录名称：');
        if (!name || !name.trim()) return;
        try {
            const resp = await fetch(`${API_BASE}/resources/mkdir`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ type: currentType, path: currentSubPath, name: name.trim() })
            });
            const data = await resp.json();
            if (data.success) {
                showMsg('子目录创建成功');
                await refreshList();
            } else {
                showMsg(data.error || '创建失败');
            }
        } catch (err) { showMsg(err.message); }
    }

    // ==================== 非驱动类型上传（原逻辑） ====================
    async function uploadGenericResources(files) {
        if (!files || files.length === 0) return;
        let ok = 0, fail = 0;
        for (const file of files) {
            try {
                const fd = new FormData();
                fd.append('file', file);
                fd.append('type', currentType);
                fd.append('path', currentSubPath);
                const resp = await fetch(`${API_BASE}/resources/upload`, { method: 'POST', body: fd });
                const data = await resp.json();
                if (data.success) ok++;
                else { fail++; console.error('[files.js] 上传失败:', file.name, data.error); }
            } catch (err) {
                fail++;
                console.error('[files.js] 上传异常:', file.name, err);
            }
        }
        showMsg(`上传完成：成功 ${ok} 个${fail > 0 ? `，失败 ${fail} 个` : ''}`);
        await refreshList();
        await loadResourceTypes();
        highlightType(currentType);
    }

    // ==================== 驱动上传弹窗 ====================
    function setDriverBanner(kind, html) {
        if (!driverDetectBanner) return;
        driverDetectBanner.className = 'detect-banner ' + kind;
        driverDetectBanner.innerHTML = html;
    }
    function setDriverError(msg) {
        if (driverUploadError) driverUploadError.textContent = msg || '';
    }

    function openDriverUploadModal() {
        if (!driverModalReady) {
            alert('驱动上传弹窗未就绪，请检查 files.html 是否包含 #driverUploadModal 等元素');
            return;
        }
        driverJarFile.value = '';
        if (driverFilePicker) driverFilePicker.classList.remove('has-file');
        if (driverFilePickerName) driverFilePickerName.textContent = '点击选择 .jar 文件';
        driverUploadDbType.value = 'mysql';
        driverUploadVersion.value = '';
        setDriverBanner('info', '选择文件后自动识别数据库类型与版本');
        setDriverError('');
        driverUploadModal.style.display = 'flex';
    }

    function closeDriverUploadModalFn() {
        if (driverUploadModal) driverUploadModal.style.display = 'none';
    }

    async function doDriverUpload() {
        setDriverError('');
        const file = driverJarFile.files[0];
        const dbType = driverUploadDbType.value;
        const version = driverUploadVersion.value.trim();

        // 前端校验
        if (!file) { setDriverError('请选择 Jar 文件'); return; }
        if (!file.name.toLowerCase().endsWith('.jar')) {
            setDriverError('只允许上传 .jar 文件'); return;
        }
        if (!ALLOWED_DB_TYPES.includes(dbType)) {
            setDriverError('不支持的数据库类型：' + dbType); return;
        }
        if (!version) {
            setDriverError('驱动版本不能为空，请手动填写');
            driverUploadVersion.focus();
            return;
        }
        if (version.indexOf('_') >= 0) {
            setDriverError('驱动版本不能包含下划线 _'); return;
        }

        driverUploadConfirmBtn.disabled = true;
        driverUploadConfirmBtn.textContent = '⏳ 提交中...';
        try {
            const fd = new FormData();
            fd.append('file', file);
            fd.append('type', currentType);      // drivers
            fd.append('path', currentSubPath);   // 允许子目录
            fd.append('dbType', dbType);
            fd.append('version', version);

            const resp = await fetch(`${API_BASE}/resources/upload`, { method: 'POST', body: fd });
            const data = await resp.json();
            if (data.success) {
                closeDriverUploadModalFn();
                await refreshList();
                await loadResourceTypes();
                highlightType(currentType);
            } else {
                setDriverError(data.error || '上传失败');
            }
        } catch (err) {
            setDriverError('网络错误：' + err.message);
        } finally {
            driverUploadConfirmBtn.disabled = false;
            driverUploadConfirmBtn.textContent = '保存';
        }
    }

    // ==================== 驱动弹窗事件绑定（全部 null 保护） ====================
    if (driverFilePicker && driverJarFile) {
        driverFilePicker.addEventListener('click', () => driverJarFile.click());
    }
    if (driverJarFile) {
        driverJarFile.addEventListener('change', () => {
            const file = driverJarFile.files[0];
            if (!file) return;
            if (!file.name.toLowerCase().endsWith('.jar')) {
                setDriverError('只允许上传 .jar 文件');
                driverJarFile.value = '';
                if (driverFilePicker) driverFilePicker.classList.remove('has-file');
                if (driverFilePickerName) driverFilePickerName.textContent = '点击选择 .jar 文件';
                return;
            }
            setDriverError('');
            if (driverFilePicker) driverFilePicker.classList.add('has-file');
            if (driverFilePickerName) {
                driverFilePickerName.textContent = file.name + ' (' + (file.size / 1024).toFixed(1) + ' KB)';
            }

            // 自动识别
            const detectedType = detectDbTypeFromFileName(file.name);
            const detectedVersion = detectVersionFromFileName(file.name);

            if (detectedType && driverUploadDbType) driverUploadDbType.value = detectedType;
            if (detectedVersion && driverUploadVersion) driverUploadVersion.value = detectedVersion;

            const parts = [];
            if (detectedType) parts.push('<b>类型</b>：' + (DB_TYPE_LABELS[detectedType] || detectedType));
            if (detectedVersion) parts.push('<b>版本</b>：' + escapeHtml(detectedVersion));

            if (detectedType && detectedVersion) {
                setDriverBanner('success',
                    '✅ 从文件名自动识别：' + parts.join(' · ') + '<br>请确认后提交，或手动修改');
            } else if (detectedType || detectedVersion) {
                setDriverBanner('warn',
                    '⚠️ 部分识别：' + parts.join(' · ') + '<br>未识别的字段请手动填写');
            } else {
                setDriverBanner('warn',
                    '⚠️ 未能从文件名识别类型与版本，请手动选择类型并填写版本');
            }
        });
    }
    if (closeDriverUploadModal) closeDriverUploadModal.addEventListener('click', closeDriverUploadModalFn);
    if (driverUploadCancelBtn)  driverUploadCancelBtn.addEventListener('click', closeDriverUploadModalFn);
    if (driverUploadConfirmBtn) driverUploadConfirmBtn.addEventListener('click', doDriverUpload);
    if (driverUploadModal) {
        driverUploadModal.addEventListener('click', (e) => {
            if (e.target === driverUploadModal) closeDriverUploadModalFn();
        });
    }

    // ==================== 主按钮事件（全部 null 保护） ====================
    if (uploadResourceBtn) {
        uploadResourceBtn.addEventListener('click', () => {
            if (currentType === 'drivers') {
                openDriverUploadModal();
            } else if (resourceFileInput) {
                resourceFileInput.value = '';
                resourceFileInput.click();
            }
        });
    }
    if (resourceFileInput) {
        resourceFileInput.addEventListener('change', (e) => {
            const files = Array.from(e.target.files || []);
            if (files.length > 0) uploadGenericResources(files);
        });
    }
    if (newSubDirBtn) {
        newSubDirBtn.addEventListener('click', createSubDirectory);
    }
    if (refreshResourcesBtn) {
        refreshResourcesBtn.addEventListener('click', async () => {
            await loadResourceTypes();
            await refreshList();
        });
    }

    // ==================== 启动 ====================
    loadResourceTypes();
})();