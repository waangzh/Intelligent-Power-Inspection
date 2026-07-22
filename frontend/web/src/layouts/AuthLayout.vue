<template>
  <div class="auth-layout">
    <div
      class="auth-background"
      :style="{ backgroundImage: `url(${loginBackgroundUrl})` }"
      aria-hidden="true"
    />

    <main class="auth-shell">
      <section class="brand-panel" aria-labelledby="platform-title">
        <div class="brand-lockup">
          <img class="brand-logo" :src="logoUrl" alt="电力智能巡检平台 Logo" />
          <div class="brand-copy">
            <p class="brand-eyebrow">POWER INSPECTION PLATFORM</p>
            <h1 id="platform-title">电力智能巡检平台</h1>
            <p class="brand-positioning">LocateAnything · ROS 建图 · 智能巡检</p>
          </div>
        </div>

        <p class="brand-intro">
          面向输电线路与变电站场景，提供从环境感知、任务调度到巡检闭环的一体化作业入口。
        </p>

        <div class="capability-list" aria-label="平台能力">
          <div class="capability-item">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M2.5 12S6 6.5 12 6.5 21.5 12 21.5 12 18 17.5 12 17.5 2.5 12 2.5 12Z" />
              <circle cx="12" cy="12" r="2.8" />
            </svg>
            <span>视觉检测</span>
          </div>
          <div class="capability-item">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m12 2.8 7.8 4.5v9L12 20.8l-7.8-4.5v-9L12 2.8Z" />
              <path d="m4.5 7.5 7.5 4.3 7.5-4.3M12 20.5v-8.7" />
            </svg>
            <span>三维感知</span>
          </div>
          <div class="capability-item">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <rect x="4" y="6" width="16" height="13" rx="3" />
              <path d="M9 6V3.5M15 6V3.5M8 12h.01M16 12h.01M9 16h6" />
            </svg>
            <span>自主巡检</span>
          </div>
        </div>

        <p class="brand-footnote"><span /> 统一身份认证 · 作业数据全程可追溯</p>
      </section>

      <section class="auth-panel" aria-label="账号访问">
        <router-view />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import loginBackgroundUrl from '../../img/login_bg.png'
import logoUrl from '../../img/logo.png'
</script>

<style scoped>
.auth-layout {
  --auth-navy: #0b3470;
  --auth-blue: #1768f2;
  --auth-muted: #6b7d97;
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: #edf4fd;
  color: #16345c;
}

.auth-background {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-color: #edf4fd;
  background-position: center bottom;
  background-repeat: no-repeat;
  background-size: cover;
}

.auth-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(440px, 0.88fr);
  align-items: center;
  gap: clamp(48px, 7vw, 116px);
  width: min(1420px, calc(100% - 96px));
  min-height: 100vh;
  margin: 0 auto;
  padding: 56px 0 72px;
}

.brand-panel {
  align-self: center;
  max-width: 720px;
  padding-left: clamp(6px, 2vw, 30px);
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: clamp(24px, 2.5vw, 38px);
}

.brand-logo {
  width: clamp(112px, 10vw, 154px);
  height: clamp(112px, 10vw, 154px);
  flex: 0 0 auto;
  object-fit: contain;
  filter: drop-shadow(0 14px 24px rgba(26, 89, 176, 0.14));
}

.brand-copy {
  min-width: 0;
}

.brand-eyebrow {
  margin: 0 0 12px;
  color: #3c73b8;
  font-family: 'Bahnschrift', 'DIN Alternate', 'Microsoft YaHei', sans-serif;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.2em;
}

.brand-copy h1 {
  margin: 0;
  color: var(--auth-navy);
  font-family: 'Microsoft YaHei UI', 'Source Han Sans SC', sans-serif;
  font-size: clamp(38px, 3.4vw, 58px);
  font-weight: 750;
  line-height: 1.15;
  letter-spacing: 0.045em;
  white-space: nowrap;
}

.brand-positioning {
  margin: 14px 0 0;
  color: #5e708b;
  font-family: 'Bahnschrift', 'Microsoft YaHei', sans-serif;
  font-size: clamp(15px, 1.3vw, 20px);
  letter-spacing: 0.045em;
}

.brand-intro {
  max-width: 620px;
  margin: 34px 0 0;
  color: #637792;
  font-size: 16px;
  line-height: 1.9;
}

.capability-list {
  display: flex;
  align-items: center;
  margin-top: 34px;
}

.capability-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 150px;
  padding: 2px 30px;
  color: #174b8e;
  font-size: 16px;
  font-weight: 650;
  white-space: nowrap;
}

.capability-item:first-child {
  padding-left: 0;
}

.capability-item + .capability-item {
  border-left: 1px solid rgba(75, 113, 166, 0.22);
}

.capability-item svg {
  width: 27px;
  height: 27px;
  fill: none;
  stroke: #1768f2;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.brand-footnote {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 34px 0 0;
  color: #71839b;
  font-size: 13px;
  letter-spacing: 0.04em;
}

.brand-footnote span {
  width: 24px;
  height: 2px;
  background: #f2a621;
}

.auth-panel {
  width: 100%;
  max-width: 560px;
  justify-self: end;
}

.auth-panel :deep(.auth-card) {
  position: relative;
  width: 100%;
  padding: 48px 50px 42px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow:
    0 22px 62px rgba(39, 77, 126, 0.17),
    0 2px 8px rgba(44, 79, 122, 0.05);
  backdrop-filter: blur(16px);
}

