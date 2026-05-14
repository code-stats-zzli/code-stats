import { apiUrl } from '../api.js';

export const Database = {
    template: `
        <div class="card" style="display: flex; gap: 20px; height: 70vh;">
            <div style="width: 280px; border-right: 1px solid rgba(0,102,255,0.2); overflow-y: auto;">
                <div class="tree" id="dbTree"></div>
            </div>
            <div style="flex:1; overflow-y: auto;">
                <div v-if="currentTable">
                    <h3>{{ currentDb }}.{{ currentTable }}</h3>
                    <div v-if="columns.length">
                        <table>
                            <thead><tr><th>列名</th><th>类型</th><th>大小</th><th>可空</th><th>自增</th><th>备注</th></tr></thead>
                            <tbody>
                                <tr v-for="col in columns">
                                    <td>{{ col.columnName }}</td><td>{{ col.typeName }}</td><td>{{ col.columnSize }}</td>
                                    <td>{{ col.nullable === 'YES' ? '是' : '否' }}</td><td>{{ col.autoIncrement === 'YES' ? '是' : '否' }}</td>
                                    <td>{{ col.remarks || '-' }}</td>
                                </table>
                            </tbody>
                        </table>
                    </div>
                    <div v-else class="empty-tip">暂无列信息</div>
                </div>
                <div v-else class="empty-tip">请从左侧选择一张表</div>
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
        }
    },
    mounted() {
        this.loadDatabases();
    }
};