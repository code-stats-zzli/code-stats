// components/Counter.js
export const Counter = {
    template: `
        <div class="counter">
            <h3>计数器示例</h3>
            <p>当前计数：{{ count }}</p>
            <button @click="increment">+1</button>
            <button @click="decrement">-1</button>
        </div>
    `,
    data() {
        return { count: 0 };
    },
    methods: {
        increment() {
            this.count++;
        },
        decrement() {
            this.count--;
        }
    },
    style: `
        .counter {
            padding: 16px;
            border-radius: 16px;
            background: #f0f4fa;
            text-align: center;
        }
        button {
            margin: 8px;
            padding: 6px 16px;
        }
    `
};