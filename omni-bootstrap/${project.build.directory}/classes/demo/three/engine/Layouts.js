export function applyLayout(layout, objects, objectMap) {
    const { type } = layout;
    if (type === 'radial') return applyRadial(layout, objects, objectMap);
    if (type === 'grid') return applyGrid(layout, objects, objectMap);
    if (type === 'layered') return applyLayered(layout, objects, objectMap);
    console.warn('未知布局类型:', type);
}

function applyRadial(layout, objects, objectMap) {
    const radius = layout.radius || 5;
    const y = layout.y || 0;
    const startAngle = layout.startAngle || 0;
    const targets = objects.filter(o => (layout.targets ? layout.targets.includes(o.id) : o.type === 'primitive'));
    targets.forEach((cfg, i) => {
        const angle = startAngle + (i / targets.length) * Math.PI * 2;
        const obj = objectMap.get(cfg.id);
        if (!obj) return;
        obj.position.set(radius * Math.cos(angle), y, radius * Math.sin(angle));
    });
}

function applyGrid(layout, objects, objectMap) {
    const cols = layout.cols || 4;
    const spacingX = layout.spacingX || 3;
    const spacingY = layout.spacingY || 3;
    const targets = objects.filter(o => (layout.targets ? layout.targets.includes(o.id) : true));
    const startX = -((cols - 1) * spacingX) / 2;
    targets.forEach((cfg, i) => {
        const col = i % cols;
        const row = Math.floor(i / cols);
        const obj = objectMap.get(cfg.id);
        if (!obj) return;
        obj.position.set(startX + col * spacingX, -row * spacingY, 0);
    });
}

function applyLayered(layout, objects, objectMap) {
    // 按 layerKey（默认 type 或 userData.layer）分层
    const layerKey = layout.layerKey || 'layer';
    const spacingX = layout.spacingX || 3.5;
    const spacingY = layout.spacingY || 5;
    const layerOrder = layout.layers || [];
    const groups = {};
    objects.forEach(o => {
        const layer = o[layerKey] || o.userData?.layer || 'default';
        if (!groups[layer]) groups[layer] = [];
        groups[layer].push(o);
    });
    const order = layerOrder.length ? layerOrder : Object.keys(groups);
    order.forEach((layerName, layerIdx) => {
        const list = groups[layerName] || [];
        const totalWidth = (list.length - 1) * spacingX;
        const startX = -totalWidth / 2;
        const y = layout.startY !== undefined ? layout.startY - layerIdx * spacingY : -layerIdx * spacingY;
        list.forEach((cfg, i) => {
            const obj = objectMap.get(cfg.id);
            if (!obj) return;
            obj.position.set(startX + i * spacingX, y, 0);
        });
    });
}