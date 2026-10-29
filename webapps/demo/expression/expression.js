(function () {
    'use strict';

    const varGroups = [
        { id: 'g1', name: '默认变量', collapsed: false, vars: [
                { id: 'v1', name: 'name', raw: 'World' },
                { id: 'v2', name: 'age',  raw: '18' }
            ]}
    ];
    let functionMeta = [];
    let uid = 100;

    const nextId = (p) => p + (++uid);

    const varTree     = document.getElementById('varTree');
    const addGroupBtn = document.getElementById('addGroupBtn');
    const addVarBtn   = document.getElementById('addVarBtn');
    const opGroup     = document.getElementById('opGroup');
    const editor      = document.getElementById('exprEditor');
    const evaluateBtn = document.getElementById('evaluateBtn');
    const clearBtn    = document.getElementById('clearBtn');
    const exampleBtn  = document.getElementById('exampleBtn');
    const exprResult  = document.getElementById('exprResult');
    const resultMeta  = document.getElementById('resultMeta');
    const fnList      = document.getElementById('fnList');
    const fnSearch    = document.getElementById('fnSearch');

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"']/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[m]));
    }

    function insertAtCursor(text) {
        const start = editor.selectionStart, end = editor.selectionEnd;
        const before = editor.value.substring(0, start);
        const after  = editor.value.substring(end);
        editor.value = before + text + after;
        editor.selectionStart = editor.selectionEnd = start + text.length;
        editor.focus();
    }

    function parseValue(raw) {
        if (raw == null) return null;
        const s = String(raw).trim();
        if (s === '') return '';
        if (s === 'null') return null;
        if (s === 'true') return true;
        if (s === 'false') return false;
        if (/^-?\d+$/.test(s)) return Number(s);
        if (/^-?\d+\.\d+$/.test(s)) return Number(s);
        if (/^[\[{]/.test(s)) { try { return JSON.parse(s); } catch (e) {} }
        return String(raw);
    }

    function truncate(s, n) {
        if (!s) return '';
        return s.length > n ? s.substring(0, n) + '…' : s;
    }

    /* ==================== 变量树 ==================== */

    function renderVarTree() {
        if (!varGroups.length) {
            varTree.innerHTML = '<div class="empty-tip">暂无变量，点击下方 "+ 变量" 添加</div>';
            return;
        }
        varTree.innerHTML = varGroups.map(g => `
            <div class="var-group" data-gid="${g.id}">
                <div class="var-group-head" data-action="toggle-group" data-gid="${g.id}">
                    <span class="toggle">${g.collapsed ? '▶' : '▼'}</span>
                    <span class="group-name">${escapeHtml(g.name)}</span>
                    <span class="group-actions">
                        <button data-action="add-var-in-group" data-gid="${g.id}" title="新增变量">+</button>
                        <button data-action="rename-group" data-gid="${g.id}" title="重命名">✎</button>
                        <button data-action="del-group" data-gid="${g.id}" title="删除">×</button>
                    </span>
                </div>
                <div class="var-list ${g.collapsed ? 'collapsed' : ''}">
                    ${g.vars.length === 0
            ? '<div class="empty-tip" style="padding:6px;">空分组</div>'
            : g.vars.map(v => `
                            <div class="var-item" data-vid="${v.id}" data-gid="${g.id}" title="点击插入到编辑器">
                                <span class="var-name">$${escapeHtml(v.name)}</span>
                                <span class="var-value">= ${escapeHtml(truncate(v.raw, 20))}</span>
                                <span class="var-actions">
                                    <button data-action="edit-var" data-vid="${v.id}" data-gid="${g.id}" title="编辑值">✎</button>
                                    <button data-action="del-var"  data-vid="${v.id}" data-gid="${g.id}" title="删除">×</button>
                                </span>
                            </div>`).join('')
        }
                </div>
            </div>`).join('');
    }

    varTree.addEventListener('click', (e) => {
        const target = e.target.closest('[data-action], .var-item');
        if (!target) return;

        if (target.classList.contains('var-item') && !e.target.closest('.var-actions')) {
            const g = varGroups.find(x => x.id === target.dataset.gid);
            const v = g && g.vars.find(x => x.id === target.dataset.vid);
            if (v) insertAtCursor('$' + v.name);
            return;
        }

        const action = target.dataset.action;
        const gid = target.dataset.gid;
        const vid = target.dataset.vid;

        if (action === 'toggle-group') {
            const g = varGroups.find(x => x.id === gid);
            if (g) { g.collapsed = !g.collapsed; renderVarTree(); }
        } else if (action === 'add-var-in-group') {
            addVariableToGroup(gid);
        } else if (action === 'rename-group') {
            const g = varGroups.find(x => x.id === gid);
            if (!g) return;
            const name = prompt('分组名称', g.name);
            if (name && name.trim()) { g.name = name.trim(); renderVarTree(); }
        } else if (action === 'del-group') {
            if (!confirm('确定删除该分组及其中所有变量？')) return;
            const idx = varGroups.findIndex(x => x.id === gid);
            if (idx >= 0) { varGroups.splice(idx, 1); renderVarTree(); }
        } else if (action === 'edit-var') {
            const g = varGroups.find(x => x.id === gid);
            const v = g && g.vars.find(x => x.id === vid);
            if (!v) return;
            const raw = prompt(`变量 $${v.name} 的值\n（支持 null / true / false / 数字 / JSON / 字符串）`, v.raw);
            if (raw !== null) { v.raw = raw; renderVarTree(); }
        } else if (action === 'del-var') {
            const g = varGroups.find(x => x.id === gid);
            if (!g) return;
            const idx = g.vars.findIndex(x => x.id === vid);
            if (idx >= 0) { g.vars.splice(idx, 1); renderVarTree(); }
        }
    });

    function addVariableToGroup(gid) {
        const g = varGroups.find(x => x.id === gid);
        if (!g) return;
        const name = prompt('变量名（会自动加 $ 前缀）', 'myVar');
        if (!name) return;
        const cleanName = name.trim().replace(/^\$/, '');
        if (!/^[A-Za-z_]\w*$/.test(cleanName)) {
            alert('变量名只能包含字母、数字、下划线，且不能以数字开头');
            return;
        }
        if (g.vars.some(v => v.name === cleanName)) {
            alert('该分组已存在同名变量');
            return;
        }
        const raw = prompt(`变量 $${cleanName} 的值`, '');
        if (raw === null) return;
        g.vars.push({ id: nextId('v'), name: cleanName, raw });
        renderVarTree();
    }

    addGroupBtn.addEventListener('click', () => {
        const name = prompt('新分组名称', '变量组');
        if (!name || !name.trim()) return;
        varGroups.push({ id: nextId('g'), name: name.trim(), collapsed: false, vars: [] });
        renderVarTree();
    });

    addVarBtn.addEventListener('click', () => {
        if (!varGroups.length) {
            varGroups.push({ id: nextId('g'), name: '默认变量', collapsed: false, vars: [] });
        }
        addVariableToGroup(varGroups[varGroups.length - 1].id);
    });

    /* ==================== 运算符 ==================== */

    const OPERATORS = [
        { label: '+', insert: ' + ' }, { label: '-', insert: ' - ' },
        { label: '*', insert: ' * ' }, { label: '/', insert: ' / ' },
        { label: '%', insert: ' % ' }, { label: '(', insert: '(' },
        { label: ')', insert: ')' },   { label: '==', insert: ' == ' },
        { label: '!=', insert: ' != ' }, { label: '>', insert: ' > ' },
        { label: '<', insert: ' < ' }, { label: '>=', insert: ' >= ' },
        { label: '<=', insert: ' <= ' }, { label: '&&', insert: ' && ' },
        { label: '||', insert: ' || ' }, { label: '!', insert: '!' },
        { label: '?:', insert: ' ?  :  ' }, { label: ',', insert: ', ' }
    ];
    opGroup.innerHTML = OPERATORS.map(op =>
        `<button data-insert="${escapeHtml(op.insert)}">${escapeHtml(op.label)}</button>`).join('');
    opGroup.addEventListener('click', (e) => {
        const btn = e.target.closest('button[data-insert]');
        if (btn) insertAtCursor(btn.dataset.insert);
    });

    /* ==================== 函数库 ==================== */

    async function loadFunctionMeta() {
        try {
            const resp = await fetch(window.apiUrl('/expression/api/functions'));
            functionMeta = await resp.json();
            if (!Array.isArray(functionMeta)) throw new Error('函数列表格式错误');
            renderFunctionList();
        } catch (err) {
            fnList.innerHTML = `<div class="loading" style="color:#dc2626;">❌ 加载失败：${escapeHtml(err.message)}</div>`;
        }
    }

    function renderFunctionList(keyword) {
        const kw = (keyword || '').trim().toLowerCase();
        let html = '';
        functionMeta.forEach(cat => {
            const matched = (cat.functions || []).filter(f =>
                !kw
                || f.name.toLowerCase().includes(kw)
                || (f.signature || '').toLowerCase().includes(kw)
                || (f.desc || '').toLowerCase().includes(kw));
            if (!matched.length) return;
            html += `
                <div class="fn-category">
                    <div class="fn-category-title">${escapeHtml(cat.title)}</div>
                    ${matched.map(f => `
                        <div class="fn-item" data-insert="${escapeHtml(f.name + '(')}" title="点击插入到编辑器">
                            <div class="fn-name">${escapeHtml(f.signature)}</div>
                            <div class="fn-desc">${escapeHtml(f.desc)}</div>
                            ${f.example ? `<div class="fn-example">例：${escapeHtml(f.example)}</div>` : ''}
                        </div>`).join('')}
                </div>`;
        });
        fnList.innerHTML = html || '<div class="empty-tip">没有匹配的函数</div>';
    }

    fnList.addEventListener('click', (e) => {
        const item = e.target.closest('.fn-item');
        if (item) insertAtCursor(item.dataset.insert);
    });
    fnSearch.addEventListener('input', (e) => renderFunctionList(e.target.value));

    /* ==================== 求值 ==================== */

    function collectVariables() {
        const map = {};
        varGroups.forEach(g => g.vars.forEach(v => { map['$' + v.name] = parseValue(v.raw); }));
        return map;
    }

    async function evaluate() {
        const expression = editor.value.trim();
        if (!expression) { showResult('error', '请输入表达式'); return; }

        evaluateBtn.disabled = true;
        evaluateBtn.textContent = '⏳ 求值中...';
        exprResult.className = 'result-content';
        exprResult.textContent = '求值中...';
        resultMeta.textContent = '';

        try {
            const resp = await fetch(window.apiUrl('/expression/api/evaluate'), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                body: JSON.stringify({ expression, variables: collectVariables() })
            });
            const data = await resp.json();
            if (data.success) {
                showResult('success', prettyPrint(data.result, data.resultType));
                resultMeta.textContent = `类型: ${data.resultType} · 耗时: ${data.durationMs}ms`;
            } else {
                showResult('error', '❌ ' + (data.error || '未知错误'));
                resultMeta.textContent = data.durationMs != null ? `耗时: ${data.durationMs}ms` : '';
            }
        } catch (err) {
            showResult('error', '❌ 请求失败: ' + err.message);
        } finally {
            evaluateBtn.disabled = false;
            evaluateBtn.textContent = '▶ 求值 (Ctrl+Enter)';
        }
    }

    function prettyPrint(value, type) {
        if (value === null || value === undefined) return '(null)';
        if (type === 'string') return value;
        if (typeof value === 'object') return JSON.stringify(value, null, 2);
        return String(value);
    }

    function showResult(kind, text) {
        exprResult.className = 'result-content ' + kind;
        exprResult.textContent = text;
    }

    evaluateBtn.addEventListener('click', evaluate);
    editor.addEventListener('keydown', (e) => {
        if (e.ctrlKey && e.key === 'Enter') { e.preventDefault(); evaluate(); }
        if (e.key === 'Tab') { e.preventDefault(); insertAtCursor('  '); }
    });

    clearBtn.addEventListener('click', () => {
        editor.value = ''; showResult('', '尚未求值');
        resultMeta.textContent = ''; editor.focus();
    });

    exampleBtn.addEventListener('click', () => {
        editor.value =
            "'Hello, ' + $name + '! 今年 ' + $age + ' 岁，明年 ' + ($age + 1) + ' 岁\n" +
            "// 试试这些（把下面任意一行取消注释）\n" +
            "// substring($name, 0, 3)\n" +
            "// toUpperCase($name) + ' / ' + length($name)\n" +
            "// year(today()) + ' 年 ' + month(today()) + ' 月 ' + day(today()) + ' 日'\n" +
            "// round(3.14159, 2)\n" +
            "// addDays(today(), 7)\n" +
            "// diffDays('2024-01-01', '2024-12-31')\n" +
            "// ifNull($name, '匿名用户')";
        editor.focus();
    });

    /* ==================== 启动 ==================== */
    renderVarTree();
    loadFunctionMeta();
    editor.focus();
})();