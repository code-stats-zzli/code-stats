(function() {
    const API_BASE = window.apiUrl('/api/project');

    const navList = document.getElementById('navList');
    const projectContent = document.getElementById('projectContent');
    let currentModuleId = null;

    // 需要高亮的技术关键词（覆盖所有模块）
    const TECH_KEYWORDS = [
        // 框架核心
        'IoC', 'DI', '依赖注入', '控制反转', '@Autowired', '@Component', '@Service',
        '@Controller', '@Repository', '@Mapper', '@Select', 'Mapper代理', '动态代理',
        'Connector', 'Pipeline', 'Valve', '责任链', '类加载器', 'WebappClassLoader',
        'DispatcherServlet', 'HandlerMapping', '视图解析', 'ModelAndView', 'ResponseBody',
        'JdbcTemplate', 'RowMapper', '连接池', 'SimpleDataSource', 'maxActive', '连接验证', 'Semaphore',
        'PatternLayout', '日志级别', 'RollingFileAppender', 'AsyncAppender', 'LevelRangeFilter',
        '缓存', 'Caffeine', 'TTL', 'CacheManager', 'LocalCache',
        '表达式引擎', 'Lexer', 'Parser', 'Evaluator', 'FunctionResolver', 'VariableResolver',
        '启动流程', 'Bootstrap', 'Catalina', 'AnnotationConfigApplicationContext', 'SpringApplication',
        // 代码分析
        '代码分析', 'Pipeline模式', '多线程分析', 'GitIgnore', '复杂度计算', '合并文件',
        '依赖图', '循环依赖', 'Tarjan', '方法调用链', '调用树', 'PlantUML', 'SimpleJavaLexer',
        // 目录管理
        '目录管理', '文件上传', '路径安全校验', 'ZIP打包', '根类型', '本地目录', '服务器目录',
        // Agent
        'Ollama', 'DeepSeek', '自定义API', '项目助手', 'ngram', '知识库', 'CharNgramLM',
        // 数据库
        'DataModel', 'DatabaseMetadataService', '元数据', 'DatabaseMetaData',
        '数据源管理', '驱动管理', '动态数据源', 'SQL查询', 'ER视图',
        // 3D
        'Three.js', 'OrbitControls', 'CSS2DRenderer', 'ECharts GL', 'SceneEngine',
        '3D依赖图', '3D柱状图', '调用链拓扑', '实时监控', 'JVM监控', '系统监控',
        // 脚本引擎
        '脚本引擎', 'ScriptExecutor', 'JavaCompiler', 'PythonExecutor', 'CExecutor', 'CppExecutor',
        // 自动化测试
        '自动化测试', 'Pipeline', 'StepExecutor', 'ActionHandler', 'AssertionHandler',
        'VariableResolver', '用例管理', '执行中心', '报告中心',
        // 页面搭建
        '页面搭建', 'CanvasManager', 'BaseElement', 'ElementRegistry', '拖拽搭建',
        // 题库
        '题库分析', 'Excel合并', 'OCR', 'Tesseract', 'html2canvas',
        // 爬虫
        '网页爬虫', 'CSDN', 'Playwright', 'Stealth', '反爬虫',
        // 其他
        'SimpleVue', 'V4J', '响应式', 'Watcher', 'Dep', '组件化'
    ];

    function escapeRegex(str) {
        return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    function highlightKeywords(container, keywords) {
        if (!container) return;
        const walker = document.createTreeWalker(
            container,
            NodeFilter.SHOW_TEXT,
            {
                acceptNode: function(node) {
                    if (node.parentElement && (node.parentElement.tagName === 'SCRIPT' || node.parentElement.tagName === 'STYLE')) {
                        return NodeFilter.FILTER_REJECT;
                    }
                    if (node.parentElement && node.parentElement.classList && node.parentElement.classList.contains('keyword')) {
                        return NodeFilter.FILTER_REJECT;
                    }
                    return NodeFilter.FILTER_ACCEPT;
                }
            }
        );
        const nodesToReplace = [];
        while (walker.nextNode()) {
            nodesToReplace.push(walker.currentNode);
        }
        for (let node of nodesToReplace) {
            const originalText = node.textContent;
            if (!originalText.trim()) continue;
            let newHtml = originalText;
            const sortedKeywords = [...keywords].sort((a,b) => b.length - a.length);
            for (let kw of sortedKeywords) {
                const regex = new RegExp(`(${escapeRegex(kw)})`, 'gi');
                newHtml = newHtml.replace(regex, `<span class="keyword" data-question="解释 ${kw} 在 CodeStats 中的作用和实现原理">$1</span>`);
            }
            if (newHtml !== originalText) {
                const span = document.createElement('span');
                span.innerHTML = newHtml;
                node.parentNode.replaceChild(span, node);
            }
        }
        container.querySelectorAll('.keyword').forEach(el => {
            el.removeEventListener('click', keywordClickHandler);
            el.addEventListener('click', keywordClickHandler);
        });
    }

    function keywordClickHandler(e) {
        e.stopPropagation();
        let question = this.getAttribute('data-question');
        if (!question) {
            const kw = this.innerText.trim();
            question = `什么是 ${kw}？它在 CodeStats 中如何工作？`;
        }
        if (window.parent && window.parent !== window) {
            window.parent.postMessage({
                type: 'askProjectAssistant',
                question: question
            }, '*');
        } else {
            alert('请在主页面中使用项目助手');
        }
    }

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
            <li data-id="${item.id}" class="nav-item">
                <div style="display:flex; align-items:center; justify-content:space-between; width:100%;">
                    <span class="nav-text">
                        <strong>${escapeHtml(item.title)}</strong>
                        <div class="nav-summary">${escapeHtml(item.summary)}</div>
                    </span>
                    <button class="nav-ask-btn" data-question="请详细介绍 ${item.title} 模块的功能、核心组件和使用方式">💬 提问</button>
                </div>
            </li>
        `).join('');
        document.querySelectorAll('.nav-list li').forEach(li => {
            const id = li.dataset.id;
            const askBtn = li.querySelector('.nav-ask-btn');
            if (askBtn) {
                askBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    const question = askBtn.getAttribute('data-question');
                    if (window.parent && window.parent !== window) {
                        window.parent.postMessage({
                            type: 'askProjectAssistant',
                            question: question
                        }, '*');
                    } else {
                        alert('请在主页面中使用项目助手');
                    }
                });
            }
            li.addEventListener('click', (e) => {
                if (e.target === askBtn || askBtn?.contains(e.target)) return;
                if (id) selectModule(id);
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
            highlightKeywords(projectContent, TECH_KEYWORDS);
        } catch (err) {
            console.error(err);
            projectContent.innerHTML = '<div class="content-loading" style="color:red;">加载失败，请稍后重试</div>';
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    loadNav();
})();