(function() {
    const API = window.apiUrl('/datasource/api');
    let currentEditId = null;   // 当前编辑的数据源ID（字符串UUID）
    let dsList = [];

    // ==================== DOM 元素 ====================
    const dsContainer = document.getElementById('dsListContainer');
    const editId = document.getElementById('editId');
    const formTitle = document.getElementById('formTitle');
    const nameInput = document.getElementById('name');
    const dbTypeSelect = document.getElementById('dbType');
    const hostInput = document.getElementById('host');
    const portInput = document.getElementById('port');
    const dbNameInput = document.getElementById('databaseName');
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');
    const driverClassInput = document.getElementById('driverClass');
    const driverJarInput = document.getElementById('driverJar');
    const connPropsInput = document.getElementById('connectionProperties');
    const versionInput = document.getElementById('version');
    const statusSelect = document.getElementById('status');
    const formMessage = document.getElementById('formMessage');

    // ==================== 辅助函数 ====================

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    function showMessage(msg, isError = false) {
        if (!formMessage) {
            alert(msg);
            return;
        }
        formMessage.style.color = isError ? '#dc2626' : '#22c55e';
        formMessage.innerText = msg;
        setTimeout(() => {
            if (formMessage.innerText === msg) formMessage.innerText = '';
        }, 4000);
    }

    /**
     * ★ 关键：先判断 content-type 再解析，非 JSON 响应给出可读错误
     */
    async function parseResponse(resp) {
        const ct = resp.headers.get('content-type') || '';
        if (ct.includes('application/json')) {
            return await resp.json();
        }
        const text = await resp.text();
        throw new Error(`后端返回非 JSON (HTTP ${resp.status}): ${text.substring(0, 200)}`);
    }

    // ==================== 加载数据源列表 ====================

    async function loadDataSources() {
        try {
            const resp = await fetch(API + '/list');
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            dsList = await parseResponse(resp);
            renderDsList();
            if (dsList.length > 0 && !currentEditId) {
                selectDataSource(dsList[0].id);
            } else if (dsList.length === 0) {
                clearForm();
            }
        } catch (err) {
            console.error('[DataSourceManager] 加载列表失败', err);
            if (dsContainer) {
                dsContainer.innerHTML = `<div class="error-tip">❌ ${escapeHtml(err.message)}</div>`;
            }
        }
    }

    // ==================== 渲染左侧列表 ====================

    function renderDsList() {
        if (!dsContainer) return;
        if (!dsList.length) {
            dsContainer.innerHTML = '<div class="empty-tip">暂无数据源，点击“新增”添加</div>';
            return;
        }
        dsContainer.innerHTML = dsList.map(ds => `
            <div class="ds-item ${currentEditId === ds.id ? 'active' : ''}" data-id="${escapeHtml(ds.id)}">
                <div class="ds-name">${escapeHtml(ds.name)}</div>
                <div class="ds-type">${escapeHtml(ds.dbType)} | ${escapeHtml(ds.host)}:${escapeHtml(String(ds.port || ''))}/${escapeHtml(ds.databaseName)}</div>
                <div style="margin-top: 8px; display: flex; gap: 8px; justify-content: flex-end;">
                    <button class="btn-secondary edit-ds" data-id="${escapeHtml(ds.id)}" style="padding: 2px 12px;">编辑</button>
                    <button class="btn-secondary delete-ds" data-id="${escapeHtml(ds.id)}" style="padding: 2px 12px;">删除</button>
                </div>
            </div>
        `).join('');

        document.querySelectorAll('.ds-item').forEach(el => {
            el.addEventListener('click', (e) => {
                if (e.target.classList.contains('edit-ds') || e.target.classList.contains('delete-ds')) return;
                selectDataSource(el.dataset.id);
            });
        });
        document.querySelectorAll('.edit-ds').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                selectDataSource(btn.dataset.id);
            });
        });
        document.querySelectorAll('.delete-ds').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                const id = btn.dataset.id;
                if (confirm('确定删除该数据源吗？')) {
                    await deleteDataSource(id);
                }
            });
        });
    }

    // ==================== 选中数据源，回显到表单 ====================

    async function selectDataSource(id) {
        const ds = dsList.find(d => d.id === id);
        if (!ds) return;

        currentEditId = ds.id;
        editId.value = ds.id;
        nameInput.value = ds.name || '';
        dbTypeSelect.value = ds.dbType || 'mysql';
        hostInput.value = ds.host || '';
        portInput.value = ds.port || '';
        dbNameInput.value = ds.databaseName || '';
        usernameInput.value = ds.username || '';
        passwordInput.value = ds.password || '';
        driverClassInput.value = ds.driverClass || '';
        driverJarInput.value = ds.driverJar || '';
        connPropsInput.value = ds.connectionProperties || '';
        versionInput.value = ds.version || '';
        statusSelect.value = ds.status != null ? ds.status : 1;
        formTitle.innerText = '✏️ 编辑数据源';
        showMessage('');
        renderDsList();
    }

    // ==================== 清空表单（新增模式） ====================

    function clearForm() {
        currentEditId = null;
        editId.value = '';
        nameInput.value = '';
        dbTypeSelect.value = 'mysql';
        hostInput.value = 'localhost';
        portInput.value = '3306';
        dbNameInput.value = '';
        usernameInput.value = '';
        passwordInput.value = '';
        driverClassInput.value = 'com.mysql.cj.jdbc.Driver';
        driverJarInput.value = '';
        connPropsInput.value = 'useSSL=false&serverTimezone=UTC';
        versionInput.value = '8.0';
        statusSelect.value = '1';
        formTitle.innerText = '➕ 新增数据源';
        showMessage('');
        document.querySelectorAll('.ds-item').forEach(el => el.classList.remove('active'));
    }

    // ==================== 保存数据源（核心修复） ====================

    async function saveDataSource() {
        const portRaw = portInput.value.trim();
        const config = {
            id: editId.value || null,
            name: nameInput.value.trim(),
            dbType: dbTypeSelect.value,
            host: hostInput.value.trim(),
            port: portRaw === '' ? null : parseInt(portRaw),   // ★ 空字符串转 null
            databaseName: dbNameInput.value.trim(),
            username: usernameInput.value.trim(),
            password: passwordInput.value,
            driverClass: driverClassInput.value.trim(),
            driverJar: driverJarInput.value.trim(),
            connectionProperties: connPropsInput.value.trim(),
            version: versionInput.value.trim(),
            status: parseInt(statusSelect.value)
        };

        // 前端本地校验
        if (!config.name)        { showMessage('请填写名称', true);       return; }
        if (!config.host)        { showMessage('请填写主机', true);       return; }
        if (!config.port)        { showMessage('请填写端口', true);       return; }
        if (!config.databaseName){ showMessage('请填写数据库名', true);   return; }

        console.log('[DataSourceManager] 保存数据源:', config);

        try {
            const resp = await fetch(API + '/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });

            // ★ parseResponse 内部会判断 content-type，非 JSON 会抛出可读错误
            const data = await parseResponse(resp);

            if (data.success) {
                showMessage('保存成功！');
                await loadDataSources();
                if (!config.id) clearForm();
                if (window.parent && window.parent.refreshDataSourceSelect) {
                    window.parent.refreshDataSourceSelect();
                }
            } else {
                // 后端现在一定会带 error 字段（不再"未知错误"）
                showMessage('保存失败：' + (data.error || '未知错误'), true);
                console.warn('[DataSourceManager] 保存失败:', data);
            }
        } catch (err) {
            console.error('[DataSourceManager] 保存异常', err);
            showMessage('请求异常：' + err.message, true);
        }
    }

    // ==================== 删除数据源 ====================

    async function deleteDataSource(id) {
        try {
            const resp = await fetch(API + '/delete?id=' + encodeURIComponent(id), { method: 'POST' });
            const data = await parseResponse(resp);
            if (data.success) {
                if (currentEditId === id) clearForm();
                await loadDataSources();
                if (window.parent && window.parent.refreshDataSourceSelect) {
                    window.parent.refreshDataSourceSelect();
                }
            } else {
                showMessage('删除失败：' + (data.error || '未知错误'), true);
            }
        } catch (err) {
            showMessage('删除异常：' + err.message, true);
        }
    }

    // ==================== 测试连接 ====================

    async function testConnection() {
        const portRaw = portInput.value.trim();
        const config = {
            name: nameInput.value.trim() || 'test',
            dbType: dbTypeSelect.value,
            host: hostInput.value.trim(),
            port: portRaw === '' ? null : parseInt(portRaw),
            databaseName: dbNameInput.value.trim(),
            username: usernameInput.value.trim(),
            password: passwordInput.value,
            driverClass: driverClassInput.value.trim(),
            driverJar: driverJarInput.value.trim(),
            connectionProperties: connPropsInput.value.trim(),
            version: versionInput.value.trim()
        };
        if (!config.host || !config.port || !config.databaseName) {
            alert('请填写主机、端口和数据库名');
            return;
        }
        try {
            const resp = await fetch(API + '/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });
            const data = await parseResponse(resp);
            alert(data.message || (data.success ? '连接成功' : '连接失败'));
        } catch (err) {
            alert('测试连接失败：' + err.message);
        }
    }

    // ==================== 绑定按钮事件 ====================

    document.getElementById('newDsBtn').addEventListener('click', clearForm);
    document.getElementById('saveBtn').addEventListener('click', saveDataSource);
    document.getElementById('testConnBtn').addEventListener('click', testConnection);
    document.getElementById('cancelBtn').addEventListener('click', clearForm);

    // ==================== 初始化加载 ====================

    loadDataSources();
})();(function() {
    const API = window.apiUrl('/datasource/api');
    let currentEditId = null;   // 当前编辑的数据源ID（字符串UUID）
    let dsList = [];

    // ==================== DOM 元素 ====================
    const dsContainer = document.getElementById('dsListContainer');
    const editId = document.getElementById('editId');
    const formTitle = document.getElementById('formTitle');
    const nameInput = document.getElementById('name');
    const dbTypeSelect = document.getElementById('dbType');
    const hostInput = document.getElementById('host');
    const portInput = document.getElementById('port');
    const dbNameInput = document.getElementById('databaseName');
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');
    const driverClassInput = document.getElementById('driverClass');
    const driverJarInput = document.getElementById('driverJar');
    const connPropsInput = document.getElementById('connectionProperties');
    const versionInput = document.getElementById('version');
    const statusSelect = document.getElementById('status');
    const formMessage = document.getElementById('formMessage');

    // ==================== 辅助函数 ====================

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    function showMessage(msg, isError = false) {
        if (!formMessage) {
            alert(msg);
            return;
        }
        formMessage.style.color = isError ? '#dc2626' : '#22c55e';
        formMessage.innerText = msg;
        setTimeout(() => {
            if (formMessage.innerText === msg) formMessage.innerText = '';
        }, 4000);
    }

    /**
     * ★ 关键：先判断 content-type 再解析，非 JSON 响应给出可读错误
     */
    async function parseResponse(resp) {
        const ct = resp.headers.get('content-type') || '';
        if (ct.includes('application/json')) {
            return await resp.json();
        }
        const text = await resp.text();
        throw new Error(`后端返回非 JSON (HTTP ${resp.status}): ${text.substring(0, 200)}`);
    }

    // ==================== 加载数据源列表 ====================

    async function loadDataSources() {
        try {
            const resp = await fetch(API + '/list');
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            dsList = await parseResponse(resp);
            renderDsList();
            if (dsList.length > 0 && !currentEditId) {
                selectDataSource(dsList[0].id);
            } else if (dsList.length === 0) {
                clearForm();
            }
        } catch (err) {
            console.error('[DataSourceManager] 加载列表失败', err);
            if (dsContainer) {
                dsContainer.innerHTML = `<div class="error-tip">❌ ${escapeHtml(err.message)}</div>`;
            }
        }
    }

    // ==================== 渲染左侧列表 ====================

    function renderDsList() {
        if (!dsContainer) return;
        if (!dsList.length) {
            dsContainer.innerHTML = '<div class="empty-tip">暂无数据源，点击“新增”添加</div>';
            return;
        }
        dsContainer.innerHTML = dsList.map(ds => `
            <div class="ds-item ${currentEditId === ds.id ? 'active' : ''}" data-id="${escapeHtml(ds.id)}">
                <div class="ds-name">${escapeHtml(ds.name)}</div>
                <div class="ds-type">${escapeHtml(ds.dbType)} | ${escapeHtml(ds.host)}:${escapeHtml(String(ds.port || ''))}/${escapeHtml(ds.databaseName)}</div>
                <div style="margin-top: 8px; display: flex; gap: 8px; justify-content: flex-end;">
                    <button class="btn-secondary edit-ds" data-id="${escapeHtml(ds.id)}" style="padding: 2px 12px;">编辑</button>
                    <button class="btn-secondary delete-ds" data-id="${escapeHtml(ds.id)}" style="padding: 2px 12px;">删除</button>
                </div>
            </div>
        `).join('');

        document.querySelectorAll('.ds-item').forEach(el => {
            el.addEventListener('click', (e) => {
                if (e.target.classList.contains('edit-ds') || e.target.classList.contains('delete-ds')) return;
                selectDataSource(el.dataset.id);
            });
        });
        document.querySelectorAll('.edit-ds').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                selectDataSource(btn.dataset.id);
            });
        });
        document.querySelectorAll('.delete-ds').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                const id = btn.dataset.id;
                if (confirm('确定删除该数据源吗？')) {
                    await deleteDataSource(id);
                }
            });
        });
    }

    // ==================== 选中数据源，回显到表单 ====================

    async function selectDataSource(id) {
        const ds = dsList.find(d => d.id === id);
        if (!ds) return;

        currentEditId = ds.id;
        editId.value = ds.id;
        nameInput.value = ds.name || '';
        dbTypeSelect.value = ds.dbType || 'mysql';
        hostInput.value = ds.host || '';
        portInput.value = ds.port || '';
        dbNameInput.value = ds.databaseName || '';
        usernameInput.value = ds.username || '';
        passwordInput.value = ds.password || '';
        driverClassInput.value = ds.driverClass || '';
        driverJarInput.value = ds.driverJar || '';
        connPropsInput.value = ds.connectionProperties || '';
        versionInput.value = ds.version || '';
        statusSelect.value = ds.status != null ? ds.status : 1;
        formTitle.innerText = '✏️ 编辑数据源';
        showMessage('');
        renderDsList();
    }

    // ==================== 清空表单（新增模式） ====================

    function clearForm() {
        currentEditId = null;
        editId.value = '';
        nameInput.value = '';
        dbTypeSelect.value = 'mysql';
        hostInput.value = 'localhost';
        portInput.value = '3306';
        dbNameInput.value = '';
        usernameInput.value = '';
        passwordInput.value = '';
        driverClassInput.value = 'com.mysql.cj.jdbc.Driver';
        driverJarInput.value = '';
        connPropsInput.value = 'useSSL=false&serverTimezone=UTC';
        versionInput.value = '8.0';
        statusSelect.value = '1';
        formTitle.innerText = '➕ 新增数据源';
        showMessage('');
        document.querySelectorAll('.ds-item').forEach(el => el.classList.remove('active'));
    }

    // ==================== 保存数据源（核心修复） ====================

    async function saveDataSource() {
        const portRaw = portInput.value.trim();
        const config = {
            id: editId.value || null,
            name: nameInput.value.trim(),
            dbType: dbTypeSelect.value,
            host: hostInput.value.trim(),
            port: portRaw === '' ? null : parseInt(portRaw),   // ★ 空字符串转 null
            databaseName: dbNameInput.value.trim(),
            username: usernameInput.value.trim(),
            password: passwordInput.value,
            driverClass: driverClassInput.value.trim(),
            driverJar: driverJarInput.value.trim(),
            connectionProperties: connPropsInput.value.trim(),
            version: versionInput.value.trim(),
            status: parseInt(statusSelect.value)
        };

        // 前端本地校验
        if (!config.name)        { showMessage('请填写名称', true);       return; }
        if (!config.host)        { showMessage('请填写主机', true);       return; }
        if (!config.port)        { showMessage('请填写端口', true);       return; }
        if (!config.databaseName){ showMessage('请填写数据库名', true);   return; }

        console.log('[DataSourceManager] 保存数据源:', config);

        try {
            const resp = await fetch(API + '/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });

            // ★ parseResponse 内部会判断 content-type，非 JSON 会抛出可读错误
            const data = await parseResponse(resp);

            if (data.success) {
                showMessage('保存成功！');
                await loadDataSources();
                if (!config.id) clearForm();
                if (window.parent && window.parent.refreshDataSourceSelect) {
                    window.parent.refreshDataSourceSelect();
                }
            } else {
                // 后端现在一定会带 error 字段（不再"未知错误"）
                showMessage('保存失败：' + (data.error || '未知错误'), true);
                console.warn('[DataSourceManager] 保存失败:', data);
            }
        } catch (err) {
            console.error('[DataSourceManager] 保存异常', err);
            showMessage('请求异常：' + err.message, true);
        }
    }

    // ==================== 删除数据源 ====================

    async function deleteDataSource(id) {
        try {
            const resp = await fetch(API + '/delete?id=' + encodeURIComponent(id), { method: 'POST' });
            const data = await parseResponse(resp);
            if (data.success) {
                if (currentEditId === id) clearForm();
                await loadDataSources();
                if (window.parent && window.parent.refreshDataSourceSelect) {
                    window.parent.refreshDataSourceSelect();
                }
            } else {
                showMessage('删除失败：' + (data.error || '未知错误'), true);
            }
        } catch (err) {
            showMessage('删除异常：' + err.message, true);
        }
    }

    // ==================== 测试连接 ====================

    async function testConnection() {
        const portRaw = portInput.value.trim();
        const config = {
            name: nameInput.value.trim() || 'test',
            dbType: dbTypeSelect.value,
            host: hostInput.value.trim(),
            port: portRaw === '' ? null : parseInt(portRaw),
            databaseName: dbNameInput.value.trim(),
            username: usernameInput.value.trim(),
            password: passwordInput.value,
            driverClass: driverClassInput.value.trim(),
            driverJar: driverJarInput.value.trim(),
            connectionProperties: connPropsInput.value.trim(),
            version: versionInput.value.trim()
        };
        if (!config.host || !config.port || !config.databaseName) {
            alert('请填写主机、端口和数据库名');
            return;
        }
        try {
            const resp = await fetch(API + '/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });
            const data = await parseResponse(resp);
            alert(data.message || (data.success ? '连接成功' : '连接失败'));
        } catch (err) {
            alert('测试连接失败：' + err.message);
        }
    }

    // ==================== 绑定按钮事件 ====================

    document.getElementById('newDsBtn').addEventListener('click', clearForm);
    document.getElementById('saveBtn').addEventListener('click', saveDataSource);
    document.getElementById('testConnBtn').addEventListener('click', testConnection);
    document.getElementById('cancelBtn').addEventListener('click', clearForm);

    // ==================== 初始化加载 ====================

    loadDataSources();
})();