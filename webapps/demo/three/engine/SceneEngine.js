import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { CSS2DRenderer, CSS2DObject } from 'three/addons/renderers/CSS2DRenderer.js';
import { ResourceManager } from './ResourceManager.js';
import { ObjectFactory } from './ObjectFactory.js';
import { InteractionManager } from './InteractionManager.js';
import { AnimationManager } from './AnimationManager.js';
import { applyLayout } from './Layouts.js';

export class SceneEngine {
    constructor(container, options = {}) {
        this.container = typeof container === 'string'
            ? document.querySelector(container)
            : container;
        this.options = options;
        this.config = null;
        this.scene = null;
        this.camera = null;
        this.renderer = null;
        this.labelRenderer = null;
        this.controls = null;
        this.objectMap = new Map();
        this.resourceManager = new ResourceManager();
        this.objectFactory = null;
        this.interactionManager = null;
        this.animationManager = new AnimationManager();
        this.clock = new THREE.Clock();
        this._raf = null;
    }

    async load(configOrUrl) {
        this.config = typeof configOrUrl === 'string'
            ? await fetch(configOrUrl).then(r => r.json())
            : configOrUrl;
        await this._build();
    }

    async _build() {
        const cfg = this.config;
        const W = this.container.clientWidth;
        const H = this.container.clientHeight;

        // 1. 场景
        this.scene = new THREE.Scene();
        this.scene.background = new THREE.Color(cfg.scene?.background || '#0a0f1a');
        if (cfg.scene?.fog) {
            const f = cfg.scene.fog;
            this.scene.fog = new THREE.Fog(f.color, f.near, f.far);
        }

        // 2. 相机
        this.camera = this._buildCamera(cfg.camera, W, H);

        // 3. 渲染器
        this.renderer = new THREE.WebGLRenderer({ antialias: true });
        this.renderer.setSize(W, H);
        this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
        this.renderer.shadowMap.enabled = !!cfg.render?.shadow;
        this.container.appendChild(this.renderer.domElement);

        // 4. 标签渲染器
        this.labelRenderer = new CSS2DRenderer();
        this.labelRenderer.setSize(W, H);
        this.labelRenderer.domElement.style.position = 'absolute';
        this.labelRenderer.domElement.style.top = '0';
        this.labelRenderer.domElement.style.left = '0';
        this.labelRenderer.domElement.style.pointerEvents = 'none';
        this.container.appendChild(this.labelRenderer.domElement);

        // 5. 控制器
        if (cfg.camera?.controls !== false) {
            this.controls = new OrbitControls(this.camera, this.renderer.domElement);
            const c = cfg.camera.controls || {};
            this.controls.enableDamping = c.damping !== undefined ? true : true;
            this.controls.dampingFactor = c.damping || 0.08;
            this.controls.autoRotate = !!c.autoRotate;
            this.controls.autoRotateSpeed = c.autoRotateSpeed || 1.5;
            this.controls.target.set(...(cfg.camera.lookAt || [0,0,0]));
            this.controls.update();
        }

        // 6. 灯光
        (cfg.lights || []).forEach(l => {
            const light = this._buildLight(l);
            if (light) this.scene.add(light);
        });

        // 7. 辅助工具
        if (cfg.helpers?.grid) {
            const g = cfg.helpers.grid;
            const grid = new THREE.GridHelper(g.size || 20, g.divisions || 20, g.color || 0x4da6ff, g.color2 || 0x2a4a7a);
            grid.position.y = g.y || -2.5;
            this.scene.add(grid);
        }
        if (cfg.helpers?.axes) {
            this.scene.add(new THREE.AxesHelper(cfg.helpers.axes.size || 5));
        }

        // 8. 资源
        this.objectFactory = new ObjectFactory(this.scene, this.resourceManager);
        await this.resourceManager.preload(cfg.resources || {});

        // 9. 对象
        for (const objCfg of (cfg.objects || [])) {
            const obj = await this.objectFactory.create(objCfg);
            if (!obj) continue;
            this.scene.add(obj);
            this.objectMap.set(objCfg.id, obj);
        }

        // 10. 布局（覆盖对象的 position）
        if (cfg.layout) {
            applyLayout(cfg.layout, cfg.objects, this.objectMap);
        }

        // 11. 交互
        if (cfg.interactions) {
            this.interactionManager = new InteractionManager(
                this.container, this.camera, this.renderer.domElement, this.objectMap
            );
            this.interactionManager.bind(cfg.interactions);
        }

        // 12. 动画
        if (cfg.animations) {
            this.animationManager.register(cfg.animations, this.objectMap);
        }

        // 13. 渲染循环
        this._animate();
        window.addEventListener('resize', () => this.resize());
        return this;
    }

