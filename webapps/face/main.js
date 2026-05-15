import { Component } from '../vue/index.js';
import { Intro } from './components/Intro.js';
import { Database } from './components/Database.js';
import { Directory } from './components/Directory.js';
import { Agent } from './components/Agent.js';
import { CodeStats } from './components/CodeStats.js';

const App = new Component({
    template: `
        <div>
            <header class="topbar">
                <div class="topbar-brand">🔷 CodeStats · SimpleVue 重构版</div>
                <nav class="topbar-tabs">
                    <button class="topbar-tab" :class="activeTab === 'intro' ? 'active' : ''" @click="setActiveTab('intro')">🚀 项目介绍</button>
                    <button class="topbar-tab" :class="activeTab === 'database' ? 'active' : ''" @click="setActiveTab('database')">🗄️ 数据库</button>
                    <button class="topbar-tab" :class="activeTab === 'directory' ? 'active' : ''" @click="setActiveTab('directory')">📂 目录管理</button>
                    <button class="topbar-tab" :class="activeTab === 'agent' ? 'active' : ''" @click="setActiveTab('agent')">🧠 AI Agent</button>
                    <button class="topbar-tab" :class="activeTab === 'codestats' ? 'active' : ''" @click="setActiveTab('codestats')">📈 代码分析</button>
                </nav>
            </header>
            <main class="main-content">
                <div class="tab-panel" :class="activeTab === 'intro' ? 'active' : ''" id="intro-panel"></div>
                <div class="tab-panel" :class="activeTab === 'database' ? 'active' : ''" id="database-panel"></div>
                <div class="tab-panel" :class="activeTab === 'directory' ? 'active' : ''" id="directory-panel"></div>
                <div class="tab-panel" :class="activeTab === 'agent' ? 'active' : ''" id="agent-panel"></div>
                <div class="tab-panel" :class="activeTab === 'codestats' ? 'active' : ''" id="codestats-panel"></div>
            </main>
        </div>
    `,
    data() {
        return { activeTab: 'intro' };
    },
    methods: {
        setActiveTab(tab) {
            this.activeTab = tab;
            // 切换到数据库页签时重新加载树（解决因DOM重建导致树丢失的问题）
            if (tab === 'database' && window.__refreshDatabase) {
                setTimeout(() => window.__refreshDatabase(), 50);
            }
            // 切换到目录管理时可能也需要刷新树（可选）
            if (tab === 'directory' && window.__refreshDirectory) {
                setTimeout(() => window.__refreshDirectory(), 50);
            }
        }
    },
    mounted() {
        // 挂载各模块组件到对应容器
        new Component(Intro).mount('#intro-panel');
        new Component(Database).mount('#database-panel');
        new Component(Directory).mount('#directory-panel');
        new Component(Agent).mount('#agent-panel');
        new Component(CodeStats).mount('#codestats-panel');
    }
});

App.mount('#app-root');