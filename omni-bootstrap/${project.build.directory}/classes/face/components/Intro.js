// webapps/face/components/Intro.js
import { apiUrl } from '../api.js';

export const Intro = {
    template: `
        <div class="project-container">
            <aside class="project-sidebar">
                <div class="sidebar-header">
                    <h3>📖 平台导航</h3>
                </div>
                <ul class="nav-list" id="introNavList"></ul>
            </aside>
            <main class="project-content" id="introDetailContent">
                <div class="content-loading">加载中...</div>
            </main>
        </div>
    `,
    data() {
        return {
            navItems: [],
            currentModuleId: null
        };
    },
    methods: {
        async loadNav() {
            const navContainer = document.getElementById('introNavList');
            const detailContainer = document.getElementById('introDetailContent');
            try {
                const resp = await fetch(apiUrl('/api/project/nav'));
                if (!resp.ok) throw new Error('HTTP ' + resp.status);
                this.navItems = await resp.json();
                this.renderNavList();
                if (this.navItems.length > 0) {
                    this.selectModule(this.navItems[0].id);
                } else {
                    if (detailContainer) detailContainer.innerHTML = '<div style="padding:20px;color:orange;">暂无导航数据</div>';
                }
            } catch (err) {
                console.error('加载导航失败:', err);
                if (navContainer) navContainer.innerHTML = '<li style="color:red;padding:12px;">⚠️ 后端未启动，无法加载导航</li>';
                if (detailContainer) detailContainer.innerHTML = `
                    <div style="padding:20px;background:#fff0f0;border-radius:16px;color:#c00;">
                        <strong>⚠️ 后端服务未响应</strong><br>
                        请确保 CodeStats 后端已启动（端口8080），并且 <code>/api/project/nav</code> 接口可访问。
                    </div>
                `;
            }
        },
        renderNavList() {
            const container = document.getElementById('introNavList');
            if (!container) return;
            container.innerHTML = this.navItems.map(item => `
                <li data-id="${item.id}" class="nav-item ${this.currentModuleId === item.id ? 'active' : ''}">
                    <div class="nav-title">${this.escapeHtml(item.title)}</div>
                    <div class="nav-summary">${this.escapeHtml(item.summary)}</div>
                </li>
            `).join('');
            container.querySelectorAll('.nav-item').forEach(li => {
                li.addEventListener('click', () => {
                    const id = li.dataset.id;
                    this.selectModule(id);
                });
            });
        },
        async selectModule(moduleId) {
            this.currentModuleId = moduleId;
            document.querySelectorAll('#introNavList .nav-item').forEach(li => {
                li.classList.toggle('active', li.dataset.id === moduleId);
            });
            const detailContainer = document.getElementById('introDetailContent');
            detailContainer.innerHTML = '<div class="content-loading">加载中...</div>';
            try {
                const resp = await fetch(apiUrl(`/api/project/detail?moduleId=${encodeURIComponent(moduleId)}`));
                if (!resp.ok) throw new Error('HTTP ' + resp.status);
                const html = await resp.text();
                detailContainer.innerHTML = html;
            } catch (err) {
                console.error('加载详情失败:', err);
                detailContainer.innerHTML = '<div style="padding:20px;color:red;">加载详情失败，请检查后端接口</div>';
            }
        },
        escapeHtml(str) {
            if (!str) return '';
            return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
        }
    },
    mounted() {
        this.loadNav();
    }
};