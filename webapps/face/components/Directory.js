import { apiUrl } from '../api.js';

export const Directory = {
    template: `
        <div class="card">
            <h2>📁 目录管理</h2>
            <div style="display: flex; gap: 20px;">
                <div style="width: 260px; border-right: 1px solid rgba(0,102,255,0.2); padding-right: 16px;">
                    <div id="fileTree" class="tree"></div>
                </div>
                <div style="flex:1;">
                    <div id="fileListPanel" v-bind:style="editingFile ? 'display:none' : 'display:block'">
                        <div class="toolbar" style="margin-bottom: 12px;">
                            <button class="btn-secondary" @click="newFolder">📁 新建目录</button>
                            <button class="btn-secondary" @click="refresh">🔄 刷新</button>
                        </div>
                        <div v-if="currentPath" style="margin-bottom: 12px; font-family: monospace;">当前路径：{{ currentPath }}</div>
                        <div id="fileTableWrapper"></div>
                    </div>
                    <div id="editorPanel" v-bind:style="editingFile ? 'display:block' : 'display:none'">
                        <div class="editor-header" style="margin-bottom: 12px;">
                            <strong>编辑：{{ editingFile ? editingFile.path : '' }}</strong>
                            <button class="btn-secondary" @click="closeEditor">关闭</button>
                        </div>
                        <textarea id="fileEditor" rows="20" style="width:100%; font-family:monospace;"></textarea>
                        <div style="margin-top: 12px;">
                            <button class="btn-primary" @click="saveFile">保存</button>
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
        formatSize(bytes) {
            if (bytes < 1024) return bytes + ' B';
            if (bytes < 1024*1024) return (bytes/1024).toFixed(1) + ' KB';
            return (bytes/(1024*1024)).toFixed(1) + ' MB';
        },
        async fetchFileList(path) {
            const url = apiUrl(`/files/api/list?rootType=${this.rootType}&path=${encodeURIComponent(path)}`);
            const res = await fetch(url);
            if (res.ok) {
                this.fileList = await res.json();
                this.renderFileList();
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
            let html = '<table><thead><tr><th>名称</th><th>类型</th><th>大小</th><th>修改时间</th><th>操作</th></tr></thead><tbody>';
            for (const item of this.fileList) {
                html += `<tr>
                    <td><span class="dir-link" data-path="${item.path}" data-type="${item.type}">${escapeHtml(item.name)}</span></td>
                    <td>${item.type === 'directory' ? '目录' : '文件'}</td>
                    <td>${item.type === 'file' ? this.formatSize(item.size) : '-'}</td>
                    <td>${item.modified || '-'}</td>
                    <td>
                        ${item.type === 'file' ? `<button class="btn-secondary edit-btn" data-path="${item.path}">编辑</button>` : ''}
                        <button class="btn-secondary rename-btn" data-path="${item.path}" data-name="${item.name}">重命名</button>
                        <button class="btn-secondary delete-btn" data-path="${item.path}">删除</button>
                    </td>
                </tr>`;
            }
            html += '</tbody></table>';
            wrapper.innerHTML = html;
            // 绑定动态事件
            wrapper.querySelectorAll('.dir-link').forEach(el => {
                el.addEventListener('click', (e) => {
                    const path = el.dataset.path;
                    const type = el.dataset.type;
                    if (type === 'directory') {
                        this.currentPath = path;
                        this.fetchFileList(path);
                        this.renderTree(); // 刷新树高亮
                    } else {
                        this.editFile({ path, type: 'file' });
                    }
                });
            });
            wrapper.querySelectorAll('.edit-btn').forEach(btn => {
                btn.addEventListener('click', () => this.editFile({ path: btn.dataset.path, type: 'file' }));
            });
            wrapper.querySelectorAll('.rename-btn').forEach(btn => {
                btn.addEventListener('click', () => this.renameItem(btn.dataset.path, btn.dataset.name));
            });
            wrapper.querySelectorAll('.delete-btn').forEach(btn => {
                btn.addEventListener('click', () => this.deleteItem(btn.dataset.path));
            });
        },
        async fetchTree() {
            const url = apiUrl(`/files/api/tree?rootType=${this.rootType}&path=`);
            const res = await fetch(url);
            if (res.ok) {
                const data = await res.json();
                this.renderTree(data);
            }
        },
        renderTree(node, level = 0) {
            const wrapper = document.getElementById('fileTree');
            if (!wrapper) return;
            const buildHTML = (n, lvl) => {
                const indent = lvl * 20;
                let html = `<div style="padding-left:${indent}px; margin:4px 0; cursor:pointer;" data-path="${n.path}" data-type="${n.type}">
                    ${n.type === 'directory' ? '📁' : '📄'} ${escapeHtml(n.name)}
                </div>`;
                if (n.children && n.children.length) {
                    html += `<div class="tree-children" style="display:none;">`;
                    n.children.forEach(child => html += buildHTML(child, lvl+1));
                    html += `</div>`;
                }
                return html;
            };
            wrapper.innerHTML = buildHTML(node, 0);
            wrapper.querySelectorAll('[data-path]').forEach(el => {
                el.addEventListener('click', async (e) => {
                    e.stopPropagation();
                    const path = el.dataset.path;
                    const type = el.dataset.type;
                    if (type === 'directory') {
                        this.currentPath = path;
                        await this.fetchFileList(path);
                        // 展开/折叠子节点
                        const childrenDiv = el.nextElementSibling;
                        if (childrenDiv && childrenDiv.classList.contains('tree-children')) {
                            const isOpen = childrenDiv.style.display === 'block';
                            childrenDiv.style.display = isOpen ? 'none' : 'block';
                        }
                    } else {
                        this.editFile({ path, type: 'file' });
                    }
                });
            });
        },
        async editFile(item) {
            const url = apiUrl(`/files/api/read?rootType=${this.rootType}&path=${encodeURIComponent(item.path)}`);
            const res = await fetch(url);
            if (res.ok) {
                const data = await res.json();
                this.editingFile = item;
                this.fileContent = data.content;
                const editor = document.getElementById('fileEditor');
                if (editor) editor.value = data.content;
            } else {
                alert('读取文件失败');
            }
        },
        async saveFile() {
            const content = document.getElementById('fileEditor').value;
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
                await this.fetchFileList(this.currentPath);
            } else {
                alert('保存失败');
            }
        },
        closeEditor() {
            this.editingFile = null;
            this.fileContent = '';
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
                await this.fetchFileList(this.currentPath);
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
                await this.fetchFileList(this.currentPath);
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
                await this.fetchFileList(this.currentPath);
                await this.fetchTree();
            } else {
                alert('删除失败');
            }
        },
        refresh() {
            if (this.currentPath) this.fetchFileList(this.currentPath);
            this.fetchTree();
        }
    },
    mounted() {
        this.fetchTree();
        this.fetchFileList('');
    }
};

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>]/g, function(m) {
        if (m === '&') return '&amp;';
        if (m === '<') return '&lt;';
        if (m === '>') return '&gt;';
        return m;
    });
}