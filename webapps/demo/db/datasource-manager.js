(function() {
    const API = window.apiUrl('/driver/api');
    let drivers = [];

    const tableBody = document.getElementById('driverTableBody');
    const modal = document.getElementById('modal');
    const modalTitle = document.getElementById('modalTitle');
    const nameInput = document.getElementById('name');
    const jarFileInput = document.getElementById('jarFile');
    const saveBtn = document.getElementById('saveBtn');
    const cancelBtn = document.getElementById('cancelBtn');
    const uploadBtn = document.getElementById('uploadBtn');
    const closeSpan = document.querySelector('.close');

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
                    <button data-action="download" data-id="${d.id}">⬇️ 下载</button>
                    <button data-action="delete" data-id="${d.id}">🗑️ 删除</button>
                </td>
            </tr>
        `).join('');

        document.querySelectorAll('[data-action="download"]').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const id = btn.dataset.id;
                window.open(API + '/download?id=' + id, '_blank');
            });
        });
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

    function openModal() {
        modalTitle.innerText = '上传驱动';
        nameInput.value = '';
        jarFileInput.value = '';
        modal.style.display = 'flex';
    }

    async function saveDriver() {
        const name = nameInput.value.trim();
        if (!name) {
            alert('请填写驱动名称');
            return;
        }
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
            alert('上传失败: ' + data.error);
        }
    }

    function closeModal() {
        modal.style.display = 'none';
    }

    uploadBtn.addEventListener('click', openModal);
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