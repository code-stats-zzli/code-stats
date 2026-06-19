(function () {
    'use strict';

    const DEFAULT_TAB = 'intro';

    const topbarTabs = document.getElementById('topbarTabs');
    const toastContainer = document.getElementById('toastContainer');
    const topbarStatus = document.getElementById('topbarStatus');

    let activeTab = DEFAULT_TAB;

    function $$(selector, context = document) {
        return Array.from(context.querySelectorAll(selector));
    }

    function showToast(message, type = 'info', duration = 3000) {
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.textContent = message;
        toastContainer.appendChild(toast);
        setTimeout(() => {
            toast.classList.add('toast-fade-out');
            toast.addEventListener('transitionend', () => toast.remove());
            setTimeout(() => { if (toast.parentNode) toast.remove(); }, 500);
        }, duration);
    }

    function updateStatus(text, ok = true) {
        if (!topbarStatus) return;
        const dot = topbarStatus.querySelector('.status-dot');
        const label = topbarStatus.querySelector('.status-text');
        if (dot) dot.style.backgroundColor = ok ? '#22c55e' : '#ef4444';
        if (label) label.textContent = text;
    }

    function switchTab(tabName) {
        if (tabName === activeTab) return;

        $$('.topbar-tab').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === tabName);
        });
        $$('.tab-panel').forEach(panel => {
            panel.classList.toggle('active', panel.id === `panel-${tabName}`);
        });
        activeTab = tabName;

        // 特殊面板的初始化（新增 3D 面板无需额外逻辑）
        if (tabName === 'database') {
            initDatabasePanel();
        } else if (tabName === 'codestats') {
            initCodeStatsPanel();
        }
        // 3D 面板不需要特殊初始化
    }

    function initDatabasePanel() {
        const iframeDB = document.getElementById('iframeDatabase');
        if (!iframeDB) return;
        iframeDB.onload = () => updateStatus('数据库已加载', true);
        iframeDB.onerror = () => {
            updateStatus('数据库加载失败', false);
            showToast('数据库面板加载异常', 'error');
        };
    }

    function initCodeStatsPanel() {
        const iframeCS = document.getElementById('iframeCodeStats');
        if (!iframeCS) return;
        iframeCS.onload = () => updateStatus('代码分析已加载', true);
        iframeCS.onerror = () => {
            updateStatus('代码分析加载失败', false);
            showToast('代码分析面板加载异常', 'error');
        };
    }

    async function loadIntroPanel() {
        updateStatus('项目介绍已加载', true);
    }

    if (topbarTabs) {
        topbarTabs.addEventListener('click', (e) => {
            const tabBtn = e.target.closest('.topbar-tab');
            if (!tabBtn) return;
            const tabName = tabBtn.dataset.tab;
            if (tabName) switchTab(tabName);
        });
    }

    async function init() {
        const hashTab = window.location.hash.replace('#', '');
        const startTab = hashTab || DEFAULT_TAB;
        switchTab(startTab);
        await loadIntroPanel();
        if (startTab === 'database') {
            initDatabasePanel();
        } else if (startTab === 'codestats') {
            initCodeStatsPanel();
        }
        updateStatus('就绪', true);
    }

    window.Platform = { switchTab, showToast, updateStatus, getActiveTab: () => activeTab };
    init().catch(err => {
        console.error('初始化失败', err);
        updateStatus('启动失败', false);
        showToast('首页初始化异常', 'error');
    });
})();