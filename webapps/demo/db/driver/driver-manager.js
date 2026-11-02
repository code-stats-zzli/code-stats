(function() {
    function getContextPath() {
        var path = window.location.pathname;
        var parts = path.split('/');
        if (parts.length > 1 && parts[1]) return '/' + parts[1];
        return '';
    }
    var CONTEXT_PATH = getContextPath();
    function apiUrl(path) {
        if (path.startsWith('/')) return CONTEXT_PATH + path;
        return CONTEXT_PATH + '/' + path;
    }

    const API_BASE = apiUrl('/driver/api');
    let drivers = [];

    const tableBody = document.getElementById('driverTableBody');
    const modal = document.getElementById('driverModal');
    const modalTitle = document.getElementById('modalTitle');
    const editIdInput = document.getElementById('editId');
    const dbTypeSelect = document.getElementById('dbType');
    const driverVersionInput = document.getElementById('driverVersion');
    const jarFileInput = document.getElementById('jarFile');
    const fileOptionalHint = document.getElementById('fileOptionalHint');
    const saveBtn = document.getElementById('saveDriverBtn');
    const cancelBtn = document.getElementById('cancelDriverBtn');
    const addBtn = document.getElementById('addDriverBtn');
    const closeSpan = document.getElementById('closeModalBtn');
    const filterDbType = document.getElementById('filterDbType');
    const formError = document.getElementById('formError');

    // 资源目录相关
    const importFromDirBtn = document.getElementById('importFromDirBtn');
    const resourceDirModal = document.getElementById('resourceDirModal');
    const closeResourceDirBtn = document.getElementById('closeResourceDirBtn');
    const closeResourceDirFooterBtn = document.getElementById('closeResourceDirFooterBtn');
    const syncResourceDirBtn = document.getElementById('syncResourceDirBtn');
    const resourceDirInfo = document.getElementById('resourceDirInfo');
    const resourceDirList = document.getElementById('resourceDirList');
    const resourceDirError = document.getElementById('resourceDirError');

    let isEditMode = false;

    // 前端只允许 4 种类型
    const ALLOWED_DB_TYPES = ['mysql', 'oracle', 'sqlserver', 'postgresql'];

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }
    function escapeAttr(str) {
        return String(str || '').replace(/&/g,'&amp;').replace(/</g,'&lt;')
            .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }
    function showError(msg) { formError.textContent = msg || ''; }

    // ==================== 列表 ====================

    async function loadDrivers() {
        try {
            const type = filterDbType ? filterDbType.value : '';
            const url = API_BASE + '/list' + (type ? '?dbType=' + encodeURIComponent(type) : '');
            const resp = await fetch(url);
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            drivers = await resp.json();
            renderTable();
        } catch (err) {
            console.error(err);
            tableBody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:red;">加载驱动列表失败</td></tr>';
        }
    }

    function renderTable() {
        if (!drivers.length) {
            tableBody.innerHTML = '<tr class="empty-row"><td colspan="5">暂无驱动，请点击「新增驱动」上传 jar 文件</td></tr>';
            return;
        }
        tableBody.innerHTML = drivers.map(d => `
            <tr>
                <td><span class="badge ${escapeAttr(d.dbType || 'default')}">${escapeHtml(d.dbType || '-')}</span></td>
                <td>${escapeHtml(d.version || '-')}</td>
                <td>${escapeHtml(d.fileName)}</td>
                <td>${d.createTime ? new Date(d.createTime).toLocaleString() : '-'}</td>
                <td class="action-btns">
                    <button class="edit-driver" data-id="${escapeAttr(d.id)}">✏️ 编辑</button>
                    <button class="download-driver" data-id="${escapeAttr(d.id)}">⬇️ 下载</button>
                    <button class="delete-driver" data-id="${escapeAttr(d.id)}">🗑️ 删除</button>
                </td>
            </tr>
        `).join('');

        document.querySelectorAll('.edit-driver').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const d = drivers.find(x => x.id === btn.dataset.id);
                if (d) openEditModal(d);
            });
        });
        document.querySelectorAll('.download-driver').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                window.open(API_BASE + '/download?id=' + encodeURIComponent(btn.dataset.id), '_blank');
            });
        });
        document.querySelectorAll('.delete-driver').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                if (!confirm('确定删除该驱动吗？（会同时删除 resources/drivers 中的文件）')) return;
                const id = btn.dataset.id;
                const resp = await fetch(API_BASE + '/delete?id=' + encodeURIComponent(id), { method: 'POST' });
                const data = await resp.json();
                if (data.success) loadDrivers();
                else alert('删除失败：' + (data.error || '未知错误'));
            });
        });
    }

    // ==================== 弹窗 ====================

    function openAddModal() {
        isEditMode = false;
        editIdInput.value = '';
        dbTypeSelect.value = 'mysql';
        driverVersionInput.value = '';
        jarFileInput.value = '';
        modalTitle.innerText = '新增驱动';
        fileOptionalHint.textContent = '（必选）';
        showError('');
        modal.style.display = 'flex';
    }

    function openEditModal(d) {
        isEditMode = true;
        editIdInput.value = d.id;
        dbTypeSelect.value = d.dbType || 'mysql';
        driverVersionInput.value = d.version || '';
        modalTitle.innerText = '编辑驱动';
        fileOptionalHint.textContent = '（可选，不选则保持原有 JAR）';
        jarFileInput.value = '';
        showError('');
        modal.style.display = 'flex';
    }

    function closeModal() { modal.style.display = 'none'; }

    async function saveDriver() {
        showError('');
        const dbType = dbTypeSelect.value;
        const version = driverVersionInput.value.trim();

        if (!ALLOWED_DB_TYPES.includes(dbType)) {
            showError('不支持的数据库类型：' + dbType);
            return;
        }
        if (!version) { showError('请填写驱动版本'); return; }
        if (version.indexOf('_') >= 0) {
            showError('驱动版本不能包含下划线 _（会破坏资源目录命名规则）');
            return;
        }

        const file = jarFileInput.files[0];

        if (isEditMode) {
            const id = editIdInput.value;
            const formData = new FormData();
            formData.append('id', id);
            formData.append('dbType', dbType);
            formData.append('version', version);
            if (file) {
                formData.append('file', file);
                formData.append('fileName', file.name);
            }
            try {
                const resp = await fetch(API_BASE + '/update', { method: 'POST', body: formData });
                const data = await resp.json();
                if (data.success) { loadDrivers(); closeModal(); }
                else showError(data.error || '更新失败');
            } catch (err) { showError('网络错误：' + err.message); }
        } else {
            if (!file) { showError('请选择 Jar 文件'); return; }
            const formData = new FormData();
            formData.append('dbType', dbType);
            formData.append('version', version);
            formData.append('file', file);
            formData.append('fileName', file.name);
            try {
                const resp = await fetch(API_BASE + '/upload', { method: 'POST', body: formData });
                const data = await resp.json();
                if (data.success) { loadDrivers(); closeModal(); }
                else showError(data.error || '上传失败');
            } catch (err) { showError('网络错误：' + err.message); }
        }
    }

    // ==================== 资源目录 ====================

    async function openResourceDirModal() {
        resourceDirModal.style.display = 'flex';
        resourceDirInfo.textContent = '';
        resourceDirError.textContent = '';
        resourceDirList.innerHTML = '<div class="loading">扫描中...</div>';
        syncResourceDirBtn.disabled = false;

        try {
            const resp = await fetch(API_BASE + '/resource/list');
            const data = await resp.json();
            if (!data.success) {
                resourceDirError.textContent = '❌ ' + (data.error || '扫描失败');
                resourceDirList.innerHTML = '';
                return;
            }
            resourceDirInfo.innerHTML = `📁 目录：<code>${escapeHtml(data.dir || '')}</code>
                 · 共发现 <b>${data.items ? data.items.length : 0}</b> 个驱动 jar`;
            if (!data.items || data.items.length === 0) {
                resourceDirList.innerHTML = '<div class="empty-tip">目录为空，或文件名不符合 {dbType}_{version}_{fileName}.jar 规范</div>';
                syncResourceDirBtn.disabled = true;
                return;
            }
            let html = `<table class="data-table" style="width:100%;border-collapse:collapse;font-size:13px;">
                <thead><tr>
                    <th>类型</th><th>版本</th><th>原始文件名</th><th>大小</th>
                </tr></thead><tbody>`;
            data.items.forEach(it => {
                html += `<tr>
                    <td><span class="badge ${escapeAttr(it.dbType || 'default')}">${escapeHtml(it.dbType)}</span></td>
                    <td>${escapeHtml(it.version)}</td>
                    <td>${escapeHtml(it.fileName)}</td>
                    <td>${it.size != null ? (it.size/1024).toFixed(1) + ' KB' : '-'}</td>
                </tr>`;
            });
            html += `</tbody></table>`;
            resourceDirList.innerHTML = html;
        } catch (e) {
            resourceDirError.textContent = '❌ 请求异常：' + e.message;
        }
    }

    async function syncAll() {
        syncResourceDirBtn.disabled = true;
        syncResourceDirBtn.textContent = '⏳ 导入中...';
        try {
            const resp = await fetch(API_BASE + '/resource/sync', { method: 'POST' });
            const data = await resp.json();
            if (data.success) {
                alert(`✅ 导入完成，新增 ${data.imported} 条`);
                await loadDrivers();
                await openResourceDirModal();
            } else {
                alert('❌ 导入失败：' + (data.error || '未知'));
            }
        } catch (e) {
            alert('请求异常：' + e.message);
        } finally {
            syncResourceDirBtn.disabled = false;
            syncResourceDirBtn.textContent = '🔄 全部导入到数据库';
        }
    }

    // ==================== 事件绑定 ====================

    if (addBtn) addBtn.addEventListener('click', openAddModal);
    if (saveBtn) saveBtn.addEventListener('click', saveDriver);
    if (cancelBtn) cancelBtn.addEventListener('click', closeModal);
    if (closeSpan) closeSpan.addEventListener('click', closeModal);
    if (filterDbType) filterDbType.addEventListener('change', loadDrivers);
    window.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
        if (e.target === resourceDirModal) resourceDirModal.style.display = 'none';
    });

    if (importFromDirBtn) importFromDirBtn.addEventListener('click', openResourceDirModal);
    if (closeResourceDirBtn) closeResourceDirBtn.addEventListener('click', () => {
        resourceDirModal.style.display = 'none';
    });
    if (closeResourceDirFooterBtn) closeResourceDirFooterBtn.addEventListener('click', () => {
        resourceDirModal.style.display = 'none';
    });
    if (syncResourceDirBtn) syncResourceDirBtn.addEventListener('click', syncAll);

    loadDrivers();
})();