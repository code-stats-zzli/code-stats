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
    const driverNameInput = document.getElementById('driverName');
    const driverVersionInput = document.getElementById('driverVersion');
    const jarFileInput = document.getElementById('jarFile');
    const fileOptionalHint = document.getElementById('fileOptionalHint');
    const saveBtn = document.getElementById('saveDriverBtn');
    const cancelBtn = document.getElementById('cancelDriverBtn');
    const addBtn = document.getElementById('addDriverBtn');
    const closeSpan = document.getElementById('closeModalBtn');
    const filterDbType = document.getElementById('filterDbType');
    const formError = document.getElementById('formError');

    let isEditMode = false;

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }
    function escapeAttr(str) {
        return String(str || '').replace(/&/g,'&amp;').replace(/</g,'&lt;')
            .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }
    function showError(msg) { formError.textContent = msg || ''; }

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
            tableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:red;">加载驱动列表失败</td></tr>';
        }
    }

    function renderTable() {
        if (!drivers.length) {
            tableBody.innerHTML = '<tr class="empty-row"><td colspan="6">暂无驱动，请点击「新增驱动」上传 jar 文件</td></tr>';
            return;
        }
        tableBody.innerHTML = drivers.map(d => `
            <tr>
                <td>${escapeHtml(d.name)}</td>
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
                if (!confirm('确定删除该驱动吗？')) return;
                const id = btn.dataset.id;
                const resp = await fetch(API_BASE + '/delete?id=' + encodeURIComponent(id), { method: 'POST' });
                const data = await resp.json();
                if (data.success) loadDrivers();
                else alert('删除失败：' + (data.error || '未知错误'));
            });
        });
    }

    function openAddModal() {
        isEditMode = false;
        editIdInput.value = '';
        dbTypeSelect.value = 'mysql';
        driverNameInput.value = '';
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
        driverNameInput.value = d.name || '';
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
        const name = driverNameInput.value.trim();
        const version = driverVersionInput.value.trim();

        if (!dbType) { showError('请选择数据库类型'); return; }
        if (!name) { showError('请填写驱动名称'); return; }
        if (!version) { showError('请填写驱动版本'); return; }

        const file = jarFileInput.files[0];

        if (isEditMode) {
            const id = editIdInput.value;
            const formData = new FormData();
            formData.append('id', id);
            formData.append('dbType', dbType);
            formData.append('name', name);
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
            formData.append('name', name);
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

    if (addBtn) addBtn.addEventListener('click', openAddModal);
    if (saveBtn) saveBtn.addEventListener('click', saveDriver);
    if (cancelBtn) cancelBtn.addEventListener('click', closeModal);
    if (closeSpan) closeSpan.addEventListener('click', closeModal);
    if (filterDbType) filterDbType.addEventListener('change', loadDrivers);
    window.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });

    loadDrivers();
})();