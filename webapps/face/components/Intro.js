import { apiUrl } from '../api.js';

export const Intro = {
    template: `
        <div class="card">
            <h2>🚀 项目介绍</h2>
            <div v-if="loading">加载中...</div>
            <div v-else>
                <h3>{{ info.title }}</h3>
                <p>{{ info.subtitle }}</p>
                <h4>功能模块</h4>
                <ul><li v-for="mod in info.modules"><strong>{{ mod.name }}</strong>：{{ mod.desc }}</li></ul>
                <h4>技术栈</h4>
                <ul><li v-for="tech in info.techStack">{{ tech }}</li></ul>
            </div>
        </div>
    `,
    data() {
        return { loading: true, info: { title: '', subtitle: '', modules: [], techStack: [] } };
    },
    async mounted() {
        try {
            const res = await fetch(apiUrl('/api/info'));
            this.info = await res.json();
        } catch(e) { console.error(e); }
        finally { this.loading = false; }
    }
};