import * as THREE from 'three';

export class AnimationManager {
    constructor() {
        this.entries = [];       // 简单的内置动画
        this.mixers = [];        // GLTF 动画
    }

    register(animations, objectMap) {
        animations.forEach(a => {
            const obj = objectMap.get(a.target);
            if (!obj) return;
            if (a.type === 'rotate') {
                this.entries.push({
                    obj,
                    type: 'rotate',
                    axis: a.axis || 'y',
                    speed: a.speed || 0.01
                });
            } else if (a.type === 'float') {
                const baseY = obj.position.y;
                this.entries.push({
                    obj,
                    type: 'float',
                    baseY,
                    amplitude: a.amplitude || 0.3,
                    speed: a.speed || 1.5,
                    phase: Math.random() * Math.PI * 2
                });
            } else if (a.type === 'gltf') {
                // GLTF 内置动画
                const clips = obj.userData.__animations || [];
                const clip = clips.find(c => c.name === a.clip) || clips[0];
                if (clip) {
                    const mixer = new THREE.AnimationMixer(obj);
                    const action = mixer.clipAction(clip);
                    action.setLoop(a.loop ? THREE.LoopRepeat : THREE.LoopOnce, Infinity);
                    action.play();
                    this.mixers.push(mixer);
                }
            }
        });
    }

    update(delta) {
        for (const e of this.entries) {
            if (e.type === 'rotate') {
                e.obj.rotation[e.axis] += e.speed * delta * 60;
            } else if (e.type === 'float') {
                e.phase += delta * e.speed;
                e.obj.position.y = e.baseY + Math.sin(e.phase) * e.amplitude;
            }
        }
        for (const m of this.mixers) m.update(delta);
    }

    clear() {
        this.entries = [];
        this.mixers = [];
    }
}