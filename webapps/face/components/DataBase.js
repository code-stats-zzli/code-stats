import { apiUrl } from '../api.js';

export const Database = {
    template: `
        <div class="card" style="display: flex; gap: 20px; height: 70vh;">
            <div style="width: 280px; border-right: 1px solid rgba(0,102,255,0.2); overflow-y: auto;">
                <div id="dbTree" class="tree"></div>
            </div>
            <div style="flex:1; overflow-y: auto;">
                <div id="tableDetail">
                    <div class="empty-tip">请从左侧选择一张表</div>
                </div>
            </div>
        </div>
    `,
    data() {
        return { currentDb: '', currentTable: '', columns: [] };
    },
    methods: {
        async loadDatabases() {
            const url = apiUrl('/dbclient/api/databases');
            const res = await fetch(url);
            const dbs = await res.json();
            const container = document.getElementById('dbTree');
            if (!container) return;
            container.innerHTML = '';
            for (const db of dbs) {
                const dbDiv = document.createElement('div');
                dbDiv.className = 'tree-item';
                dbDiv.innerHTML = `<span class="toggle">▶</span> 🗄️ ${db}`;
                dbDiv.style.paddingLeft = '8px';
                const childContainer = document.createElement('div');
                childContainer.className = 'tree-children';
                childContainer.style.display = 'none';
                dbDiv.addEventListener('click', async (e) => {
                    e.stopPropagation();
                    const isOpen = childContainer.style.display === 'block';
                    if (!isOpen && childContainer.children.length === 0) {
                        const tablesRes = await fetch(apiUrl(`/dbclient/api/tables?db=${encodeURIComponent(db)}`));
                        const tables = await tablesRes.json();
                        tables.forEach(t => {
                            const tableDiv = document.createElement('div');
                            tableDiv.className = 'tree-item';
                            tableDiv.innerHTML = `<span class="toggle leaf">•</span> 📄 ${t.name}`;
                            tableDiv.style.paddingLeft = '24px';
                            tableDiv.addEventListener('click', () => this.selectTable(db, t.name));
                            childContainer.appendChild(tableDiv);
                        });
                    }
                    childContainer.style.display = isOpen ? 'none' : 'block';
                    dbDiv.querySelector('.toggle').textContent = isOpen ? '▶' : '▼';
                });
                container.appendChild(dbDiv);
                container.appendChild(childContainer);
            }
        },
        async selectTable(db, table) {
            this.currentDb = db;
            this.currentTable = table;
            const url = apiUrl(`/dbclient/api/columns?db=${encodeURIComponent(db)}&table=${encodeURIComponent(table)}`);
            const res = await fetch(url);
            this.columns = await res.json();
            this.renderTableDetail();
        },
        renderTableDetail() {
            const container = document.getElementById('tableDetail');
            if (!container) return;
            if (!this.currentTable) {
                container.innerHTML = '<div class="empty-tip">请从左侧选择一张表</div>';
                return;
            }
            let html = `<h3>${this.currentDb}.${this.currentTable}</h3>`;
            html += '<table><thead><tr><th>列名</th><th>类型</th><th>大小</th><th>可空</th><th>自增</th><th>备注</th></tr></thead><tbody>';
            this.columns.forEach(col => {
                html += `<tr>
                    <td>${escapeHtml(col.columnName)}</td>
                    <td>${escapeHtml(col.typeName)}</td>
                    <td>${col.columnSize}</td>
                    <td>${col.nullable === 'YES' ? '是' : '否'}</td>
                    <td>${col.autoIncrement === 'YES' ? '是' : '否'}</td>
                    <td>${escapeHtml(col.remarks || '-')}</td>
                </tr>`;
            });
            html += '</tbody></table>';
            container.innerHTML = html;
        }
    },
    mounted() {
        this.loadDatabases();
    }
};

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>]/g, function(m) {
        if (m === '&') return '&amp;';
        if (m === '<') return '&lt;';
        if (m === '>') return '&gt;';
        return m;
    });
}