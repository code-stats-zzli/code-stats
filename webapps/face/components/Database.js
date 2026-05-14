import { apiUrl } from '../api.js';

export const Database = {
    template: `
        <div class="database-container">
            <div class="db-sidebar">
                <div class="db-tree-header">🗄️ 数据库连接</div>
                <div id="dbTree" class="db-tree"></div>
            </div>
            <div class="db-main">
                <div class="tab-header">
                    <button class="tab-btn" data-tab="props">属性</button>
                    <button class="tab-btn" data-tab="data">数据</button>
                    <button class="tab-btn" data-tab="diagram">图</button>
                </div>
                <div class="tab-content">
                    <div id="propsPane" class="tab-pane active">
                        <div id="propsContent">请从左侧选择一张表</div>
                    </div>
                    <div id="dataPane" class="tab-pane">
                        <div id="dataContent">数据视图功能开发中</div>
                    </div>
                    <div id="diagramPane" class="tab-pane">
                        <div id="diagramContent">ER 图功能开发中</div>
                    </div>
                </div>
            </div>
        </div>
    `,
    data() {
        return {
            currentDb: '',
            currentTable: '',
            columns: []
        };
    },
    methods: {
        async loadDatabases() {
            const container = document.getElementById('dbTree');
            if (!container) return;
            container.innerHTML = '<div class="loading">加载中...</div>';
            try {
                const url = apiUrl('/dbclient/api/databases');
                const res = await fetch(url);
                if (!res.ok) throw new Error(`HTTP ${res.status}`);
                const dbs = await res.json();
                if (!dbs.length) {
                    container.innerHTML = '<div class="empty-tip">未获取到数据库，请检查连接配置</div>';
                    return;
                }
                this.renderTree(dbs);
            } catch (err) {
                console.error(err);
                container.innerHTML = `<div class="error-tip">❌ 加载失败：${err.message}</div>`;
            }
        },
        renderTree(databases) {
            const container = document.getElementById('dbTree');
            if (!container) return;
            container.innerHTML = '';
            for (const db of databases) {
                const dbDiv = document.createElement('div');
                dbDiv.className = 'tree-item';
                dbDiv.innerHTML = `<span class="toggle">▶</span> 🗄️ ${escapeHtml(db)}`;
                dbDiv.style.paddingLeft = '8px';

                const childContainer = document.createElement('div');
                childContainer.className = 'tree-children';
                childContainer.style.display = 'none';

                dbDiv.addEventListener('click', async (e) => {
                    e.stopPropagation();
                    const isOpen = childContainer.style.display === 'block';
                    if (!isOpen && childContainer.children.length === 0) {
                        childContainer.innerHTML = '<div class="loading">加载表...</div>';
                        try {
                            const tablesUrl = apiUrl(`/dbclient/api/tables?db=${encodeURIComponent(db)}`);
                            const tablesRes = await fetch(tablesUrl);
                            if (!tablesRes.ok) throw new Error('加载表失败');
                            const tables = await tablesRes.json();
                            childContainer.innerHTML = '';
                            for (const t of tables) {
                                const tableDiv = document.createElement('div');
                                tableDiv.className = 'tree-item';
                                tableDiv.innerHTML = `<span class="toggle leaf">•</span> 📄 ${escapeHtml(t.name)}`;
                                tableDiv.style.paddingLeft = '24px';
                                tableDiv.addEventListener('click', (e) => {
                                    e.stopPropagation();
                                    this.selectTable(db, t.name);
                                });
                                childContainer.appendChild(tableDiv);
                            }
                            if (tables.length === 0) {
                                childContainer.innerHTML = '<div class="empty-tip">无表</div>';
                            }
                        } catch (err) {
                            childContainer.innerHTML = `<div class="error-tip">${err.message}</div>`;
                        }
                    }
                    childContainer.style.display = isOpen ? 'none' : 'block';
                    const toggleSpan = dbDiv.querySelector('.toggle');
                    toggleSpan.textContent = isOpen ? '▶' : '▼';
                });
                container.appendChild(dbDiv);
                container.appendChild(childContainer);
            }
        },
        async selectTable(db, table) {
            this.currentDb = db;
            this.currentTable = table;
            const propsPane = document.getElementById('propsContent');
            if (!propsPane) return;
            propsPane.innerHTML = '<div class="loading">加载列信息...</div>';
            try {
                const url = apiUrl(`/dbclient/api/columns?db=${encodeURIComponent(db)}&table=${encodeURIComponent(table)}`);
                const res = await fetch(url);
                if (!res.ok) throw new Error(`HTTP ${res.status}`);
                const columns = await res.json();
                this.columns = columns;
                this.renderTableProps();
                this.activateTab('props');
            } catch (err) {
                propsPane.innerHTML = `<div class="error-tip">❌ 加载失败：${err.message}</div>`;
            }
        },
        renderTableProps() {
            const container = document.getElementById('propsContent');
            if (!container) return;
            if (!this.currentTable) {
                container.innerHTML = '<div class="empty-tip">请从左侧选择一张表</div>';
                return;
            }
            let html = `<h3>${escapeHtml(this.currentDb)}.${escapeHtml(this.currentTable)}</h3>`;
            // 模拟表属性 (与原demo保持一致)
            html += `<table class="props-table"><tbody>
                <tr><td>引擎</td><td>InnoDB</td></tr>
                <tr><td>字符集</td><td>utf8mb4</td></tr>
                <tr><td>排序规则</td><td>utf8mb4_0900_ai_ci</td></tr>
            </tbody></table>`;
            html += `<h4>列信息</h4>
                <table class="data-table">
                    <thead>
                        <tr><th>列名</th><th>类型</th><th>大小</th><th>可空</th><th>自增</th><th>备注</th></tr>
                    </thead>
                    <tbody>`;
            for (const col of this.columns) {
                html += `<tr>
                    <td>${escapeHtml(col.columnName)}</td>
                    <td>${escapeHtml(col.typeName)}</td>
                    <td>${col.columnSize || ''}</td>
                    <td>${col.nullable === 'YES' ? '是' : '否'}</td>
                    <td>${col.autoIncrement === 'YES' ? '是' : '否'}</td>
                    <td>${escapeHtml(col.remarks || '-')}</td>
                </tr>`;
            }
            html += `</tbody></table>`;
            container.innerHTML = html;
        },
        activateTab(tabName) {
            const btns = document.querySelectorAll('.tab-btn');
            const panes = {
                props: document.getElementById('propsPane'),
                data: document.getElementById('dataPane'),
                diagram: document.getElementById('diagramPane')
            };
            btns.forEach(btn => {
                const t = btn.dataset.tab;
                if (t === tabName) btn.classList.add('active');
                else btn.classList.remove('active');
            });
            Object.keys(panes).forEach(key => {
                if (panes[key]) panes[key].classList.toggle('active', key === tabName);
            });
        }
    },
    mounted() {
        this.loadDatabases();
        // 绑定 tab 切换事件
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const tab = btn.dataset.tab;
                if (tab) this.activateTab(tab);
            });
        });
        // 对外暴露刷新方法
        window.__refreshDatabase = () => this.loadDatabases();
    }
};

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>]/g, (m) => {
        if (m === '&') return '&amp;';
        if (m === '<') return '&lt;';
        if (m === '>') return '&gt;';
        return m;
    });
}