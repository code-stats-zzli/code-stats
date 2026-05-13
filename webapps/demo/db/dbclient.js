(function() {
    const API_BASE = '/demo/dbclient/api';

    // 树根节点
    const treeRoot = document.getElementById('treeRoot');
    // 属性显示区
    const propertiesContent = document.getElementById('propertiesContent');
    // 选项卡
    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabPanes = document.querySelectorAll('.tab-pane');

    // 当前选中的数据库、表
    let currentDb = null;
    let currentTable = null;

    // ---------- 工具函数 ----------
    async function fetchJSON(url) {
        const res = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return await res.json();
    }

    // ---------- 树节点创建 ----------
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

        // 点击展开/折叠
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

    // 给节点追加子ul
    function appendChildren(li, childrenUl) {
        li.insertAdjacentElement('afterend', childrenUl);
    }

    // 高亮选中的节点
    function setActive(node) {
        document.querySelectorAll('.tree-item.active').forEach(el => el.classList.remove('active'));
        if (node) node.classList.add('active');
    }

    // ---------- 加载逻辑 ----------
    // 初始化：创建两个连接
    function initConnections() {
        const connections = ['localhost', 'localhost:3307'];
        connections.forEach(connName => {
            const connItem = createTreeItem(connName, 'connection', async (item) => {
                setActive(item);
                // 展开时加载数据库列表
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

    // 加载数据库列表（挂载到对应连接下）
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

    // 加载表列表
    async function loadTables(tableUl, dbName) {
        try {
            const tables = await fetchJSON(API_BASE + '/tables?db=' + encodeURIComponent(dbName));
            tables.forEach(table => {
                const tableItem = createTreeItem(table.name, 'table', (item) => {
                    setActive(item);
                    selectTable(dbName, table.name);
                }, true); // 可展开，点击展开则加载列

                // 预先创建列、视图、索引等子节点（静态占位）
                const detailUl = document.createElement('ul');
                detailUl.className = 'tree-children';
                // 列节点（点击会加载真实列信息）
                const colItem = createTreeItem('列', 'columns', async (colLi) => {
                    // 点击“列”时加载列信息
                    const childrenUl = colLi.nextElementSibling;
                    if (childrenUl && childrenUl.children.length === 0) {
                        await loadColumns(childrenUl, dbName, table.name);
                    }
                }, true);
                detailUl.appendChild(colItem);
                const colChildrenUl = document.createElement('ul');
                colChildrenUl.className = 'tree-children';
                appendChildren(colItem, colChildrenUl);

                // 其他静态节点
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

    // 加载列信息（树内仅显示列名）
    async function loadColumns(colUl, dbName, tableName) {
        try {
            const columns = await fetchJSON(API_BASE + '/columns?db=' + encodeURIComponent(dbName) + '&table=' + encodeURIComponent(tableName));
            columns.forEach(col => {
                // 列显示为 “name (type)” 形式
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

    // 选中表时右侧属性页展示
    async function selectTable(dbName, tableName) {
        currentTable = tableName;
        // 切换到属性选项卡
        switchTab('properties');

        try {
            const columns = await fetchJSON(API_BASE + '/columns?db=' + encodeURIComponent(dbName) + '&table=' + encodeURIComponent(tableName));
            // 构建属性展示
            let html = `<h2>${dbName}.${tableName}</h2>`;
            html += '<table class="props-table"><tbody>';
            // 模拟一些表属性（实际可从新API获取，这里用静态示例）
            html += '<tr><td>Engine</td><td>InnoDB</td></tr>';
            html += '<tr><td>Character Set</td><td>utf8mb4</td></tr>';
            html += '<tr><td>Collation</td><td>utf8mb4_0900_ai_ci</td></tr>';
            html += '</tbody></table>';

            html += '<h3>Columns</h3>';
            // 列信息表格
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

    // 选项卡切换
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

    // 选项卡点击事件
    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            switchTab(btn.dataset.tab);
        });
    });

    // 启动
    initConnections();
})();