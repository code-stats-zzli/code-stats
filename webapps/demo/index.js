/**
 * CodeStats Platform - 首页交互
 * 页签切换、数据库面板和代码分析面板初始化
 * 项目介绍已独立为 project.html，不再需要动态模块列表
 */
(function () {
    'use strict';

    const API_BASE = '/demo';
    const DEFAULT_TAB = 'intro';

    // DOM 元素
    const topbarTabs = document.getElementById('topbarTabs');
    const toastContainer = document.getElementById('toastContainer');
    const topbarStatus = document.getElementById('topbarStatus');

    let activeTab = DEFAULT_TAB;

    // ---------- 工具函数 ----------
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

    // 页签切换
    function switchTab(tabName) {
        if (tabName === activeTab) return;

        // 更新按钮状态
        $$('.topbar-tab').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === tabName);
        });

        // 更新面板显示
        $$('.tab-panel').forEach(panel => {
            panel.classList.toggle('active', panel.id === `panel-${tabName}`);
        });

        activeTab = tabName;

        // 特殊面板初始化
        if (tabName === 'database') {
            initDatabasePanel();
        } else if (tabName === 'codestats') {
            initCodeStatsPanel();
        }
    }

    // 初始化数据库 iframe
    function initDatabasePanel() {
        const iframeDB = document.getElementById('iframeDatabase');
        if (!iframeDB) return;
        iframeDB.onload = () => updateStatus('数据库已加载', true);
        iframeDB.onerror = () => {
            updateStatus('数据库加载失败', false);
            showToast('数据库面板加载异常', 'error');
        };
    }

    // 初始化代码分析 iframe
    function initCodeStatsPanel() {
        const iframeCS = document.getElementById('iframeCodeStats');
        if (!iframeCS) return;
        iframeCS.onload = () => updateStatus('代码分析已加载', true);
        iframeCS.onerror = () => {
            updateStatus('代码分析加载失败', false);
            showToast('代码分析面板加载异常', 'error');
        };
    }

    // ---------- 项目介绍面板已改为独立 iframe，无需任何动态加载逻辑 ----------
    async function loadIntroPanel() {
        // 项目介绍使用独立的 project.html，此处仅更新状态
        updateStatus('项目介绍已加载', true);
    }

    // ---------- 事件绑定 ----------
    if (topbarTabs) {
        topbarTabs.addEventListener('click', (e) => {
            const tabBtn = e.target.closest('.topbar-tab');
            if (!tabBtn) return;
            const tabName = tabBtn.dataset.tab;
            if (tabName) switchTab(tabName);
        });
    }

    // ---------- 启动 ----------
    async function init() {
        const hashTab = window.location.hash.replace('#', '');
        const startTab = hashTab || DEFAULT_TAB;
        switchTab(startTab);

        await loadIntroPanel();   // 仅状态提示

        if (startTab === 'database') {
            initDatabasePanel();
        } else if (startTab === 'codestats') {
            initCodeStatsPanel();
        }

        updateStatus('就绪', true);
    }

    // 暴露部分全局接口（供其他脚本或调试使用）
    window.Platform = { switchTab, showToast, updateStatus, getActiveTab: () => activeTab };

    init().catch(err => {
        console.error('初始化失败', err);
        updateStatus('启动失败', false);
        showToast('首页初始化异常', 'error');
    });
})();