// webapps/demo/sys/modules/cluster/cluster.js
(function () {
    'use strict';

    SysCenter.register({
        id: 'cluster', name: '集群管理', icon: '🖧',
        desc: '主节点 / Worker 节点 / 部署包 / 端口探测 / 部署（SSH 推送 或 手动下载）',
        mount: mountCluster
    });

    function mountCluster(container) {
        // 注意：这里禁止写 ${user.dir} 这类 JS 模板插值，
        // 因为 user 在浏览器端不存在，会抛 ReferenceError。
        container.innerHTML = `
            <div class="cluster-wrap">
                <div class="cluster-card">
                    <div class="cluster-card-title">🏛️ 主节点</div>
                    <div id="masterInfo" class="loading">加载中...</div>
                </div>

                <div class="cluster-card">
                    <div class="cluster-card-title">
                        🔗 节点列表
                        <button id="refreshNodesBtn" class="btn-secondary btn-sm"
                                style="margin-left:auto;">🔄 刷新</button>
                    </div>
                    <div id="nodesWrap" class="loading">加载中...</div>
                </div>

                <div class="cluster-card">
                    <div class="cluster-card-title">🚀 部署新 Worker</div>

                    <div class="channel-tabs" id="channelTabs">
                        <button class="channel-tab active" data-channel="ssh">🔐 SSH 推送（内网服务器）</button>
                        <button class="channel-tab" data-channel="local">💻 本机（主服务所在机）</button>
                        <button class="channel-tab" data-channel="manual">📥 手动下载（员工 PC / 跨 NAT）</button>
                    </div>

                    <div class="form-row">
                        <label>部署包</label>
                        <select id="jarSelect" style="min-width:280px;">
                            <option value="">加载中...</option>
                        </select>
                        <span id="jarMeta" style="font-size:11px;color:#94a3b8;"></span>
                    </div>

                    <div class="form-row" id="sshRow">
                        <label>目标机</label>
                        <input id="sshHost" placeholder="192.168.1.10" style="width:160px;">
                        <label>用户</label>
                        <input id="sshUser" placeholder="root" style="width:110px;">
                        <label>SSH端口</label>
                        <input id="sshPort" type="number" value="22" style="width:70px;">
                        <label>密钥</label>
                        <input id="sshKeyPath" placeholder="/root/.ssh/id_rsa（留空用默认）"
                               style="width:230px;">
                        <button id="checkJdkBtn" class="btn-secondary btn-sm">🔍 检测 JDK</button>
                    </div>

                    <div class="form-row" id="localRow" style="display:none;">
                        <label>部署目录</label>
                        <input id="localDeployDir"
                               placeholder="留空则使用 user.dir/cluster/workers"
                               style="min-width:380px;">
                        <button id="checkLocalJdkBtn" class="btn-secondary btn-sm">🔍 检测 JDK</button>
                    </div>

                    <div class="form-row" id="manualRow" style="display:none;">
                        <span style="font-size:12px;color:#94a3b8;">
                            手动模式：主服务只生成下载地址 + 启动命令，由用户自行在目标机执行
                        </span>
                    </div>

                    <div class="form-row" id="clientIpHintRow" style="display:none;">
                        <span style="font-size:12px;color:#0066ff;">
                            🖥️ 当前浏览器所在主机 IP：<b id="clientIpText">-</b>
                            （可修改下方目标机 IP 后再部署到本机）
                        </span>
                    </div>

                    <div id="jdkResult"></div>

                    <div class="form-row">
                        <label>端口</label>
                        <input id="portInput" type="number" placeholder="留空自动探测"
                               style="width:130px;">
                        <button id="probePortBtn" class="btn-secondary btn-sm">🔍 探测端口</button>
                        <span id="probeHint" style="font-size:12px;color:#94a3b8;"></span>
                    </div>

                    <div style="margin-top:12px;">
                        <button id="deployBtn" class="btn-primary btn-sm" disabled>🚀 部署</button>
                        <button id="resetFormBtn" class="btn-secondary btn-sm">🗑 重置</button>
                    </div>

                    <div id="deployResult"></div>
                </div>
            </div>
        `;

        const $ = id => container.querySelector('#' + id);
        let currentChannel = 'ssh';           // ★ 默认走 SSH
        let selectedJavaPath = '';
        let cachedPackages = [];
        /** ★ 新增：服务器网络信息（/server-info 返回） */
        let serverInfo = null;

        const API = window.apiUrl('/cluster/api');

        function escapeHtml(s) {
            if (s == null) return '';
            return String(s).replace(/[&<>"]/g, m => ({
                '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
            }[m]));
        }
        function fmtTime(t) {
            if (!t) return '-';
            return new Date(t).toLocaleString('zh-CN');
        }
        function fmtSize(b) {
            if (b == null) return '-';
            if (b < 1024) return b + ' B';
            if (b < 1024*1024) return (b/1024).toFixed(1) + ' KB';
            return (b/1024/1024).toFixed(2) + ' MB';
        }

        // ============== ★ 新增：加载服务器 / 客户端网络信息 ==============
        async function loadServerInfo() {
            try {
                const r = await fetch(API + '/server-info', { cache: 'no-store' })
                    .then(x => x.json());
                if (!r.success) return null;
                serverInfo = r.data || {};
                return serverInfo;
            } catch (e) {
                console.warn('[cluster] 获取服务器信息失败', e);
                return null;
            }
        }

        // ============== 主节点信息（含真实 IP 与客户端 IP） ==============
        async function loadMaster() {
            try {
                // ★ 并行拉 master + server-info，一次渲染
                const [masterResp, si] = await Promise.all([
                    fetch(API + '/master', { cache: 'no-store' }).then(x => x.json()),
                    loadServerInfo()
                ]);
                if (!masterResp.success) throw new Error(masterResp.error);
                const m = masterResp.data || {};
                const clientIp = si && si.clientIp ? si.clientIp : '-';
                const candidateIps = si && si.candidateIps ? si.candidateIps : [];

                // ★ 主节点卡片额外展示：真实服务器 IP + 客户端 IP + 候选网卡
                const items = [
                    item('节点 ID', m.nodeId),
                    item('服务器 IP', m.host + ':' + m.port),
                    item('客户端 IP', clientIp),
                    item('Context', m.contextPath),
                    item('JDK', m.jdkVersion),
                    item('OS', m.osName + ' / ' + m.osArch),
                    item('启动时间', fmtTime(m.startTime)),
                    item('Master URL', m.masterUrl),
                    item('Token', m.tokenPreview)
                ];
                if (candidateIps.length > 1) {
                    items.push(item('其他候选 IP', candidateIps.slice(1).join(' / ')));
                }

                $('masterInfo').outerHTML = `
                    <div class="master-grid" id="masterInfo">
                        ${items.join('')}
                    </div>
                `;

                // ★ SSH 目标机默认填客户端 IP
                applyClientIpToSshHost(si);
            } catch (e) {
                $('masterInfo').outerHTML =
                    `<div id="masterInfo" class="result-err result-box">❌ ${escapeHtml(e.message)}</div>`;
            }
        }

        function item(lbl, val) {
            return `<div class="master-item">
                <div class="lbl">${escapeHtml(lbl)}</div>
                <div class="val">${escapeHtml(val || '-')}</div>
            </div>`;
        }

        // ★ 把客户端 IP 自动填入 SSH 目标机（仅默认，允许用户修改）
        function applyClientIpToSshHost(si) {
            if (!si || !si.clientIp || si.clientIp === 'unknown') return;
            const hintRow = $('clientIpHintRow');
            const hintText = $('clientIpText');
            if (hintRow && hintText) {
                hintText.textContent = si.clientIp;
                hintRow.style.display = 'flex';
            }
            const sshHostInput = $('sshHost');
            if (sshHostInput && !sshHostInput.value) {
                sshHostInput.value = si.clientIp;
                sshHostInput.title = '已自动填充当前浏览器所在主机的 IP，可修改';
            }
        }

        // ============== 节点列表 ==============
        let pollTimer = null;
        async function loadNodes() {
            try {
                const r = await fetch(API + '/nodes', { cache: 'no-store' }).then(x => x.json());
                if (!r.success) throw new Error(r.error);
                const nodes = r.nodes || [];
                if (nodes.length === 0) {
                    $('nodesWrap').innerHTML = '<div class="empty-tip">暂无节点</div>';
                    return;
                }
                let html = `<table class="node-table"><thead><tr>
                    <th>角色</th><th>状态</th><th>节点 ID</th><th>Host:Port</th>
                    <th>通道</th><th>部署包</th><th>JDK</th><th>PID</th>
                    <th>启动</th><th>心跳</th><th>操作</th>
                </tr></thead><tbody>`;
                for (const n of nodes) {
                    const stateCls = 'state-' + (n.state || 'UNKNOWN');
                    const hbAgo = n.lastHeartbeat > 0
                        ? Math.floor((Date.now() - n.lastHeartbeat) / 1000) + 's 前'
                        : '-';
                    const stopBtn = n.role === 'WORKER'
                        ? `<button class="btn-danger-sm" data-stop="${escapeHtml(n.nodeId)}">停止</button>`
                        : '';
                    html += `<tr>
                        <td>${n.role === 'MASTER' ? '🏛️ MASTER' : '🧩 WORKER'}</td>
                        <td><span class="state-badge ${stateCls}">${escapeHtml(n.state)}</span></td>
                        <td title="${escapeHtml(n.nodeId)}">${escapeHtml((n.nodeId||'').substring(0,12))}…</td>
                        <td>${escapeHtml((n.host||'-') + ':' + (n.port||'-'))}</td>
                        <td>${escapeHtml(n.channel || '-')}</td>
                        <td>${escapeHtml(n.jarName || '-')}</td>
                        <td>${escapeHtml(n.jdkVersion || '-')}</td>
                        <td>${escapeHtml(n.pid || '-')}</td>
                        <td>${fmtTime(n.startTime)}</td>
                        <td>${hbAgo}</td>
                        <td>${stopBtn}</td>
                    </tr>`;
                }
                html += '</tbody></table>';
                $('nodesWrap').outerHTML = `<div id="nodesWrap">${html}</div>`;
                container.querySelectorAll('[data-stop]').forEach(btn => {
                    btn.addEventListener('click', () => stopNode(btn.dataset.stop));
                });
            } catch (e) {
                $('nodesWrap').outerHTML =
                    `<div id="nodesWrap" class="result-err result-box">❌ ${escapeHtml(e.message)}</div>`;
            }
        }

        async function stopNode(nodeId) {
            if (!confirm('确定停止节点 ' + nodeId.substring(0, 8) + ' 吗？')) return;
            try {
                const r = await fetch(API + '/stop', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ nodeId })
                }).then(x => x.json());
                if (r.manualOnly && r.stopCommand) {
                    alert('手动停止命令：\n\n' + r.stopCommand);
                } else if (!r.success) {
                    alert('停止失败：' + (r.error || r.message || '未知'));
                }
                loadNodes();
            } catch (e) { alert('请求异常：' + e.message); }
        }

        // ============== 部署包列表 ==============
        async function loadPackages() {
            try {
                const r = await fetch(API + '/packages', { cache: 'no-store' }).then(x => x.json());
                if (!r.success) throw new Error(r.error);
                cachedPackages = r.packages || [];
                const sel = $('jarSelect');
                if (cachedPackages.length === 0) {
                    sel.innerHTML = '<option value="">（resources/deploy 下没有 .jar）</option>';
                    return;
                }
                sel.innerHTML = '<option value="">-- 选择部署包 --</option>' + cachedPackages.map(p =>
                    `<option value="${escapeHtml(p.jarName)}">${escapeHtml(p.jarName)}  (${fmtSize(p.size)})</option>`
                ).join('');
                sel.addEventListener('change', () => {
                    const p = cachedPackages.find(x => x.jarName === sel.value);
                    $('jarMeta').textContent = p
                        ? `MD5 ${p.md5.substring(0,12)}…  ·  ${fmtSize(p.size)}  ·  ${fmtTime(p.modified)}`
                        : '';
                    checkEnable();
                });
            } catch (e) {
                $('jarSelect').innerHTML = `<option value="">加载失败: ${escapeHtml(e.message)}</option>`;
            }
        }

        // ============== 通道切换 ==============
        container.querySelectorAll('.channel-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                container.querySelectorAll('.channel-tab').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                currentChannel = tab.dataset.channel;
                $('sshRow').style.display    = currentChannel === 'ssh' ? 'flex' : 'none';
                $('localRow').style.display  = currentChannel === 'local' ? 'flex' : 'none';
                $('manualRow').style.display = currentChannel === 'manual' ? 'flex' : 'none';
                $('jdkResult').innerHTML = '';
                selectedJavaPath = '';
                checkEnable();
            });
        });

        function checkEnable() {
            const jar = $('jarSelect').value;
            const port = parseInt($('portInput').value) || 0;
            const can = jar && port > 0;
            $('deployBtn').disabled = !can;
        }
        $('portInput').addEventListener('input', checkEnable);

        // ============== JDK 检测 ==============
        async function checkJdk(channel) {
            $('jdkResult').innerHTML = '<div class="loading">检测 JDK 中...</div>';
            const body = { channel };
            if (channel === 'ssh') {
                body.host     = $('sshHost').value.trim();
                body.sshUser  = $('sshUser').value.trim();
                body.sshPort  = parseInt($('sshPort').value) || 22;
                body.sshKeyPath = $('sshKeyPath').value.trim();
                if (!body.host || !body.sshUser) {
                    $('jdkResult').innerHTML = '<div class="result-err result-box">请填写目标机 host 和 user</div>';
                    return;
                }
            }
            try {
                const r = await fetch(API + '/check-jdk', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                }).then(x => x.json());
                if (!r.success) throw new Error(r.error);
                const info = r.jdk || {};
                if (info.error) {
                    $('jdkResult').innerHTML =
                        `<div class="result-err result-box">${escapeHtml(info.error)}</div>`;
                    return;
                }
                const cands = info.candidates || [];
                if (cands.length === 0) {
                    $('jdkResult').innerHTML =
                        '<div class="result-err result-box">未找到可用 JDK</div>';
                    return;
                }
                selectedJavaPath = cands[0].path;
                $('jdkResult').innerHTML = `
                    <div class="jdk-list">
                        ${cands.map((c, i) => `
                            <label class="jdk-item">
                                <input type="radio" name="jdkPick" value="${escapeHtml(c.path)}"
                                       ${i === 0 ? 'checked' : ''}>
                                <span class="ver">JDK ${escapeHtml(c.version)}</span>
                                <span class="path" title="${escapeHtml(c.path)}">${escapeHtml(c.path)}</span>
                            </label>
                        `).join('')}
                    </div>
                `;
                container.querySelectorAll('input[name="jdkPick"]').forEach(r => {
                    r.addEventListener('change', () => { selectedJavaPath = r.value; });
                });
            } catch (e) {
                $('jdkResult').innerHTML =
                    `<div class="result-err result-box">❌ ${escapeHtml(e.message)}</div>`;
            }
        }
        $('checkJdkBtn').addEventListener('click', () => checkJdk('ssh'));
        $('checkLocalJdkBtn').addEventListener('click', () => checkJdk('local'));

        // ============== 端口探测 ==============
        $('probePortBtn').addEventListener('click', async () => {
            const body = { channel: currentChannel };
            if (currentChannel === 'ssh') {
                body.host = $('sshHost').value.trim();
                body.sshUser = $('sshUser').value.trim();
                body.sshPort = parseInt($('sshPort').value) || 22;
                body.sshKeyPath = $('sshKeyPath').value.trim();
                if (!body.host || !body.sshUser) {
                    $('probeHint').textContent = '请先填目标机 host 和 user';
                    return;
                }
            }
            const want = parseInt($('portInput').value) || 0;
            body.port = want;
            $('probeHint').textContent = '探测中...';
            try {
                const r = await fetch(API + '/probe-port', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                }).then(x => x.json());
                if (r.success) {
                    $('portInput').value = r.port;
                    $('probeHint').textContent = '✅ ' + r.message;
                    checkEnable();
                } else {
                    $('probeHint').textContent = '❌ ' + r.message;
                }
            } catch (e) {
                $('probeHint').textContent = '❌ ' + e.message;
            }
        });

        // ============== 部署 ==============
        $('deployBtn').addEventListener('click', async () => {
            const body = {
                channel: currentChannel,
                jarName: $('jarSelect').value,
                port: parseInt($('portInput').value) || 0,
                javaPath: selectedJavaPath || ''
            };
            if (currentChannel === 'ssh') {
                body.host = $('sshHost').value.trim();
                body.sshUser = $('sshUser').value.trim();
                body.sshPort = parseInt($('sshPort').value) || 22;
                body.sshKeyPath = $('sshKeyPath').value.trim();
                body.deployDir = '/opt/codestats-workers/' + Date.now();
            } else if (currentChannel === 'local') {
                body.deployDir = $('localDeployDir').value.trim();
            }

            $('deployResult').innerHTML = '<div class="result-info result-box">⏳ 部署中...</div>';
            try {
                const r = await fetch(API + '/deploy', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                }).then(x => x.json());
                if (r.success) {
                    if (r.manualOnly) {
                        $('deployResult').innerHTML = `
                            <div class="result-ok result-box">
                                ✅ 手动部署已就绪，请到目标机执行下面两步：
                                <div style="margin-top:8px;font-size:11px;color:#64748b;">
                                    下载地址：
                                </div>
                                <div class="manual-block">
                                    <button class="copy-btn" data-copy="${escapeHtml(r.downloadUrl)}">📋 复制</button>
                                    ${escapeHtml(r.downloadUrl)}
                                </div>
                                <div style="margin-top:8px;font-size:11px;color:#64748b;">
                                    启动命令：
                                </div>
                                <div class="manual-block">
                                    <button class="copy-btn" data-copy="${escapeHtml(r.startCommand)}">📋 复制</button>
                                    ${escapeHtml(r.startCommand)}
                                </div>
                                <div style="margin-top:8px;color:#0066ff;">
                                    💡 Worker 启动后会通过注册接口自动出现到节点列表
                                </div>
                            </div>`;
                    } else {
                        $('deployResult').innerHTML = `
                            <div class="result-ok result-box">
                                ✅ 部署成功：nodeId=${escapeHtml(r.nodeId || '')}
                                ${r.pid ? ' pid=' + r.pid : ''}
                            </div>`;
                    }
                    bindCopyButtons();
                    loadNodes();
                } else {
                    $('deployResult').innerHTML =
                        `<div class="result-err result-box">❌ ${escapeHtml(r.error || '未知错误')}</div>`;
                }
            } catch (e) {
                $('deployResult').innerHTML =
                    `<div class="result-err result-box">❌ ${escapeHtml(e.message)}</div>`;
            }
        });

        function bindCopyButtons() {
            container.querySelectorAll('[data-copy]').forEach(btn => {
                btn.addEventListener('click', () => {
                    navigator.clipboard.writeText(btn.dataset.copy)
                        .then(() => { btn.textContent = '✅ 已复制'; setTimeout(() => btn.textContent = '📋 复制', 1500); })
                        .catch(() => alert('复制失败'));
                });
            });
        }

        $('resetFormBtn').addEventListener('click', () => {
            $('portInput').value = '';
            $('probeHint').textContent = '';
            $('jdkResult').innerHTML = '';
            $('deployResult').innerHTML = '';
            selectedJavaPath = '';
            // ★ 重置时重新应用客户端 IP
            applyClientIpToSshHost(serverInfo);
            checkEnable();
        });

        $('refreshNodesBtn').addEventListener('click', loadNodes);

        // ============== 初始化 & 轮询 ==============
        loadMaster();          // ★ 会并行请求 /master + /server-info，并自动填客户端 IP
        loadPackages();
        loadNodes();

        pollTimer = setInterval(() => {
            if (!document.body.contains(container)) {
                clearInterval(pollTimer);
                pollTimer = null;
                return;
            }
            loadNodes();
        }, 3000);

        window.addEventListener('beforeunload', () => {
            if (pollTimer) clearInterval(pollTimer);
        });
    }
})();