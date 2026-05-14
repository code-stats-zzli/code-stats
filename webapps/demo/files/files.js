(function() {
    const API_BASE = window.apiUrl('/files/api');
    let currentRootType = 'local';
    let currentPath = '';
    let currentEditPath = null;

    const tabBtns = document.querySelectorAll('.tab-btn');
    const treeContainer = document.getElementById('treeContainer');
    const pathSpan = document.getElementById('currentPath');
    const fileTableBody = document.getElementById('fileTableBody');
    const fileListPanel = document.getElementById('fileListPanel');
    const editorPanel = document.getElementById('editorPanel');
    const fileEditor = document.getElementById('fileEditor');
    const editFileNameSpan = document.getElementById('editFileName');
    const newFolderBtn = document.getElementById('newFolderBtn');
    const uploadBtn = document.getElementById('uploadBtn');
    const refreshBtn = document.getElementById('refreshBtn');
    const closeEditorBtn = document.getElementById('closeEditorBtn');
    const saveFileBtn = document.getElementById('saveFileBtn');

    function showMsg(msg, isError = false) {
        alert(msg);
    }

    async function fetchTree(path = '') {
        const url = `${API_BASE}/tree?rootType=${currentRootType}&path=${encodeURIComponent(path)}`;
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
            const rootData = await fetchTree('');
            treeContainer.innerHTML = '';
            const rootNode = createTreeNode(rootData, 0);
            treeContainer.appendChild(rootNode);
        } catch (err) {
            showMsg(err.message, true);
        }
    }

    function createTreeNode(node, level) {
        const li = document.createElement('li');
        li.className = 'tree-node';
        const div = document.createElement('div');
        div.className = 'tree-item';
        div.style.paddingLeft = (level * 16 + 8) + 'px';
        const toggle = document.createElement('span');
        toggle.className = 'toggle';
        if (node.type === 'directory') {
            toggle.textContent = node.expanded ? '▼' : '▶';
            toggle.onclick = (e) => {
                e.stopPropagation();
                toggleNode(li, node, toggle);
            };
        } else {
            toggle.textContent = ' ';
        }
        const icon = document.createElement('span');
        icon.className = 'icon';
        icon.textContent = node.type === 'directory' ? '📁' : '📄';
        const nameSpan = document.createElement('span');
        nameSpan.className = 'name';
        nameSpan.textContent = node.name;
        div.appendChild(toggle);
        div.appendChild(icon);
        div.appendChild(nameSpan);
        div.onclick = () => {
            if (node.type === 'directory') {
                navigateTo(node.path);
            } else {
                openEditor(node.path);
            }
        };
        li.appendChild(div);
        if (node.children && node.children.length) {
            const childrenUl = document.createElement('ul');
            childrenUl.className = 'tree-children';
            if (node.expanded) childrenUl.classList.add('open');
            node.children.forEach(child => {
                childrenUl.appendChild(createTreeNode(child, level + 1));
            });
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
                    childrenUl.innerHTML = '';
                    node.children.forEach(child => {
                        childrenUl.appendChild(createTreeNode(child, 0));
                    });
                } catch (err) {
                    showMsg(err.message, true);
                    return;
                }
            }
            childrenUl.classList.toggle('open');
            toggleSpan.textContent = childrenUl.classList.contains('open') ? '▼' : '▶';
        }
    }

    async function navigateTo(path) {
        currentPath = path;
        pathSpan.textContent = path || '/';
        try {
            const list = await fetchFileList(path);
            renderFileList(list);
            editorPanel.style.display = 'none';
            fileListPanel.style.display = 'block';
        } catch (err) {
            showMsg(err.message, true);
        }
    }

    function renderFileList(files) {
        fileTableBody.innerHTML = '';
        files.forEach(file => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${escapeHtml(file.name)}</td>
                <td>${file.type === 'directory' ? '目录' : '文件'}</td>
                <td>${file.type === 'file' ? formatSize(file.size) : '-'}</td>
                <td>${file.modified}</td>
                <td class="actions">
                    ${file.type === 'file' ? `<span class="action-icon" data-action="edit" data-path="${file.path}">✏️ 编辑</span>` : ''}
                    <span class="action-icon" data-action="rename" data-path="${file.path}">✏️ 重命名</span>
                    <span class="action-icon" data-action="delete" data-path="${file.path}">🗑️ 删除</span>
                </td>
            `;
            fileTableBody.appendChild(row);
        });
        document.querySelectorAll('.action-icon').forEach(icon => {
            icon.addEventListener('click', (e) => {
                e.stopPropagation();
                const action = icon.dataset.action;
                const path = icon.dataset.path;
                if (action === 'edit') openEditor(path);
                else if (action === 'rename') renameFileOrDir(path);
                else if (action === 'delete') deleteFileOrDir(path);
            });
        });
    }

    function formatSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024*1024) return (bytes/1024).toFixed(1) + ' KB';
        return (bytes/(1024*1024)).toFixed(1) + ' MB';
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
        } catch (err) {
            showMsg(err.message, true);
        }
    }

    async function saveFile() {
        if (!currentEditPath) return;
        const content = fileEditor.value;
        try {
            const res = await fetch(`${API_BASE}/save`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    rootType: currentRootType,
                    path: currentEditPath,
                    content: content
                })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('保存成功');
                navigateTo(currentPath);
            } else {
                showMsg(data.error || '保存失败', true);
            }
        } catch (err) {
            showMsg(err.message, true);
        }
    }

    async function renameFileOrDir(oldPath) {
        const newName = prompt('请输入新名称', oldPath.split(/[\\/]/).pop());
        if (!newName) return;
        try {
            const res = await fetch(`${API_BASE}/rename`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    rootType: currentRootType,
                    path: oldPath,
                    newName: newName
                })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('重命名成功');
                navigateTo(currentPath);
                renderTree();
            } else {
                showMsg(data.error || '重命名失败', true);
            }
        } catch (err) {
            showMsg(err.message, true);
        }
    }

    async function deleteFileOrDir(path) {
        if (!confirm(`确定删除 ${path} 吗？`)) return;
        try {
            const res = await fetch(`${API_BASE}/delete`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    rootType: currentRootType,
                    path: path
                })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('删除成功');
                navigateTo(currentPath);
                renderTree();
            } else {
                showMsg(data.error || '删除失败', true);
            }
        } catch (err) {
            showMsg(err.message, true);
        }
    }

    async function newFolder() {
        const folderName = prompt('请输入目录名称');
        if (!folderName) return;
        try {
            const res = await fetch(`${API_BASE}/mkdir`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    rootType: currentRootType,
                    parentPath: currentPath,
                    name: folderName
                })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('目录创建成功');
                navigateTo(currentPath);
                renderTree();
            } else {
                showMsg(data.error || '创建失败', true);
            }
        } catch (err) {
            showMsg(err.message, true);
        }
    }

    async function switchRootType(type) {
        currentRootType = type;
        currentPath = '';
        await renderTree();
        navigateTo('');
    }

    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            tabBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            switchRootType(btn.dataset.rootType);
        });
    });

    newFolderBtn.addEventListener('click', newFolder);
    refreshBtn.addEventListener('click', () => navigateTo(currentPath));
    closeEditorBtn.addEventListener('click', () => {
        editorPanel.style.display = 'none';
        fileListPanel.style.display = 'block';
        currentEditPath = null;
    });
    saveFileBtn.addEventListener('click', saveFile);
    uploadBtn.addEventListener('click', () => {
        const input = document.createElement('input');
        input.type = 'file';
        input.onchange = async () => {
            const file = input.files[0];
            if (!file) return;
            const formData = new FormData();
            formData.append('file', file);
            formData.append('rootType', currentRootType);
            formData.append('targetPath', currentPath);
            try {
                const res = await fetch(`${API_BASE}/upload`, {
                    method: 'POST',
                    body: formData
                });
                const data = await res.json();
                if (data.success) {
                    showMsg('上传成功');
                    navigateTo(currentPath);
                } else {
                    showMsg(data.error || '上传失败', true);
                }
            } catch (err) {
                showMsg(err.message, true);
            }
        };
        input.click();
    });

    function escapeHtml(str) {
        return str.replace(/[&<>]/g, function(m) {
            if (m === '&') return '&amp;';
            if (m === '<') return '&lt;';
            if (m === '>') return '&gt;';
            return m;
        });
    }

    switchRootType('local');
})();