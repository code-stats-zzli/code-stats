/**
 * 系统管理 · 远程 SSH / SCP 模块。
 *
 * 支持：连接管理（增删改查 + 测试） / 目录浏览 / 命令执行 / 文件编辑 / 上传下载 / 新建目录 / 重命名 / 删除。
 * 挂载方式与 log.js、cluster.js 完全一致，通过 SysCenter.register 注册。
 */
(function () {
    'use strict';

    SysCenter.register({
        id: 'ssh',
        name: '远程 SSH',
        icon: '🖥️',
        desc: 'SSH / SCP 远程服务器管理：连接测试 / 命令执行 / 目录浏览 / 文件上传下载与编辑',
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

                        <!-- Tab 1: 目录浏览 -->
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

                        <!-- Tab 2: 命令行 -->
                        <div class="ssh-pane" id="sshPaneCmd">
                            <textarea id="sshCmdInput" rows="4" placeholder="输入远程命令，Ctrl+Enter 执行"></textarea>
                            <div class="ssh-cmd-actions">
                                <button id="sshRunCmdBtn" class="btn-primary btn-sm">▶ 执行</button>
                                <button id="sshClearCmdBtn" class="btn-secondary btn-sm">🗑 清空</button>
                                <span id="sshCmdMeta" style="margin-left:auto;font-size:12px;color:#94a3b8;"></span>
                            </div>
                            <pre id="sshCmdOutput" class="ssh-terminal"></pre>
                        </div>

                        <!-- Tab 3: 文件编辑 -->
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

            <!-- 连接编辑弹窗 -->
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
                        <label>私钥路径（可选，留空走默认 ~/.ssh）</label>
                        <input id="sshKeyPath" type="text" placeholder="C:\\Users\\me\\.ssh\\id_rsa 或 /root/.ssh/id_rsa">
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

        // ==================== 工具 ====================

        function escapeHtml(s) {
            if (s == null) return '';
            return String(s).replace(/[&<>"]/g, m => ({
                '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
            }[m]));
        }
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
                $('sshConnList').innerHTML = '<div class="empty-tip" style="padding:20px;text-align:center;">暂无连接</div>';
                return;
            }
            $('sshConnList').innerHTML = connections.map(c => `
                <div class="ssh-conn-item ${c.id === currentId ? 'active' : ''}" data-id="${escapeHtml(c.id)}">
                    <div class="ssh-conn-name">
                        <span class="ssh-conn-status ${c.lastTestOk ? 'ok' : 'unk'}">${c.lastTestOk ? '●' : '○'}</span>
                        <span>${escapeHtml(c.name || c.host)}</span>
                    </div>
                    <div class="ssh-conn-meta">${escapeHtml(c.username)}@${escapeHtml(c.host)}:${c.port}${c.osType && c.osType !== 'auto' ? ' · ' + c.osType : ''}</div>
                    <div class="ssh-conn-actions">
                        <button class="ssh-mini-btn" data-action="test">🔌 测试</button>
                        <button class="ssh-mini-btn" data-action="edit">✎ 编辑</button>
                        <button class="ssh-mini-btn ssh-mini-danger" data-action="delete">🗑</button>
                    </div>
                </div>
            `).join('');

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
                if (d.success) alert('✅ 连接成功\nOS: ' + (d.osType || '未知'));
                else alert('❌ 连接失败：' + (d.error || '未知错误'));
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
            $('sshRemark').value = c ? (c.remark || '') : '';
            $('sshConnModal').style.display = 'flex';
        }
        function closeConnModal() { $('sshConnModal').style.display = 'none'; }

        async function saveConnection() {
            const body = {
                id: $('sshConnId').value || null,
                name: $('sshName').value.trim(),
                host: $('sshHost').value.trim(),
                port: parseInt($('sshPort').value) || 22,
                username: $('sshUsername').value.trim(),
                keyPath: $('sshKeyPath').value.trim(),
                remark: $('sshRemark').value.trim(),
                osType: 'auto'
            };
            if (!body.name) { alert('请填写名称'); return; }
            if (!body.host) { alert('请填写 Host'); return; }
            if (!body.username) { alert('请填写用户名'); return; }
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

        // ==================== 选择连接 ====================

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
            renderConn