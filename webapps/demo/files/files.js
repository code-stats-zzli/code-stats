(function() {
    const API_BASE = window.apiUrl('/files/api');

    let currentResourceType = 'images';
    let resourceTypes = {};

    const resourceTypeList    = document.getElementById('resourceTypeList');
    const resourceTitle       = document.getElementById('resourceTitle');
    const resourceListPanel   = document.getElementById('resourceListPanel');
    const uploadResourceBtn   = document.getElementById('uploadResourceBtn');
    const refreshResourcesBtn = document.getElementById('refreshResourcesBtn');
    const resourceFileInput   = document.getElementById('resourceFileInput');

    const TYPE_ICONS = {
        images:      '🖼️',
        pages:       '📄',
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

    // ==================== 加载类型列表 ====================

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
                el.addEventListener('click', () => switchResourceType(el.dataset.type));
            });

            if (currentResourceType && resourceTypes[currentResourceType]) {
                highlightResourceType(currentResourceType);
            } else {
                const firstType = Object.keys(resourceTypes)[0] || 'images';
                switchResourceType(firstType);
            }
        } catch (err) {
            resourceTypeList.innerHTML = `<div class="error-tip">❌ 加载失败: ${escapeHtml(err.message)}</div>`;
        }
    }

    function highlightResourceType(type) {
        currentResourceType = type;
        resourceTypeList.querySelectorAll('.resource-type-item').forEach(el => {
            el.classList.toggle('active', el.dataset.type === type);
        });
        const label = resourceTypes[type] || type;
        resourceTitle.textContent = `${getTypeIcon(type)} ${label} (资源管理)`;
    }

    async function switchResourceType(type) {
        highlightResourceType(type);
        await loadResources(type);
    }

    // ==================== 加载资源列表 ====================

    async function loadResources(type) {
        resourceListPanel.innerHTML = '<div class="loading">加载中...</div>';
        try {
            const resp = await fetch(`${API_BASE}/resources/list?type=${encodeURIComponent(type)}`);
            const list = await resp.json();
            if (!Array.isArray(list)) {
                resourceListPanel.innerHTML = `<div class="error-tip">❌ ${escapeHtml(list.error || '加载失败')}</div>`;
                return;
            }
            if (list.length === 0) {
                resourceListPanel.innerHTML = `<div class="empty-tip">📭 暂无资源，点击「上传资源」添加</div>`;
                return;
            }
            renderResourceList(type, list);
        } catch (err) {
            resourceListPanel.innerHTML = `<div class="error-tip">❌ ${escapeHtml(err.message)}</div>`;
        }
    }

    function renderResourceList(type, list) {
        const isImage = type === 'images';
        let html = '<div class="resource-grid">';
        for (const item of list) {
            const displayName = item.displayName || item.storedName;
            const encoded = encodeURIComponent(displayName);
            const previewUrl = `${API_BASE}/resources/preview?type=${encodeURIComponent(type)}&name=${encoded}`;

            let thumbHtml;
            if (isImage) {
                thumbHtml = `<img class="res-thumb" src="${previewUrl}&t=${Date.now()}"
                                  alt="${escapeHtml(displayName)}"
                                  onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                             <div class="res-thumb-fallback" style="display:none;">🖼️</div>`;
            } else {
                thumbHtml = `<div class="res-thumb-fallback">${getTypeIcon(type)}</div>`;
            }

            html += `
                <div class="resource-card" data-name="${escapeHtml(displayName)}">
                    <div class="res-thumb-wrap">${thumbHtml}</div>
                    <div class="res-info">
                        <div class="res-name" title="${escapeHtml(displayName)}">${escapeHtml(displayName)}</div>
                        <div class="res-meta">${formatSize(item.size)} · ${item.modified}</div>
                    </div>
                    <div class="res-actions">
                        <button class="res-action-btn" data-action="download" title="下载">⬇️</button>
                        <button class="res-action-btn res-delete" data-action="delete" title="删除">🗑️</button>
                    </div>
                </div>
            `;
        }
        html += '</div>';
        resourceListPanel.innerHTML = html;

        resourceListPanel.querySelectorAll('.res-action-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const card = btn.closest('.resource-card');
                const displayName = card.dataset.name;
                const action = btn.dataset.action;
                if (action === 'download') downloadResource(type, displayName);
                else if (action === 'delete') deleteResource(type, displayName);
            });
        });
    }

    function downloadResource(type, displayName) {
        const url = `${API_BASE}/resources/download?type=${encodeURIComponent(type)}&name=${encodeURIComponent(displayName)}`;
        window.open(url, '_blank');
    }

    async function deleteResource(type, displayName) {
        if (!confirm(`确定删除资源「${displayName}」吗？`)) return;
        try {
            const resp = await fetch(`${API_BASE}/resources/delete`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ type, displayName })
            });
            const data = await resp.json();
            if (data.success) {
                showMsg('删除成功');
                await loadResources(type);
                await loadResourceTypes();
                highlightResourceType(type);
            } else {
                showMsg(data.error || '删除失败', true);
            }
        } catch (err) { showMsg(err.message, true); }
    }

    async function uploadResources(files) {
        if (!files || files.length === 0) return;
        const type = currentResourceType;
        let successCount = 0;
        let failCount = 0;

        for (const file of files) {
            try {
                const formData = new FormData();
                formData.append('file', file);
                formData.append('type', type);
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
        await loadResources(type);
        await loadResourceTypes();
        highlightResourceType(type);
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
    refreshResourcesBtn.addEventListener('click', async () => {
        await loadResourceTypes();
        await switchResourceType(currentResourceType);
    });

    // ==================== 启动 ====================
    loadResourceTypes();
})();