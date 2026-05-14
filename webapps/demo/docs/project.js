(function() {
    const API_BASE = window.apiUrl('/api/project');

    const navList = document.getElementById('navList');
    const projectContent = document.getElementById('projectContent');
    let currentModuleId = null;

    async function loadNav() {
        try {
            const resp = await fetch(API_BASE + '/nav');
            if (!resp.ok) throw new Error('加载导航失败');
            const navItems = await resp.json();
            renderNav(navItems);
            if (navItems.length > 0) {
                selectModule(navItems[0].id);
            }
        } catch (err) {
            console.error(err);
            navList.innerHTML = '<li style="padding:12px;color:red;">加载导航失败，请检查后端服务</li>';
        }
    }

    function renderNav(items) {
        navList.innerHTML = items.map(item => `
            <li data-id="${item.id}">
                <div class="nav-title">${item.title}</div>
                <div class="nav-summary">${item.summary}</div>
            </li>
        `).join('');
        document.querySelectorAll('.nav-list li').forEach(li => {
            li.addEventListener('click', () => {
                const id = li.dataset.id;
                selectModule(id);
            });
        });
    }

    async function selectModule(moduleId) {
        document.querySelectorAll('.nav-list li').forEach(li => {
            li.classList.toggle('active', li.dataset.id === moduleId);
        });
        currentModuleId = moduleId;
        projectContent.innerHTML = '<div class="content-loading">加载中...</div>';
        try {
            const resp = await fetch(API_BASE + '/detail?moduleId=' + encodeURIComponent(moduleId));
            if (!resp.ok) throw new Error('加载详情失败');
            const html = await resp.text();
            projectContent.innerHTML = html;
        } catch (err) {
            console.error(err);
            projectContent.innerHTML = '<div class="content-loading" style="color:red;">加载失败，请稍后重试</div>';
        }
    }

    loadNav();
})();