// components/TodoList.js
export const TodoList = {
    template: `
        <div class="todo-list">
            <h3>待办事项</h3>
            <input type="text" v-model="newTodo" placeholder="添加新任务" />
            <button @click="addTodo">添加</button>
            <ul>
                <li v-for="(todo, idx) in todos" :key="idx">
                    {{ todo }}
                    <button @click="removeTodo(idx)">删除</button>
                </li>
            </ul>
        </div>
    `,
    data() {
        return {
            newTodo: '',
            todos: ['学习 SimpleVue', '写示例代码']
        };
    },
    methods: {
        addTodo() {
            if (this.newTodo.trim()) {
                this.todos.push(this.newTodo);
                this.newTodo = '';
            }
        },
        removeTodo(idx) {
            this.todos.splice(idx, 1);
        }
    },
    style: `
        .todo-list { padding: 16px; background: #fff; border-radius: 16px; }
        ul { list-style: none; padding: 0; }
        li { margin: 8px 0; display: flex; justify-content: space-between; align-items: center; }
    `
};