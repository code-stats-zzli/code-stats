// ---------- 页签切换 ----------
const navItems = document.querySelectorAll('.nav-item');
const iframe = document.getElementById('sceneFrame');

const pageMap = {
    modules: '../modules/modules.html',
    barchart: '../barchart/barchart.html',
    callchain: '../callchain/callchain.html',
    trace: '../trace/trace.html',
    realtime: '../realtime/realtime-monitor.html',
    jstats: '../jstats/jstats-monitor.html',
    configurable: '../scene/configurable.html'
};

function switchTab(tabId) {
    navItems.forEach(item => {
        item.classList.toggle('active', item.dataset.tab === tabId);
    });
    const url = pageMap[tabId];
    if (url) iframe.src = url;
}

navItems.forEach(item => {
    item.addEventListener('click', () => {
        const tab = item.dataset.tab;
        if (tab) switchTab(tab);
    });
});

const hash = window.location.hash.replace('#', '');
if (hash && pageMap[hash]) {
    switchTab(hash);
    navItems.forEach(item => {
        item.classList.toggle('active', item.dataset.tab === hash);
    });
}