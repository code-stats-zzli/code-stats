(function() {
    // 独立定义 apiUrl 函数，不依赖外部 global.js
    function getContextPath() {
        var path = window.location.pathname;
        var parts = path.split('/');
        if (parts.length > 1 && parts[1]) {
            return '/' + parts[1];
        }
        return '';
    }
    var CONTEXT_PATH = getContextPath();
    function apiUrl(path) {
        if (path.startsWith('/')) {
            return CONTEXT_PATH + path;
        }
        return CONTEXT_PATH + '/' + path;
    }

    const API_BASE = apiUrl('/driver/api');
    let drivers = [];

    // DOM 元素
    const tableBody = document.getElementById('driverTableBody');
    const modal = document.getElementById('driverModal');
    const modalTitle = document.getElementById('modalTitle');
    const editIdInput = document.getElementById('editId');
    const driverNameInput = document.getElementById('driverName');
    const jarFileInput = document.getElementById('jarFile');
    const fileOptionalHint = document.getElementById('fileOptionalHint');
    const saveBtn = document.getElementById('saveDriverBtn');
    const cancelBtn = document.getElementById('cancelDriverBtn');
    const addBtn = document.getElementById('addDriverBtn');
    const closeSpan = document.getElementById('closeModalBtn');

    let isEditMode = false;

    // 加载驱动列表
    async function loadDrivers() {
        try {
            const resp = await fetch(API_BASE + '/list');
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            drivers = await resp.json();
            renderTable();
        } catch (err) {
            console.error(err);
            tableBody.innerHTML = '<tr><td colspan="4" style="text-align:center;color:red;">加载驱动列表失败</td></tr>';
        }
    }

    // 渲染表格
    function renderTable() {
        if (!drivers.length) {
            tableBody.innerHTML = '<tr class="empty-row"><td colspan="4">暂无驱动，请点击“新增驱动”上传 jar 文件</td></tr>';
            return;
        }
        tableBody.innerHTML = drivers.map(d => `
            <tr>
                <td>${escapeHtml(d.name)}</td>
                <td>${escapeHtml(d.fileName)}</td>
                <td>${new Date(d.createTime).toLocaleString()}</td>
                <td class="action-btns">
                    <button class="edit-driver" data-id="${d.id}" data-name="${escapeHtml(d.name)}">✏️ 编辑</button>
                    <button class="download-driver" data-id="${d.id}">⬇️ 下载</button>
                    <button class="delete-driver" data-id="${d.id}">🗑️ 删除</button>
                </td>
            </tr>
        `).join('');

        // 编辑事件
        document.querySelectorAll('.edit-driver').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const id = btn.dataset.id;
                const name = btn.dataset.name;
                openEditModal(id, name);
            });
        });
        // 下载事件
        document.querySelectorAll('.download-driver').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const id = btn.dataset.id;
                window.open(API_BASE + '/download?id=' + id, '_blank');
            });
        });
        // 删除事件
        document.querySelectorAll('.delete-driver').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                if (confirm('确定删除该驱动吗？')) {
                    const id = btn.dataset.id;
                    const resp = await fetch(API_BASE + '/delete?id=' + id, { method: 'POST' });
                    const data = await resp.json();
                    if (data.success) loadDrivers();
                    else alert('删除失败：' + (data.error || '未知错误'));
                }
            });
        });
    }

    // 打开新增模态框
    function openAddModal() {
        isEditMode = false;
        editIdInput.value = '';
        driverNameInput.value = '';
        jarFileInput.value = '';
        modalTitle.innerText = '新增驱动';
        fileOptionalHint.textContent = '（必选）';
        modal.style.display = 'flex';
    }

    // 打开编辑模态框
    function openEditModal(id, name) {
        isEditMode = true;
        editIdInput.value = id;
        driverNameInput.value = name;
        modalTitle.innerText = '编辑驱动';
        fileOptionalHint.textContent = '（可选，不选则保持原有JAR）';
        jarFileInput.value = '';
        modal.style.display = 'flex';
    }

    // 关闭模态框
    function closeModal() {
        modal.style.display = 'none';
    }

    // 保存驱动（新增或编辑）
    async function saveDriver() {
        const name = driverNameInput.value.trim();
        if (!name) {
            alert('请填写驱动名称');
            return;
        }

        if (isEditMode) {
            // 编辑模式
            const id = editIdInput.value;
            const file = jarFileInput.files[0];
            const formData = new FormData();
            formData.append('id', id);
            formData.append('name', name);
            if (file) {
                formData.append('file', file);
                formData.append('fileName', file.name);
            }
            try {
                const resp = await fetch(API_BASE + '/update', { method: 'POST', body: formData });
                const data = await resp.json();
                if (data.success) {
                    loadDrivers();
                    closeModal();
                } else {
                    alert('更新失败：' + (data.error || '未知错误'));
                }
            } catch (err) {
                alert('网络错误：' + err.message);
            }
        } else {
            // 新增模式
            const file = jarFileInput.files[0];
            if (!file) {
                alert('请选择 Jar 文件');
                return;
            }
            const formData = new FormData();
            formData.append('file', file);
            formData.append('name', name);
            formData.append('fileName', file.name);
            try {
                const resp = await fetch(API_BASE + '/upload', { method: 'POST', body: formData });
                const data = await resp.json();
                if (data.success) {
                    loadDrivers();
                    closeModal();
                } else {
                    alert('上传失败：' + (data.error || '未知错误'));
                }
            } catch (err) {
                alert('网络错误：' + err.message);
            }
        }
    }

    // 绑定事件
    if (addBtn) addBtn.addEventListener('click', openAddModal);
    if (saveBtn) saveBtn.addEventListener('click', saveDriver);
    if (cancelBtn) cancelBtn.addEventListener('click', closeModal);
    if (closeSpan) closeSpan.addEventListener('click', closeModal);
    window.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });

    // 辅助函数：转义HTML
    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    // 初始化加载
    loadDrivers();
})();