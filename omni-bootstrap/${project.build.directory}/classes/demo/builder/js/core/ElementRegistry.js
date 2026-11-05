/**
 * 元素注册中心（单例）
 */
export class ElementRegistry {
    static _elements = new Map();

    /** 注册一个元素类 */
    static register(ElementClass) {
        if (!ElementClass || !ElementClass.name) {
            throw new Error('无效的元素类');
        }
        this._elements.set(ElementClass.name, ElementClass);
    }

    /** 按类名获取 */
    static get(name) {
        return this._elements.get(name);
    }

    /** 获取所有 */
    static getAll() {
        return Array.from(this._elements.values());
    }

    /** 按分类获取 */
    static getByCategory(category) {
        return this.getAll().filter(c => (c.meta.category || 'primitive') === category);
    }
}