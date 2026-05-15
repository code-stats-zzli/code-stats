class SimpleVue {
    constructor(options) {
        this.$options = options;
        this.$data = observe(options.data());
        this.$methods = options.methods || {};
        // 将 data 中的属性代理到实例本身，方便模板中使用 this.xxx
        for (let key in this.$data) {
            Object.defineProperty(this, key, {
                enumerable: true,
                configurable: true,
                get: () => this.$data[key],
                set: (val) => { this.$data[key] = val; }
            });
        }
        // 将 methods 中的方法也代理到实例，使得事件绑定可以直接 this.methodName
        for (let key in this.$methods) {
            this[key] = this.$methods[key].bind(this);
        }
        this.$el = document.querySelector(options.el);
        // 编译模板
        new Compiler(this);
    }
}