<template>
  <div class="auth-layout">
    <div class="auth-bg" aria-hidden="true">
      <div class="bg-base" />
      <div class="bg-orb bg-orb-1" />
      <div class="bg-orb bg-orb-2" />
      <div class="bg-orb bg-orb-3" />
      <div class="bg-mesh" />
      <div class="bg-grid" />

      <!-- 四角装饰框 -->
      <svg class="deco-corner deco-tl" viewBox="0 0 120 120" fill="none">
        <path d="M8 80 L8 8 L80 8" stroke="url(#gold-line)" stroke-width="1.5" stroke-linecap="round" />
        <circle cx="8" cy="8" r="3" fill="#ffd700" opacity="0.8" />
        <circle cx="80" cy="8" r="2" fill="#64b5ff" opacity="0.6" />
      </svg>
      <svg class="deco-corner deco-tr" viewBox="0 0 120 120" fill="none">
        <path d="M112 80 L112 8 L40 8" stroke="url(#gold-line)" stroke-width="1.5" stroke-linecap="round" />
        <circle cx="112" cy="8" r="3" fill="#ffd700" opacity="0.8" />
      </svg>
      <svg class="deco-corner deco-bl" viewBox="0 0 120 120" fill="none">
        <path d="M8 40 L8 112 L80 112" stroke="url(#blue-line)" stroke-width="1.5" stroke-linecap="round" />
        <circle cx="8" cy="112" r="3" fill="#64b5ff" opacity="0.8" />
      </svg>
      <svg class="deco-corner deco-br" viewBox="0 0 120 120" fill="none">
        <path d="M112 40 L112 112 L40 112" stroke="url(#blue-line)" stroke-width="1.5" stroke-linecap="round" />
        <circle cx="112" cy="112" r="3" fill="#64b5ff" opacity="0.8" />
      </svg>

      <!-- 左侧：变电站轮廓装饰 -->
      <svg class="deco-tower" viewBox="0 0 280 400" fill="none">
        <defs>
          <linearGradient id="tower-stroke" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#64b5ff" stop-opacity="0.5" />
            <stop offset="100%" stop-color="#1a5fb4" stop-opacity="0.15" />
          </linearGradient>
        </defs>
        <!-- 输电塔 -->
        <path d="M140 40 L100 160 L110 160 L90 280 L120 280 L140 200 L160 280 L190 280 L170 160 L180 160 Z" stroke="url(#tower-stroke)" stroke-width="1.2" fill="rgba(26,95,180,0.06)" />
        <line x1="60" y1="120" x2="220" y2="120" stroke="rgba(100,181,255,0.25)" stroke-width="1" />
        <line x1="50" y1="200" x2="230" y2="200" stroke="rgba(100,181,255,0.2)" stroke-width="1" />
        <line x1="70" y1="280" x2="210" y2="280" stroke="rgba(100,181,255,0.15)" stroke-width="1" />
        <!-- 电力线 -->
        <path d="M0 100 Q140 90 280 110" stroke="rgba(255,215,0,0.2)" stroke-width="1" fill="none" />
        <path d="M0 180 Q140 175 280 190" stroke="rgba(255,215,0,0.15)" stroke-width="1" fill="none" />
        <!-- 节点 -->
        <circle cx="60" cy="100" r="4" fill="#ffd700" opacity="0.4" class="pulse-node" />
        <circle cx="220" cy="110" r="4" fill="#64b5ff" opacity="0.4" class="pulse-node" style="animation-delay: 1s" />
        <circle cx="140" cy="40" r="5" fill="#ffd700" opacity="0.6" class="pulse-node" style="animation-delay: 0.5s" />
      </svg>

      <!-- 右侧：巡检路径网络 -->
      <svg class="deco-network" viewBox="0 0 320 320" fill="none">
        <circle cx="160" cy="160" r="120" stroke="rgba(100,181,255,0.12)" stroke-width="1" stroke-dasharray="6 8" class="spin-slow" style="transform-origin: 160px 160px" />
        <circle cx="160" cy="160" r="80" stroke="rgba(100,181,255,0.18)" stroke-width="1" />
        <circle cx="160" cy="160" r="40" stroke="rgba(255,215,0,0.2)" stroke-width="1" stroke-dasharray="4 6" />
        <!-- 路径节点 -->
        <line x1="160" y1="160" x2="80" y2="80" stroke="rgba(100,181,255,0.3)" stroke-width="1" />
        <line x1="160" y1="160" x2="250" y2="70" stroke="rgba(100,181,255,0.3)" stroke-width="1" />
        <line x1="160" y1="160" x2="270" y2="200" stroke="rgba(100,181,255,0.25)" stroke-width="1" />
        <line x1="160" y1="160" x2="90" y2="240" stroke="rgba(100,181,255,0.25)" stroke-width="1" />
        <line x1="80" y1="80" x2="250" y2="70" stroke="rgba(255,215,0,0.15)" stroke-width="1" stroke-dasharray="4 4" />
        <circle cx="160" cy="160" r="6" fill="#1a5fb4" opacity="0.5" class="pulse-node" />
        <circle cx="80" cy="80" r="5" fill="#64b5ff" opacity="0.5" class="pulse-node" style="animation-delay: 0.3s" />
        <circle cx="250" cy="70" r="5" fill="#ffd700" opacity="0.5" class="pulse-node" style="animation-delay: 0.6s" />
        <circle cx="270" cy="200" r="4" fill="#64b5ff" opacity="0.4" class="pulse-node" style="animation-delay: 0.9s" />
        <circle cx="90" cy="240" r="4" fill="#64b5ff" opacity="0.4" class="pulse-node" style="animation-delay: 1.2s" />
        <!-- 机器人图标简化 -->
        <rect x="148" y="148" width="24" height="24" rx="6" stroke="rgba(255,215,0,0.5)" stroke-width="1.5" fill="rgba(255,215,0,0.08)" />
        <circle cx="154" cy="156" r="2" fill="#ffd700" opacity="0.8" />
        <circle cx="166" cy="156" r="2" fill="#ffd700" opacity="0.8" />
      </svg>

      <!-- 扫描线 -->
      <div class="scan-line" />

      <!-- 漂浮粒子 -->
      <div class="particles">
        <span v-for="i in 18" :key="i" class="particle" :style="particleStyle(i)" />
      </div>

      <!-- 底部科技条纹 -->
      <div class="deco-strip">
        <span v-for="i in 12" :key="i" class="strip-bar" :style="{ animationDelay: `${i * 0.15}s` }" />
      </div>

      <!-- 渐变定义（供角标引用） -->
      <svg width="0" height="0" style="position:absolute">
        <defs>
          <linearGradient id="gold-line" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#ffd700" stop-opacity="0.8" />
            <stop offset="100%" stop-color="#ffd700" stop-opacity="0.2" />
          </linearGradient>
          <linearGradient id="blue-line" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#64b5ff" stop-opacity="0.8" />
            <stop offset="100%" stop-color="#1a5fb4" stop-opacity="0.2" />
          </linearGradient>
        </defs>
      </svg>
    </div>

    <div class="auth-center">
      <div class="auth-header">
        <div class="logo-wrap">
          <div class="logo-ring" />
          <el-icon :size="36" color="#ffd700"><Lightning /></el-icon>
        </div>
        <h1>电力智能巡检平台</h1>
        <p class="auth-slogan">LocateAnything · ROS 建图 · 智能巡检</p>
        <div class="auth-tags">
          <span class="tag-pill">视觉检测</span>
          <span class="tag-pill">三维感知</span>
          <span class="tag-pill">自主巡检</span>
        </div>
      </div>
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
function particleStyle(i: number) {
  const left = ((i * 37 + 11) % 100)
  const top = ((i * 53 + 7) % 100)
  const size = 2 + (i % 3)
  const delay = (i * 0.7) % 5
  const duration = 4 + (i % 4)
  return {
    left: `${left}%`,
    top: `${top}%`,
    width: `${size}px`,
    height: `${size}px`,
    animationDelay: `${delay}s`,
    animationDuration: `${duration}s`,
  }
}
</script>

