/**
 * 命令层：所有会改变文档的操作都必须封装成 Command。
 * 好处：undo/redo、持久化、协同、操作日志，只需要一个入口。
 */

/** 命令管理器：维护 undo / redo 两个栈 */
export class CommandManager {
    constructor() {
        this.undoStack = [];
        this.redoStack = [];
        this.limit = 100;
        this._onChange = null;
    }
    set onChange(fn) { this._onChange = fn; }

    dispatch(cmd, diagram) {
        const next = cmd.execute(diagram);
        this.undoStack.push(cmd);
        if (this.undoStack.length > this.limit) this.undoStack.shift();
        this.redoStack = [];
        if (this._onChange) this._onChange(next);
        return next;
    }

    undo(diagram) {
        const cmd = this.undoStack.pop();
        if (!cmd) return null;
        const prev = cmd.undo(diagram);
        this.redoStack.push(cmd);
        if (this._onChange) this._onChange(prev);
        return prev;
    }

    redo(diagram) {
        const cmd = this.redoStack.pop();
        if (!cmd) return null;
        const next = cmd.execute(diagram);
        this.undoStack.push(cmd);
        if (this._onChange) this._onChange(next);
        return next;
    }

    clear() {
        this.undoStack = [];
        this.redoStack = [];
    }
}

/* ============ 具体命令 ============ */

export class AddNodeCommand {
    constructor(node) {
        this.node = node;
    }
    execute(diagram) {
        diagram.addNode(JSON.parse(JSON.stringify(this.node)));
        return diagram;
    }
    undo(diagram) {
        diagram.removeNode(this.node.id);
        return diagram;
    }
}

export class RemoveNodesCommand {
    constructor(ids) {
        this.ids = ids;
        this.removed = [];
    }
    execute(diagram) {
        this.removed = [];
        for (const id of this.ids) {
            const n = diagram.getNode(id);
            if (n) {
                this.removed.push(JSON.parse(JSON.stringify(n)));
                diagram.removeNode(id);
            }
        }
        return diagram;
    }
    undo(diagram) {
        for (const n of this.removed) {
            diagram.addNode(JSON.parse(JSON.stringify(n)));
        }
        return diagram;
    }
}

export class MoveNodesCommand {
    constructor(moves) {
        // moves = [{ id, dx, dy }, ...]
        this.moves = moves;
    }
    execute(diagram) {
        for (const m of this.moves) {
            const n = diagram.getNode(m.id);
            if (n) { n.x += m.dx; n.y += m.dy; }
        }
        return diagram;
    }
    undo(diagram) {
        for (const m of this.moves) {
            const n = diagram.getNode(m.id);
            if (n) { n.x -= m.dx; n.y -= m.dy; }
        }
        return diagram;
    }
}

export class UpdateNodeCommand {
    constructor(id, patch) {
        this.id = id;
        this.patch = patch;
        this.old = null;
    }
    execute(diagram) {
        const n = diagram.getNode(this.id);
        if (!n) return diagram;
        this.old = JSON.parse(JSON.stringify(n));
        Object.assign(n, this.patch);
        if (this.patch.props) {
            n.props = Object.assign({}, this.old.props, this.patch.props);
        }
        return diagram;
    }
    undo(diagram) {
        const n = diagram.getNode(this.id);
        if (n && this.old) Object.assign(n, JSON.parse(JSON.stringify(this.old)));
        return diagram;
    }
}