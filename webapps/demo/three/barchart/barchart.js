const container = document.getElementById('chart-container');
const API_BASE = (window.apiUrl ? window.apiUrl('/3d/api') : '/3d/api');

async function fetchBarData() {
    const params = new URLSearchParams();
    params.append('rootDir', '../../../');
    params.append('extensions', 'java');
    params.append('excludeComments', 'true');
    params.append('threadCount', '4');
    const url = API_BASE + '/bar-data?' + params.toString();
    try {
        const resp = await fetch(url);
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const data = await resp.json();
        if (data.error) throw new Error(data.error);
        return data;
    } catch (err) {
        console.warn('使用模拟数据', err);
        return {
            categories: ['Java', 'Python', 'JavaScript', 'Go', 'C++'],
            series: [15234, 8921, 6543, 3210, 1876]
        };
    }
}

async function renderChart() {
    const chart = echarts.init(container);
    window.addEventListener('resize', () => chart.resize());

    chart.showLoading();
    try {
        const data = await fetchBarData();
        const categories = data.categories || [];
        const seriesData = data.series || [];

        const option = {
            tooltip: { trigger: 'item', formatter: params => `${params.name}<br/>代码行数: ${params.value}` },
            grid3D: {
                viewControl: { autoRotate: true, autoRotateSpeed: 5, distance: 200 },
                boxWidth: 100, boxHeight: 60, boxDepth: 80,
                axisLine: { lineStyle: { color: '#4da6ff' } },
                axisLabel: { color: '#e2e8f0', fontSize: 12 },
                splitLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }
            },
            xAxis3D: { type: 'category', data: categories, axisLabel: { rotate: 30, interval: 0 } },
            yAxis3D: { type: 'value', name: '代码行数', nameTextStyle: { color: '#e2e8f0' } },
            zAxis3D: { type: 'category', data: [''], axisLabel: { show: false } },
            series: [{
                type: 'bar3D',
                data: seriesData.map((value, index) => ({ name: categories[index] || `项${index+1}`, value })),
                shading: 'lambert',
                label: { show: true, formatter: '{c}', textStyle: { color: '#fff', fontSize: 12 } },
                itemStyle: { color: '#4da6ff', opacity: 0.9 },
                emphasis: { itemStyle: { color: '#ff6b6b' } }
            }]
        };
        chart.hideLoading();
        chart.setOption(option, true);
        setTimeout(() => chart.resize(), 100);
    } catch (err) {
        chart.hideLoading();
        chart.setOption({
            title: { text: '加载数据失败，请检查后端服务', left: 'center', top: 'center', textStyle: { color: '#ef4444' } }
        });
    }
}

renderChart();