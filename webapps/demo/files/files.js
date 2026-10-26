(function() {
    const API_BASE = window.apiUrl('/files/api');

    let currentType = 'images';
    let currentSubPath = '';    // 相对于类型根的相对路径，'' 表示根
    let resourceTypes = {};

    const resourceTypeList    = document.getElementById('resourceTypeList');
    const resourceTitle       = document.getElementById('resourceTitle');
    const breadcrumb          = document.getElementById('breadcrumb');
    const resourceListPanel   = document.getElementById('resourceListPanel');
    const uploadResourceBtn   = document.getElementById('uploadResourceBtn');
    const refreshResourcesBtn = document.getElementById('refreshResourcesBtn');
    const newSubDirBtn        = document.getElementById('newSubDirBtn');
    const resourceFileInput   = document.getElementById('resourceFileInput');

    const TYPE_ICONS = {
        images:      '🖼️',
        pages:       '📄',
        sql:         '🗃️',
        attachments: '📎',
        others:      '📦'
    };
    function getTypeIcon(type) { return TYPE_ICONS[type] || '📦'; }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>"]/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
        }[m]));
    }
    function formatSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }
    function showMsg(msg, isError = false) { alert(msg); }

    // ==================== 类型列表 ====================

    async function loadResourceTypes() {
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
                        <span class="name">${label}</span>
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
            resourceTypeList.innerHTML = `<div class="error-tip">❌ 加载失败: ${escapeHtml(err.message)}</div>`;
        }
    }

    function highlightType(type) {
        currentType = type;
        resourceTypeList.querySelectorAll('.resource-type-item').forEach(el => {
            el.classList.toggle('active', el.dataset.type === type);
        });
        const label = resourceTypes[type] || type;
        resourceTitle.textContent = `${getTypeIcon(type)} ${label} (资源管理)`;
    }

    function switchType(type) {
        highlightType(type);
        currentSubPath = '';
        refreshList();
    }

    // ==================== 面包屑 ====================

    function renderBreadcrumb() {
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

    // ==================== 列表加载 ====================

    async function refreshList() {
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

            let thumbHtml;
            if (isDir) {
                thumbHtml = `<div class="res-thumb-fallback folder-icon">📁</div>`;
            } else if (currentType === 'images') {
                const previewUrl = `${API_BASE}/resources/preview?type=${encodeURIComponent(currentType)}&path=${encodeURIComponent(item.fullRelPath)}`;
                thumbHtml = `<img class="res-thumb" src="${previewUrl}&t=${Date.now()}"
                                  alt="${escapeHtml(displayName)}"
                                  onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                             <div class="res-thumb-fallback" style="display:none;">🖼️</div>`;
            } else if (currentType === 'sql') {
                thumbHtml = `<div class="res-thumb-fallback sql-icon">🗃️</div>`;
            } else if (currentType === 'pages') {
                thumbHtml = `<div class="res-thumb-fallback page-icon">📄</div>`;
            } else {
                thumbHtml = `<div class="res-thumb-fallback">${getTypeIcon(currentType)}</div>`;
            }

            html += `
                <div class="resource-card ${isDir ? 'is-dir' : 'is-file'}"
                     data-name="${escapeHtml(displayName)}"
                     data-full-path="${escapeHtml(item.fullRelPath)}"
                     data-is-dir="${isDir}"
                     title="${isDir ? '双击进入目录' : '双击打开 / 下载'}">
                    <div class="res-thumb-wrap">${thumbHtml}</div>
                    <div class="res-info">
                        <div class="res-name">${escapeHtml(displayName)}</div>
                        <div class="res-meta">${isDir ? '目录' : formatSize(item.size) + ' · ' + item.modified}</div>
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

        // 卡片事件
        resourceListPanel.querySelectorAll('.resource-card').forEach(card => {
            // 双击
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

        // 按钮事件
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

    // ==================== 双击行为 ====================

    function getFileExt(name) {
        const i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase() : '';
    }

    function handleFileDblClick(fullPath) {
        const name = fullPath.split('/').pop();
        const ext = getFileExt(name);

        // ★ SQL 文件 → 打开数据库页面并加载
        if (ext === 'sql') {
            const url = window.apiUrl('/demo/db/dbclient.html')
                + '?loadSql=' + encodeURIComponent(fullPath)
                + '&loadSqlType=' + encodeURIComponent(currentType);
            window.open(url, '_blank');
            return;
        }

        // ★ HTML 页面 → 新窗口预览
        if (ext === 'html' || ext === 'htm') {
            const url = `${API_BASE}/resources/preview?type=${encodeURIComponent(currentType)}&path=${encodeURIComponent(fullPath)}`;
            window.open(url, '_blank');
            return;
        }

        // 图片 / PDF → 预览
        if (['png','jpg','jpeg','gif','svg','webp','bmp','ico','pdf'].includes(ext)) {
            const url = `${API_BASE}/resources/preview?type=${encodeURIComponent(currentType)}&path=${encodeURIComponent(fullPath)}`;
            window.open(url, '_blank');
            return;
        }

        // 其他 → 下载
        downloadResource(fullPath);
    }

    // ==================== 操作 ====================

    function downloadResource(fullPath) {
        const url = `${API_BASE}/resources/download?type=${encodeURIComponent(currentType)}&path=${encodeURIComponent(fullPath)}`;
        window.open(url, '_blank');
    }

    async function deleteResource(fullPath) {
        const name = fullPath.split('/').pop();
        const displayName = name;
        if (!confirm(`确定删除「${displayName}」吗？如果是目录会递归删除其内所有文件。`)) return;
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
                showMsg(data.error || '删除失败', true);
            }
        } catch (err) { showMsg(err.message, true); }
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
                showMsg(data.error || '创建失败', true);
            }
        } catch (err) { showMsg(err.message, true); }
    }

    async function uploadResources(files) {
        if (!files || files.length === 0) return;
        let successCount = 0;
        let failCount = 0;

        for (const file of files) {
            try {
                const formData = new FormData();
                formData.append('file', file);
                formData.append('type', currentType);
                formData.append('path', currentSubPath);
                const resp = await fetch(`${API_BASE}/resources/upload`, {
                    method: 'POST',
                    body: formData
                });
                const data = await resp.json();
                if (data.success) successCount++;
                else { failCount++; console.error('上传失败:', file.name, data.error); }
            } catch (err) {
                failCount++;
                console.error('上传异常:', file.name, err);
            }
        }

        showMsg(`上传完成：成功 ${successCount} 个${failCount > 0 ? `，失败 ${failCount} 个` : ''}`, failCount > 0);
        await refreshList();
        await loadResourceTypes();
        highlightType(currentType);
    }

    // ==================== 事件绑定 ====================

    uploadResourceBtn.addEventListener('click', () => {
        resourceFileInput.value = '';
        resourceFileInput.click();
    });
    resourceFileInput.addEventListener('change', (e) => {
        const files = Array.from(e.target.files || []);
        if (files.length > 0) uploadResources(files);
    });
    newSubDirBtn.addEventListener('click', createSubDirectory);
    refreshResourcesBtn.addEventListener('click', async () => {
        await loadResourceTypes();
        await refreshList();
    });

    // ==================== 启动 ====================
    loadResourceTypes();
})();