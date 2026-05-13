(function() {
    const API_BASE = '/demo/api/project';

    // DOM 元素
    const navList = document.getElementById('navList');
    const projectContent = document.getElementById('projectContent');

    // 当前选中的模块ID
    let currentModuleId = null;

    // 加载导航菜单
    async function loadNav() {
        try {
            const resp = await fetch(`${API_BASE}/nav`);
            if (!resp.ok) throw new Error('加载导航失败');
            const navItems = await resp.json();
            renderNav(navItems);
            // 默认选中第一个（项目架构介绍）
            if (navItems.length > 0) {
                selectModule(navItems[0].id);
            }
        } catch (err) {
            console.error(err);
            navList.innerHTML = '<li style="padding:12px;color:red;">加载导航失败，请检查后端服务</li>';
        }
    }

    // 渲染左侧菜单
    function renderNav(items) {
        navList.innerHTML = items.map(item => `
            <li data-id="${item.id}">
                <div class="nav-title">${item.title}</div>
                <div class="nav-summary">${item.summary}</div>
            </li>
        `).join('');

        // 绑定点击事件
        document.querySelectorAll('.nav-list li').forEach(li => {
            li.addEventListener('click', () => {
                const id = li.dataset.id;
                selectModule(id);
            });
        });
    }

    // 选中模块，高亮并加载详情
    async function selectModule(moduleId) {
        // 更新高亮
        document.querySelectorAll('.nav-list li').forEach(li => {
            li.classList.toggle('active', li.dataset.id === moduleId);
        });

        currentModuleId = moduleId;
        // 显示加载中
        projectContent.innerHTML = '<div class="content-loading">加载中...</div>';

        try {
            const resp = await fetch(`${API_BASE}/detail?moduleId=${moduleId}`);
            if (!resp.ok) throw new Error('加载详情失败');
            const html = await resp.text();
            projectContent.innerHTML = html;
        } catch (err) {
            console.error(err);
            projectContent.innerHTML = '<div class="content-loading" style="color:red;">加载失败，请稍后重试</div>';
        }
    }

    // 启动
    loadNav();
})();