(function () {
    'use strict';

    // ==================== Context Path ====================
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

    const ALLOWED_DB_TYPES = ['mysql', 'oracle', 'sqlserver', 'postgresql'];
    const DB_TYPE_LABELS = {
        mysql: 'MySQL',
        oracle: 'Oracle',
        sqlserver: 'SQL Server',
        postgresql: 'PostgreSQL'
    };
    const VERSION_PATTERN = /(\d+(?:\.\d+){1,3})/;

    // ==================== 文件名识别 ====================
    function detectDbTypeFromFileName(fileName) {
        if (!fileName) return null;
        const lower = fileName.toLowerCase();
        if (lower.includes('mysql')) return 'mysql';
        if (lower.includes('ojdbc') || lower.includes('oracle')) return 'oracle';
        if (lower.includes('postgresql') || lower.includes('postgres')
            || lower.includes('-pg-') || lower.endsWith('-pg.jar')) return 'postgresql';
        if (lower.includes('mssql') || lower.includes('sqljdbc') || lower.includes('sqlserver')) {
            return 'sqlserver';
        }
        return null;
    }
    function detectVersionFromFileName(fileName) {
        if (!fileName) return null;
        const m = fileName.match(VERSION_PATTERN);
        return m ? m[1] : null;
    }

    // ==================== DOM ====================
    const $ = id => document.getElementById(id);

    const tableBody            = $('driverTableBody');
    const modal                = $('driverModal');
    const modalTitle           = $('modalTitle');
    const editIdInput          = $('editId');
    const dbTypeSelect         = $('dbType');
    const driverVersionInput   = $('driverVersion');
    const jarFileInput         = $('jarFile');
    const filePicker           = $('filePicker');
    const filePickerName       = $('filePickerName');
    const fileOptionalHint     = $('fileOptionalHint');
    const detectBanner         = $('detectBanner');
    const saveBtn              = $('saveDriverBtn');
    const cancelBtn            = $('cancelDriverBtn');
    const addBtn               = $('addDriverBtn');
    const closeSpan            = $('closeModalBtn');
    const filterDbType         = $('filterDbType');
    const formError            = $('formError');

    const importFromDirBtn         = $('importFromDirBtn');
    const resourceDirModal         = $('resourceDirModal');
    const closeResourceDirBtn      = $('closeResourceDirBtn');
    const closeResourceDirFooterBtn= $('closeResourceDirFooterBtn');
    const syncResourceDirBtn       = $('syncResourceDirBtn');
    const resourceDirInfo          = $('resourceDirInfo');
    const resourceDirList          = $('resourceDirList');
    const resourceDirError         = $('resourceDirError');

    let drivers = [];
    let isEditMode = false;

    // ==================== 工具 ====================
    function escapeHtml(str) {
        if (!str) return '';
        return String(str).replace(/[&<>"]/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
        }[m]));
    }
    function escapeAttr(str) {
        return String(str == null ? '' : str)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
    function showError(msg) { formError.textContent = msg || ''; }

    function setDetectBanner(type, html) {
        detectBanner.className = 'detect-banner ' + type;
        detectBanner.innerHTML = html;
    }

    // ==================== ★ 核心改动：使用 entry/list 合并视图 ====================
    async function loadDrivers() {
        try {
            const type = filterDbType ? filterDbType.value : '';
            const resp = await fetch(API_BASE + '/entry/list');
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            const data = await resp.json();
            if (!data.success) throw new Error(data.error || '加载失败');
            let items = data.items || [];
            if (type) items = items.filter(d => d.dbType === type);
            drivers = items;
            renderTable();
        } catch (err) {
            console.error(err);
            tableBody.innerHTML =
                '<tr><td colspan="6" style="text-align:center;color:red;">加载驱动列表失败</td></tr>';
        }
    }

    function renderTable() {
        if (!drivers.length) {
            tableBody.innerHTML =
                '<tr class="empty-row"><td colspan="6">暂无驱动，请点击「新增驱动」上传 jar 文件</td></tr>';
            return;
        }
        tableBody.innerHTML = drivers.map(d => {
            const storageName = d.dbType + '_' + d.version + '_' + d.fileName;
            const sourceLabel = d.source === 'db' ? '数据库'
                : d.source === 'dir' ? '目录'
                    : '双来源';
            const sourceClass = d.source === 'db' ? 'src-db'
                : d.source === 'dir' ? 'src-dir'
                    : 'src-both';
            const hasDb = d.source === 'db' || d.source === 'both';
            return `
                <tr>
                    <td><span class="badge ${escapeAttr(d.dbType || 'default')}">${escapeHtml(d.dbType || '-')}</span></td>
                    <td>${escapeHtml(d.version || '-')}</td>
                    <td title="${escapeAttr(d.fileName)}">${escapeHtml(d.fileName)}</td>
                    <td title="${escapeAttr(storageName)}" style="font-family:monospace;font-size:12px;color:#64748b;">${escapeHtml(storageName)}</td>
                    <td><span class="src-badge ${sourceClass}">${sourceLabel}</span></td>
                    <td class="action-btns">
                        ${hasDb ? `
                            <button class="edit-driver" data-key="${escapeAttr(d.key)}">✏️ 编辑</button>
                            <button class="download-driver" data-key="${escapeAttr(d.key)}">⬇️ 下载</button>
                        ` : `
                            <button class="import-driver" data-key="${escapeAttr(d.key)}">📥 导入数据库</button>
                        `}
                        <button class="delete-driver" data-key="${escapeAttr(d.key)}">🗑️ 删除</button>
                    </td>
                </tr>
            `;
        }).join('');

        // 编辑（仅 DB 来源）
        document.querySelectorAll('.edit-driver').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const d = drivers.find(x => x.key === btn.dataset.key);
                if (d && d.id) openEditModal(d);
            });
        });
        // 下载（仅 DB 来源）
        document.querySelectorAll('.download-driver').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const d = drivers.find(x => x.key === btn.dataset.key);
                if (d && d.id) {
                    window.open(API_BASE + '/download?id=' + encodeURIComponent(d.id), '_blank');
                }
            });
        });
        // 目录来源：导入数据库
        document.querySelectorAll('.import-driver').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const d = drivers.find(x => x.key === btn.dataset.key);
                if (!d) return;
                if (confirm(`将从 resources/drivers 目录同步所有驱动到数据库\n（含「${DB_TYPE_LABELS[d.dbType] || d.dbType} ${d.version}」）\n\n继续？`)) {
                    syncAll(true);
                }
            });
        });
        // 删除（DB / 目录都会删）
        document.querySelectorAll('.delete-driver').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                const d = drivers.find(x => x.key === btn.dataset.key);
                if (!d) return;
                if (!confirm(`确定删除驱动 ${DB_TYPE_LABELS[d.dbType] || d.dbType} ${d.version} 吗？\n（数据库记录 + resources/drivers 目录文件都会删除）`)) return;
                try {
                    const resp = await fetch(API_BASE + '/entry/delete', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ key: d.key, dbType: d.dbType, version: d.version })
                    });
                    const data = await resp.json();
                    if (data.success) loadDrivers();
                    else alert('删除失败：' + (data.error || '未知错误'));
                } catch (err) {
                    alert('请求异常：' + err.message);
                }
            });
        });
    }

    // ==================== 新增 / 编辑弹窗 ====================
    function resetFilePicker() {
        filePicker.classList.remove('has-file');
        filePickerName.textContent = '点击选择 .jar 文件';
        jarFileInput.value = '';
    }

    function openAddModal() {
        isEditMode = false;
        editIdInput.value = '';
        dbTypeSelect.value = 'mysql';
        driverVersionInput.value = '';
        resetFilePicker();
        modalTitle.innerText = '新增驱动';
        fileOptionalHint.textContent = '*';
        setDetectBanner('info', '选择文件后自动识别数据库类型与版本');
        showError('');
        modal.style.display = 'flex';
    }

    function openEditModal(d) {
        isEditMode = true;
        editIdInput.value = d.id;
        dbTypeSelect.value = d.dbType || 'mysql';
        driverVersionInput.value = d.version || '';
        resetFilePicker();
        if (d.fileName) {
            filePicker.classList.add('has-file');
            filePickerName.textContent = d.fileName + '（不选则保持原文件）';
        }
        modalTitle.innerText = '编辑驱动';
        fileOptionalHint.textContent = '（可选）';
        setDetectBanner('info', '不选新文件则保持原有 jar');
        showError('');
        modal.style.display = 'flex';
    }

    function closeModal() { modal.style.display = 'none'; }

    // ==================== 文件选择 ====================
    filePicker.addEventListener('click', () => jarFileInput.click());

    jarFileInput.addEventListener('change', () => {
        const file = jarFileInput.files[0];
        if (!file) { resetFilePicker(); return; }
        if (!file.name.toLowerCase().endsWith('.jar')) {
            showError('只允许上传 .jar 文件');
            jarFileInput.value = '';
            resetFilePicker();
            return;
        }
        showError('');
        filePicker.classList.add('has-file');
        filePickerName.textContent = file.name + ' (' + (file.size / 1024).toFixed(1) + ' KB)';

        const detectedType = detectDbTypeFromFileName(file.name);
        const detectedVersion = detectVersionFromFileName(file.name);

        if (detectedType) dbTypeSelect.value = detectedType;
        if (detectedVersion) driverVersionInput.value = detectedVersion;

        const parts = [];
        if (detectedType) parts.push('<b>类型</b>：' + (DB_TYPE_LABELS[detectedType] || detectedType));
        if (detectedVersion) parts.push('<b>版本</b>：' + escapeHtml(detectedVersion));

        if (detectedType && detectedVersion) {
            setDetectBanner('success',
                '✅ 从文件名自动识别：' + parts.join(' · ') + '<br>请确认后提交，或手动修改');
        } else if (detectedType || detectedVersion) {
            setDetectBanner('warn',
                '⚠️ 部分识别：' + parts.join(' · ') + '<br>未识别的字段请手动填写');
        } else {
            setDetectBanner('warn',
                '⚠️ 未能从文件名识别类型与版本，请手动选择类型并填写版本');
        }
    });

    // ==================== 保存驱动 ====================
    async function saveDriver() {
        showError('');
        const dbType = dbTypeSelect.value;
        const version = driverVersionInput.value.trim();
        const file = jarFileInput.files[0];

        if (!ALLOWED_DB_TYPES.includes(dbType)) {
            showError('不支持的数据库类型：' + dbType); return;
        }
        if (!version) {
            showError('驱动版本不能为空，请手动填写');
            driverVersionInput.focus(); return;
        }
        if (version.indexOf('_') >= 0) {
            showError('驱动版本不能包含下划线 _'); return;
        }
        if (!isEditMode && !file) {
            showError('请选择 Jar 文件'); return;
        }

        saveBtn.disabled = true;
        saveBtn.textContent = '⏳ 提交中...';
        try {
            const formData = new FormData();
            if (isEditMode) formData.append('id', editIdInput.value);
            formData.append('dbType', dbType);
            formData.append('version', version);
            if (file) {
                formData.append('file', file);
                formData.append('fileName', file.name);
            }
            const url = isEditMode ? (API_BASE + '/update') : (API_BASE + '/upload');
            const resp = await fetch(url, { method: 'POST', body: formData });
            const data = await resp.json();
            if (data.success) {
                closeModal();
                await loadDrivers();
            } else {
                showError(data.error || '保存失败');
            }
        } catch (err) {
            showError('网络错误：' + err.message);
        } finally {
            saveBtn.disabled = false;
            saveBtn.textContent = '保存';
        }
    }

    // ==================== 资源目录导入 ====================
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
            resourceDirInfo.innerHTML =
                '📁 目录：<code>' + escapeHtml(data.dir || '') + '</code>' +
                ' · 共发现 <b>' + (data.items ? data.items.length : 0) + '</b> 个驱动 jar' +
                '<br><span style="color:#94a3b8;font-size:11px;">命名规范：{类型}_{版本}_{原始文件名}.jar</span>';

            if (!data.items || data.items.length === 0) {
                resourceDirList.innerHTML =
                    '<div style="text-align:center;padding:30px;color:#64748b;">目录为空，或文件名不符合 {类型}_{版本}_{文件名}.jar 规范</div>';
                syncResourceDirBtn.disabled = true;
                return;
            }
            let html = '<table style="width:100%;border-collapse:collapse;font-size:13px;">' +
                '<thead><tr style="background:rgba(0,102,255,0.1);">' +
                '<th style="padding:8px 12px;text-align:left;">类型</th>' +
                '<th style="padding:8px 12px;text-align:left;">版本</th>' +
                '<th style="padding:8px 12px;text-align:left;">原始文件名</th>' +
                '<th style="padding:8px 12px;text-align:left;">大小</th>' +
                '</tr></thead><tbody>';
            data.items.forEach(it => {
                html += '<tr style="border-bottom:1px solid #e2e8f0;">' +
                    '<td style="padding:8px 12px;"><span class="badge ' + escapeAttr(it.dbType || 'default') + '">' + escapeHtml(it.dbType) + '</span></td>' +
                    '<td style="padding:8px 12px;">' + escapeHtml(it.version) + '</td>' +
                    '<td style="padding:8px 12px;">' + escapeHtml(it.fileName) + '</td>' +
                    '<td style="padding:8px 12px;">' + (it.size != null ? (it.size / 1024).toFixed(1) + ' KB' : '-') + '</td>' +
                    '</tr>';
            });
            html += '</tbody></table>';
            resourceDirList.innerHTML = html;
        } catch (e) {
            resourceDirError.textContent = '❌ 请求异常：' + e.message;
        }
    }

    async function syncAll(silent) {
        syncResourceDirBtn.disabled = true;
        syncResourceDirBtn.textContent = '⏳ 导入中...';
        try {
            const resp = await fetch(API_BASE + '/resource/sync', { method: 'POST' });
            const data = await resp.json();
            if (data.success) {
                if (!silent) alert('✅ 导入完成，新增 ' + data.imported + ' 条');
                await loadDrivers();
                if (resourceDirModal.style.display === 'flex') await openResourceDirModal();
            } else {
                if (!silent) alert('❌ 导入失败：' + (data.error || '未知'));
            }
        } catch (e) {
            if (!silent) alert('请求异常：' + e.message);
        } finally {
            syncResourceDirBtn.disabled = false;
            syncResourceDirBtn.textContent = '🔄 全部导入到数据库';
        }
    }

    // ==================== 事件绑定 ====================
    addBtn.addEventListener('click', openAddModal);
    saveBtn.addEventListener('click', saveDriver);
    cancelBtn.addEventListener('click', closeModal);
    closeSpan.addEventListener('click', closeModal);
    filterDbType.addEventListener('change', loadDrivers);
    window.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
        if (e.target === resourceDirModal) resourceDirModal.style.display = 'none';
    });

    importFromDirBtn.addEventListener('click', openResourceDirModal);
    closeResourceDirBtn.addEventListener('click', () => {
        resourceDirModal.style.display = 'none';
    });
    closeResourceDirFooterBtn.addEventListener('click', () => {
        resourceDirModal.style.display = 'none';
    });
    syncResourceDirBtn.addEventListener('click', () => syncAll(false));

    loadDrivers();
})();