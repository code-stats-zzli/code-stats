(function() {
    const API = window.apiUrl('/datasource/api');
    let currentEditId = null;   // 当前编辑的数据源ID（字符串UUID）
    let dsList = [];

    // DOM 元素
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

    // ========== 辅助函数 ==========
    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    function showMessage(msg, isError = false) {
        formMessage.style.color = isError ? '#dc2626' : '#22c55e';
        formMessage.innerText = msg;
        setTimeout(() => {
            if (formMessage.innerText === msg) formMessage.innerText = '';
        }, 3000);
    }

    // ========== 加载数据源列表 ==========
    async function loadDataSources() {
        try {
            const resp = await fetch(API + '/list');
            if (!resp.ok) throw new Error('加载失败');
            dsList = await resp.json();
            renderDsList();
            // 列表有数据且无选中项时，选中第一个；列表为空时清空表单（保留默认值）
            if (dsList.length > 0 && !currentEditId) {
                selectDataSource(dsList[0].id);
            } else if (dsList.length === 0) {
                clearForm();
            }
        } catch (err) {
            dsContainer.innerHTML = `<div class="error-tip">❌ ${err.message}</div>`;
        }
    }

    // ========== 渲染左侧列表 ==========
    function renderDsList() {
        if (!dsList.length) {
            dsContainer.innerHTML = '<div class="empty-tip">暂无数据源，点击“新增”添加</div>';
            return;
        }
        dsContainer.innerHTML = dsList.map(ds => `
            <div class="ds-item ${currentEditId === ds.id ? 'active' : ''}" data-id="${ds.id}">
                <div class="ds-name">${escapeHtml(ds.name)}</div>
                <div class="ds-type">${ds.dbType} | ${ds.host}:${ds.port}/${ds.databaseName}</div>
                <div style="margin-top: 8px; display: flex; gap: 8px; justify-content: flex-end;">
                    <button class="btn-secondary edit-ds" data-id="${ds.id}" style="padding: 2px 12px;">编辑</button>
                    <button class="btn-secondary delete-ds" data-id="${ds.id}" style="padding: 2px 12px;">删除</button>
                </div>
            </div>
        `).join('');

        // 卡片点击选中
        document.querySelectorAll('.ds-item').forEach(el => {
            el.addEventListener('click', (e) => {
                if (e.target.classList.contains('edit-ds') || e.target.classList.contains('delete-ds')) return;
                const id = el.dataset.id;
                selectDataSource(id);
            });
        });
        // 编辑按钮
        document.querySelectorAll('.edit-ds').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const id = btn.dataset.id;
                selectDataSource(id);
            });
        });
        // 删除按钮
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

    // ========== 选中数据源，回显到表单 ==========
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
        renderDsList(); // 高亮当前选中的项
    }

    // ========== 清空表单（新增模式），保留默认填充值 ==========
    function clearForm() {
        currentEditId = null;
        editId.value = '';
        nameInput.value = '';                    // 名称留空，用户填写
        dbTypeSelect.value = 'mysql';
        hostInput.value = 'localhost';           // 默认主机
        portInput.value = '3306';                // 默认端口
        dbNameInput.value = '';                  // 数据库名留空
        usernameInput.value = '';
        passwordInput.value = '';
        driverClassInput.value = 'com.mysql.cj.jdbc.Driver';  // 默认驱动
        driverJarInput.value = '';
        connPropsInput.value = 'useSSL=false&serverTimezone=UTC';
        versionInput.value = '8.0';
        statusSelect.value = '1';
        formTitle.innerText = '➕ 新增数据源';
        showMessage('');
        // 移除左侧列表的高亮样式
        document.querySelectorAll('.ds-item').forEach(el => el.classList.remove('active'));
    }

    // ========== 保存数据源（新增或更新） ==========
    async function saveDataSource() {
        const config = {
            id: editId.value || null,          // 新增时 id 为 null
            name: nameInput.value.trim(),
            dbType: dbTypeSelect.value,
            host: hostInput.value.trim(),
            port: portInput.value.trim(),
            databaseName: dbNameInput.value.trim(),
            username: usernameInput.value.trim(),
            password: passwordInput.value,
            driverClass: driverClassInput.value.trim(),
            driverJar: driverJarInput.value.trim(),
            connectionProperties: connPropsInput.value.trim(),
            version: versionInput.value.trim(),
            status: parseInt(statusSelect.value)
        };
        if (!config.name || !config.host || !config.port || !config.databaseName) {
            showMessage('请填写名称、主机、端口、数据库名', true);
            return;
        }
        try {
            const resp = await fetch(API + '/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });
            const data = await resp.json();
            if (data.success) {
                showMessage('保存成功！');
                await loadDataSources();                // 刷新列表
                if (!config.id) clearForm();            // 新增完成后清空表单，继续新增
                // 通知父窗口（数据库客户端）刷新数据源下拉框
                if (window.parent && window.parent.refreshDataSourceSelect) {
                    window.parent.refreshDataSourceSelect();
                }
            } else {
                showMessage('保存失败：' + (data.error || '未知错误'), true);
            }
        } catch (err) {
            showMessage('请求异常：' + err.message, true);
        }
    }

    // ========== 删除数据源 ==========
    async function deleteDataSource(id) {
        try {
            const resp = await fetch(API + '/delete?id=' + id, { method: 'POST' });
            const data = await resp.json();
            if (data.success) {
                if (currentEditId === id) clearForm();
                await loadDataSources();
                if (window.parent && window.parent.refreshDataSourceSelect) {
                    window.parent.refreshDataSourceSelect();
                }
            } else {
                alert('删除失败');
            }
        } catch (err) {
            alert('删除异常：' + err.message);
        }
    }

    // ========== 测试连接：发送当前表单的所有数据 ==========
    async function testConnection() {
        // 收集当前表单的所有数据（与保存时一致，但不包含 id 和 status）
        const config = {
            name: nameInput.value.trim() || 'test',
            dbType: dbTypeSelect.value,
            host: hostInput.value.trim(),
            port: portInput.value.trim(),
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
            const data = await resp.json();
            alert(data.message);
        } catch (err) {
            alert('测试连接失败：' + err.message);
        }
    }

    // ========== 绑定按钮事件 ==========
    document.getElementById('newDsBtn').addEventListener('click', clearForm);
    document.getElementById('saveBtn').addEventListener('click', saveDataSource);
    document.getElementById('testConnBtn').addEventListener('click', testConnection);
    document.getElementById('cancelBtn').addEventListener('click', clearForm);

    // 初始化加载
    loadDataSources();
})();