<style scoped>
.auth-layout {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  overflow: hidden;
  box-sizing: border-box;
}

.auth-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.bg-base {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #061018 0%, #0c2240 40%, #143560 70%, #0a1a30 100%);
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
  animation: orb-float 18s ease-in-out infinite;
}

.bg-orb-1 {
  width: 520px;
  height: 520px;
  top: -120px;
  right: -80px;
  background: radial-gradient(circle, rgba(26, 95, 180, 0.85) 0%, transparent 70%);
}

.bg-orb-2 {
  width: 480px;
  height: 480px;
  bottom: -100px;
  left: -100px;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.7) 0%, transparent 70%);
  animation-delay: -6s;
}

.bg-orb-3 {
  width: 300px;
  height: 300px;
  top: 40%;
  left: 50%;
  background: radial-gradient(circle, rgba(255, 215, 0, 0.3) 0%, transparent 70%);
  animation-delay: -12s;
}

.bg-mesh {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 60% at 15% 85%, rgba(26, 95, 180, 0.2) 0%, transparent 55%),
    radial-gradient(ellipse 60% 50% at 90% 10%, rgba(100, 180, 255, 0.18) 0%, transparent 50%);
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(100, 181, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(100, 181, 255, 0.04) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 95% 85% at 50% 50%, black 10%, transparent 75%);
}