    _buildCamera(cfg, W, H) {
        cfg = cfg || {};
        const aspect = W / H;
        let cam;
        if (cfg.type === 'orthographic') {
            const s = cfg.frustumSize || 20;
            cam = new THREE.OrthographicCamera(
                -s * aspect / 2, s * aspect / 2, s / 2, -s / 2, cfg.near || 0.1, cfg.far || 1000
            );
        } else {
            cam = new THREE.PerspectiveCamera(cfg.fov || 45, aspect, cfg.near || 0.1, cfg.far || 1000);
        }
        cam.position.set(...(cfg.position || [15, 10, 20]));
        cam.lookAt(...(cfg.lookAt || [0, 0, 0]));
        return cam;
    }

    _buildLight(cfg) {
        const color = cfg.color || '#ffffff';
        const intensity = cfg.intensity !== undefined ? cfg.intensity : 1;
        switch (cfg.type) {
            case 'ambient': return new THREE.AmbientLight(color, intensity);
            case 'directional': {
                const l = new THREE.DirectionalLight(color, intensity);
                l.position.set(...(cfg.position || [5, 10, 7]));
                l.castShadow = !!cfg.castShadow;
                return l;
            }
            case 'point': {
                const l = new THREE.PointLight(color, intensity, cfg.distance || 0);
                l.position.set(...(cfg.position || [0, 5, 0]));
                return l;
            }
            case 'spot': {
                const l = new THREE.SpotLight(color, intensity);
                l.position.set(...(cfg.position || [0, 10, 0]));
                return l;
            }
            case 'hemisphere': {
                return new THREE.HemisphereLight(cfg.sky || '#ffffff', cfg.ground || '#000000', intensity);
            }
            default: return null;
        }
    }

    _animate() {
        this._raf = requestAnimationFrame(() => this._animate());
        const delta = this.clock.getDelta();
        if (this.controls) this.controls.update();
        this.animationManager.update(delta);
        this.renderer.render(this.scene, this.camera);
        this.labelRenderer.render(this.scene, this.camera);
    }

    resize() {
        const W = this.container.clientWidth;
        const H = this.container.clientHeight;
        if (this.camera.isPerspectiveCamera) {
            this.camera.aspect = W / H;
        } else {
            // 正交相机重建
            const s = this.config.camera.frustumSize || 20;
            const aspect = W / H;
            this.camera.left = -s * aspect / 2;
            this.camera.right = s * aspect / 2;
            this.camera.top = s / 2;
            this.camera.bottom = -s / 2;
        }
        this.camera.updateProjectionMatrix();
        this.renderer.setSize(W, H);
        this.labelRenderer.setSize(W, H);
    }

    /**
     * 运行时更新对象数据（绑定到 userData.data）
     */
    updateData(dataMap) {
        for (const [id, data] of Object.entries(dataMap)) {
            const obj = this.objectMap.get(id);
            if (!obj) continue;
            obj.userData.data = { ...(obj.userData.data || {}), ...data };
            // 位置更新
            if (data.position) {
                obj.position.set(...data.position);
            }
            // 标签更新
            const labelObj = obj.getObjectByName('__label__');
            if (labelObj && labelObj.element) {
                const text = obj.userData.labelTemplate || '';
                labelObj.element.textContent = this._interpolate(text, obj.userData.data);
            }
        }
    }

    _interpolate(tpl, data) {
        return tpl.replace(/\{(\w+)\}/g, (_, k) => data?.[k] ?? '');
    }

    destroy() {
        if (this._raf) cancelAnimationFrame(this._raf);
        this.animationManager.clear();
        this.resourceManager.dispose();
        if (this.renderer) {
            this.renderer.dispose();
            this.container.removeChild(this.renderer.domElement);
        }
        if (this.labelRenderer) {
            this.container.removeChild(this.labelRenderer.domElement);
        }
    }
}