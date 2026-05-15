// webapps/face/components/Directory.js
import { apiUrl } from '../api.js';

export const Directory = {
    template: `
        <div class="card directory-card">
            <h2>📁 目录管理</h2>
            <div style="display: flex; gap: 20px; height: calc(100vh - 200px);">
                <div style="width: 280px; border-right: 1px solid rgba(0,102,255,0.2); overflow-y: auto;">
                    <div id="fileTreeContainer" class="tree"></div>
                </div>
                <div style="flex:1; display: flex; flex-direction: column;">
                    <div id="fileListPanel" style="flex:1;">
                        <div class="toolbar">
                            <button id="newFolderBtnDir" class="btn-secondary">📁 新建目录</button>
                            <button id="refreshBtnDir" class="btn-secondary">🔄 刷新</button>
                        </div>
                        <div id="currentPathDisplay" style="margin: 12px 0; font-family: monospace;"></div>
                        <div id="fileTableWrapper" style="overflow-x: auto;"></div>
                    </div>
                    <div id="editorPanelDir" style="display: none; flex-direction: column; height: 100%;">
                        <div class="editor-header">
                            <strong id="editFileNameLabel"></strong>
                            <button id="closeEditorBtnDir" class="btn-secondary">关闭</button>
                        </div>
                        <textarea id="fileEditorDir" rows="20" style="width:100%; font-family:monospace; flex:1;"></textarea>
                        <div style="margin-top: 12px;">
                            <button id="saveFileBtnDir" class="btn-primary">保存</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `,
    data() {
        return {
            rootType: 'local',
            currentPath: '',
            fileList: [],
            editingFile: null,
            fileContent: ''
        };
    },
    methods: {
        async fetchTree() {
            const container = document.getElementById('fileTreeContainer');
            if (!container) return;
            try {
                const url = apiUrl(`/files/api/tree?rootType=${this.rootType}&path=&withChildren=true`);
                const res = await fetch(url);
                if (!res.ok) throw new Error('获取目录树失败');
                const root = await res.json();
                this.renderTree(root, container);
            } catch (err) {
                container.innerHTML = `<div class="error">${err.message}</div>`;
            }
        },
        renderTree(node, container, level = 0) {
            const div = document.createElement('div');
            div.className = 'tree-item';
            div.style.paddingLeft = `${level * 20 + 8}px`;
            div.innerHTML = `
                <span class="toggle">${node.hasChildren ? '▶' : '•'}</span>
                <span class="icon">${node.type === 'directory' ? '📁' : '📄'}</span>
                <span class="name">${this.escapeHtml(node.name)}</span>
            `;
            const childrenDiv = document.createElement('div');
            childrenDiv.className = 'tree-children';
            childrenDiv.style.display = 'none';

            div.addEventListener('click', async (e) => {
                e.stopPropagation();
                if (node.type === 'directory') {
                    this.currentPath = node.path;
                    await this.fetchFileList();
                    // 展开/折叠
                    const isOpen = childrenDiv.style.display === 'block';
                    if (!isOpen && node.hasChildren && (!node.children || node.children.length === 0)) {
                        // 懒加载子节点
                        try {
                            const url = apiUrl(`/files/api/tree?rootType=${this.rootType}&path=${encodeURIComponent(node.path)}&withChildren=true`);
                            const res = await fetch(url);
                            const childData = await res.json();
                            node.children = childData.children || [];
                            childrenDiv.innerHTML = '';
                            node.children.forEach(child => this.renderTree(child, childrenDiv, level + 1));
                        } catch (err) {
                            childrenDiv.innerHTML = `<div class="error">加载失败</div>`;
                        }
                    }
                    childrenDiv.style.display = isOpen ? 'none' : 'block';
                    const toggleSpan = div.querySelector('.toggle');
                    toggleSpan.textContent = isOpen ? '▶' : '▼';
                } else {
                    // 文件直接编辑
                    this.editFile(node.path);
                }
            });
            container.appendChild(div);
            container.appendChild(childrenDiv);
            if (node.children && node.children.length) {
                node.children.forEach(child => this.renderTree(child, childrenDiv, level + 1));
                childrenDiv.style.display = 'block';
                const toggleSpan = div.querySelector('.toggle');
                toggleSpan.textContent = '▼';
            }
        },
        async fetchFileList() {
            const url = apiUrl(`/files/api/list?rootType=${this.rootType}&path=${encodeURIComponent(this.currentPath)}`);
            const res = await fetch(url);
            if (res.ok) {
                this.fileList = await res.json();
                this.renderFileList();
                document.getElementById('currentPathDisplay').innerHTML = `当前路径：${this.currentPath === '' ? '/' : '/' + this.currentPath}`;
            } else {
                alert('获取文件列表失败');
            }
        },
        renderFileList() {
            const wrapper = document.getElementById('fileTableWrapper');
            if (!wrapper) return;
            if (!this.fileList.length) {
                wrapper.innerHTML = '<div class="empty-tip">目录为空</div>';
                return;
            }
            let html = `<table class="data-table"><thead><tr>
                <th>名称</th><th>类型</th><th>大小</th><th>修改时间</th><th>操作</th>
            </tr></thead><tbody>`;
            for (const item of this.fileList) {
                html += `<tr>
                    <td class="dir-link" data-path="${this.escapeAttr(item.path)}" data-type="${item.type}">${this.escapeHtml(item.name)}</td>
                    <td>${item.type === 'directory' ? '目录' : '文件'}</td>
                    <td>${item.type === 'file' ? this.formatSize(item.size) : '-'}</td>
                    <td>${item.modified || '-'}</td>
                    <td>
                        ${item.type === 'file' ? `<button class="btn-secondary edit-btn" data-path="${this.escapeAttr(item.path)}">编辑</button>` : ''}
                        <button class="btn-secondary rename-btn" data-path="${this.escapeAttr(item.path)}" data-name="${this.escapeAttr(item.name)}">重命名</button>
                        <button class="btn-secondary delete-btn" data-path="${this.escapeAttr(item.path)}">删除</button>
                    </td>
                </tr>`;
            }
            html += `</tbody></table>`;
            wrapper.innerHTML = html;

            // 绑定事件
            wrapper.querySelectorAll('.dir-link').forEach(el => {
                el.addEventListener('click', () => {
                    const path = el.dataset.path;
                    const type = el.dataset.type;
                    if (type === 'directory') {
                        this.currentPath = path;
                        this.fetchFileList();
                        this.fetchTree(); // 刷新树高亮（可选）
                    } else {
                        this.editFile(path);
                    }
                });
            });
            wrapper.querySelectorAll('.edit-btn').forEach(btn => {
                btn.addEventListener('click', () => this.editFile(btn.dataset.path));
            });
            wrapper.querySelectorAll('.rename-btn').forEach(btn => {
                btn.addEventListener('click', () => this.renameItem(btn.dataset.path, btn.dataset.name));
            });
            wrapper.querySelectorAll('.delete-btn').forEach(btn => {
                btn.addEventListener('click', () => this.deleteItem(btn.dataset.path));
            });
        },
        async editFile(path) {
            const url = apiUrl(`/files/api/read?rootType=${this.rootType}&path=${encodeURIComponent(path)}`);
            const res = await fetch(url);
            if (res.ok) {
                const data = await res.json();
                this.editingFile = { path };
                document.getElementById('editFileNameLabel').innerText = path;
                document.getElementById('fileEditorDir').value = data.content;
                document.getElementById('fileListPanel').style.display = 'none';
                document.getElementById('editorPanelDir').style.display = 'flex';
            } else {
                alert('读取文件失败');
            }
        },
        async saveFile() {
            const content = document.getElementById('fileEditorDir').value;
            const url = apiUrl('/files/api/save');
            const res = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    rootType: this.rootType,
                    path: this.editingFile.path,
                    content: content
                })
            });
            if (res.ok) {
                alert('保存成功');
                this.closeEditor();
                await this.fetchFileList();
                await this.fetchTree();
            } else {
                alert('保存失败');
            }
        },
        closeEditor() {
            this.editingFile = null;
            document.getElementById('fileListPanel').style.display = 'block';
            document.getElementById('editorPanelDir').style.display = 'none';
        },
        async newFolder() {
            const name = prompt('请输入目录名');
            if (!name) return;
            const url = apiUrl('/files/api/mkdir');
            const res = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    rootType: this.rootType,
                    parentPath: this.currentPath,
                    name: name
                })
            });
            if (res.ok) {
                await this.fetchFileList();
                await this.fetchTree();
            } else {
                alert('创建失败');
            }
        },
        async renameItem(oldPath, oldName) {
            const newName = prompt('请输入新名称', oldName);
            if (!newName) return;
            const url = apiUrl('/files/api/rename');
            const res = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    rootType: this.rootType,
                    path: oldPath,
                    newName: newName
                })
            });
            if (res.ok) {
                await this.fetchFileList();
                await this.fetchTree();
            } else {
                alert('重命名失败');
            }
        },
        async deleteItem(path) {
            if (!confirm(`确定删除 ${path} 吗？`)) return;
            const url = apiUrl('/files/api/delete');
            const res = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    rootType: this.rootType,
                    path: path
                })
            });
            if (res.ok) {
                await this.fetchFileList();
                await this.fetchTree();
            } else {
                alert('删除失败');
            }
        },
        refresh() {
            this.fetchFileList();
            this.fetchTree();
        },
        formatSize(bytes) {
            if (bytes < 1024) return bytes + ' B';
            if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
            return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        },
        escapeHtml(str) {
            if (!str) return '';
            return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
        },
        escapeAttr(str) {
            return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
        }
    },
    mounted() {
        if (this.rootType === undefined) this.rootType = 'local';
        if (this.currentPath === undefined) this.currentPath = '';
        // 延迟执行，确保 DOM 完全挂载
        setTimeout(() => {
            this.fetchTree();
            this.fetchFileList();
        }, 50);
        // 绑定按钮事件
        document.getElementById('newFolderBtnDir')?.addEventListener('click', () => this.newFolder());
        document.getElementById('refreshBtnDir')?.addEventListener('click', () => this.refresh());
        document.getElementById('closeEditorBtnDir')?.addEventListener('click', () => this.closeEditor());
        document.getElementById('saveFileBtnDir')?.addEventListener('click', () => this.saveFile());
        window.__refreshDirectory = () => {
            this.fetchTree();
            this.fetchFileList();
        };
    }
};