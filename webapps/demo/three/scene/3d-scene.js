// ---------- 页签切换 ----------
const navItems = document.querySelectorAll('.nav-item');
const iframe = document.getElementById('sceneFrame');

// 各场景对应的 URL（相对于当前目录）
const pageMap = {
    modules: '../modules/modules.html',
    barchart: '../barchart/barchart.html',
    callchain: '../callchain/callchain.html',
    trace: '../trace/trace.html'   // 新增动态调用链
};

// 切换页签
function switchTab(tabId) {
    // 更新导航高亮
    navItems.forEach(item => {
        item.classList.toggle('active', item.dataset.tab === tabId);
    });

    // 加载对应的 iframe
    const url = pageMap[tabId];
    if (url) {
        iframe.src = url;
    }
}

// 绑定点击事件
navItems.forEach(item => {
    item.addEventListener('click', () => {
        const tab = item.dataset.tab;
        if (tab) switchTab(tab);
    });
});

// 初始化：默认选中 modules（已在 HTML 中 active）
// 若 URL 有 hash，则切换到对应页签
const hash = window.location.hash.replace('#', '');
if (hash && pageMap[hash]) {
    switchTab(hash);
    // 更新导航高亮
    navItems.forEach(item => {
        item.classList.toggle('active', item.dataset.tab === hash);
    });
}