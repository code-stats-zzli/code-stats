(function () {
    const API = window.apiUrl('/systemmonitor/api');
    let timer = null;
    let paused = false;

    const grid = document.getElementById('cardGrid');
    const detailPanel = document.getElementById('detailPanel');

    async function fetchAll() {
        if (paused) return;
        try {
            const resp = await fetch(API + '/all');
            const list = await resp.json();
            renderCards(list);
            document.getElementById('updateTime').textContent =
                '更新于 ' + new Date().toLocaleTimeString();
        } catch (e) {
            console.error(e);
        }
    }

    function renderCards(list) {
        grid.innerHTML = list.map(item => {
            const primary = pickPrimary(item);
            return `
                <div class="card status-${item.status}" data-id="${item.id}">
                    <div class="card-icon">${item.icon || '📦'}</div>
                    <div class="card-name">${item.name}</div>
                    <div class="card-primary">${primary}</div>
                    <div class="card-cat">${item.category}</div>
                </div>`;
        }).join('');

        grid.querySelectorAll('.card').forEach(el => {
            el.addEventListener('click', () => showDetail(el.dataset.id));
        });
    }

    function pickPrimary(item) {
        const rt = item.runtimeInfo || {};
        for (const k of ['usageText', 'usagePercent', 'threadCount', 'loadedClassCount']) {
            if (rt[k] !== undefined && rt[k] !== null) return rt[k];
        }
        return '-';
    }

    async function showDetail(id) {
        const resp = await fetch(API + '/item?id=' + encodeURIComponent(id));
        const info = await resp.json();
        detailPanel.style.display = 'block';
        detailPanel.innerHTML = `
            <h3>${info.icon || ''} ${info.name} <small>[${info.category}]</small></h3>
            <h4>核心信息</h4>
            <table class="kv-table">${kvRows(info.coreInfo)}</table>
            <h4>运行时信息</h4>
            <table class="kv-table">${kvRows(info.runtimeInfo)}</table>
            <button class="btn-secondary" onclick="document.getElementById('detailPanel').style.display='none'">关闭</button>
        `;
    }

    function kvRows(obj) {
        if (!obj) return '';
        return Object.entries(obj).map(([k, v]) => {
            if (typeof v === 'object') v = JSON.stringify(v);
            return `<tr><td>${k}</td><td>${v}</td></tr>`;
        }).join('');
    }

    function start() {
        const interval = parseInt(document.getElementById('intervalSel').value);
        if (timer) clearInterval(timer);
        fetchAll();
        timer = setInterval(fetchAll, interval);
    }

    document.getElementById('intervalSel').addEventListener('change', start);
    document.getElementById('pauseBtn').addEventListener('click', (e) => {
        paused = !paused;
        e.target.textContent = paused ? '▶ 继续' : '⏸ 暂停';
    });

    start();
})();