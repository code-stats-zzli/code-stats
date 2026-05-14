(function() {
    const API_BASE = window.apiUrl('/dbclient/api');

    const treeRoot = document.getElementById('treeRoot');
    const propertiesContent = document.getElementById('propertiesContent');
    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabPanes = document.querySelectorAll('.tab-pane');

    let currentDb = null;
    let currentTable = null;

    async function fetchJSON(url) {
        const res = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return await res.json();
    }

    function createTreeItem(text, type, onClick, isExpandable = false) {
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
        const connections = ['localhost', 'localhost:3307'];
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

    async function selectTable(dbName, tableName) {
        currentTable = tableName;
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
            html += '<table><thead><tr>';
            displayCols.forEach(col => { html += `<th>${col}</th>`; });
            html += '</tr></thead><tbody>';
            columns.forEach(row => {
                html += '<tr>';
                displayCols.forEach(col => {
                    const key = mapping[col];
                    let val = row[key] !== undefined ? row[key] : '';
                    if (typeof val === 'boolean') val = val ? 'YES' : 'NO';
                    html += `<td>${val}</td>`;
                });
                html += '</tr>';
            });
            html += '</tbody></table>';
            propertiesContent.innerHTML = html;
        } catch (err) {
            propertiesContent.innerHTML = `<div class="error">Failed to load: ${err.message}</div>`;
        }
    }

    function switchTab(tabName) {
        tabBtns.forEach(btn => {
            if (btn.dataset.tab === tabName) btn.classList.add('active');
            else btn.classList.remove('active');
        });
        tabPanes.forEach(pane => {
            if (pane.id === `tab-${tabName}`) pane.classList.add('active');
            else pane.classList.remove('active');
        });
    }

    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            switchTab(btn.dataset.tab);
        });
    });

    initConnections();
})();