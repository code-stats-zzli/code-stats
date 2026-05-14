// js/simple-vue/Compiler.js
import { Watcher } from './Watcher.js';
import { parseEventName } from './utils.js';

export class Compiler {
    constructor(vm) {
        this.vm = vm;
        this.el = vm.$el;
        this.compile(this.el);
    }

    compile(node) {
        if (node.nodeType === 1) { // 元素节点
            this.compileElement(node);
            node.childNodes.forEach(child => this.compile(child));
        } else if (node.nodeType === 3) { // 文本节点
            this.compileText(node);
        }
    }

    compileElement(node) {
        // 处理属性（指令）
        const attrs = Array.from(node.attributes);
        for (let attr of attrs) {
            const name = attr.name;
            const value = attr.value;
            // 事件 @click="handler"
            const eventName = parseEventName(name);
            if (eventName) {
                this.bindEvent(node, eventName, value);
                node.removeAttribute(name);
            }
            // v-bind:prop="data"
            else if (name.startsWith('v-bind:')) {
                const prop = name.split(':')[1];
                this.bindProp(node, prop, value);
                node.removeAttribute(name);
            }
            // v-model 双向绑定
            else if (name === 'v-model') {
                this.bindModel(node, value);
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
                    const val = this.evaluateExpression(expressions[i]);
                    newText += parts[i] + (val !== undefined ? val : '');
                }
                newText += parts[parts.length - 1];
                node.textContent = newText;
            };
            updateText();
            // 每个表达式建立一个 Watcher
            expressions.forEach(expr => {
                new Watcher(this.vm, expr, updateText);
            });
        }
    }

    evaluateExpression(exp) {
        try {
            return new Function('vm', `with(vm) { return ${exp} }`)(this.vm);
        } catch(e) {
            console.warn('表达式解析错误:', exp, e);
            return '';
        }
    }

    bindEvent(node, event, handlerName) {
        const handler = this.vm.$methods[handlerName];
        if (handler) {
            node.addEventListener(event, handler.bind(this.vm));
        }
    }

    bindProp(node, prop, expression) {
        const update = () => {
            const value = this.evaluateExpression(expression);
            node.setAttribute(prop, value);
        };
        update();
        new Watcher(this.vm, expression, update);
    }

    bindModel(node, expression) {
        const update = () => {
            const value = this.evaluateExpression(expression);
            if (node.value !== undefined) node.value = value;
            else node.textContent = value;
        };
        update();
        new Watcher(this.vm, expression, update);
        node.addEventListener('input', (e) => {
            this.vm.$data[expression] = e.target.value;
        });
    }
}