.auth-panel :deep(.auth-card)::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 4px;
  background: linear-gradient(90deg, #0d4eb1, #1f7bf0 70%, #f0a627);
}

.auth-panel :deep(.auth-card--register) {
  max-height: calc(100vh - 96px);
  overflow-y: auto;
}

.auth-panel :deep(.auth-card h2) {
  margin: 0;
  color: #0b3470;
  font-size: 34px;
  font-weight: 750;
  line-height: 1.25;
  letter-spacing: 0.05em;
}

.auth-panel :deep(.subtitle) {
  margin: 10px 0 30px;
  color: var(--auth-muted);
  font-size: 15px;
  line-height: 1.7;
}

.auth-panel :deep(.el-form-item) {
  margin-bottom: 22px;
}

.auth-panel :deep(.el-input__wrapper) {
  min-height: 54px;
  padding: 0 17px;
  border-radius: 9px;
  background: #fff;
  box-shadow: 0 0 0 1px #d7e2ef inset !important;
  transition: box-shadow 0.2s ease;
}

.auth-panel :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #a9c1df inset !important;
}

.auth-panel :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #1768f2 inset, 0 0 0 3px rgba(23, 104, 242, 0.08) !important;
}

.auth-panel :deep(.el-input__prefix-inner) {
  color: #6b7f9c;
  font-size: 19px;
}

.auth-panel :deep(.el-input__inner) {
  color: #142c4f;
  font-size: 15px;
}

.auth-panel :deep(.el-checkbox__label) {
  color: #58708e;
  font-size: 14px;
}

.auth-panel :deep(.submit-btn) {
  width: 100%;
  height: 52px;
  border: 0;
  border-radius: 8px;
  background: linear-gradient(100deg, #1674ef, #0f54be);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.12em;
  box-shadow: 0 10px 22px rgba(18, 91, 198, 0.24);
}

.auth-panel :deep(.submit-btn:hover),
.auth-panel :deep(.submit-btn:focus) {
  background: linear-gradient(100deg, #2483ff, #155ecb);
  transform: translateY(-1px);
}

.auth-panel :deep(.submit-btn .el-icon) {
  margin-right: 8px;
  font-size: 18px;
}

.auth-panel :deep(.auth-footer) {
  margin-top: 5px;
  color: #7889a0;
  text-align: center;
  font-size: 14px;
}

.auth-panel :deep(.auth-footer a),
.auth-panel :deep(.link) {
  margin-left: 5px;
  color: var(--auth-blue);
  font-weight: 600;
  text-decoration: none;
}

.auth-panel :deep(.el-divider) {
  margin: 30px 0 24px;
  border-color: #e6edf5;
}

.auth-panel :deep(.el-divider__text) {
  padding: 0 15px;
  background: #fff;
  color: #8998ab;
  font-size: 13px;
}

.auth-panel :deep(.demo-accounts) {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
}

.auth-panel :deep(.demo-tag) {
  width: 100%;
  height: 38px;
  justify-content: center;
  border-radius: 7px;
  background: #fff;
  cursor: pointer;
  font-size: 12px;
  font-weight: 650;
}

@media (max-width: 1120px) {
  .auth-shell {
    grid-template-columns: minmax(0, 0.9fr) minmax(420px, 1.1fr);
    gap: 48px;
    width: min(1040px, calc(100% - 56px));
  }

  .brand-lockup {
    align-items: flex-start;
  }

  .brand-logo {
    width: 92px;
    height: 92px;
  }

  .brand-copy h1 {
    font-size: 34px;
    white-space: normal;
  }

  .brand-positioning {
    font-size: 14px;
  }

  .brand-intro {
    margin-top: 26px;
    font-size: 14px;
  }

  .capability-list {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
    margin-top: 26px;
  }

  .capability-item {
    min-width: 0;
    padding: 0;
  }

  .capability-item + .capability-item {
    border-left: 0;
  }
}

@media (max-width: 820px) {
  .auth-layout {
    overflow: auto;
  }

  .auth-shell {
    display: flex;
    flex-direction: column;
    gap: 30px;
    width: min(560px, calc(100% - 32px));
    min-height: 100vh;
    padding: 30px 0 42px;
  }

  .brand-panel {
    width: 100%;
    padding: 0 8px;
  }

  .brand-lockup {
    align-items: center;
    gap: 18px;
  }

  .brand-logo {
    width: 72px;
    height: 72px;
  }

  .brand-eyebrow {
    margin-bottom: 5px;
    font-size: 9px;
  }

  .brand-copy h1 {
    font-size: clamp(25px, 7vw, 34px);
    white-space: nowrap;
  }

  .brand-positioning {
    margin-top: 7px;
    font-size: 12px;
  }

  .brand-intro,
  .capability-list,
  .brand-footnote {
    display: none;
  }

  .auth-panel {
    max-width: none;
  }

  .auth-background {
    background-position: 34% bottom;
  }
}

@media (max-width: 520px) {
  .auth-shell {
    width: min(100% - 24px, 460px);
    gap: 22px;
    padding-top: 22px;
  }

  .brand-logo {
    width: 58px;
    height: 58px;
  }

  .brand-copy h1 {
    font-size: 23px;
  }

  .brand-positioning {
    letter-spacing: 0;
  }

  .auth-panel :deep(.auth-card) {
    padding: 34px 24px 28px;
    border-radius: 16px;
  }

  .auth-panel :deep(.auth-card h2) {
    font-size: 28px;
  }

  .auth-panel :deep(.demo-accounts) {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-panel :deep(.submit-btn) {
    transition: none;
  }
}
</style>
