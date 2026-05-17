// ========== 树形导航模块 ==========
const TreeModule = (function() {
    let treeRoot = null;

    // 创建树节点元素
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

    // 加载数据库列表
    async function loadDatabases(dbUl, connName) {
        try {
            const databases = await DB.fetchJSON(DB.apiBase + '/databases');
            for (const dbName of databases) {
                const dbItem = createTreeItem(dbName, 'database', async (item) => {
                    setActive(item);
                    DB.setCurrentDb(dbName);
                    const childrenUl = item.nextElementSibling;
                    if (childrenUl && childrenUl.children.length === 0) {
                        await loadTables(childrenUl, dbName);
                    }
                }, true);
                dbUl.appendChild(dbItem);
                const tableUl = document.createElement('ul');
                tableUl.className = 'tree-children';
                appendChildren(dbItem, tableUl);
            }
            if (databases.length === 0) {
                dbUl.innerHTML = '<li class="empty-state">No databases</li>';
            } else {
                // 默认展开并选中第一个数据库
                const firstDb = dbUl.querySelector('.tree-item');
                if (firstDb) {
                    const toggle = firstDb.querySelector('.tree-toggle');
                    const childrenUl = firstDb.nextElementSibling;
                    if (childrenUl && childrenUl.classList) {
                        childrenUl.classList.add('open');
                        if (toggle) toggle.classList.replace('collapsed', 'expanded');
                    }
                    firstDb.click();
                }
            }
        } catch (err) {
            console.error(err);
            dbUl.innerHTML = '<li class="error">Failed to load databases</li>';
        }
    }

    // 加载表列表
    async function loadTables(tableUl, dbName) {
        try {
            const tables = await DB.fetchJSON(DB.apiBase + '/tables?db=' + encodeURIComponent(dbName));
            for (const table of tables) {
                const tableItem = createTreeItem(table.name, 'table', (item) => {
                    setActive(item);
                    DB.setCurrentDb(dbName);
                    DB.setCurrentTable(table.name);
                    // 触发各模块刷新
                    PropertiesModule.loadProperties(dbName, table.name);
                    DataModule.resetAndLoad(dbName, table.name);
                    // 兼容增强版 SQL 模块刷新提示
                    if (window.__refreshSqlHints) window.__refreshSqlHints();
                }, true);
                const detailUl = document.createElement('ul');
                detailUl.className = 'tree-children';

                // 添加“列”子节点
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

                // 其他静态节点
                ['视图', '索引', '存储过程', '触发器', '事件'].forEach(label => {
                    const staticItem = createTreeItem(label, 'static', null, false);
                    detailUl.appendChild(staticItem);
                });

                tableUl.appendChild(tableItem);
                appendChildren(tableItem, detailUl);
            }
            if (tables.length === 0) {
                tableUl.innerHTML = '<li class="empty-state">No tables</li>';
            } else {
                // 默认选中第一个表
                const firstTable = tableUl.querySelector('.tree-item');
                if (firstTable) firstTable.click();
            }
        } catch (err) {
            console.error(err);
            tableUl.innerHTML = '<li class="error">Failed to load tables</li>';
        }
    }

    // 加载列信息（树形展示用）
    async function loadColumns(colUl, dbName, tableName) {
        try {
            const columns = await DB.fetchJSON(DB.apiBase + '/columns?db=' + encodeURIComponent(dbName) + '&table=' + encodeURIComponent(tableName));
            for (const col of columns) {
                const colText = `${col.columnName} (${col.typeName})`;
                const colLeaf = createTreeItem(colText, 'column', null, false);
                colUl.appendChild(colLeaf);
            }
            if (columns.length === 0) {
                colUl.innerHTML = '<li class="empty-state">No columns</li>';
            }
        } catch (err) {
            console.error(err);
            colUl.innerHTML = '<li class="error">Failed to load columns</li>';
        }
    }

    // 初始化连接
    function initConnections() {
        if (!treeRoot) {
            console.error('treeRoot element not found');
            return;
        }
        treeRoot.innerHTML = '';
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

    return {
        init: function() {
            treeRoot = document.getElementById('treeRoot');
            if (treeRoot) {
                initConnections();
            } else {
                console.error('TreeModule: cannot find treeRoot element');
            }
        }
    };
})();