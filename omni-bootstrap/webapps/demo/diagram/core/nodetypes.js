/**
 * 插件层：节点类型定义。
 * 新增一种图元 = 新增一个对象到 NodeTypes，不改其他任何文件。
 *
 * 每个类型需要：
 *   type          类型标识
 *   label         中文名
 *   icon          工具栏图标
 *   defaultSize   默认尺寸
 *   defaultProps  默认属性
 *   render(node)  返回 SVG 元素（坐标原点在节点的 (x,y)，内部用局部坐标）
 */

const NS = 'http://www.w3.org/2000/svg';

function svg(tag, attrs = {}) {
    const el = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attrs)) {
        if (v !== undefined && v !== null) el.setAttribute(k, v);
    }
    return el;
}

function pointsToPath(points) {
    if (!points || !points.length) return '';
    let d = 'M ' + points[0].x + ' ' + points[0].y;
    for (let i = 1; i < points.length; i++) {
        d += ' L ' + points[i].x + ' ' + points[i].y;
    }
    return d;
}

export const NodeTypes = {
    rect: {
        type: 'rect',
        label: '矩形',
        icon: '▭',
        defaultSize: { w: 120, h: 80 },
        defaultProps: { fill: '#ffffff', stroke: '#1e293b', strokeWidth: 2, rx: 0 },
        render(node) {
            return svg('rect', {
                width: Math.abs(node.w),
                height: Math.abs(node.h),
                rx: node.props.rx || 0,
                fill: node.props.fill || '#ffffff',
                stroke: node.props.stroke || '#1e293b',
                'stroke-width': node.props.strokeWidth || 2
            });
        }
    },

    ellipse: {
        type: 'ellipse',
        label: '椭圆',
        icon: '◯',
        defaultSize: { w: 100, h: 100 },
        defaultProps: { fill: '#ffffff', stroke: '#1e293b', strokeWidth: 2 },
        render(node) {
            return svg('ellipse', {
                cx: Math.abs(node.w) / 2,
                cy: Math.abs(node.h) / 2,
                rx: Math.abs(node.w) / 2,
                ry: Math.abs(node.h) / 2,
                fill: node.props.fill || '#ffffff',
                stroke: node.props.stroke || '#1e293b',
                'stroke-width': node.props.strokeWidth || 2
            });
        }
    },

    line: {
        type: 'line',
        label: '直线',
        icon: '／',
        defaultSize: { w: 100, h: 0 },
        defaultProps: { stroke: '#1e293b', strokeWidth: 2 },
        render(node) {
            return svg('line', {
                x1: 0, y1: 0, x2: node.w, y2: node.h,
                stroke: node.props.stroke || '#1e293b',
                'stroke-width': node.props.strokeWidth || 2
            });
        }
    },

    arrow: {
        type: 'arrow',
        label: '箭头',
        icon: '→',
        defaultSize: { w: 100, h: 0 },
        defaultProps: { stroke: '#1e293b', strokeWidth: 2 },
        render(node) {
            return svg('line', {
                x1: 0, y1: 0, x2: node.w, y2: node.h,
                stroke: node.props.stroke || '#1e293b',
                'stroke-width': node.props.strokeWidth || 2,
                'marker-end': 'url(#dg-arrow)'
            });
        }
    },

    text: {
        type: 'text',
        label: '文本',
        icon: 'T',
        defaultSize: { w: 120, h: 24 },
        defaultProps: { text: '文本', fill: '#1e293b', fontSize: 16, fontWeight: 'normal' },
        render(node) {
            const t = svg('text', {
                x: 4,
                y: (node.props.fontSize || 16) + 2,
                'font-size': node.props.fontSize || 16,
                'font-weight': node.props.fontWeight || 'normal',
                fill: node.props.fill || '#1e293b',
                'font-family': 'Inter, sans-serif'
            });
            t.textContent = node.props.text || '';
            return t;
        }
    },

    pen: {
        type: 'pen',
        label: '自由笔',
        icon: '✎',
        defaultSize: { w: 100, h: 100 },
        defaultProps: { stroke: '#1e293b', strokeWidth: 2, points: [] },
        render(node) {
            return svg('path', {
                d: pointsToPath(node.props.points || []),
                fill: 'none',
                stroke: node.props.stroke || '#1e293b',
                'stroke-width': node.props.strokeWidth || 2,
                'stroke-linecap': 'round',
                'stroke-linejoin': 'round'
            });
        }
    }
};

export const NodeTypeList = Object.values(NodeTypes);