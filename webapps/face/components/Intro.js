import { apiUrl } from '../api.js';

export const Intro = {
    template: `
        <div class="card">
            <h2>🚀 项目介绍</h2>
            <div v-bind:style="loading ? 'display:block' : 'display:none'">加载中...</div>
            <div v-bind:style="loading ? 'display:none' : 'display:block'">
                <h3>{{ title }}</h3>
                <p>{{ subtitle }}</p>
                <h4>功能模块</h4>
                <div id="modulesContainer"></div>
                <h4>技术栈</h4>
                <div id="techStackContainer"></div>
            </div>
        </div>
    `,
    data() {
        return { loading: true, title: '', subtitle: '', modules: [], techStack: [] };
    },
    methods: {
        renderModules() {
            const container = this.$el.querySelector('#modulesContainer');
            if (!container) return;
            container.innerHTML = this.modules.map(mod => `<li><strong>${mod.name}</strong>：${mod.desc}</li>`).join('');
        },
        renderTechStack() {
            const container = this.$el.querySelector('#techStackContainer');
            if (!container) return;
            container.innerHTML = this.techStack.map(tech => `<li>${tech}</li>`).join('');
        }
    },
    async mounted() {
        try {
            const res = await fetch(apiUrl('/api/info'));
            const info = await res.json();
            this.title = info.title;
            this.subtitle = info.subtitle;
            this.modules = info.modules || [];
            this.techStack = info.techStack || [];
            this.loading = false;
            this.renderModules();
            this.renderTechStack();
        } catch(e) {
            console.error(e);
            this.loading = false;
        }
    }
};