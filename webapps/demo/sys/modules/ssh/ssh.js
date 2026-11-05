(function () {
    'use strict';

    SysCenter.register({
        id: 'ssh',
        name: '远程 SSH',
        icon: '🖥️',
        desc: 'SSH 远程服务器管理：连接测试 / 命令执行 / 目录浏览 / 文件上传下载与编辑 / 常用命令文档（密码不入库，仅内存缓存）',
        mount: mountSsh
    });

    // ============================================================
    // 文档步骤数据（模块级常量，避免在 mountSsh 内重复构造）
    // ============================================================
    const DOC_STEPS = [
        {
            title: '打开终端 / 建立 SSH 连接',
            icon: '🖥️',
            blocks: [
                {
                    title: 'Windows · 打开 PowerShell',
                    desc: '任选一种方式',
                    commands: [
                        { label: '直接启动',        cmd: 'powershell' },
                        { label: '推荐（跳过配置文件加载）', cmd: 'powershell -NoProfile' },
                        { label: '快捷键',          cmd: 'Win + X → Windows PowerShell，或 Win + R 输入 powershell' }
                    ]
                },
                {
                    title: 'Linux / macOS · 打开 Shell',
                    desc: '',
                    commands: [
                        { label: 'Bash',  cmd: 'bash' },
                        { label: 'sh',    cmd: 'sh' }
                    ]
                },
                {
                    title: 'SSH 连接目标主机',
                    desc: '默认端口 22；非标准端口用 -p 指定',
                    commands: [
                        { label: '默认端口',    cmd: 'ssh user@host' },
                        { label: '自定义端口',  cmd: 'ssh user@host -p 2222' },
                        { label: '指定私钥',    cmd: 'ssh -i /path/to/private_key user@host' }
                    ]
                }
            ]
        },
        {
            title: '探测 SSH 连接服务是否可用',
            icon: '🔌',
            blocks: [
                {
                    title: 'Windows · PowerShell（推荐）',
                    desc: 'Test-NetConnection 是 PowerShell 内置能力，不依赖 telnet',
                    commands: [
                        { label: '探测 22 端口',            cmd: 'Test-NetConnection -ComputerName <host> -Port 22' },
                        { label: '静默模式（只返回 True/False）', cmd: 'Test-NetConnection -ComputerName 192.168.1.10 -Port 22 -InformationLevel Quiet' }
                    ]
                },
                {
                    title: 'Windows · CMD',
                    desc: 'CMD 不支持内建 TCP 探测，可调用 PowerShell 或 telnet',
                    commands: [
                        { label: '调用 PowerShell 探测', cmd: 'powershell "Test-NetConnection -ComputerName <host> -Port 22"' },
                        { label: 'telnet（需先启用 telnet 客户端）', cmd: 'telnet <host> 22' }
                    ]
                },
                {
                    title: 'Linux / macOS',
                    desc: '',
                    commands: [
                        { label: 'nc 探测',                cmd: 'nc -zv <host> 22' },
                        { label: 'bash /dev/tcp 探测',      cmd: 'timeout 3 bash -c "</dev/tcp/<host>/22" && echo OK' },
                        { label: 'ssh 详细日志',           cmd: 'ssh -v user@host' }
                    ]
                },
                {
                    title: '查看本机 SSH 端口监听状态',
                    desc: '确认目标机 sshd 是否正在监听',
                    commands: [
                        { label: 'Windows', cmd: 'netstat -ano | findstr :22' },
                        { label: 'Linux',   cmd: 'ss -tlnp | grep :22' },
                        { label: 'Linux（旧版）', cmd: 'netstat -tlnp | grep :22' }
                    ]
                }
            ]
        },
        {
            title: '启动 / 关闭服务',
            icon: '⚙️',
            blocks: [
                {
                    title: 'Windows · PowerShell（示例服务 MySQL80）',
                    desc: '用 Get-Service 确认服务名，再 Start/Stop',
                    commands: [
                        { label: '查看状态',    cmd: 'Get-Service MySQL80' },
                        { label: '启动',        cmd: 'Start-Service MySQL80' },
                        { label: '停止',        cmd: 'Stop-Service MySQL80' },
                        { label: '重启',        cmd: 'Restart-Service MySQL80' },
                        { label: '设为自动启动', cmd: 'Set-Service MySQL80 -StartupType Automatic' }
                    ]
                },
                {
                    title: 'Windows · CMD',
                    desc: 'sc 与 net 命令在旧系统上更通用',
                    commands: [
                        { label: '查询状态', cmd: 'sc query MySQL80' },
                        { label: '启动',    cmd: 'sc start MySQL80' },
                        { label: '停止',    cmd: 'sc stop MySQL80' },
                        { label: '启动（net）', cmd: 'net start MySQL80' },
                        { label: '停止（net）', cmd: 'net stop MySQL80' }
                    ]
                },
                {
                    title: 'Linux · systemctl（systemd 系统）',
                    desc: '示例服务 nginx',
                    commands: [
                        { label: '查看状态', cmd: 'systemctl status nginx' },
                        { label: '启动',    cmd: 'systemctl start nginx' },
                        { label: '停止',    cmd: 'systemctl stop nginx' },
                        { label: '重启',    cmd: 'systemctl restart nginx' },
                        { label: '开机自启', cmd: 'systemctl enable nginx' }
                    ]
                },
                {
                    title: 'Linux · service（SysV / 兼容层）',
                    desc: '',
                    commands: [
                        { label: '查看状态', cmd: 'service nginx status' },
                        { label: '启动',    cmd: 'service nginx start' },
                        { label: '停止',    cmd: 'service nginx stop' }
                    ]
                },
                {
                    title: 'sshd 服务本身',
                    desc: '如果 SSH 不通，优先确认 sshd 状态',
                    commands: [
                        { label: 'Windows OpenSSH', cmd: 'Get-Service sshd' },
                        { label: 'Windows 启动',   cmd: 'Start-Service sshd' },
                        { label: 'Linux 状态',     cmd: 'systemctl status sshd' },
                        { label: 'Linux 启动',     cmd: 'systemctl start sshd' }
                    ]
                }
            ]
        },
        {
            title: '端口探测',
            icon: '🔍',
            blocks: [
                {
                    title: 'Windows · PowerShell',
                    desc: '',
                    commands: [
                        { label: '探测端口',           cmd: 'Test-NetConnection -ComputerName <host> -Port <port>' },
                        { label: '静默模式',           cmd: 'Test-NetConnection -ComputerName 192.168.1.10 -Port 8080 -InformationLevel Quiet' }
                    ]
                },
                {
                    title: 'Linux',
                    desc: '',
                    commands: [
                        { label: 'nc 探测',       cmd: 'nc -zv <host> <port>' },
                        { label: '查看监听端口', cmd: 'ss -tlnp | grep :<port>' },
                        { label: '查占用进程',   cmd: 'lsof -i :<port>' }
                    ]
                },
                {
                    title: '跨平台 · curl',
                    desc: 'curl 支持 telnet:// 协议探测 TCP 端口',
                    commands: [
                        { label: 'telnet 探测', cmd: 'curl -v telnet://<host>:<port>' }
                    ]
                }
            ]
        }
    ];

    // ============================================================
    // 主挂载函数
    // ============================================================
    function mountSsh(container) {
        injectDocStyle();

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
                            <button class="ssh-tab" data-tab="commands">📖 常用命令</button>
                        </div>

                        <!-- ============ Tab 1: 目录浏览 ============ -->
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

                        <!-- ============ Tab 2: 命令行 ============ -->
                        <div class="ssh-pane" id="sshPaneCmd">
                            <textarea id="sshCmdInput" rows="4"
                                      placeholder="输入远程命令，Ctrl+Enter 执行"></textarea>
                            <div class="ssh-cmd-actions">
                                <button id="sshRunCmdBtn" class="btn-primary btn-sm">▶ 执行</button>
                                <button id="sshClearCmdBtn" class="btn-secondary btn-sm">🗑 清空</button>
                                <span id="sshCmdMeta" style="margin-left:auto;font-size:12px;color:#94a3b8;"></span>
                            </div>
                            <pre id="sshCmdOutput" class="ssh-terminal"></pre>
                        </div>

                        <!-- ============ Tab 3: 文件编辑 ============ -->
                        <div class="ssh-pane" id="sshPaneEditor">
                            <div class="ssh-editor-bar">
                                <input id="sshEditorPath"
                                       placeholder="从「目录浏览」双击文件打开" readonly>
                                <button id="sshSaveBtn" class="btn-primary btn-sm">💾 保存</button>
                            </div>
                            <textarea id="sshEditorContent" class="ssh-editor"
                                      placeholder="双击左侧目录中的文件以加载内容进行编辑"></textarea>
                        </div>

                        <!-- ============ Tab 4: 常用命令文档（原「服务/端口」） ============ -->
                        <div class="ssh-pane" id="sshPaneCommands">
                            <div class="ssh-doc-wrap">
                                <div class="ssh-doc-intro">
                                    📖 本页为只读文档，提供打开终端、探测 SSH 服务、启停服务、端口探测的常见命令。
                                    点击每条命令右侧的 📋 按钮即可复制。
                                </div>
                                ${renderDocSteps()}
                            </div>
                        </div>
                    </div>
                </main>
            </div>

            <!-- ============ 连接弹窗（保持不变） ============ -->
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

        // ============================================================
        // 引用
        // ============================================================
        const $ = id => container.querySelector('#' + id);
        const API = (typeof window.apiUrl === 'function')
            ? window.apiUrl('/sys/api/ssh')
            : '/sys/api/ssh';

        let connections = [];
        let currentId = null;
        let currentPath = '';
        let currentOs = 'linux';

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

        // ============================================================
        // 文档复制按钮（事件委托，一次绑定）
        // ============================================================
        container.addEventListener('click', (e) => {
            const btn = e.target.closest('.ssh-doc-copy');
            if (!btn) return;
            const cmd = btn.dataset.cmd || '';
            if (!cmd) return;
            const restore = () => { btn.textContent = '📋'; };
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(cmd)
                    .then(() => { btn.textContent = '✅'; setTimeout(restore, 1200); })
                    .catch(() => { fallbackCopy(cmd); });
            } else {
                fallbackCopy(cmd);
            }
            function fallbackCopy(text) {
                const ta = document.createElement('textarea');
                ta.value = text;
                ta.style.cssText = 'position:fixed;left:-9999px;top:0;';
                document.body.appendChild(ta);
                ta.select();
                try {
                    document.execCommand('copy');
                    btn.textContent = '✅';
                    setTimeout(restore, 1200);
                } catch (_) {
                    alert('复制失败，请手动选择文本复制');
                } finally {
                    document.body.removeChild(ta);
                }
            }
        });

        // ============================================================
        // 连接列表
        // ============================================================
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
                <div class="ssh-conn-item ${c.id === currentId ? 'active' : ''}"
                     data-id="${escapeAttr(c.id)}">
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
                if (d.success) alert('✅ 连接成功\nOS: ' + (d.osType || '未知'));
                else alert('❌ 连接失败：' + (d.error || '未知错误'));
                await loadConnections();
                if (currentId === id) {
                    const cur = connections.find(x => x.id === id);
                    if (cur) currentOs = (cur.osType === 'windows') ? 'windows' : 'linux';
                }
            } catch (e) { alert('测试失败：' + e.message); }
        }

        async function deleteConnection(id) {
            if (!confirm('确定删除该连接？')) return;
            try {
                const r = await jpost(API + '/connection/delete', { id });
                if (!r.success) throw new Error(r.error || '删除失败');
                if (currentId === id) selectConnection(null);
                await loadConnections();
            } catch (e) { alert('删除失败：' + e.message); }
        }

        // ============================================================
        // 连接弹窗
        // ============================================================
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
            } catch (e) { alert('保存失败：' + e.message); }
        }

        // ============================================================
        // 选中连接
        // ============================================================
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
            $('sshCmdOutput').textContent = '';
            $('sshCmdMeta').textContent = '';
            $('sshEditorPath').value = '';
            $('sshEditorContent').value = '';
            switchTab('files');
            refreshFileList();
        }

        // ============================================================
        // Tab 切换
        // ============================================================
        function switchTab(tabId) {
            container.querySelectorAll('.ssh-tab').forEach(t =>
                t.classList.toggle('active', t.dataset.tab === tabId));
            const map = {
                files:    'sshPaneFiles',
                cmd:      'sshPaneCmd',
                editor:   'sshPaneEditor',
                commands: 'sshPaneCommands'
            };
            Object.keys(map).forEach(k => {
                const pane = $(map[k]);
                if (pane) pane.classList.toggle('active', k === tabId);
            });
        }
        container.querySelectorAll('.ssh-tab').forEach(t => {
            t.addEventListener('click', () => switchTab(t.dataset.tab));
        });

        // ============================================================
        // 文件列表
        // ============================================================
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
                <div class="ssh-file-row ${isDir ? 'is-dir' : ''}"
                     data-path="${escapeAttr(f.path)}" data-dir="${isDir}">
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
                alert('名称不能包含斜杠'); return;
            }
            const to = parent ? (parent + sep + newName) : newName;
            try {
                const r = await jpost(API + '/rename', { id: currentId, from: p, to: to });
                if (!r.success) throw new Error(r.error || '重命名失败');
                refreshFileList();
            } catch (e) { alert('重命名失败：' + e.message); }
        }

        async function deleteFile(p) {
            if (!confirm('确定删除 ' + p + ' ？')) return;
            try {
                const r = await jpost(API + '/delete', { id: currentId, path: p });
                if (!r.success) throw new Error(r.error || '删除失败');
                refreshFileList();
            } catch (e) { alert('删除失败：' + e.message); }
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
            } catch (e) { alert('创建失败：' + e.message); }
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
            } catch (e) { alert('上传失败：' + e.message); }
        }

        // ============================================================
        // 命令行
        // ============================================================
        async function runCommand() {
            if (!currentId) return;
            const cmd = $('sshCmdInput').value;
            if (!cmd || !cmd.trim()) { alert('请输入命令'); return; }
            const out = $('sshCmdOutput');
            out.textContent = '⏳ 执行中...\n';
            $('sshCmdMeta').textContent = '';
            const t0 = Date.now();
            try {
                const r = await jpost(API + '/exec', { id: currentId, cmd: cmd });
                if (!r.success) throw new Error(r.error || '执行失败');
                const d = r.data || {};
                let text = '';
                if (d.stdout) text += d.stdout;
                if (d.stderr) text += (text ? '\n' : '') + '[stderr]\n' + d.stderr;
                if (!text) text = '(无输出)';
                if (d.timeout) text += '\n[超时]';
                out.textContent = text;
                $('sshCmdMeta').textContent =
                    `exit=${d.exitCode} · ${d.durationMs}ms · ${Date.now() - t0}ms`;
            } catch (e) {
                out.textContent = '❌ ' + e.message;
            }
        }

        // ============================================================
        // 文件编辑
        // ============================================================
        async function openInEditor(p) {
            if (!currentId) return;
            $('sshEditorPath').value = p;
            $('sshEditorContent').value = '加载中...';
            switchTab('editor');
            try {
                const r = await jpost(API + '/read', { id: currentId, path: p });
                if (!r.success) throw new Error(r.error || '读取失败');
                $('sshEditorContent').value = (r.data && r.data.content) || '';
            } catch (e) {
                $('sshEditorContent').value = '❌ 读取失败：' + e.message;
            }
        }
        async function saveEditor() {
            const p = $('sshEditorPath').value;
            if (!p) { alert('没有打开的文件'); return; }
            if (!currentId) return;
            const content = $('sshEditorContent').value;
            const b64 = btoa(unescape(encodeURIComponent(content)));
            try {
                const r = await jpost(API + '/write', { id: currentId, path: p, content: b64 });
                if (!r.success) throw new Error(r.error || '保存失败');
                alert('✅ 已保存');
            } catch (e) { alert('保存失败：' + e.message); }
        }

        // ============================================================
        // 事件绑定
        // ============================================================
        $('sshAddBtn').addEventListener('click', () => openConnModal(null));
        $('sshModalClose').addEventListener('click', closeConnModal);
        $('sshCancelConnBtn').addEventListener('click', closeConnModal);
        $('sshSaveConnBtn').addEventListener('click', saveConnection);
        $('sshConnModal').addEventListener('click', (e) => {
            if (e.target === $('sshConnModal')) closeConnModal();
        });

        $('sshGoBtn').addEventListener('click', async () => {
            currentPath = $('sshPathInput').value.trim();
            await refreshFileList();
        });
        $('sshUpBtn').addEventListener('click', async () => {
            if (!currentPath) return;
            const sep = currentOs === 'windows' ? '\\' : '/';
            let p = currentPath.replace(/[\\/]+$/, '');
            const idx = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
            if (idx > 0) {
                p = p.substring(0, idx);
                if (currentOs === 'windows' && /^[A-Za-z]:$/.test(p)) p += '\\';
            } else {
                p = currentOs === 'windows' ? 'C:\\' : '/';
            }
            currentPath = p;
            $('sshPathInput').value = currentPath;
            await refreshFileList();
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
        $('sshClearCmdBtn').addEventListener('click', () => {
            $('sshCmdInput').value = '';
            $('sshCmdOutput').textContent = '';
            $('sshCmdMeta').textContent = '';
        });
        $('sshCmdInput').addEventListener('keydown', (e) => {
            if (e.ctrlKey && e.key === 'Enter') {
                e.preventDefault();
                runCommand();
            }
        });

        $('sshSaveBtn').addEventListener('click', saveEditor);

        // ============================================================
        // 启动
        // ============================================================
        loadConnections();
    }

    // ============================================================
    // 文档步骤渲染
    // ============================================================
    function renderDocSteps() {
        return DOC_STEPS.map((step, si) => `
            <div class="ssh-doc-step">
                <div class="ssh-doc-step-header">
                    <span class="ssh-doc-step-num">${si + 1}</span>
                    <span class="ssh-doc-step-title">${step.icon} ${step.title}</span>
                </div>
                ${step.blocks.map(b => `
                    <div class="ssh-doc-block">
                        <div class="ssh-doc-block-title">${b.title}</div>
                        ${b.desc ? `<div class="ssh-doc-block-desc">${b.desc}</div>` : ''}
                        ${b.commands.map(c => `
                            <div class="ssh-doc-cmd">
                                ${c.label ? `<span class="ssh-doc-cmd-label">${c.label}</span>` : ''}
                                <code class="ssh-doc-cmd-code">${escapeHtmlGlobal(c.cmd)}</code>
                                <button class="ssh-doc-copy" data-cmd="${escapeAttrGlobal(c.cmd)}"
                                        title="复制该命令">📋</button>
                            </div>
                        `).join('')}
                    </div>
                `).join('')}
            </div>
        `).join('');
    }

    function escapeHtmlGlobal(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"]/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
        }[m]));
    }
    function escapeAttrGlobal(s) { return escapeHtmlGlobal(s); }

    // ============================================================
    // 样式注入（只在第一次挂载时注入）
    // ============================================================
    function injectDocStyle() {
        if (document.getElementById('ssh-doc-style')) return;
        const style = document.createElement('style');
        style.id = 'ssh-doc-style';
        style.textContent = `
            .ssh-doc-wrap {
                flex: 1;
                overflow-y: auto;
                padding: 14px 18px 20px 18px;
                min-height: 0;
            }
            .ssh-doc-intro {
                font-size: 12.5px;
                color: var(--text-muted);
                background: rgba(0, 102, 255, 0.06);
                border-left: 3px solid var(--accent-primary);
                padding: 10px 14px;
                border-radius: 8px;
                margin-bottom: 16px;
                line-height: 1.7;
            }
            .ssh-doc-step {
                margin-bottom: 20px;
                background: rgba(255, 255, 255, 0.55);
                border: 1px solid rgba(0, 102, 255, 0.15);
                border-radius: 12px;
                overflow: hidden;
            }
            .ssh-doc-step-header {
                display: flex;
                align-items: center;
                gap: 10px;
                padding: 12px 16px;
                background: rgba(0, 102, 255, 0.07);
                border-bottom: 1px solid rgba(0, 102, 255, 0.15);
            }
            .ssh-doc-step-num {
                width: 24px;
                height: 24px;
                border-radius: 50%;
                background: var(--accent-primary);
                color: #fff;
                font-size: 12px;
                font-weight: 700;
                display: flex;
                align-items: center;
                justify-content: center;
                flex-shrink: 0;
            }
            .ssh-doc-step-title {
                font-size: 14px;
                font-weight: 600;
                color: var(--accent-primary);
            }
            .ssh-doc-block {
                padding: 10px 16px 14px 16px;
                border-top: 1px dashed rgba(0, 102, 255, 0.12);
            }
            .ssh-doc-block:first-child { border-top: none; }
            .ssh-doc-block-title {
                font-size: 13px;
                font-weight: 600;
                color: var(--text-dark);
                margin: 4px 0 6px 0;
            }
            .ssh-doc-block-desc {
                font-size: 11.5px;
                color: var(--text-muted);
                margin-bottom: 6px;
                line-height: 1.5;
            }
            .ssh-doc-cmd {
                display: flex;
                align-items: center;
                gap: 8px;
                padding: 6px 10px;
                background: #0a0f1a;
                border-radius: 8px;
                margin: 5px 0;
                border: 1px solid rgba(0, 102, 255, 0.15);
            }
            .ssh-doc-cmd-label {
                font-size: 11px;
                color: #94a3b8;
                flex-shrink: 0;
                min-width: 76px;
                font-family: 'Inter', sans-serif;
            }
            .ssh-doc-cmd-code {
                flex: 1;
                color: #a5d6ff;
                font-family: 'JetBrains Mono', monospace;
                font-size: 12px;
                word-break: break-all;
                white-space: pre-wrap;
                line-height: 1.5;
            }
            .ssh-doc-copy {
                flex-shrink: 0;
                background: rgba(0, 102, 255, 0.15);
                border: 1px solid rgba(0, 102, 255, 0.35);
                color: #a5d6ff;
                border-radius: 6px;
                padding: 2px 8px;
                cursor: pointer;
                font-size: 12px;
                transition: 0.15s;
            }
            .ssh-doc-copy:hover {
                background: var(--accent-primary);
                color: #fff;
                border-color: var(--accent-primary);
            }
            .ssh-doc-wrap::-webkit-scrollbar { width: 6px; }
            .ssh-doc-wrap::-webkit-scrollbar-thumb {
                background: rgba(0, 102, 255, 0.3);
                border-radius: 4px;
            }
        `;
        document.head.appendChild(style);
    }
})();