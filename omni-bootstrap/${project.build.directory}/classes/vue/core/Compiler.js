// webapps/vue/core/Compiler.js
import { Watcher } from './Watcher.js';
import { parseEventName } from './utils.js';

export class Compiler {
    constructor(vm) {
        this.vm = vm;
        this.el = vm.$el;
        this.compile(this.el);
    }

    compile(node) {
        if (node.nodeType === 1) {
            this.compileElement(node);
            node.childNodes.forEach(child => this.compile(child));
        } else if (node.nodeType === 3) {
            this.compileText(node);
        }
    }

    compileElement(node) {
        const attrs = Array.from(node.attributes);
        for (let attr of attrs) {
            const name = attr.name;
            const value = attr.value;

            const eventName = parseEventName(name);
            if (eventName) {
                this.bindEvent(node, eventName, value);
                node.removeAttribute(name);
            }
            else if (name.startsWith(':')) {
                const prop = name.slice(1);
                this.bindProp(node, prop, value);
                node.removeAttribute(name);
            }
            else if (name.startsWith('v-bind:')) {
                const prop = name.split(':')[1];
                this.bindProp(node, prop, value);
                node.removeAttribute(name);
            }
            else if (name === 'v-model') {
                this.bindModel(node, value);
                node.removeAttribute(name);
            }
            else if (name === 'v-if') {
                this.bindIf(node, value);
                node.removeAttribute(name);
            }
            else if (name === 'v-show') {
                this.bindShow(node, value);
                node.removeAttribute(name);
            }
        }
    }

    compileText(node) {
        const text = node.textContent;
        const reg = /\{\{(.*?)\}\}/g;
        if (reg.test(text)) {
            const expressions = [];
            let lastIndex = 0;
            let match;
            const parts = [];
            while ((match = reg.exec(text)) !== null) {
                const expr = match[1].trim();
                parts.push(text.slice(lastIndex, match.index));
                expressions.push(expr);
                lastIndex = match.index + match[0].length;
            }
            parts.push(text.slice(lastIndex));

            const updateText = () => {
                let newText = '';
                for (let i = 0; i < expressions.length; i++) {
                    const val = this.safeEvaluate(expressions[i]);
                    newText += parts[i] + (val !== undefined && val !== null ? val : '');
                }
                newText += parts[parts.length - 1];
                node.textContent = newText;
            };
            updateText();
            expressions.forEach(expr => {
                new Watcher(this.vm, expr, updateText);
            });
        }
    }

    safeEvaluate(exp) {
        try {
            const fn = new Function('vm', `with(vm) { return ${exp} }`);
            return fn(this.vm);
        } catch (e) {
            console.warn('表达式解析错误:', exp, e);
            return undefined;
        }
    }

    evaluateExpression(exp) {
        return this.safeEvaluate(exp);
    }

    bindEvent(node, event, expression) {
        let methodName = expression;
        let args = [];
        const callMatch = expression.match(/^(\w+)\((.*)\)$/);
        if (callMatch) {
            methodName = callMatch[1];
            const argsStr = callMatch[2];
            if (argsStr.trim()) {
                args = argsStr.split(',').map(arg => {
                    arg = arg.trim();
                    if ((arg.startsWith("'") && arg.endsWith("'")) || (arg.startsWith('"') && arg.endsWith('"'))) {
                        return arg.slice(1, -1);
                    }
                    if (!isNaN(arg)) return parseFloat(arg);
                    try {
                        return this.safeEvaluate(arg);
                    } catch (e) {
                        console.warn(`事件参数求值失败: ${arg}`, e);
                        return undefined;
                    }
                });
            }
        }
        let handler = this.vm[methodName] || this.vm.$methods?.[methodName];
        if (handler) {
            if (args.length) {
                node.addEventListener(event, (e) => handler.apply(this.vm, args));
            } else {
                node.addEventListener(event, handler.bind(this.vm));
            }
        } else {
            console.warn(`事件处理函数 ${methodName} 未定义`);
        }
    }

    bindProp(node, prop, expression) {
        // 对于 class 属性，需要保存初始静态类，避免动态更新时叠加
        let staticClass = null;
        if (prop === 'class') {
            staticClass = node.getAttribute('class') || '';
            node.removeAttribute('class'); // 先移除，后续完全由动态值控制合并
        }
        const update = () => {
            let value = this.safeEvaluate(expression);
            if (prop === 'class') {
                let dynamicClass = '';
                if (typeof value === 'string') {
                    dynamicClass = value;
                } else if (value === true) {
                    dynamicClass = 'active'; // 支持布尔值简化写法
                } else if (value === false) {
                    dynamicClass = '';
                } else if (value && typeof value === 'object') {
                    // 支持对象语法 { active: condition }
                    dynamicClass = Object.keys(value).filter(k => value[k]).join(' ');
                }
                const finalClass = (staticClass + ' ' + dynamicClass).trim();
                node.setAttribute('class', finalClass);
            } else {
                if (value !== undefined && value !== null) {
                    node.setAttribute(prop, value);
                } else {
                    node.removeAttribute(prop);
                }
            }
        };
        update();
        new Watcher(this.vm, expression, update);
    }

    bindModel(node, expression) {
        const update = () => {
            const value = this.safeEvaluate(expression);
            if (node.value !== undefined) node.value = value;
            else node.textContent = value;
        };
        update();
        new Watcher(this.vm, expression, update);
        node.addEventListener('input', (e) => {
            const keys = expression.split('.');
            let target = this.vm.$data;
            for (let i = 0; i < keys.length - 1; i++) {
                if (target[keys[i]] === undefined) target[keys[i]] = {};
                target = target[keys[i]];
            }
            target[keys[keys.length - 1]] = e.target.value;
        });
    }

    bindIf(node, expression) {
        const parent = node.parentNode;
        const placeholder = document.createComment('v-if');
        let removed = false;
        const update = () => {
            let value = false;
            try {
                const result = this.safeEvaluate(expression);
                value = !!result;
            } catch (e) {
                value = false;
            }
            if (value) {
                if (removed) {
                    parent.replaceChild(node, placeholder);
                    removed = false;
                }
            } else {
                if (!removed) {
                    parent.replaceChild(placeholder, node);
                    removed = true;
                }
            }
        };
        update();
        new Watcher(this.vm, expression, update);
    }

    bindShow(node, expression) {
        const update = () => {
            let value = false;
            try {
                const result = this.safeEvaluate(expression);
                value = !!result;
            } catch (e) {
                value = false;
            }
            node.style.display = value ? '' : 'none';
        };
        update();
        new Watcher(this.vm, expression, update);
    }
}