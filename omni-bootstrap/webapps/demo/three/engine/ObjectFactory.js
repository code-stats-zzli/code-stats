import * as THREE from 'three';
import { CSS2DObject } from 'three/addons/renderers/CSS2DRenderer.js';

export class ObjectFactory {
    constructor(scene, resourceManager) {
        this.scene = scene;
        this.rm = resourceManager;
    }

    async create(cfg) {
        const obj = this._createObject(cfg);
        if (!obj) return null;

        // 变换
        obj.position.set(...(cfg.position || [0, 0, 0]));
        if (cfg.rotation) {
            if (Array.isArray(cfg.rotation)) {
                obj.rotation.set(...cfg.rotation);
            } else if (typeof cfg.rotation === 'object') {
                if (cfg.rotation.y !== undefined) obj.rotation.y = cfg.rotation.y;
            }
        }
        if (cfg.scale !== undefined) {
            if (Array.isArray(cfg.scale)) obj.scale.set(...cfg.scale);
            else obj.scale.setScalar(cfg.scale);
        }
        if (cfg.visible === false) obj.visible = false;

        obj.userData.id = cfg.id;
        obj.userData.data = cfg.data || {};

        // 标签
        if (cfg.label) {
            const labelObj = this._createLabel(cfg.label);
            if (labelObj) {
                labelObj.name = '__label__';
                obj.userData.labelTemplate = cfg.label.text || '';
                obj.add(labelObj);
            }
        }

        return obj;
    }

    _createObject(cfg) {
        switch (cfg.type) {
            case 'primitive': return this._createPrimitive(cfg);
            case 'model': return this._createModel(cfg);
            case 'group': return new THREE.Group();
            case 'sprite': return this._createSprite(cfg);
            default:
                console.warn('未知对象类型:', cfg.type);
                return new THREE.Group();
        }
    }

    _createPrimitive(cfg) {
        const geo = this._createGeometry(cfg.geometry);
        const mat = this._createMaterial(cfg.material);
        return new THREE.Mesh(geo, mat);
    }

    _createGeometry(g = {}) {
        const type = g.type || 'box';
        switch (type) {
            case 'box':
                return new THREE.BoxGeometry(g.width || 1, g.height || 1, g.depth || 1);
            case 'sphere':
                return new THREE.SphereGeometry(g.radius || 0.5, g.segments || 32, g.segments || 32);
            case 'cylinder':
                return new THREE.CylinderGeometry(g.radiusTop || 0.5, g.radiusBottom || 0.5, g.height || 1, g.segments || 32);
            case 'cone':
                return new THREE.ConeGeometry(g.radius || 0.5, g.height || 1, g.segments || 32);
            case 'torus':
                return new THREE.TorusGeometry(g.radius || 0.5, g.tube || 0.2, 16, g.segments || 32);
            case 'plane':
                return new THREE.PlaneGeometry(g.width || 1, g.height || 1);
            case 'ring':
                return new THREE.RingGeometry(g.innerRadius || 0.3, g.outerRadius || 0.5, g.segments || 32);
            default:
                return new THREE.BoxGeometry(1, 1, 1);
        }
    }

    _createMaterial(m = {}) {
        const type = m.type || 'phong';
        const params = {
            color: m.color || '#4da6ff',
            transparent: m.opacity !== undefined && m.opacity < 1,
            opacity: m.opacity !== undefined ? m.opacity : 1,
            side: m.side === 'double' ? THREE.DoubleSide : THREE.FrontSide,
            wireframe: !!m.wireframe
        };
        if (m.emissive) params.emissive = new THREE.Color(m.emissive);
        if (m.texture) {
            const tex = this.rm.getTexture(m.texture);
            if (tex) params.map = tex;
        }
        switch (type) {
            case 'basic': return new THREE.MeshBasicMaterial(params);
            case 'lambert': return new THREE.MeshLambertMaterial(params);
            case 'standard':
                return new THREE.MeshStandardMaterial({
                    ...params,
                    metalness: m.metalness !== undefined ? m.metalness : 0.5,
                    roughness: m.roughness !== undefined ? m.roughness : 0.5
                });
            case 'phong':
            default:
                return new THREE.MeshPhongMaterial({
                    ...params,
                    shininess: m.shininess !== undefined ? m.shininess : 40
                });
        }
    }

    _createModel(cfg) {
        const model = this.rm.getModel(cfg.ref);
        if (!model) {
            console.warn('模型未加载:', cfg.ref);
            return new THREE.Group();
        }
        const cloned = model.clone(true);
        // 如果配置了材质覆盖
        if (cfg.material) {
            const mat = this._createMaterial(cfg.material);
            cloned.traverse(o => {
                if (o.isMesh) {
                    o.material = mat;
                }
            });
        }
        // 保存动画
        cloned.userData.__animations = model.userData.__animations || [];
        return cloned;
    }

    _createSprite(cfg) {
        const tex = cfg.texture ? this.rm.getTexture(cfg.texture) : null;
        const mat = new THREE.SpriteMaterial({ map: tex, color: cfg.color || '#ffffff' });
        const sprite = new THREE.Sprite(mat);
        sprite.scale.set(...(cfg.scale || [1, 1, 1]));
        return sprite;
    }

    _createLabel(labelCfg) {
        const div = document.createElement('div');
        div.textContent = labelCfg.text || '';
        div.style.cssText = `
            color: ${labelCfg.color || '#e2e8f0'};
            font-size: ${labelCfg.fontSize || '12px'};
            font-weight: 500;
            text-shadow: 0 0 8px rgba(0,0,0,0.9);
            background: ${labelCfg.background || 'rgba(0,0,0,0.6)'};
            padding: 2px 10px;
            border-radius: 12px;
            border: 1px solid rgba(255,255,255,0.15);
            white-space: nowrap;
            pointer-events: none;
        `;
        const label = new CSS2DObject(div);
        label.position.set(...(labelCfg.offset || [0, 1, 0]));
        return label;
    }
}