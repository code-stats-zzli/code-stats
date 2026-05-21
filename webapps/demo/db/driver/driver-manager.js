(function() {
    const API = window.apiUrl('/driver/api');
    let drivers = [];

    const tableBody = document.getElementById('driverTableBody');
    const modal = document.getElementById('modal');
    const modalTitle = document.getElementById('modalTitle');
    const editIdInput = document.getElementById('editId');
    const nameInput = document.getElementById('name');
    const jarFileInput = document.getElementById('jarFile');
    const fileGroup = document.getElementById('fileGroup');
    const fileOptionalHint = document.getElementById('fileOptionalHint');
    const saveBtn = document.getElementById('saveBtn');
    const cancelBtn = document.getElementById('cancelBtn');
    const uploadBtn = document.getElementById('uploadBtn');
    const closeSpan = document.querySelector('.close');

    let isEditMode = false;

    async function loadDrivers() {
        const resp = await fetch(API + '/list');
        drivers = await resp.json();
        renderTable();
    }

    function renderTable() {
        tableBody.innerHTML = drivers.map(d => `
            <tr>
                <td>${escapeHtml(d.name)}</td>
                <td>${escapeHtml(d.fileName)}</td>
                <td>${new Date(d.createTime).toLocaleString()}</td>
                <td class="action-btns">
                    <button data-action="edit" data-id="${d.id}" data-name="${escapeHtml(d.name)}">✏️ 编辑</button>
                    <button data-action="download" data-id="${d.id}">⬇️ 下载</button>
                    <button data-action="delete" data-id="${d.id}">🗑️ 删除</button>
                </td>
            </tr>
        `).join('');

        // 编辑按钮
        document.querySelectorAll('[data-action="edit"]').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const id = btn.dataset.id;
                const name = btn.dataset.name;
                openEditModal(id, name);
            });
        });
        // 下载按钮
        document.querySelectorAll('[data-action="download"]').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const id = btn.dataset.id;
                window.open(API + '/download?id=' + id, '_blank');
            });
        });
        // 删除按钮
        document.querySelectorAll('[data-action="delete"]').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                if (confirm('确定删除该驱动吗？')) {
                    const id = btn.dataset.id;
                    const resp = await fetch(API + '/delete?id=' + id, { method: 'POST' });
                    const data = await resp.json();
                    if (data.success) loadDrivers();
                    else alert('删除失败');
                }
            });
        });
    }

    function openEditModal(id, name) {
        isEditMode = true;
        editIdInput.value = id;
        nameInput.value = name;
        modalTitle.innerText = '编辑驱动';
        // 编辑时文件上传变为可选，提示文字修改
        fileOptionalHint.textContent = '（可选，不选则保持原有JAR）';
        jarFileInput.value = ''; // 清空文件选择
        modal.style.display = 'flex';
    }

    function openUploadModal() {
        isEditMode = false;
        editIdInput.value = '';
        nameInput.value = '';
        jarFileInput.value = '';
        modalTitle.innerText = '上传驱动';
        fileOptionalHint.textContent = '（必选）';
        modal.style.display = 'flex';
    }

    async function saveDriver() {
        const name = nameInput.value.trim();
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
            const resp = await fetch(API + '/update', {
                method: 'POST',
                body: formData
            });
            const data = await resp.json();
            if (data.success) {
                loadDrivers();
                closeModal();
            } else {
                alert('更新失败：' + (data.error || '未知错误'));
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
            const resp = await fetch(API + '/upload', {
                method: 'POST',
                body: formData
            });
            const data = await resp.json();
            if (data.success) {
                loadDrivers();
                closeModal();
            } else {
                alert('上传失败：' + (data.error || '未知错误'));
            }
        }
    }

    function closeModal() {
        modal.style.display = 'none';
    }

    uploadBtn.addEventListener('click', openUploadModal);
    saveBtn.addEventListener('click', saveDriver);
    cancelBtn.addEventListener('click', closeModal);
    closeSpan.addEventListener('click', closeModal);
    window.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    loadDrivers();
})();