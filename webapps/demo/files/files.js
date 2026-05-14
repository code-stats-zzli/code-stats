// ======== D:\work\git\CodeStats\webapps\demo\files\files.js ========
(function() {
    const API_BASE = window.apiUrl('/files/api');
    let currentRootType = 'local';   // 'local' 或 'server'
    let currentPath = '';
    let currentEditPath = null;
    let rootPathInfo = {};

    // DOM 元素
    const treeContainer = document.getElementById('treeContainer');
    const pathSpan = document.getElementById('currentPath');
    const fileTableBody = document.getElementById('fileTableBody');
    const fileListPanel = document.getElementById('fileListPanel');
    const editorPanel = document.getElementById('editorPanel');
    const fileEditor = document.getElementById('fileEditor');
    const editFileNameSpan = document.getElementById('editFileName');
    const newFolderBtn = document.getElementById('newFolderBtn');
    const newFileBtn = document.getElementById('newFileBtn');
    const uploadBtn = document.getElementById('uploadBtn');
    const refreshBtn = document.getElementById('refreshBtn');
    const closeEditorBtn = document.getElementById('closeEditorBtn');
    const saveFileBtn = document.getElementById('saveFileBtn');
    const rootLocalBtn = document.getElementById('rootLocalBtn');
    const rootServerBtn = document.getElementById('rootServerBtn');

    function showMsg(msg, isError = false) {
        alert(msg);
    }

    // 获取根信息
    async function fetchRootInfo() {
        try {
            const res = await fetch(`${API_BASE}/root?rootType=${currentRootType}`);
            if (res.ok) {
                const data = await res.json();
                rootPathInfo = data;
            }
        } catch (err) {
            console.warn(err);
        }
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

    // 渲染树
    async function renderTree() {
        try {
            const rootNode = await fetchTree('');
            treeContainer.innerHTML = '';
            const rootLi = createTreeNode(rootNode, 0);
            treeContainer.appendChild(rootLi);
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

        // 只有目录且拥有子节点时才显示 toggle 箭头
        const hasChildren = node.hasChildren === true;
        const toggle = document.createElement('span');
        toggle.className = 'toggle';
        if (node.type === 'directory' && hasChildren) {
            toggle.textContent = node.expanded ? '▼' : '▶';
            toggle.onclick = (e) => {
                e.stopPropagation();
                toggleNode(li, node, toggle);
            };
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
                    node.hasChildren = data.hasChildren;
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
        const displayPath = path === '' ? '/' : '/' + path;
        pathSpan.textContent = displayPath;
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
                    <span class="action-icon" data-action="rename" data-path="${file.path}" data-name="${file.name}">✏️ 重命名</span>
                    <span class="action-icon" data-action="delete" data-path="${file.path}">🗑️ 删除</span>
                </td>
            `;
            fileTableBody.appendChild(row);
        });
        // 名称点击（目录进入，文件编辑）
        document.querySelectorAll('.file-name').forEach(el => {
            el.addEventListener('click', (e) => {
                const path = el.dataset.path;
                const type = el.dataset.type;
                if (type === 'directory') navigateTo(path);
                else openEditor(path);
            });
        });
        // 操作按钮
        document.querySelectorAll('.action-icon').forEach(icon => {
            icon.addEventListener('click', (e) => {
                e.stopPropagation();
                const action = icon.dataset.action;
                const path = icon.dataset.path;
                const name = icon.dataset.name;
                if (action === 'edit') openEditor(path);
                else if (action === 'rename') renameItem(path, name);
                else if (action === 'delete') deleteItem(path);
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
                body: JSON.stringify({ rootType: currentRootType, path: currentEditPath, content: content })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('保存成功');
                navigateTo(currentPath);
                renderTree();
            } else {
                showMsg(data.error || '保存失败', true);
            }
        } catch (err) {
            showMsg(err.message, true);
        }
    }

    async function renameItem(oldPath, oldName) {
        const newName = prompt('请输入新名称', oldName);
        if (!newName) return;
        try {
            const res = await fetch(`${API_BASE}/rename`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: currentRootType, path: oldPath, newName: newName })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('重命名成功');
                await navigateTo(currentPath);
                await renderTree();
            } else {
                showMsg(data.error || '重命名失败', true);
            }
        } catch (err) {
            showMsg(err.message, true);
        }
    }

    async function deleteItem(path) {
        if (!confirm(`确定删除 ${path} 吗？`)) return;
        try {
            const res = await fetch(`${API_BASE}/delete`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: currentRootType, path: path })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('删除成功');
                await navigateTo(currentPath);
                await renderTree();
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
                body: JSON.stringify({ rootType: currentRootType, parentPath: currentPath, name: folderName })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('目录创建成功');
                await navigateTo(currentPath);
                await renderTree();
            } else {
                showMsg(data.error || '创建失败', true);
            }
        } catch (err) {
            showMsg(err.message, true);
        }
    }

    async function newFile() {
        const fileName = prompt('请输入文件名（含扩展名）', 'newfile.txt');
        if (!fileName) return;
        try {
            const res = await fetch(`${API_BASE}/createFile`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rootType: currentRootType, parentPath: currentPath, fileName: fileName })
            });
            const data = await res.json();
            if (data.success) {
                showMsg('文件创建成功');
                await navigateTo(currentPath);
                await renderTree();
            } else {
                showMsg(data.error || '创建失败', true);
            }
        } catch (err) {
            showMsg(err.message, true);
        }
    }

    function uploadFile() {
        const input = document.createElement('input');
        input.type = 'file';
        input.onchange = async () => {
            const file = input.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = async (e) => {
                const base64 = btoa(unescape(encodeURIComponent(e.target.result)));
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
                    } else {
                        showMsg(data.error || '上传失败', true);
                    }
                } catch (err) {
                    showMsg(err.message, true);
                }
            };
            reader.readAsText(file);
        };
        input.click();
    }

    async function switchRoot(rootType) {
        currentRootType = rootType;
        currentPath = '';
        if (rootType === 'local') {
            rootLocalBtn.classList.add('active');
            rootServerBtn.classList.remove('active');
        } else {
            rootServerBtn.classList.add('active');
            rootLocalBtn.classList.remove('active');
        }
        await fetchRootInfo();
        await renderTree();
        await navigateTo('');
    }

    // 事件绑定
    rootLocalBtn.addEventListener('click', () => switchRoot('local'));
    rootServerBtn.addEventListener('click', () => switchRoot('server'));
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

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => {
            if (m === '&') return '&amp;';
            if (m === '<') return '&lt;';
            if (m === '>') return '&gt;';
            return m;
        });
    }

    // 初始化
    (async () => {
        await switchRoot('local');
    })();
})();