/* ---- 装饰元素 ---- */
.deco-corner {
  position: absolute;
  width: 100px;
  height: 100px;
  opacity: 0.7;
}

.deco-tl { top: 24px; left: 24px; }
.deco-tr { top: 24px; right: 24px; }
.deco-bl { bottom: 24px; left: 24px; }
.deco-br { bottom: 24px; right: 24px; }

.deco-tower {
  position: absolute;
  left: 4%;
  bottom: 8%;
  width: 220px;
  height: 320px;
  opacity: 0.85;
  animation: tower-glow 6s ease-in-out infinite;
}

.deco-network {
  position: absolute;
  right: 5%;
  top: 12%;
  width: 260px;
  height: 260px;
  opacity: 0.75;
}

.spin-slow {
  animation: spin 30s linear infinite;
  transform-origin: center;
}

.scan-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(100, 181, 255, 0.5), rgba(255, 215, 0, 0.4), transparent);
  box-shadow: 0 0 20px rgba(100, 181, 255, 0.4);
  animation: scan 8s ease-in-out infinite;
  opacity: 0.6;
}

.particles {
  position: absolute;
  inset: 0;
}

.particle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
  box-shadow: 0 0 6px rgba(100, 181, 255, 0.6);
  animation: particle-float 5s ease-in-out infinite;
}

.deco-strip {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  display: flex;
  gap: 4px;
  padding: 0 10%;
  opacity: 0.5;
}

.strip-bar {
  flex: 1;
  height: 100%;
  background: linear-gradient(90deg, transparent, #64b5ff, transparent);
  animation: strip-pulse 3s ease-in-out infinite;
}

.pulse-node {
  animation: node-pulse 3s ease-in-out infinite;
}

@keyframes orb-float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(20px, -16px) scale(1.04); }
  66% { transform: translate(-14px, 10px) scale(0.97); }
}

@keyframes scan {
  0% { top: -2px; opacity: 0; }
  10% { opacity: 0.6; }
  90% { opacity: 0.6; }
  100% { top: 100%; opacity: 0; }
}

@keyframes particle-float {
  0%, 100% { transform: translateY(0) scale(1); opacity: 0.3; }
  50% { transform: translateY(-20px) scale(1.2); opacity: 0.8; }
}

@keyframes node-pulse {
  0%, 100% { opacity: 0.3; r: 4; }
  50% { opacity: 0.8; }
}

