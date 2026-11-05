/**
 * SysCenter —— 系统管理模块注册中心
 *
 * 设计目标：
 *  - 新增系统功能只需新增一个 modules/xxx/xxx.js，内部调用 SysCenter.register
 *  - 菜单、标题、内容容器自动渲染，无需修改本文件
 *
 * 模块契约：
 *   {
 *     id:   'log',              // 唯一标识
 *     name: '系统日志',          // 菜单显示名
 *     icon: '📋',               // 菜单图标
 *     desc: '实时查看日志...',   // 内容区副标题
 *     mount(container) { ... }  // 渲染逻辑；container 已清空，可直接 appendChild
 *   }
 */
(function () {
    'use strict';

    const SysCenter = {
        modules: [],
        currentId: null,
        _inited: false,

        /** 注册模块（幂等） */
        register(mod) {
            if (!mod || !mod.id) {
                console.warn('[SysCenter] 无效模块，已忽略', mod);
                return;
            }
            if (this.modules.find(m => m.id === mod.id)) {
                return;
            }
            this.modules.push(mod);
            if (this._inited) {
                this._renderMenu();
                if (!this.currentId) {
                    this.switchTo(mod.id);
                }
            }
        },

        /** 启动：渲染菜单 + 切换到第一个模块 */
        start() {
            this._inited = true;
            this._renderMenu();
            if (this.modules.length > 0) {
                this.switchTo(this.modules[0].id);
            } else {
                this._showEmpty();
            }
        },

        _showEmpty() {
            const titleEl = document.getElementById('sysContentTitle');
            const descEl = document.getElementById('sysContentDesc');
            const bodyEl = document.getElementById('sysContentBody');
            if (titleEl) titleEl.textContent = '系统管理';
            if (descEl) descEl.textContent = '暂无可用模块';
            if (bodyEl) bodyEl.innerHTML =
                '<div class="sys-error">暂无系统功能模块。请在 webapps/demo/sys/modules/ 下添加模块并注册。</div>';
        },

        _renderMenu() {
            const el = document.getElementById('sysMenu');
            if (!el) return;
            el.innerHTML = this.modules.map(m => `
                <div class="sys-menu-item ${m.id === this.currentId ? 'active' : ''}"
                     data-id="${escapeAttr(m.id)}"
                     title="${escapeAttr(m.name || m.id)}">
                    <span class="sys-menu-icon">${m.icon || '📦'}</span>
                    <span class="sys-menu-name">${escapeHtml(m.name || m.id)}</span>
                </div>
            `).join('');
            el.querySelectorAll('.sys-menu-item').forEach(item => {
                item.addEventListener('click', () => this.switchTo(item.dataset.id));
            });
        },

        /** 切换到指定模块 */
        switchTo(id) {
            const mod = this.modules.find(m => m.id === id);
            if (!mod) return;
            this.currentId = id;

            document.querySelectorAll('.sys-menu-item').forEach(el => {
                el.classList.toggle('active', el.dataset.id === id);
            });

            const titleEl = document.getElementById('sysContentTitle');
            const descEl = document.getElementById('sysContentDesc');
            if (titleEl) titleEl.textContent = mod.name || id;
            if (descEl) descEl.textContent = mod.desc || '';

            const bodyEl = document.getElementById('sysContentBody');
            if (!bodyEl) return;
            bodyEl.innerHTML = '';
            const container = document.createElement('div');
            container.className = 'sys-module-container';
            bodyEl.appendChild(container);

            try {
                if (typeof mod.mount === 'function') {
                    mod.mount(container);
                }
            } catch (e) {
                console.error('[SysCenter] 模块挂载失败', id, e);
                container.innerHTML =
                    `<div class="sys-error">❌ 模块挂载失败：${escapeHtml(e.message)}</div>`;
            }
        }
    };

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"']/g, m => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[m]));
    }
    function escapeAttr(s) { return escapeHtml(s); }

    window.SysCenter = SysCenter;

    // 延迟到同步脚本全部执行完后再启动（保证 log.js 等模块先完成 register）
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => SysCenter.start());
    } else {
        setTimeout(() => SysCenter.start(), 0);
    }
})();