// js/simple-vue/StyleManager.js
import { nextUid } from './utils.js';

export class StyleManager {
    static styleMap = new Map();

    static process(cssText, componentId) {
        if (!cssText) return '';
        // 转换选择器，添加 [data-v-xxx]
        const transformed = cssText.replace(/([^{]+)(\{[^}]*\})/g, (match, selectors, rules) => {
            const newSelectors = selectors.split(',').map(sel => {
                sel = sel.trim();
                if (sel === ':host') return `[data-v-${componentId}]`;
                if (sel === ':host-context') return `[data-v-${componentId}]`;
                return `${sel}[data-v-${componentId}]`;
            }).join(', ');
            return `${newSelectors} ${rules}`;
        });
        return transformed;
    }

    static inject(componentId, cssText) {
        if (!cssText) return;
        if (this.styleMap.has(componentId)) return;
        const transformed = this.process(cssText, componentId);
        const styleEl = document.createElement('style');
        styleEl.setAttribute('data-v', componentId);
        styleEl.textContent = transformed;
        document.head.appendChild(styleEl);
        this.styleMap.set(componentId, styleEl);
    }
}