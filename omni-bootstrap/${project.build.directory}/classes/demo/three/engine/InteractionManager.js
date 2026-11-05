import * as THREE from 'three';

export class InteractionManager {
    constructor(container, camera, domElement, objectMap) {
        this.container = container;
        this.camera = camera;
        this.domElement = domElement;
        this.objectMap = objectMap;
        this.raycaster = new THREE.Raycaster();
        this.mouse = new THREE.Vector2();
        this.hovered = null;
        this.handlers = {};
        this._onMouseMove = this._onMouseMove.bind(this);
        this._onClick = this._onClick.bind(this);
    }

    bind(interactions) {
        this.handlers = interactions || {};
        this.domElement.addEventListener('mousemove', this._onMouseMove);
        this.domElement.addEventListener('click', this._onClick);
    }

    _onMouseMove(e) {
        if (!this.handlers.hover) return;
        const hit = this._pick(e);
        if (hit !== this.hovered) {
            if (this.hovered) this._setEmissive(this.hovered, null);
            this.hovered = hit;
            if (hit) this._setEmissive(hit, '#ff6b6b');
            this._updateCursor(!!hit);
        }
    }

    _onClick(e) {
        const hit = this._pick(e);
        if (!hit) return;
        const cfg = this.handlers.click;
        if (!cfg) return;
        if (cfg.type === 'panel') {
            const data = hit.userData.data || {};
            const content = (cfg.content || '').replace(/\{(\w+)\}/g, (_, k) => data[k] ?? '');
            this._showPanel(cfg.title || hit.userData.id, content);
        } else if (cfg.type === 'emit') {
            window.dispatchEvent(new CustomEvent(cfg.event, { detail: hit.userData }));
        }
    }

    _pick(e) {
        const rect = this.domElement.getBoundingClientRect();
        this.mouse.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
        this.mouse.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
        this.raycaster.setFromCamera(this.mouse, this.camera);
        const targets = [];
        this.objectMap.forEach(obj => {
            obj.traverse(o => { if (o.isMesh) targets.push(o); });
        });
        const intersects = this.raycaster.intersectObjects(targets, false);
        if (intersects.length === 0) return null;
        // 回溯到顶层对象
        let node = intersects[0].object;
        while (node.parent && !node.userData.id) node = node.parent;
        return node;
    }

    _setEmissive(obj, color) {
        obj.traverse(o => {
            if (o.isMesh && o.material) {
                if (!o.material.__originalEmissive) {
                    o.material.__originalEmissive = o.material.emissive ? o.material.emissive.getHex() : 0x000000;
                }
                if (color) {
                    o.material.emissive = new THREE.Color(color);
                } else if (o.material.emissive) {
                    o.material.emissive.setHex(o.material.__originalEmissive);
                }
            }
        });
    }

    _updateCursor(isHover) {
        this.domElement.style.cursor = isHover ? 'pointer' : '';
    }

    _showPanel(title, content) {
        let panel = document.getElementById('__scene_panel__');
        if (!panel) {
            panel = document.createElement('div');
            panel.id = '__scene_panel__';
            panel.style.cssText = `
                position: absolute;
                right: 20px;
                top: 20px;
                min-width: 240px;
                max-width: 320px;
                background: rgba(10,15,26,0.9);
                backdrop-filter: blur(12px);
                border: 1px solid rgba(0,102,255,0.5);
                border-radius: 16px;
                padding: 16px;
                color: #e2e8f0;
                font-size: 13px;
                z-index: 100;
                box-shadow: 0 8px 24px rgba(0,0,0,0.5);
            `;
            this.container.appendChild(panel);
        }
        panel.innerHTML = `
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
                <strong style="color:#4da6ff;">${title}</strong>
                <span style="cursor:pointer;color:#94a3b8;" onclick="this.closest('#__scene_panel__').remove()">✖</span>
            </div>
            <div>${content}</div>
        `;
    }
}