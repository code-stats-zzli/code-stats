// ========== 增强型 SQL 编辑器（无高亮，仅保留提示+格式化+清空） ==========
const EnhancedSqlModule = (function() {
    // DOM 元素引用
    let textarea = null;
    let popup = null;
    let sqlResultArea = null;
    let executeBtn = null;
    let formatBtn = null;
    let exampleBtn = null;
    let clearBtn = null;

    // 提示相关状态
    let currentHintList = [];
    let selectedHintIndex = -1;
    let hintStartPos = { ch: 0 };
    let currentWord = '';

    // 缓存数据
    let tableNames = [];
    let columnMap = new Map();

    // MySQL 关键字与函数
    const MYSQL_KEYWORDS = [
        'SELECT', 'INSERT', 'UPDATE', 'DELETE', 'REPLACE', 'FROM', 'WHERE', 'JOIN', 'LEFT JOIN', 'RIGHT JOIN',
        'INNER JOIN', 'OUTER JOIN', 'ON', 'AND', 'OR', 'NOT', 'IN', 'EXISTS', 'BETWEEN', 'LIKE',
        'ORDER BY', 'GROUP BY', 'HAVING', 'LIMIT', 'OFFSET', 'UNION', 'UNION ALL', 'DISTINCT',
        'COUNT', 'SUM', 'AVG', 'MAX', 'MIN', 'AS', 'TABLE', 'CREATE', 'ALTER', 'DROP', 'INDEX',
        'PRIMARY KEY', 'FOREIGN KEY', 'REFERENCES', 'DEFAULT', 'NULL', 'NOT NULL', 'AUTO_INCREMENT'
    ];
    const MYSQL_FUNCTIONS = [
        'NOW()', 'CURDATE()', 'CURTIME()', 'DATE()', 'TIME()', 'YEAR()', 'MONTH()', 'DAY()',
        'CONCAT()', 'SUBSTRING()', 'LENGTH()', 'UPPER()', 'LOWER()', 'TRIM()',
        'IF()', 'CASE', 'COALESCE()', 'NULLIF()',
        'COUNT()', 'SUM()', 'AVG()', 'MAX()', 'MIN()', 'GROUP_CONCAT()',
        'VERSION()', 'DATABASE()', 'USER()'
    ];
    let staticHints = [...MYSQL_KEYWORDS, ...MYSQL_FUNCTIONS];

    function getCurrentDb() { return DB.getCurrentDb(); }
    function getCurrentTable() { return DB.getCurrentTable(); }

    async function loadTableNames() {
        const db = getCurrentDb();
        if (!db) { tableNames = []; return; }
        try {
            const tables = await DB.fetchJSON(DB.apiBase + '/tables?db=' + encodeURIComponent(db));
            tableNames = tables.map(t => t.name);
        } catch (err) { console.error(err); tableNames = []; }
    }

    async function loadColumnsForTable(tableName) {
        if (!tableName) return [];
        if (columnMap.has(tableName)) return columnMap.get(tableName);
        const db = getCurrentDb();
        if (!db) return [];
        try {
            const columns = await DB.fetchJSON(DB.apiBase + '/columns?db=' + encodeURIComponent(db) + '&table=' + encodeURIComponent(tableName));
            const cols = columns.map(c => c.columnName);
            columnMap.set(tableName, cols);
            return cols;
        } catch (err) { console.error(err); return []; }
    }

    function extractTableNamesFromSql(sql) {
        const pattern = /(?:FROM|JOIN|INTO|UPDATE)\s+([`"]?)(\w+)\1/gi;
        const tables = new Set();
        let match;
        while ((match = pattern.exec(sql)) !== null) tables.add(match[2]);
        return Array.from(tables);
    }

    async function refreshDynamicHints() {
        await loadTableNames();
        const currentTable = getCurrentTable();
        if (currentTable) await loadColumnsForTable(currentTable);
        if (textarea) {
            const sql = textarea.value;
            const involvedTables = extractTableNamesFromSql(sql);
            for (const tbl of involvedTables) await loadColumnsForTable(tbl);
        }
    }

    function getCursorIndex() { return textarea.selectionStart; }
    function getCurrentWordBeforeCursor() {
        const cursorPos = textarea.selectionStart;
        const text = textarea.value;
        let start = cursorPos;
        while (start > 0 && /[\w\.]/.test(text[start - 1])) start--;
        const word = text.substring(start, cursorPos);
        return { word, start };
    }

    function generateHintList(word) {
        if (!word) return [];
        const upperWord = word.toUpperCase();
        const matches = new Set();
        staticHints.forEach(h => { if (h.toUpperCase().startsWith(upperWord)) matches.add(h); });
        tableNames.forEach(t => { if (t.toUpperCase().startsWith(upperWord)) matches.add(t); });
        const currentTable = getCurrentTable();
        if (currentTable && columnMap.has(currentTable)) {
            columnMap.get(currentTable).forEach(col => {
                if (col.toUpperCase().startsWith(upperWord)) matches.add(col);
            });
        }
        const dotIndex = word.lastIndexOf('.');
        if (dotIndex !== -1) {
            const tableName = word.substring(0, dotIndex);
            const fieldPrefix = word.substring(dotIndex + 1);
            if (columnMap.has(tableName)) {
                columnMap.get(tableName).forEach(col => {
                    if (col.toUpperCase().startsWith(fieldPrefix.toUpperCase())) {
                        matches.add(`${tableName}.${col}`);
                    }
                });
            }
        }
        return Array.from(matches).sort();
    }

    function showPopup(hints, startCharIndex) {
        if (!hints.length) { hidePopup(); return; }
        currentHintList = hints;
        selectedHintIndex = 0;
        const rect = textarea.getBoundingClientRect();
        const style = getComputedStyle(textarea);
        const lineHeight = parseInt(style.lineHeight) || 20;
        const fontWidth = 7.5;
        const cursorLine = textarea.value.substr(0, startCharIndex).split('\n').length - 1;
        const lineStart = textarea.value.lastIndexOf('\n', startCharIndex - 1) + 1;
        const charIndexInLine = startCharIndex - lineStart;
        const left = rect.left + charIndexInLine * fontWidth;
        const top = rect.top + (cursorLine + 1) * lineHeight;
        popup.style.left = left + 'px';
        popup.style.top = top + 'px';
        popup.style.display = 'block';
        renderPopup();
    }

    function hidePopup() {
        if (popup) popup.style.display = 'none';
        currentHintList = [];
        selectedHintIndex = -1;
    }

    function renderPopup() {
        if (!popup) return;
        const itemsHtml = currentHintList.map((hint, idx) => {
            const activeClass = idx === selectedHintIndex ? 'active' : '';
            return `<div class="hint-item ${activeClass}" data-value="${hint}">${escapeHtml(hint)}</div>`;
        }).join('');
        popup.innerHTML = itemsHtml;
        popup.querySelectorAll('.hint-item').forEach((item, idx) => {
            item.addEventListener('mouseenter', () => { selectedHintIndex = idx; renderPopup(); });
            item.addEventListener('click', () => { insertHint(currentHintList[idx]); hidePopup(); textarea.focus(); });
        });
    }

    function insertHint(hint) {
        const cursorPos = textarea.selectionStart;
        const text = textarea.value;
        const start = hintStartPos.ch;
        const newText = text.substring(0, start) + hint + text.substring(cursorPos);
        textarea.value = newText;
        textarea.setSelectionRange(start + hint.length, start + hint.length);
        textarea.dispatchEvent(new Event('input'));
    }

    function onKeyDown(e) {
        if (popup && popup.style.display === 'block') {
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                selectedHintIndex = (selectedHintIndex + 1) % currentHintList.length;
                renderPopup();
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                selectedHintIndex = (selectedHintIndex - 1 + currentHintList.length) % currentHintList.length;
                renderPopup();
            } else if (e.key === 'Enter' || e.key === 'Tab') {
                e.preventDefault();
                if (selectedHintIndex >= 0 && currentHintList[selectedHintIndex]) {
                    insertHint(currentHintList[selectedHintIndex]);
                    hidePopup();
                }
            } else if (e.key === 'Escape') {
                hidePopup();
            }
        }
    }

    function onInput() {
        const { word, start } = getCurrentWordBeforeCursor();
        if (word && word.length > 0) {
            const hints = generateHintList(word);
            if (hints.length) {
                hintStartPos = { ch: start };
                showPopup(hints, start);
            } else hidePopup();
        } else hidePopup();
        // 不再调用高亮更新
    }

    // ========== SQL 格式化 ==========
    function formatSql() {
        let sql = textarea.value;
        if (!sql.trim()) return;

        let formatted = sql.replace(/\s+/g, ' ').trim();

        formatted = formatted
            .replace(/\bselect\b/gi, 'SELECT')
            .replace(/\bfrom\b/gi, 'FROM')
            .replace(/\bwhere\b/gi, 'WHERE')
            .replace(/\bjoin\b/gi, 'JOIN')
            .replace(/\bleft join\b/gi, 'LEFT JOIN')
            .replace(/\bright join\b/gi, 'RIGHT JOIN')
            .replace(/\binner join\b/gi, 'INNER JOIN')
            .replace(/\border by\b/gi, 'ORDER BY')
            .replace(/\bgroup by\b/gi, 'GROUP BY')
            .replace(/\bhaving\b/gi, 'HAVING')
            .replace(/\blimit\b/gi, 'LIMIT')
            .replace(/\bunion\b/gi, 'UNION')
            .replace(/\binsert into\b/gi, 'INSERT INTO')
            .replace(/\bvalues\b/gi, 'VALUES')
            .replace(/\bupdate\b/gi, 'UPDATE')
            .replace(/\bset\b/gi, 'SET')
            .replace(/\bdelete from\b/gi, 'DELETE FROM')
            .replace(/\bcreate table\b/gi, 'CREATE TABLE')
            .replace(/\balter table\b/gi, 'ALTER TABLE')
            .replace(/\bdrop table\b/gi, 'DROP TABLE');

        formatted = formatted.replace(/\s(SELECT|FROM|WHERE|JOIN|LEFT JOIN|RIGHT JOIN|INNER JOIN|ORDER BY|GROUP BY|HAVING|LIMIT|UNION|INSERT INTO|VALUES|UPDATE|SET|DELETE FROM)\s/gi, '\n$1 ');
        formatted = formatted.replace(/\n\s*\n/g, '\n').trim();

        textarea.value = formatted;
        textarea.dispatchEvent(new Event('input'));
    }

    // ========== 清空 SQL ==========
    function clearSql() {
        textarea.value = '';
        textarea.dispatchEvent(new Event('input'));
        if (sqlResultArea) sqlResultArea.innerHTML = '';
        textarea.focus();
    }

    // ========== 示例SQL ==========
    function setExampleSql() {
        const currentTable = getCurrentTable();
        if (currentTable) {
            textarea.value = `SELECT * FROM \`${currentTable}\` LIMIT 20;`;
        } else {
            textarea.value = 'SELECT 1 AS test;\n-- 请先从左侧选择一个表';
        }
        textarea.dispatchEvent(new Event('input'));
    }

    // ========== 执行SQL（使用 DB.postJSON 自动附带 dsId） ==========
    async function executeSql() {
        const sql = textarea.value.trim();
        if (!sql) { alert('请输入SQL语句'); return; }
        const currentDb = getCurrentDb();
        if (!currentDb) { alert('请先从左侧选择一个数据库'); return; }
        sqlResultArea.innerHTML = '<div class="loading">执行中...</div>';
        try {
            // 通过 DB.postJSON 发送，会自动在 URL 上附加 dsId
            const data = await DB.postJSON(DB.apiBase + '/sqlQuery', { db: currentDb, sql: sql });
            if (data.error) {
                sqlResultArea.innerHTML = `<div class="error">${data.error}</div>`;
                return;
            }
            if (Array.isArray(data) && data.length > 0) {
                const columns = Object.keys(data[0]);
                let html = '<table class="sql-result-table"><thead><tr>';
                columns.forEach(col => html += `<th>${DB.escapeHtml(col)}</th>`);
                html += '</tr></thead><tbody>';
                data.forEach(row => {
                    html += '<tr>';
                    columns.forEach(col => {
                        let val = row[col];
                        if (val === null || val === undefined) val = '';
                        html += `<td>${DB.escapeHtml(String(val))}</td>`;
                    });
                    html += '</tr>';
                });
                html += '</tbody></table>';
                sqlResultArea.innerHTML = html;
            } else {
                sqlResultArea.innerHTML = '<p>查询成功，无结果返回。</p>';
            }
        } catch (err) {
            sqlResultArea.innerHTML = `<div class="error">请求失败: ${err.message}</div>`;
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m]));
    }

    // ========== UI 初始化（仅保留编辑器、按钮和提示层） ==========
    function initUI() {
        textarea = document.getElementById('sqlEditor');
        if (!textarea) return;

        // 添加清空按钮到按钮栏
        const buttonContainer = document.querySelector('#tab-sql .sql-editor > div:first-child');
        if (buttonContainer && !document.getElementById('clearSqlBtn')) {
            clearBtn = document.createElement('button');
            clearBtn.id = 'clearSqlBtn';
            clearBtn.className = 'btn-secondary';
            clearBtn.textContent = '🗑️ 清空SQL';
            clearBtn.addEventListener('click', clearSql);
            buttonContainer.appendChild(clearBtn);
        }

        // 确保 textarea 样式干净（移除可能的高亮包装器残留）
        if (textarea.parentElement && textarea.parentElement.classList.contains('sql-editor-wrapper')) {
            const wrapper = textarea.parentElement;
            const highlighter = wrapper.querySelector('.sql-highlighter');
            if (highlighter) highlighter.remove();
            // 解包：将 textarea 移出 wrapper 并删除 wrapper
            wrapper.parentNode.insertBefore(textarea, wrapper);
            wrapper.remove();
        }
        // 重新设置基础样式，避免背景混乱
        textarea.style.backgroundColor = 'white';
        textarea.style.color = '#1e293b';
        textarea.style.fontFamily = 'monospace';
        textarea.style.fontSize = '14px';
        textarea.style.lineHeight = '1.5';
        textarea.style.padding = '12px';
        textarea.style.border = '1px solid rgba(0,102,255,0.3)';
        textarea.style.borderRadius = '16px';
        textarea.style.resize = 'vertical';

        // 创建提示层
        popup = document.createElement('div');
        popup.className = 'sql-hint-popup';
        popup.style.position = 'fixed';
        popup.style.backgroundColor = '#1e1e2f';
        popup.style.border = '1px solid #0066ff';
        popup.style.borderRadius = '8px';
        popup.style.maxHeight = '200px';
        popup.style.overflowY = 'auto';
        popup.style.zIndex = '10000';
        popup.style.display = 'none';
        popup.style.boxShadow = '0 4px 12px rgba(0,0,0,0.3)';
        document.body.appendChild(popup);

        // 注入必要的样式
        const style = document.createElement('style');
        style.textContent = `
            .sql-hint-popup .hint-item {
                padding: 6px 12px;
                cursor: pointer;
                font-family: monospace;
                color: #e2e8f0;
            }
            .sql-hint-popup .hint-item.active {
                background-color: #0066ff;
                color: white;
            }
            .sql-hint-popup .hint-item:hover {
                background-color: #334155;
            }
        `;
        document.head.appendChild(style);

        textarea.addEventListener('input', onInput);
        textarea.addEventListener('keydown', onKeyDown);
        textarea.addEventListener('click', () => hidePopup());
        textarea.addEventListener('blur', () => setTimeout(hidePopup, 200));
    }

    async function refresh() {
        await refreshDynamicHints();
    }

    async function init() {
        sqlResultArea = document.getElementById('sqlResultArea');
        executeBtn = document.getElementById('executeSqlBtn');
        formatBtn = document.getElementById('formatSqlBtn');
        exampleBtn = document.getElementById('exampleSqlBtn');
        if (executeBtn) executeBtn.addEventListener('click', executeSql);
        if (formatBtn) formatBtn.addEventListener('click', formatSql);
        if (exampleBtn) exampleBtn.addEventListener('click', setExampleSql);
        initUI();
        await refreshDynamicHints();
        window.__refreshSqlHints = refresh;
    }

    return { init, refresh };
})();

// 自动初始化
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => EnhancedSqlModule.init());
} else {
    EnhancedSqlModule.init();
}