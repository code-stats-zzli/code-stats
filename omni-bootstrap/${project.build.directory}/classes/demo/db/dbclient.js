(function() {
    const API_BASE = window.apiUrl('/dbclient/api');

    const treeRoot = document.getElementById('treeRoot');
    const propertiesContent = document.getElementById('propertiesContent');
    const dataContent = document.getElementById('dataContent');
    const sqlResultArea = document.getElementById('sqlResultArea');
    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabPanes = document.querySelectorAll('.tab-pane');

    let currentDb = null;
    let currentTable = null;
    let currentPage = 1;
    let currentPageSize = 20;
    let totalRecords = 0;

    // ========== 原有树形加载逻辑保持不变（略作修改以支持选择后加载数据） ==========
    async function fetchJSON(url) {
        const res = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return await res.json();
    }

    function createTreeItem(text, type, onClick, isExpandable = false) {
        // 与原逻辑相同，略...
        const li = document.createElement('li');
        li.className = 'tree-item';
        const toggle = document.createElement('span');
        toggle.className = 'tree-toggle';
        if (isExpandable) {
            toggle.classList.add('collapsed');
        } else {
            toggle.classList.add('leaf');
        }
        li.appendChild(toggle);
        const label = document.createElement('span');
        label.textContent = text;
        li.appendChild(label);
        if (isExpandable) {
            li.addEventListener('click', (e) => {
                e.stopPropagation();
                const childrenUl = li.nextElementSibling;
                if (childrenUl && childrenUl.classList.contains('tree-children')) {
                    const isOpen = childrenUl.classList.contains('open');
                    if (isOpen) {
                        childrenUl.classList.remove('open');
                        toggle.classList.replace('expanded', 'collapsed');
                    } else {
                        childrenUl.classList.add('open');
                        toggle.classList.replace('collapsed', 'expanded');
                    }
                }
                if (onClick) onClick(li);
            });
        } else {
            li.addEventListener('click', (e) => {
                e.stopPropagation();
                if (onClick) onClick(li);
            });
        }
        return li;
    }

    function appendChildren(li, childrenUl) {
        li.insertAdjacentElement('afterend', childrenUl);
    }

    function setActive(node) {
        document.querySelectorAll('.tree-item.active').forEach(el => el.classList.remove('active'));
        if (node) node.classList.add('active');
    }

    function initConnections() {
        const connections = ['localhost'];
        connections.forEach(connName => {
            const connItem = createTreeItem(connName, 'connection', async (item) => {
                setActive(item);
                const childrenUl = item.nextElementSibling;
                if (childrenUl && childrenUl.children.length === 0) {
                    await loadDatabases(childrenUl, connName);
                }
            }, true);
            treeRoot.appendChild(connItem);
            const dbUl = document.createElement('ul');
            dbUl.className = 'tree-children';
            appendChildren(connItem, dbUl);
        });
    }

    async function loadDatabases(dbUl, connName) {
        try {
            const databases = await fetchJSON(API_BASE + '/databases');
            databases.forEach(dbName => {
                const dbItem = createTreeItem(dbName, 'database', async (item) => {
                    setActive(item);
                    currentDb = dbName;
                    const childrenUl = item.nextElementSibling;
                    if (childrenUl && childrenUl.children.length === 0) {
                        await loadTables(childrenUl, dbName);
                    }
                }, true);
                dbUl.appendChild(dbItem);
                const tableUl = document.createElement('ul');
                tableUl.className = 'tree-children';
                appendChildren(dbItem, tableUl);
            });
            if (databases.length === 0) {
                dbUl.innerHTML = '<li class="empty-state" style="color:#aaa;padding:8px 20px;">No databases</li>';
            }
        } catch (err) {
            dbUl.innerHTML = '<li class="error" style="color:red;padding:8px 20px;">Failed to load</li>';
        }
    }

    async function loadTables(tableUl, dbName) {
        try {
            const tables = await fetchJSON(API_BASE + '/tables?db=' + encodeURIComponent(dbName));
            tables.forEach(table => {
                const tableItem = createTreeItem(table.name, 'table', (item) => {
                    setActive(item);
                    selectTable(dbName, table.name);
                }, true);
                const detailUl = document.createElement('ul');
                detailUl.className = 'tree-children';
                const colItem = createTreeItem('列', 'columns', async (colLi) => {
                    const childrenUl = colLi.nextElementSibling;
                    if (childrenUl && childrenUl.children.length === 0) {
                        await loadColumns(childrenUl, dbName, table.name);
                    }
                }, true);
                detailUl.appendChild(colItem);
                const colChildrenUl = document.createElement('ul');
                colChildrenUl.className = 'tree-children';
                appendChildren(colItem, colChildrenUl);
                ['视图', '索引', '存储过程', '触发器', '事件'].forEach(label => {
                    const staticItem = createTreeItem(label, 'static', null, false);
                    detailUl.appendChild(staticItem);
                });
                tableUl.appendChild(tableItem);
                appendChildren(tableItem, detailUl);
            });
            if (tables.length === 0) {
                tableUl.innerHTML = '<li class="empty-state" style="color:#aaa;padding:8px 20px;">No tables</li>';
            }
        } catch (err) {
            tableUl.innerHTML = '<li class="error" style="color:red;padding:8px 20px;">Error</li>';
        }
    }

    async function loadColumns(colUl, dbName, tableName) {
        try {
            const columns = await fetchJSON(API_BASE + '/columns?db=' + encodeURIComponent(dbName) + '&table=' + encodeURIComponent(tableName));
            columns.forEach(col => {
                const colText = `${col.columnName} (${col.typeName})`;
                const colLeaf = createTreeItem(colText, 'column', null, false);
                colUl.appendChild(colLeaf);
            });
            if (columns.length === 0) {
                colUl.innerHTML = '<li class="empty-state" style="color:#aaa;padding:8px 20px;">No columns</li>';
            }
        } catch (err) {
            colUl.innerHTML = '<li class="error" style="color:red;padding:8px 20px;">Error</li>';
        }
    }

    // ========== 属性面板（原有） ==========
    async function selectTable(dbName, tableName) {
        currentDb = dbName;
        currentTable = tableName;
        currentPage = 1;
        // 加载属性
        switchTab('properties');
        try {
            const columns = await fetchJSON(API_BASE + '/columns?db=' + encodeURIComponent(dbName) + '&table=' + encodeURIComponent(tableName));
            let html = `<h2>${dbName}.${tableName}</h2>`;
            html += '<table class="props-table"><tbody>';
            html += '<tr><td>Engine</td><td>InnoDB</td></tr>';
            html += '<tr><td>Character Set</td><td>utf8mb4</td></tr>';
            html += '<tr><td>Collation</td><td>utf8mb4_0900_ai_ci</td></tr>';
            html += '</tbody></table>';
            html += '<h3>Columns</h3>';
            const displayCols = ['Column Name', 'Type', 'Size', 'Nullable', 'Auto Inc', 'Remarks'];
            const mapping = {
                'Column Name': 'columnName',
                'Type': 'typeName',
                'Size': 'columnSize',
                'Nullable': 'nullable',
                'Auto Inc': 'autoIncrement',
                'Remarks': 'remarks'
            };
            html += '<table class="data-table"><thead><tr>';
            displayCols.forEach(col => { html += `<th>${col}</th>`; });
            html += '</tr></thead><tbody>';
            columns.forEach(row => {
                html += '<tr>';
                displayCols.forEach(col => {
                    const key = mapping[col];
                    let val = row[key] !== undefined ? row[key] : '';
                    if (typeof val === 'boolean') val = val ? 'YES' : 'NO';
                    html += `<td>${escapeHtml(val)}</td>`;
                });
                html += '</tr>';
            });
            html += '</tbody></table>';
            propertiesContent.innerHTML = html;
            // 自动加载数据（如果数据tab处于激活状态则刷新）
            if (document.querySelector('.tab-btn[data-tab="data"]').classList.contains('active')) {
                loadTableData();
            }
        } catch (err) {
            propertiesContent.innerHTML = `<div class="error">Failed to load: ${err.message}</div>`;
        }
    }

    // ========== 数据面板：分页加载表数据 ==========
    async function loadTableData() {
        if (!currentDb || !currentTable) {
            dataContent.innerHTML = '<p>请先从左侧选择一张表</p>';
            return;
        }
        dataContent.innerHTML = '<div class="loading">加载中...</div>';
        try {
            const url = `${API_BASE}/tableData?db=${encodeURIComponent(currentDb)}&table=${encodeURIComponent(currentTable)}&page=${currentPage}&pageSize=${currentPageSize}`;
            const res = await fetch(url);
            const result = await res.json();
            if (result.error) {
                dataContent.innerHTML = `<div class="error">${result.error}</div>`;
                return;
            }
            totalRecords = result.total || 0;
            renderDataTable(result.records);
            renderPagination();
        } catch (err) {
            dataContent.innerHTML = `<div class="error">${err.message}</div>`;
        }
    }

    function renderDataTable(records) {
        if (!records || records.length === 0) {
            dataContent.innerHTML = '<p>该表无数据</p>';
            return;
        }
        const columns = Object.keys(records[0]);
        let html = '<div style="overflow-x: auto;"><table class="data-table"><thead><tr>';
        columns.forEach(col => html += `<th>${escapeHtml(col)}</th>`);
        html += '</tr></thead><tbody>';
        records.forEach(row => {
            html += '<tr>';
            columns.forEach(col => {
                let val = row[col];
                if (val === null || val === undefined) val = '';
                html += `<td>${escapeHtml(String(val))}</td>`;
            });
            html += '</tr>';
        });
        html += '</tbody></table></div>';
        dataContent.innerHTML = html;
    }

    function renderPagination() {
        const totalPages = Math.ceil(totalRecords / currentPageSize);
        if (totalPages <= 1) {
            document.getElementById('paginationBar').innerHTML = '';
            return;
        }
        let html = '<div class="pagination">';
        html += `<button ${currentPage === 1 ? 'disabled' : ''} data-page="${currentPage-1}">上一页</button>`;
        for (let i = 1; i <= totalPages; i++) {
            if (i === currentPage) {
                html += `<button class="active" disabled>${i}</button>`;
            } else {
                html += `<button data-page="${i}">${i}</button>`;
            }
        }
        html += `<button ${currentPage === totalPages ? 'disabled' : ''} data-page="${currentPage+1}">下一页</button>`;
        html += ` <span>共 ${totalRecords} 条</span>`;
        html += '</div>';
        document.getElementById('paginationBar').innerHTML = html;
        document.querySelectorAll('#paginationBar button[data-page]').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const page = parseInt(btn.dataset.page);
                if (!isNaN(page) && page !== currentPage) {
                    currentPage = page;
                    loadTableData();
                }
            });
        });
    }

    // ========== SQL查询 ==========
    async function executeSql() {
        const sql = document.getElementById('sqlEditor').value.trim();
        if (!sql) {
            alert('请输入SQL语句');
            return;
        }
        if (!currentDb) {
            alert('请先从左侧选择一个数据库');
            return;
        }
        sqlResultArea.innerHTML = '<div class="loading">执行中...</div>';
        try {
            const resp = await fetch(API_BASE + '/sqlQuery', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ db: currentDb, sql: sql })
            });
            const data = await resp.json();
            if (data.error) {
                sqlResultArea.innerHTML = `<div class="error">${data.error}</div>`;
                return;
            }
            if (Array.isArray(data) && data.length > 0) {
                const columns = Object.keys(data[0]);
                let html = '<table class="sql-result-table"><thead><tr>';
                columns.forEach(col => html += `<th>${escapeHtml(col)}</th>`);
                html += '</tr></thead><tbody>';
                data.forEach(row => {
                    html += '<tr>';
                    columns.forEach(col => {
                        let val = row[col];
                        if (val === null || val === undefined) val = '';
                        html += `<td>${escapeHtml(String(val))}</td>`;
                    });
                    html += '</tr>';
                });
                html += '</tbody></table>';
                sqlResultArea.innerHTML = html;
            } else {
                sqlResultArea.innerHTML = '<p>查询成功，无结果返回。</p>';
            }
        } catch (err) {
            sqlResultArea.innerHTML = `<div class="error">${err.message}</div>`;
        }
    }

    // ========== Tab切换逻辑 ==========
    function switchTab(tabName) {
        tabBtns.forEach(btn => {
            if (btn.dataset.tab === tabName) btn.classList.add('active');
            else btn.classList.remove('active');
        });
        tabPanes.forEach(pane => {
            const id = pane.id;
            if ((tabName === 'properties' && id === 'tab-properties') ||
                (tabName === 'data' && id === 'tab-data') ||
                (tabName === 'diagram' && id === 'tab-diagram') ||
                (tabName === 'sql' && id === 'tab-sql')) {
                pane.classList.add('active');
            } else {
                pane.classList.remove('active');
            }
        });
        if (tabName === 'data' && currentTable) {
            loadTableData();
        }
    }

    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            switchTab(btn.dataset.tab);
        });
    });

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    // 初始化
    initConnections();
    document.getElementById('executeSqlBtn')?.addEventListener('click', executeSql);
})();