@keyframes tower-glow {
  0%, 100% { opacity: 0.75; }
  50% { opacity: 0.95; }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes strip-pulse {
  0%, 100% { opacity: 0.2; }
  50% { opacity: 0.8; }
}

/* ---- 内容区 ---- */
.auth-center {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 460px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.auth-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 28px;
  text-align: center;
}

.logo-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(12px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
}

.logo-ring {
  position: absolute;
  inset: -6px;
  border-radius: 24px;
  border: 1px solid rgba(255, 215, 0, 0.25);
  animation: ring-pulse 3s ease-in-out infinite;
}

@keyframes ring-pulse {
  0%, 100% { opacity: 0.4; transform: scale(1); }
  50% { opacity: 0.9; transform: scale(1.04); }
}

.auth-header h1 {
  margin: 18px 0 0;
  font-size: 26px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 3px;
  text-shadow: 0 2px 16px rgba(0, 0, 0, 0.4);
}

.auth-slogan {
  margin: 8px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  letter-spacing: 1.5px;
}

.auth-tags {
  display: flex;
  gap: 8px;
  margin-top: 14px;
  flex-wrap: wrap;
  justify-content: center;
}

.tag-pill {
  padding: 4px 12px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(100, 181, 255, 0.25);
  border-radius: 20px;
  letter-spacing: 1px;
}

.auth-center :deep(.auth-card) {
  position: relative;
  width: 100%;
  background: rgba(255, 255, 255, 0.93);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.55);
  border-radius: 20px;
  padding: 40px 44px;
  box-shadow:
    0 24px 64px rgba(0, 0, 0, 0.4),
    0 0 40px rgba(26, 95, 180, 0.08);
  box-sizing: border-box;
}

.auth-center :deep(.auth-card)::before {
  content: '';
  position: absolute;
  top: 0;
  left: 20%;
  right: 20%;
  height: 2px;
  background: linear-gradient(90deg, transparent, #1a5fb4, #ffd700, #1a5fb4, transparent);
  border-radius: 2px;
  opacity: 0.6;
}

.auth-center :deep(.auth-card--register) {
  max-height: calc(100vh - 240px);
  overflow-y: auto;
}

.auth-center :deep(.auth-card h2) {
  margin: 0 0 10px;
  font-size: 28px;
  font-weight: 700;
  color: #1a2b3c;
}

.auth-center :deep(.subtitle) {
  margin: 0 0 32px;
  color: #606266;
  font-size: 15px;
  line-height: 1.6;
}

.auth-center :deep(.el-form-item) {
  margin-bottom: 24px;
}

.auth-center :deep(.el-input__wrapper) {
  padding: 4px 14px;
  min-height: 44px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 1px 4px rgba(13, 33, 55, 0.06);
}

.auth-center :deep(.el-input__inner) {
  font-size: 15px;
}

.auth-center :deep(.el-checkbox__label) {
  font-size: 15px;
}

.auth-center :deep(.submit-btn) {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 2px;
  box-shadow: 0 4px 20px rgba(26, 95, 180, 0.4);
}

.auth-center :deep(.auth-footer) {
  margin-top: 24px;
  text-align: center;
  font-size: 15px;
  color: #606266;
}

.auth-center :deep(.auth-footer a) {
  color: var(--el-color-primary);
  text-decoration: none;
  margin-left: 6px;
  font-weight: 500;
}

.auth-center :deep(.el-divider__text) {
  font-size: 14px;
  color: #909399;
  background: transparent;
}

.auth-center :deep(.demo-accounts) {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.auth-center :deep(.demo-tag) {
  cursor: pointer;
  font-size: 13px;
  padding: 6px 12px;
  height: auto;
}

.auth-center :deep(.link) {
  color: var(--el-color-primary);
  font-size: 15px;
}

@media (max-width: 900px) {
  .deco-tower,
  .deco-network {
    opacity: 0.35;
    transform: scale(0.8);
  }

  .deco-tower { left: -5%; bottom: 5%; }
  .deco-network { right: -5%; top: 8%; }
}

@media (max-width: 480px) {
  .auth-layout { padding: 24px 16px; }
  .auth-center :deep(.auth-card) { padding: 32px 24px; }
  .auth-header h1 { font-size: 22px; letter-spacing: 1px; }
  .deco-corner { width: 60px; height: 60px; opacity: 0.4; }
  .deco-tower, .deco-network { display: none; }
  .auth-tags { display: none; }
}
</style>
