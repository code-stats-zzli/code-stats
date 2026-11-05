(function () {
    'use strict';

    SysCenter.register({
        id: 'ssh',
        name: '远程 SSH',
        icon: '🖥️',
        desc: 'SSH 远程服务器管理：连接测试 / 命令执行 / 目录浏览 / 文件上传下载与编辑（密码不入库，仅内存缓存）',
        mount: mountSsh
    });

    function mountSsh(container) {
        container.innerHTML = `
            <div class="ssh-wrap">
                <aside class="ssh-side">
                    <div class="ssh-side-header">
                        <span>🖥️ 服务器</span>
                        <button id="sshAddBtn" class="btn-primary btn-sm">＋ 新增</button>
                    </div>
                    <div id="sshConnList" class="ssh-conn-list">
                        <div class="loading">加载中...</div>
                    </div>
                </aside>
                <main class="ssh-main">
                    <div id="sshEmpty" class="empty-tip">👈 选择左侧服务器，或点击「＋ 新增」</div>
                    <div id="sshWork" style="display:none;">
                        <div class="ssh-tabs">
                            <button class="ssh-tab active" data-tab="files">📁 目录浏览</button>
                            <button class="ssh-tab" data-tab="cmd">⌨️ 命令行</button>
                            <button class="ssh-tab" data-tab="editor">📝 文件编辑</button>
                        </div>

                        <div class="ssh-pane active" id="sshPaneFiles">
                            <div class="ssh-path-bar">
                                <input id="sshPathInput" placeholder="/">
                                <button id="sshGoBtn" class="btn-secondary btn-sm">▶ 前往</button>
                                <button id="sshUpBtn" class="btn-secondary btn-sm">↑ 上级</button>
                                <button id="sshRefreshBtn" class="btn-secondary btn-sm">🔄 刷新</button>
                                <button id="sshNewDirBtn" class="btn-secondary btn-sm">＋ 新建目录</button>
                                <button id="sshUploadBtn" class="btn-primary btn-sm">⬆ 上传</button>
                                <input type="file" id="sshUploadInput" style="display:none;">
                            </div>
                            <div id="sshFileList" class="ssh-file-list">
                                <div class="empty-tip">加载中...</div>
                            </div>
                        </div>

                        <div class="ssh-pane" id="sshPaneCmd">
                            <textarea id="sshCmdInput" rows="4" placeholder="输入远程命令，Ctrl+Enter 执行"></textarea>
                            <div class="ssh-cmd-actions">
                                <button id="sshRunCmdBtn" class="btn-primary btn-sm">▶ 执行</button>
                                <button id="sshClearCmdBtn" class="btn-secondary btn-sm">🗑 清空</button>
                                <span id="sshCmdMeta" style="margin-left:auto;font-size:12px;color:#94a3b8;"></span>
                            </div>
                            <pre id="sshCmdOutput" class="ssh-terminal"></pre>
                        </div>

                        <div class="ssh-pane" id="sshPaneEditor">
                            <div class="ssh-editor-bar">
                                <input id="sshEditorPath" placeholder="从「目录浏览」双击文件打开" readonly>
                                <button id="sshSaveBtn" class="btn-primary btn-sm">💾 保存</button>
                            </div>
                            <textarea id="sshEditorContent" class="ssh-editor" placeholder="双击左侧目录中的文件以加载内容进行编辑"></textarea>
                        </div>
                    </div>
                </main>
            </div>

            <div id="sshConnModal" class="ssh-modal" style="display:none;">
                <div class="ssh-modal-content">
                    <div class="ssh-modal-header">
                        <span id="sshModalTitle">新增服务器</span>
                        <span id="sshModalClose" style="cursor:pointer;font-size:18px;">✖</span>
                    </div>
                    <div class="ssh-modal-body">
                        <input type="hidden" id="sshConnId">
                        <label>名称</label>
                        <input id="sshName" type="text" placeholder="如：测试服务器">

                        <div class="ssh-row2">
                            <div>
                                <label>Host</label>
                                <input id="sshHost" type="text" placeholder="192.168.1.10">
                            </div>
                            <div>
                                <label>Port</label>
                                <input id="sshPort" type="number" value="22">
                            </div>
                        </div>

                        <label>用户名</label>
                        <input id="sshUsername" type="text" placeholder="root">

                        <label>私钥路径（可选，与密码二选一）</label>
                        <input id="sshKeyPath" type="text"
                               placeholder="C:\\Users\\me\\.ssh\\id_rsa 或 /root/.ssh/id_rsa">

                        <label>密码（可选，与私钥二选一）</label>
                        <input id="sshPassword" type="password"
                               placeholder="留空则走默认私钥 ~/.ssh/id_rsa">
                        <div style="font-size:11px;color:#94a3b8;margin-top:2px;line-height:1.4;">
                            安全说明：密码只内存持有，<b>不写盘</b>。服务重启后需要重新输入。
                            编辑已有连接时若留空，表示<b>保持原密码不变</b>。
                        </div>

                        <label>备注</label>
                        <input id="sshRemark" type="text">
                    </div>
                    <div class="ssh-modal-footer">
                        <button id="sshSaveConnBtn" class="btn-primary btn-sm">保存</button>
                        <button id="sshCancelConnBtn" class="btn-secondary btn-sm">取消</button>
                    </div>
                </div>
            </div>
        `;

        const $ = id => container.querySelector('#' + id);
        const API = (typeof window.apiUrl === 'function')
            ? window.apiUrl('/sys/api/ssh')
            : '/sys/api/ssh';

        let connections = [];
        let currentId = null;
        let currentPath = '';
        let currentOs = 'linux';
        let currentTab = 'files';

        function escapeHtml(s) {
            if (s == null) return '';
            return String(s).replace(/[&<>"]/g, m => ({
                '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
            }[m]));
        }
        function escapeAttr(s) { return escapeHtml(s); }

        function fmtSize(b) {
            if (b == null || b < 0) return '-';
            if (b < 1024) return b + ' B';
            if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
            if (b < 1024 * 1024 * 1024) return (b / 1024 / 1024).toFixed(2) + ' MB';
            return (b / 1024 / 1024 / 1024).toFixed(2) + ' GB';
        }

        async function jpost(url, body) {
            const resp = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                body: JSON.stringify(body || {})
            });
            return await resp.json();
        }

        // ==================== 连接列表 ====================

        async function loadConnections() {
            try {
                const r = await fetch(API + '/connections', { cache: 'no-store' }).then(x => x.json());
                if (!r.success) throw new Error(r.error || '加载失败');
                connections = r.data || [];
                renderConnList();
            } catch (e) {
                $('sshConnList').innerHTML =
                    '<div class="ssh-error">❌ ' + escapeHtml(e.message) + '</div>';
            }
        }

        function renderConnList() {
            if (!connections.length) {
                $('sshConnList').innerHTML =
                    '<div class="empty-tip" style="padding:20px;text-align:center;">暂无连接</div>';
                return;
            }
            $('sshConnList').innerHTML = connections.map(c => {
                const statusCls = c.lastTestOk ? 'ok' : 'unk';
                const statusDot = c.lastTestOk ? '●' : '○';
                const osText = (c.osType && c.osType !== 'auto') ? ' · ' + c.osType : '';
                return `
                <div class="ssh-conn-item ${c.id === currentId ? 'active' : ''}" data-id="${escapeAttr(c.id)}">
                    <div class="ssh-conn-name">
                        <span class="ssh-conn-status ${statusCls}">${statusDot}</span>
                        <span>${escapeHtml(c.name || c.host)}</span>
                    </div>
                    <div class="ssh-conn-meta">${escapeHtml(c.username)}@${escapeHtml(c.host)}:${c.port}${escapeHtml(osText)}</div>
                    <div class="ssh-conn-actions">
                        <button class="ssh-mini-btn" data-action="test">🔌 测试</button>
                        <button class="ssh-mini-btn" data-action="edit">✎ 编辑</button>
                        <button class="ssh-mini-btn ssh-mini-danger" data-action="delete">🗑</button>
                    </div>
                </div>`;
            }).join('');

            container.querySelectorAll('.ssh-conn-item').forEach(el => {
                el.addEventListener('click', (e) => {
                    if (e.target.closest('[data-action]')) return;
                    selectConnection(el.dataset.id);
                });
            });
            container.querySelectorAll('.ssh-conn-item [data-action]').forEach(btn => {
                btn.addEventListener('click', async (e) => {
                    e.stopPropagation();
                    const id = btn.closest('.ssh-conn-item').dataset.id;
                    const action = btn.dataset.action;
                    if (action === 'test') await testConnection(id);
                    else if (action === 'edit') openConnModal(connections.find(x => x.id === id));
                    else if (action === 'delete') await deleteConnection(id);
                });
            });
        }

        async function testConnection(id) {
            try {
                const r = await jpost(API + '/connection/test', { id });
                if (!r.success) throw new Error(r.error || '请求失败');
                const d = r.data || {};
                if (d.success) {
                    alert('✅ 连接成功\nOS: ' + (d.osType || '未知'));
                } else {
                    alert('❌ 连接失败：' + (d.error || '未知错误'));
                }
                await loadConnections();
                if (currentId === id) {
                    const cur = connections.find(x => x.id === id);
                    if (cur) currentOs = (cur.osType === 'windows') ? 'windows' : 'linux';
                }
            } catch (e) {
                alert('测试失败：' + e.message);
            }
        }

        async function deleteConnection(id) {
            if (!confirm('确定删除该连接？')) return;
            try {
                const r = await jpost(API + '/connection/delete', { id });
                if (!r.success) throw new Error(r.error || '删除失败');
                if (currentId === id) selectConnection(null);
                await loadConnections();
            } catch (e) {
                alert('删除失败：' + e.message);
            }
        }

        // ==================== 弹窗 ====================

        function openConnModal(c) {
            $('sshModalTitle').textContent = c ? '编辑服务器' : '新增服务器';
            $('sshConnId').value = c ? c.id : '';
            $('sshName').value = c ? (c.name || '') : '';
            $('sshHost').value = c ? (c.host || '') : '';
            $('sshPort').value = c ? c.port : 22;
            $('sshUsername').value = c ? (c.username || '') : '';
            $('sshKeyPath').value = c ? (c.keyPath || '') : '';
            $('sshPassword').value = '';
            $('sshPassword').placeholder = c
                ? '留空表示不修改当前密码'
                : '留空则走默认私钥 ~/.ssh/id_rsa';
            $('sshRemark').value = c ? (c.remark || '') : '';
            $('sshConnModal').style.display = 'flex';
        }

        function closeConnModal() {
            $('sshConnModal').style.display = 'none';
            $('sshPassword').value = '';
        }

        async function saveConnection() {
            const body = {
                id: $('sshConnId').value || null,
                name: $('sshName').value.trim(),
                host: $('sshHost').value.trim(),
                port: parseInt($('sshPort').value, 10) || 22,
                username: $('sshUsername').value.trim(),
                keyPath: $('sshKeyPath').value.trim(),
                password: $('sshPassword').value,
                remark: $('sshRemark').value.trim(),
                osType: 'auto'
            };
            if (!body.name) { alert('请填写名称'); return; }
            if (!body.host) { alert('请填写 Host'); return; }
            if (!body.username) { alert('请填写用户名'); return; }
            if (!body.id && !body.keyPath && !body.password) {
                alert('请填写「密码」或「私钥路径」其中一项');
                return;
            }
            try {
                const r = await jpost(API + '/connection/save', body);
                if (!r.success) throw new Error(r.error || '保存失败');
                closeConnModal();
                await loadConnections();
                if (r.data && r.data.id) selectConnection(r.data.id);
            } catch (e) {
                alert('保存失败：' + e.message);
            }
        }

        // ==================== 选中连接 ====================

        function selectConnection(id) {
            currentId = id;
            const c = connections.find(x => x.id === id);
            if (!c) {
                $('sshEmpty').style.display = 'block';
                $('sshWork').style.display = 'none';
                renderConnList();
                return;
            }
            currentOs = (c.osType === 'windows') ? 'windows' : 'linux';
            currentPath = currentOs === 'windows' ? 'C:\\' : '/';
            $('sshPathInput').value = currentPath;
            $('sshEmpty').style.display = 'none';
            $('sshWork').style.display = 'flex';
            renderConnList();
            clearCommandOutput();
            clearEditor();
            switchTab('files');
            refreshFileList();
        }

        // ==================== Tab ====================

        function switchTab(tabId) {
            currentTab = tabId;
            container.querySelectorAll('.ssh-tab').forEach(t =>
                t.classList.toggle('active', t.dataset.tab === tabId));
            const map = { files: 'sshPaneFiles', cmd: 'sshPaneCmd', editor: 'sshPaneEditor' };
            Object.keys(map).forEach(k => {
                const pane = $(map[k]);
                if (pane) pane.classList.toggle('active', k === tabId);
            });
        }
        container.querySelectorAll('.ssh-tab').forEach(t => {
            t.addEventListener('click', () => switchTab(t.dataset.tab));
        });

        // ==================== 文件列表 ====================

        async function refreshFileList() {
            if (!currentId) return;
            $('sshFileList').innerHTML = '<div class="loading">加载中...</div>';
            try {
                const r = await jpost(API + '/list', { id: currentId, path: currentPath });
                if (!r.success) throw new Error(r.error || '加载失败');
                renderFileList(r.data || []);
            } catch (e) {
                $('sshFileList').innerHTML =
                    '<div class="ssh-error">❌ ' + escapeHtml(e.message) + '</div>';
            }
        }

        function renderFileList(files) {
            if (!files.length) {
                $('sshFileList').innerHTML = '<div class="empty-tip">目录为空</div>';
                return;
            }
            $('sshFileList').innerHTML = files.map(f => {
                const isDir = f.directory;
                return `
                <div class="ssh-file-row ${isDir ? 'is-dir' : ''}" data-path="${escapeAttr(f.path)}" data-dir="${isDir}">
                    <span class="ssh-file-icon">${isDir ? '📁' : '📄'}</span>
                    <span class="ssh-file-name" title="${escapeAttr(f.path)}">${escapeHtml(f.name)}</span>
                    <span class="ssh-file-meta">${isDir ? '' : fmtSize(f.size)}</span>
                    <span class="ssh-file-time">${escapeHtml(f.modified || '')}</span>
                    <span class="ssh-file-actions">
                        ${isDir ? '' : `
                            <button class="ssh-mini-btn" data-action="edit" title="编辑">✎</button>
                            <button class="ssh-mini-btn" data-action="download" title="下载">⬇</button>`}
                        <button class="ssh-mini-btn" data-action="rename" title="重命名">✏️</button>
                        <button class="ssh-mini-btn ssh-mini-danger" data-action="delete" title="删除">🗑</button>
                    </span>
                </div>`;
            }).join('');

            container.querySelectorAll('.ssh-file-row').forEach(row => {
                row.addEventListener('dblclick', async (e) => {
                    if (e.target.closest('[data-action]')) return;
                    const isDir = row.dataset.dir === 'true';
                    const p = row.dataset.path;
                    if (isDir) {
                        currentPath = p;
                        $('sshPathInput').value = currentPath;
                        await refreshFileList();
                    } else {
                        await openInEditor(p);
                    }
                });
            });
            container.querySelectorAll('.ssh-file-row [data-action]').forEach(btn => {
                btn.addEventListener('click', async (e) => {
                    e.stopPropagation();
                    const row = btn.closest('.ssh-file-row');
                    const p = row.dataset.path;
                    const action = btn.dataset.action;
                    if (action === 'edit') await openInEditor(p);
                    else if (action === 'download') downloadFile(p);
                    else if (action === 'rename') await renameFile(p);
                    else if (action === 'delete') await deleteFile(p);
                });
            });
        }

        function downloadFile(path) {
            const url = API + '/download?id=' + encodeURIComponent(currentId)
                + '&path=' + encodeURIComponent(path);
            window.open(url, '_blank');
        }

        async function renameFile(p) {
            const sep = currentOs === 'windows' ? '\\' : '/';
            const idx = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
            const parent = idx > 0 ? p.substring(0, idx) : '';
            const oldName = p.substring(idx + 1);
            const newName = prompt('新名称', oldName);
            if (!newName || newName === oldName) return;
            if (newName.includes('/') || newName.includes('\\')) {
                alert('名称不能包含斜杠');
                return;
            }
            const to = parent ? (parent + sep + newName) : newName;
            try {
                const r = await jpost(API + '/rename', { id: currentId, from: p, to: to });
                if (!r.success) throw new Error(r.error || '重命名失败');
                refreshFileList();
            } catch (e) {
                alert('重命名失败：' + e.message);
            }
        }

        async function deleteFile(p) {
            if (!confirm('确定删除 ' + p + ' ？')) return;
            try {
                const r = await jpost(API + '/delete', { id: currentId, path: p });
                if (!r.success) throw new Error(r.error || '删除失败');
                refreshFileList();
            } catch (e) {
                alert('删除失败：' + e.message);
            }
        }

        async function newDir() {
            const name = prompt('新目录名');
            if (!name || !name.trim()) return;
            try {
                const r = await jpost(API + '/mkdir', {
                    id: currentId, parent: currentPath, name: name.trim()
                });
                if (!r.success) throw new Error(r.error || '创建失败');
                refreshFileList();
            } catch (e) {
                alert('创建失败：' + e.message);
            }
        }

        async function uploadFile(file) {
            if (!file || !currentId) return;
            const fd = new FormData();
            fd.append('file', file);
            fd.append('id', currentId);
            fd.append('path', currentPath);
            try {
                const resp = await fetch(API + '/upload', { method: 'POST', body: fd });
                const r = await resp.json();
                if (!r.success) throw new Error(r.error || '上传失败');
                await refreshFileList();
            } catch (e) {
                alert('上传失败：' + e.message);
            }
        }

        // ==================== 命令行 ====================

        function clearCommandOutput() {
            $('sshCmdOutput').textContent = '';
            $('sshCmdMeta').textContent = '';
        }

        async function runCommand() {
            if (!currentId) return;
            const cmd = $('sshCmdInput').value;
            if (!cmd || !cmd.trim()) { alert('请输入命令'); return; }
            $('sshCmdOutput').textContent = '⏳ 执行中...\n';
            $('sshCmdMeta').textContent = '';
            try {
                const r = await jpost(API + '/exec', { id: currentId, cmd: cmd });
                if (!r.success) {
                    $('sshCmdOutput').textContent = '❌ ' + (r.error || '未知错误');
                    return;
                }
                const d = r.data || {};
                let out = '';
                if (d.stdout) out += d.stdout;
                if (d.stderr) out += (out && !out.endsWith('\n') ? '\n' : '') + d.stderr;
                if (!out) out = '(无输出)';
                $('sshCmdOutput').textContent = out;
                $('sshCmdMeta').textContent = 'exitCode=' + d.exitCode
                    + ' · 耗时=' + d.durationMs + 'ms'
                    + (d.timeout ? ' · ⏱超时' : '');
            } catch (e) {
                $('sshCmdOutput').textContent = '❌ ' + e.message;
            }
        }

        // ==================== 编辑器 ====================

        function clearEditor() {
            $('sshEditorPath').value = '';
            $('sshEditorContent').value = '';
        }

        async function openInEditor(p) {
            switchTab('editor');
            $('sshEditorPath').value = p;
            $('sshEditorContent').value = '加载中...';
            try {
                const r = await jpost(API + '/read', { id: currentId, path: p });
                if (!r.success) throw new Error(r.error || '读取失败');
                $('sshEditorContent').value = (r.data && r.data.content) || '';
            } catch (e) {
                $('sshEditorContent').value = '❌ ' + e.message;
            }
        }

        async function saveEditor() {
            const p = $('sshEditorPath').value;
            if (!p) { alert('请先从目录中选择文件'); return; }
            const content = $('sshEditorContent').value || '';
            const bytes = new TextEncoder().encode(content);
            let bin = '';
            for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
            const b64 = btoa(bin);
            try {
                const r = await jpost(API + '/write', { id: currentId, path: p, content: b64 });
                if (!r.success) throw new Error(r.error || '保存失败');
                alert('✅ 已保存');
            } catch (e) {
                alert('保存失败：' + e.message);
            }
        }

        // ==================== 事件绑定 ====================

        $('sshAddBtn').addEventListener('click', () => openConnModal(null));
        $('sshModalClose').addEventListener('click', closeConnModal);
        $('sshCancelConnBtn').addEventListener('click', closeConnModal);
        $('sshSaveConnBtn').addEventListener('click', saveConnection);
        $('sshConnModal').addEventListener('click', (e) => {
            if (e.target === $('sshConnModal')) closeConnModal();
        });

        $('sshGoBtn').addEventListener('click', () => {
            const v = $('sshPathInput').value.trim();
            if (v) { currentPath = v; refreshFileList(); }
        });
        $('sshPathInput').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') $('sshGoBtn').click();
        });
        $('sshUpBtn').addEventListener('click', () => {
            if (!currentPath) return;
            let p = currentPath;
            if (p.length > 1 && (p.endsWith('/') || p.endsWith('\\'))) {
                p = p.substring(0, p.length - 1);
            }
            const idx = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
            if (idx <= 0) {
                currentPath = currentOs === 'windows' ? 'C:\\' : '/';
            } else {
                currentPath = p.substring(0, idx);
                if (!currentPath && currentOs === 'windows') currentPath = 'C:';
            }
            $('sshPathInput').value = currentPath;
            refreshFileList();
        });
        $('sshRefreshBtn').addEventListener('click', refreshFileList);
        $('sshNewDirBtn').addEventListener('click', newDir);
        $('sshUploadBtn').addEventListener('click', () => $('sshUploadInput').click());
        $('sshUploadInput').addEventListener('change', (e) => {
            const f = e.target.files && e.target.files[0];
            if (f) uploadFile(f);
            e.target.value = '';
        });

        $('sshRunCmdBtn').addEventListener('click', runCommand);
        $('sshClearCmdBtn').addEventListener('click', clearCommandOutput);
        $('sshCmdInput').addEventListener('keydown', (e) => {
            if (e.ctrlKey && e.key === 'Enter') { e.preventDefault(); runCommand(); }
        });

        $('sshSaveBtn').addEventListener('click', saveEditor);

        // ==================== 页面关闭清理密码缓存 ====================

        function closeCurrentSessionBeacon() {
            if (!currentId) return;
            const url = API + '/connection/close';
            const body = JSON.stringify({ id: currentId });
            try {
                if (navigator.sendBeacon) {
                    const blob = new Blob([body], { type: 'application/json;charset=UTF-8' });
                    const ok = navigator.sendBeacon(url, blob);
                    if (ok) return;
                }
            } catch (e) {
                // fallback
            }
            try {
                fetch(url, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                    body: body,
                    keepalive: true
                });
            } catch (e) {
                // ignore
            }
        }

        window.addEventListener('beforeunload', closeCurrentSessionBeacon);
        window.addEventListener('pagehide', closeCurrentSessionBeacon);

        // 模块卸载时解绑
        const observer = new MutationObserver(() => {
            if (!document.body.contains(container)) {
                window.removeEventListener('beforeunload', closeCurrentSessionBeacon);
                window.removeEventListener('pagehide', closeCurrentSessionBeacon);
                observer.disconnect();
            }
        });
        observer.observe(document.body, { childList: true, subtree: true });

        // ==================== 启动 ====================

        loadConnections();
    }
})();