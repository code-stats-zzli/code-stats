/**
 * 入口：装配 DiagramEngine，绑定工具栏 / 属性面板 / 快捷键 / 导入导出
 */
import { DiagramEngine } from './DiagramEngine.js';

const svg = document.getElementById('dg-svg');
const engine = new DiagramEngine(svg);

// 空画布初始化
engine.load({ version: 1, nodes: [] });

/* ============ 工具栏 ============ */
function bindToolButtons() {
    document.querySelectorAll('[data-tool]').forEach(btn => {
        btn.addEventListener('click', () => {
            const tool = btn.dataset.tool;
            engine.setTool(tool);
            document.querySelectorAll('[data-tool]').forEach(b =>
                b.classList.toggle('active', b === btn)
            );
        });
    });
}
bindToolButtons();

/* ============ 工具栏动作 ============ */
document.getElementById('btn-undo')?.addEventListener('click', () => engine.undo());
document.getElementById('btn-redo')?.addEventListener('click', () => engine.redo());
document.getElementById('btn-delete')?.addEventListener('click', () => engine.deleteSelected());
document.getElementById('btn-fit')?.addEventListener('click', () => engine.zoomToFit());
document.getElementById('btn-clear')?.addEventListener('click', () => {
    if (confirm('确定清空画布？')) engine.clear();
});

/* ============ 导出 ============ */
document.getElementById('btn-export-svg')?.addEventListener('click', () => {
    downloadText(engine.exportSvg(), 'diagram.svg', 'image/svg+xml');
});
document.getElementById('btn-export-json')?.addEventListener('click', () => {
    downloadText(JSON.stringify(engine.serialize(), null, 2), 'diagram.json', 'application/json');
});
document.getElementById('btn-export-png')?.addEventListener('click', async () => {
    try {
        const dataUrl = await engine.exportPng();
        const a = document.createElement('a');
        a.href = dataUrl;
        a.download = 'diagram.png';
        a.click();
    } catch (e) {
        alert('PNG 导出失败：' + e.message);
    }
});

/* ============ 导入 ============ */
const fileInput = document.getElementById('file-import');
document.getElementById('btn-import')?.addEventListener('click', () => fileInput?.click());
fileInput?.addEventListener('change', () => {
    const file = fileInput.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => {
        try {
            engine.load(JSON.parse(e.target.result));
        } catch (err) {
            alert('导入失败：' + err.message);
        }
        fileInput.value = '';
    };
    reader.readAsText(file);
});

/* ============ 属性面板 ============ */
const inspector = document.getElementById('dg-inspector');

function refreshInspector() {
    if (!inspector) return;
    const ids = engine.selection.ids();
    if (ids.length === 0) {
        inspector.innerHTML = '<div class="dg-empty-tip">未选中任何元素</div>';
        return;
    }
    if (ids.length > 1) {
        inspector.innerHTML = `<div class="dg-empty-tip">已选中 ${ids.length} 个元素</div>`;
        return;
    }
    const node = engine.diagram.getNode(ids[0]);
    if (!node) { inspector.innerHTML = ''; return; }
    const type = engine.getNodeTypes().find(t => t.type === node.type);
    let html = `<h3>${type ? type.label : node.type}</h3>`;
    html += field('X', 'x', node.x, 'number');
    html += field('Y', 'y', node.y, 'number');
    if (!['line', 'arrow', 'pen'].includes(node.type)) {
        html += field('宽', 'w', node.w, 'number');
        html += field('高', 'h', node.h, 'number');
    }
    if (node.type === 'text') {
        html += field('文本', 'props.text', node.props.text || '', 'text');
        html += field('字号', 'props.fontSize', node.props.fontSize || 16, 'number');
    }
    if (node.type !== 'pen' && node.type !== 'line' && node.type !== 'arrow') {
        html += field('填充', 'props.fill', normalizeColor(node.props.fill), 'color');
    }
    html += field('描边', 'props.stroke', normalizeColor(node.props.stroke), 'color');
    html += field('线宽', 'props.strokeWidth', node.props.strokeWidth || 2, 'number');

    inspector.innerHTML = html;
    inspector.querySelectorAll('input[data-key]').forEach(input => {
        input.addEventListener('change', () => {
            const key = input.dataset.key;
            let value = input.value;
            if (input.type === 'number') value = parseFloat(value) || 0;
            const patch = {};
            if (key.startsWith('props.')) {
                patch.props = { [key.slice(6)]: value };
            } else {
                patch[key] = value;
            }
            engine.updateSelected(patch);
            engine.render();
        });
    });
}

function normalizeColor(v) {
    if (!v || typeof v !== 'string') return '#000000';
    if (v.startsWith('#') && (v.length === 7 || v.length === 4)) return v;
    return '#000000';
}

function field(label, key, value, type) {
    return `<label class="dg-field">
        <span>${label}</span>
        <input type="${type}" data-key="${key}" value="${escapeHtml(String(value))}">
    </label>`;
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"]/g,
        m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[m])
    );
}

engine.on('selection:change', refreshInspector);
engine.on('change', refreshInspector);

/* ============ 快捷键 ============ */
window.addEventListener('keydown', (e) => {
    if (['INPUT', 'TEXTAREA'].includes(e.target.tagName)) return;
    if (e.ctrlKey || e.metaKey) {
        if (e.key === 'z' && !e.shiftKey) { e.preventDefault(); engine.undo(); }
        else if (e.key === 'y' || (e.key === 'z' && e.shiftKey)) { e.preventDefault(); engine.redo(); }
        else if (e.key === 'a') { e.preventDefault(); engine.selectAll(); }
        else if (e.key === 's') {
            e.preventDefault();
            downloadText(JSON.stringify(engine.serialize(), null, 2), 'diagram.json', 'application/json');
        }
    } else if (e.key === 'Delete' || e.key === 'Backspace') {
        e.preventDefault();
        engine.deleteSelected();
    } else if (e.key === 'Escape') {
        engine.selection.clear();
        engine.emit('selection:change');
        engine.render();
    } else if (e.key === 'v' || e.key === 'V') { switchToolByKey('select'); }
    else if (e.key === 'r' || e.key === 'R') { switchToolByKey('rect'); }
    else if (e.key === 'o' || e.key === 'O') { switchToolByKey('ellipse'); }
    else if (e.key === 'l' || e.key === 'L') { switchToolByKey('line'); }
    else if (e.key === 'a' || e.key === 'A') { switchToolByKey('arrow'); }
    else if (e.key === 't' || e.key === 'T') { switchToolByKey('text'); }
    else if (e.key === 'p' || e.key === 'P') { switchToolByKey('pen'); }
});

function switchToolByKey(tool) {
    const btn = document.querySelector(`[data-tool="${tool}"]`);
    if (btn) btn.click();
}

function downloadText(text, filename, mime) {
    const blob = new Blob([text], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

setTimeout(() => engine.zoomToFit(), 100);
console.log('[diagram] ready', engine);