import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { OBJLoader } from 'three/addons/loaders/OBJLoader.js';
import { FBXLoader } from 'three/addons/loaders/FBXLoader.js';

export class ResourceManager {
    constructor() {
        this.cache = new Map();  // key -> loaded resource
    }

    async preload(resources = {}) {
        const tasks = [];
        for (const [key, url] of Object.entries(resources.models || {})) {
            tasks.push(this._loadModel(key, url));
        }
        for (const [key, url] of Object.entries(resources.textures || {})) {
            tasks.push(this._loadTexture(key, url));
        }
        await Promise.all(tasks);
    }

    async _loadModel(key, url) {
        if (this.cache.has(`model:${key}`)) return this.cache.get(`model:${key}`);
        const ext = url.split('.').pop().toLowerCase();
        let loader;
        if (ext === 'glb' || ext === 'gltf') loader = new GLTFLoader();
        else if (ext === 'obj') loader = new OBJLoader();
        else if (ext === 'fbx') loader = new FBXLoader();
        else {
            console.warn('不支持的模型格式:', url);
            return null;
        }
        try {
            const result = await loader.loadAsync(url);
            const model = result.scene || result;  // GLTF 是 {scene, animations}
            model.userData.__animations = result.animations || [];
            this.cache.set(`model:${key}`, model);
            return model;
        } catch (err) {
            console.error('加载模型失败:', url, err);
            return null;
        }
    }

    async _loadTexture(key, url) {
        if (this.cache.has(`tex:${key}`)) return this.cache.get(`tex:${key}`);
        try {
            const tex = await new THREE.TextureLoader().loadAsync(url);
            tex.colorSpace = THREE.SRGBColorSpace;
            this.cache.set(`tex:${key}`, tex);
            return tex;
        } catch (err) {
            console.error('加载纹理失败:', url, err);
            return null;
        }
    }

    getModel(key) { return this.cache.get(`model:${key}`); }
    getTexture(key) { return this.cache.get(`tex:${key}`); }

    dispose() {
        for (const [key, res] of this.cache) {
            if (res && res.dispose) res.dispose();
        }
        this.cache.clear();
    }
}