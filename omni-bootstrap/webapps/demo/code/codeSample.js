/**
 * codeSample.js —— 为「🎬 生成示例」按钮绑定点击事件
 * 独立文件，静态按钮在 codeStats.html 中已存在，本文件只负责绑定
 * 示例内容：4 个简单文件（Hello / Calculator / README / pom）
 */
(function () {
    'use strict';
    console.log('[codeSample] ✅ 加载');

    var SAMPLE_LINES = [
        '// ======== Hello.java ========',
        'public class Hello {',
        '    public static void main(String[] args) {',
        '        System.out.println("Hello, CodeStats!");',
        '    }',
        '}',
        '',
        '// ======== Calculator.java ========',
        'public class Calculator {',
        '    public int add(int a, int b) { return a + b; }',
        '    public int sub(int a, int b) { return a - b; }',
        '    public int mul(int a, int b) { return a * b; }',
        '    public int div(int a, int b) {',
        '        if (b == 0) throw new IllegalArgumentException("除数不能为 0");',
        '        return a / b;',
        '    }',
        '}',
        '',
        '// ======== README.md ========',
        '# 简单示例项目',
        '',
        '「生成示例」按钮生成的演示内容，包含 4 个文件：',
        '',
        '- Hello.java',
        '- Calculator.java',
        '- README.md',
        '- pom.xml',
        '',
        '// ======== pom.xml ========',
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<project>',
        '    <modelVersion>4.0.0</modelVersion>',
        '    <groupId>com.demo</groupId>',
        '    <artifactId>hello</artifactId>',
        '    <version>1.0</version>',
        '</project>',
        ''
    ];

    var SAMPLE_CONTENT = SAMPLE_LINES.join('\n');

    function fill() {
        console.log('[codeSample] 🎬 点击');
        var input = document.getElementById('importMdInput');
        if (!input) { alert('找不到 #importMdInput，请先切换到「代码导入」页签'); return; }
        input.value = SAMPLE_CONTENT;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        console.log('[codeSample] 已填入 ' + SAMPLE_CONTENT.length + ' 字符');
        var parseBtn = document.getElementById('importParseBtn');
        if (parseBtn) {
            setTimeout(function () {
                try { parseBtn.click(); } catch (e) { console.warn(e); }
            }, 50);
        }
    }

    function bind() {
        var btn = document.getElementById('importSampleBtn');
        if (!btn) return false;
        if (btn.dataset.bound === '1') return true;
        btn.dataset.bound = '1';
        btn.addEventListener('click', fill);
        console.log('[codeSample] ✅ 按钮已绑定');
        return true;
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bind);
    } else {
        bind();
    }

    // 轮询兜底（5 秒内必绑上）
    if (!bind()) {
        var n = 0, t = setInterval(function () {
            n++;
            if (bind() || n > 50) clearInterval(t);
        }, 100);
    }

    window.__codeSample = { content: SAMPLE_CONTENT, fill: fill, bind: bind };
})();