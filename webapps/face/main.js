// 使用相对路径导入 Vue 框架（自动适配任何 context）
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
                    <button class="topbar-tab" v-bind:class="{ active: activeTab === 'intro' }" @click="setActiveTab('intro')">🚀 项目介绍</button>
                    <button class="topbar-tab" v-bind:class="{ active: activeTab === 'database' }" @click="setActiveTab('database')">🗄️ 数据库</button>
                    <button class="topbar-tab" v-bind:class="{ active: activeTab === 'directory' }" @click="setActiveTab('directory')">📂 目录管理</button>
                    <button class="topbar-tab" v-bind:class="{ active: activeTab === 'agent' }" @click="setActiveTab('agent')">🧠 AI Agent</button>
                    <button class="topbar-tab" v-bind:class="{ active: activeTab === 'codestats' }" @click="setActiveTab('codestats')">📈 代码分析</button>
                </nav>
            </header>
            <main class="main-content">
                <div class="tab-panel" v-bind:class="{ active: activeTab === 'intro' }" id="intro-panel"></div>
                <div class="tab-panel" v-bind:class="{ active: activeTab === 'database' }" id="database-panel"></div>
                <div class="tab-panel" v-bind:class="{ active: activeTab === 'directory' }" id="directory-panel"></div>
                <div class="tab-panel" v-bind:class="{ active: activeTab === 'agent' }" id="agent-panel"></div>
                <div class="tab-panel" v-bind:class="{ active: activeTab === 'codestats' }" id="codestats-panel"></div>
            </main>
        </div>
    `,
    data() {
        return { activeTab: 'intro' };
    },
    methods: {
        setActiveTab(tab) { this.activeTab = tab; }
    },
    mounted() {
        new Component(Intro).mount('#intro-panel');
        new Component(Database).mount('#database-panel');
        new Component(Directory).mount('#directory-panel');
        new Component(Agent).mount('#agent-panel');
        new Component(CodeStats).mount('#codestats-panel');
    }
});

App.mount('#app-root');