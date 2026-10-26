(function() {
    const API_BASE = window.apiUrl('/files/api');

    let currentMode = 'resources';   // 默认资源模式
    let currentRootType = 'local';
    let currentPath = '';
    let currentEditPath = null;
    let rootPathInfo = {};
    let currentResourceType = 'images';
    let resourceTypes = {};

    const treeContainer       = document.getElementById('treeContainer');
    const treeWrapper         = document.getElementById('treeWrapper');
    const resourceTypeList    = document.getElementById('resourceTypeList');
    const localModeContent    = document.getElementById('localModeContent');
    const resourcesModeContent= document.getElementById('resourcesModeContent');
    const pathSpan            = document.getElementById('currentPath');
    const fileTableBody       = document.getElementById('fileTableBody');
    const fileListPanel       = document.getElementById('fileListPanel');
    const editorPanel         = document.getElementById('editorPanel');
    const fileEditor          = document.getElementById('fileEditor');
    const editFileNameSpan    = document.getElementById('editFileName');
    const newFolderBtn        = document.getElementById('newFolderBtn');
    const newFileBtn          = document.getElementById('newFileBtn');
    const uploadBtn           = document.getElementById('uploadBtn');
    const refreshBtn          = document.getElementById('refreshBtn');
    const closeEditorBtn      = document.getElementById('closeEditorBtn');
    const saveFileBtn         = document.getElementById('saveFileBtn');
    const rootLocalBtn        = document.getElementById('rootLocalBtn');
    const rootResourcesBtn    = document.getElementById('rootResourcesBtn');

    const resourceTitle       = document.getElementById('resourceTitle');
    const resourceListPanel   = document.getElementById('resourceListPanel');
    const uploadResourceBtn   = document.getElementById('uploadResourceBtn');
    const refreshResourcesBtn = document.getElementById('refreshResourcesBtn');
    const resourceFileInput   = document.getElementById('resourceFileInput');

    function showMsg(msg, isError = false) { alert(msg); }

    // ==================== 本地目录逻辑 ====================

    async function fetchRootInfo() {
        try {
            const res = await fetch(`${API_BASE}/root?rootType=${currentRootType}`);
            if (res.ok) rootPathInfo = await res.json();
        } catch (err) { console.warn(err); }
    }

    async function fetchTree(path = '') {
        const url = `${API_BASE}/tree?rootType=${currentRootType}&path=${encodeURIComponent(path)}&withChildren=true`;
        const res = await fetch(url);
        if (!res.ok) throw new Error('获取目录树失败');
        return await res.json();
    }

    async function fetchFileList(path) {
        const url = `${API_BASE}/list?rootType=${currentRootType}&path=${encodeURIComponent(path)}`;
        const res = await fetch(url);
        if (!res.ok) throw new Error('获取文件列表失败');
        return await res.json();
    }

    async function renderTree() {
        try {
            const rootNode = await fetchTree('');
            treeContainer.innerHTML = '';
            const rootLi = createTreeNode(rootNode, 0);
            treeContainer.appendChild(rootLi);
            const rootToggle = rootLi.querySelector('.toggle');
            if (rootToggle && rootToggle.textContent === '▶') rootToggle.click();
        } catch (err) { showMsg(err.message, true); }
    }

    function createTreeNode(node, level) {
        const li = document.createElement('li');
        li.className = 'tree-node';
        const div = document.createElement('div');
        div.className = 'tree-item';
        div.style.paddingLeft = (level * 16 + 8) + 'px';

        const hasChildren = node.hasChildren === true;
        const toggle = document.createElement('span');
        toggle.className = 'toggle';
        if (node.type === 'directory' && hasChildren) {
            toggle.textContent = node.expanded ? '▼' : '▶';
            toggle.onclick = (e) => { e.stopPropagation(); toggleNode(li, node, toggle); };
        } else {
            toggle.textContent = ' ';
            toggle.style.opacity = '0';
        }

        const icon = document.createElement('span');
        icon.className = 'icon';
        icon.textContent = node.type === 'directory' ? '📁' : '📄';
        const nameSpan = document.createElement('span');
        nameSpan.className = 'name';
        nameSpan.textContent = node.name;
        div.appendChild(toggle); div.appendChild(icon); div.appendChild(nameSpan);

        div.onclick = () => {
            if (node.type === 'directory') {
                navigateTo(node.path);
                const childrenUl = li.querySelector('.tree-children');
                const isExpanded = childrenUl && childrenUl.classList.contains('open');
                if (!isExpanded && hasChildren) toggle.click();
            } else {
                openEditor(node.path);
            }
        };
        li.appendChild(div);

        if (node.children && node.children.length) {
            const childrenUl = document.createElement('ul');
            childrenUl.className = 'tree-children';
            if (node.expanded) childrenUl.classList.add('open');
            node.children.forEach(child => childrenUl.appendChild(createTreeNode(child, level + 1)));
            li.appendChild(childrenUl);
        }
        return li;
    }

    async function toggleNode(li, node, toggleSpan) {
        const childrenUl = li.querySelector('.tree-children');
        if (childrenUl) {
            const isOpen = childrenUl.classList.contains('open');
            if (!isOpen && (!node.children || node.children.length === 0)) {
                try {
                    const data = await fetchTree(node.path);
                    node.children = data.children || [];
                    node.hasChildren = data.hasChildren;
                    childrenUl.innerHTML = '';
                    node.children.forEach(child => childrenUl.appendChild(createTreeNode(child, 0)));
                } catch (err) { showMsg(err.message, true); return; }
            }
            childrenUl.classList.toggle('open');
            const newState = childrenUl.classList.contains('open');
            toggleSpan.textContent = newState ? '▼' : '▶';
            node.expanded = newState;
        }
    }

    async function navigateTo(path) {
        currentPath = path;
        pathSpan.textContent = path === '' ? '/' : '/' + path;
        try {
            const list = await fetchFileList(path);
            renderFileList(list);
            editorPanel.style.display = 'none';
            fileListPanel.style.display = 'block';
        } catch (err) { showMsg(err.message, true); }
    }

    function renderFileList(files) {
        fileTableBody.innerHTML = '';
        if (files.length === 0) {
            fileTableBody.innerHTML = '<tr><td colspan="5" style="text-align:center;">目录为空</td></tr>';
            return;
        }
        files.forEach(file => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td class="file-name" data-path="${file.path}" data-type="${file.type}">${escapeHtml(file.name)}</td>
                <td>${file.type === 'directory' ? '目录' : '文件'}</td>
                <td>${file.type === 'file' ? formatSize(file.size) : '-'}</td>
                <td>${file.modified || '-'}</td>
                <td class="actions">
                    ${file.type === 'file' ? `<span class="action-icon" data-action="edit" data-path="${file.path}">✏️ 编辑</span>` : ''}
                    <span class="action-icon" data-action="download" data-path="${file.path}" data-type="${file.type}">⬇️ 下载</span>
                    <span class="action-icon" data-action="rename" data-path="${file.path}" data-name="${file.name}">✏️ 重命名</span>
                    <span class="action-icon" data-action="delete" data-path="${file.path}">🗑️ 删除</span>
                </td>
            `;
            fileTableBody.appendChild(row);
        });

        document.querySelectorAll('.file-name').forEach(el => {
            el.addEventListener('click', () => {
                const path = el.dataset.path;
                const type = el.dataset.type;
                if (type === 'directory') navigateTo(path);
                else openEditor(path);
            });
        });

        document.querySelectorAll('.action-icon').forEach(icon => {
            icon.addEventListener('click', (e) => {
                e.stopPropagation();
                const action = icon.dataset.action;
                const path = icon.dataset.path;
                const type = icon.dataset.type;
                const name = icon.dataset.name;
                if (action === 'edit') openEditor(path);
                else if (action === 'download') downloadItem(path, type);
                else if (action === 'rename') renameItem(path, name);
                else if (action === 'delete') deleteItem(path);
            });
        });
    }

    function formatSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }

    async function openEditor(path) {
        try {
            const res = await fetch(`${API_BASE}/read?rootType=${currentRootType}&path=${encodeURIComponent(path)}`);
            if (!res.ok) throw new Error('读取文件失败');
            const data = await res.json();
            currentEditPath = path;
            fileEditor.value = data.content;
            editFileNameSpan.textContent = path;
            fileListPanel.style.display = 'none';
            editorPanel.style.display = 'flex';
        } catch (err) { showMsg(err.message, true); }
    }

    async function saveFile() {
        if (!currentEditPath) return;
        const content = fileEditor.value;
        try {
            const res = await fetch(`${API_BASE}/save`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: currentRootType, path: currentEditPath, content })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('保存成功');
                navigateTo(currentPath);
                renderTree();
            } else { showMsg(data.error || '保存失败', true); }
        } catch (err) { showMsg(err.message, true); }
    }

    async function renameItem(oldPath, oldName) {
        const newName = prompt('请输入新名称', oldName);
        if (!newName) return;
        try {
            const res = await fetch(`${API_BASE}/rename`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: currentRootType, path: oldPath, newName })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('重命名成功');
                await navigateTo(currentPath);
                await renderTree();
            } else { showMsg(data.error || '重命名失败', true); }
        } catch (err) { showMsg(err.message, true); }
    }

    async function deleteItem(path) {
        if (!confirm(`确定删除 ${path} 吗？`)) return;
        try {
            const res = await fetch(`${API_BASE}/delete`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: currentRootType, path })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('删除成功');
                await navigateTo(currentPath);
                await renderTree();
            } else { showMsg(data.error || '删除失败', true); }
        } catch (err) { showMsg(err.message, true); }
    }

    function downloadItem(path, type) {
        if (type === 'directory') {
            window.open(`${API_BASE}/downloadZip?rootType=${currentRootType}&path=${encodeURIComponent(path)}`, '_blank');
        } else {
            window.open(`${API_BASE}/download?rootType=${currentRootType}&path=${encodeURIComponent(path)}`, '_blank');
        }
    }

    async function newFolder() {
        const folderName = prompt('请输入目录名称');
        if (!folderName) return;
        try {
            const res = await fetch(`${API_BASE}/mkdir`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: currentRootType, parentPath: currentPath, name: folderName })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('目录创建成功');
                await navigateTo(currentPath);
                await renderTree();
            } else { showMsg(data.error || '创建失败', true); }
        } catch (err) { showMsg(err.message, true); }
    }

    async function newFile() {
        const fileName = prompt('请输入文件名（含扩展名）', 'newfile.txt');
        if (!fileName) return;
        try {
            const res = await fetch(`${API_BASE}/createFile`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: currentRootType, parentPath: currentPath, fileName })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('文件创建成功');
                await navigateTo(currentPath);
                await renderTree();
            } else { showMsg(data.error || '创建失败', true); }
        } catch (err) { showMsg(err.message, true); }
    }

    /**
     * 本地目录上传：用 readAsArrayBuffer 读取，Base64 编码后上传，
     * 避免 readAsText 破坏图片等二进制文件
     */
    function uploadFile() {
        const input = document.createElement('input');
        input.type = 'file';
        input.onchange = () => {
            const file = input.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = async (e) => {
                // ArrayBuffer -> Uint8Array -> binary string -> Base64
                const bytes = new Uint8Array(e.target.result);
                let binary = '';
                const CHUNK = 0x8000;
                for (let i = 0; i < bytes.length; i += CHUNK) {
                    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK));
                }
                const base64 = btoa(binary);
                try {
                    const res = await fetch(`${API_BASE}/upload`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            rootType: currentRootType,
                            parentPath: currentPath,
                            fileName: file.name,
                            content: base64
                        })
                    });
                    const data = await res.json();
                    if (data.success) {
                        showMsg('上传成功');
                        await navigateTo(currentPath);
                        await renderTree();
                    } else { showMsg(data.error || '上传失败', true); }
                } catch (err) { showMsg(err.message, true); }
            };
            reader.readAsArrayBuffer(file);   // ★ 关键：用 ArrayBuffer 而非 Text
        };
        input.click();
    }

    // ==================== 资源管理逻辑 ====================

    const TYPE_ICONS = {
        images:      '🖼️',
        pages:       '📄',
        attachments: '📎',
        others:      '📦'
    };
    function getTypeIcon(type) { return TYPE_ICONS[type] || '📦'; }

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
            }
        } catch (err) {
            resourceTypeList.innerHTML = `<div class="error-tip">❌ 加载失败: ${err.message}</div>`;
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

    async function loadResources(type) {
        resourceListPanel.innerHTML = '<div class="loading">加载中...</div>';
        try {
            const resp = await fetch(`${API_BASE}/resources/list?type=${encodeURIComponent(type)}`);
            const list = await resp.json();
            if (!Array.isArray(list)) {
                resourceListPanel.innerHTML = `<div class="error-tip">❌ ${list.error || '加载失败'}</div>`;
                return;
            }
            if (list.length === 0) {
                resourceListPanel.innerHTML = `<div class="empty-tip">📭 暂无资源，点击「上传资源」添加</div>`;
                return;
            }
            renderResourceList(type, list);
        } catch (err) {
            resourceListPanel.innerHTML = `<div class="error-tip">❌ ${err.message}</div>`;
        }
    }

    function renderResourceList(type, list) {
        const isImage = type === 'images';
        let html = '<div class="resource-grid">';
        for (const item of list) {
            const displayName = item.displayName || item.storedName;
            const encoded = encodeURIComponent(displayName);
            const previewUrl = `${API_BASE}/resources/preview?type=${encodeURIComponent(type)}&name=${encoded}`;

            let thumbHtml = '';
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
            } else { showMsg(data.error || '删除失败', true); }
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

    // ==================== 模式切换 ====================

    function switchMode(mode) {
        currentMode = mode;
        if (mode === 'local') {
            rootLocalBtn.classList.add('active');
            rootResourcesBtn.classList.remove('active');
            treeWrapper.style.display = 'block';
            resourceTypeList.style.display = 'none';
            localModeContent.style.display = 'flex';
            resourcesModeContent.style.display = 'none';
            fetchRootInfo().then(() => {
                renderTree();
                navigateTo('');
            });
        } else {
            rootResourcesBtn.classList.add('active');
            rootLocalBtn.classList.remove('active');
            treeWrapper.style.display = 'none';
            resourceTypeList.style.display = 'block';
            localModeContent.style.display = 'none';
            resourcesModeContent.style.display = 'flex';
            loadResourceTypes().then(() => {
                if (!currentResourceType || !resourceTypes[currentResourceType]) {
                    const firstType = Object.keys(resourceTypes)[0] || 'images';
                    switchResourceType(firstType);
                } else {
                    switchResourceType(currentResourceType);
                }
            });
        }
    }

    // ==================== 事件绑定 ====================

    rootLocalBtn.addEventListener('click', () => switchMode('local'));
    rootResourcesBtn.addEventListener('click', () => switchMode('resources'));

    newFolderBtn.addEventListener('click', newFolder);
    newFileBtn.addEventListener('click', newFile);
    uploadBtn.addEventListener('click', uploadFile);
    refreshBtn.addEventListener('click', () => navigateTo(currentPath));
    closeEditorBtn.addEventListener('click', () => {
        editorPanel.style.display = 'none';
        fileListPanel.style.display = 'block';
        currentEditPath = null;
    });
    saveFileBtn.addEventListener('click', saveFile);

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

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => {
            if (m === '&') return '&amp;';
            if (m === '<') return '&lt;';
            if (m === '>') return '&gt;';
            return m;
        });
    }

    // 启动：默认进入资源管理模式
    switchMode('resources');
})();