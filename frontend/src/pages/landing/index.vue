<template>
  <view class="landing-page">
    <view class="top-safe-spacer" :style="{ height: topSafeHeight + 'px' }"></view>

    <view class="hero">
      <view class="brand-line">
        <view class="brand-mark">
          <text class="brand-mark-text">职</text>
        </view>
        <view class="brand-copy">
          <text class="brand-name">智绘职路 CareerLoop</text>
          <text class="brand-tag">学生成长端 · AI 求职准备教练</text>
        </view>
      </view>

      <text class="hero-title">把求职准备变成一条清晰路线</text>
      <text class="hero-desc">从目标岗位、简历诊断、模拟面试到每日行动，智绘职路会根据你的资料持续更新下一步建议。</text>

      <view class="signal-board">
        <view class="score-panel">
          <text class="score-label">求职准备度</text>
          <text class="score-value score-pending">待评估</text>
          <view class="score-bar score-bar-pending">
            <view class="score-fill score-fill-pending"></view>
          </view>
          <text class="score-note">完成画像后生成你的真实准备度</text>
        </view>
        <view class="task-panel">
          <text class="task-kicker">下一步</text>
          <text class="task-title">先建立你的求职画像</text>
          <text class="task-desc">系统会根据阶段、目标岗位和当前痛点生成第一批任务。</text>
        </view>
      </view>
    </view>

    <view class="value-strip">
      <view v-for="item in values" :key="item.title" class="value-item">
        <view class="value-icon" :class="item.tone">
          <text :class="item.icon"></text>
        </view>
        <view class="value-copy">
          <text class="value-title">{{ item.title }}</text>
          <text class="value-desc">{{ item.desc }}</text>
        </view>
      </view>
    </view>

    <view class="bottom-actions">
      <view class="primary-btn" @click="start">
        <text>开始建立我的求职路线</text>
        <text class="ri-arrow-right-line"></text>
      </view>
      <view class="secondary-btn" @click="preview">
        <text>先看看产品首页</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { enterGuestMode } from '@/utils/auth';

const LANDING_KEY = 'zhihui_landing_seen';
const topSafeHeight = ref(52);

const values = [
  {
    title: '先定方向',
    desc: '把阶段、目标岗位和当前痛点转成可执行准备路线。',
    icon: 'ri-compass-3-line',
    tone: 'tone-blue',
  },
  {
    title: '再看证据',
    desc: '用简历、测评、面试记录判断你离投递还有多远。',
    icon: 'ri-file-search-line',
    tone: 'tone-green',
  },
  {
    title: '每天推进',
    desc: '首页直接告诉你今天最该做什么，而不是让你自己翻功能。',
    icon: 'ri-calendar-check-line',
    tone: 'tone-amber',
  },
];

onMounted(() => {
  topSafeHeight.value = getMpSafeAreaMetrics().contentTop;
});

const markSeen = () => {
  uni.setStorageSync(LANDING_KEY, '1');
};

const start = () => {
  markSeen();
  uni.reLaunch({ url: '/pages/login/index' });
};

const preview = () => {
  markSeen();
  enterGuestMode();
  uni.switchTab({ url: '/pages/home/index' });
};
</script>

<style scoped>
.landing-page {
  min-height: 100vh;
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
  background: var(--paper, #faf9f6);
  color: var(--ink, #2c2b29);
  font-family: var(--font-sans, "PingFang SC", "Microsoft YaHei", sans-serif);
  box-sizing: border-box;
}

.top-safe-spacer {
  width: 100%;
  flex-shrink: 0;
}

.hero {
  padding: 16px 22px 0;
}

.brand-line {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-sm, 4px);
  background: var(--vermilion, #c23b22);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 5px 14px rgba(194, 59, 34, 0.12);
}

.brand-mark-text {
  color: #ffffff;
  font-size: 22px;
  font-weight: 600;
  font-family: var(--font-serif, "Songti SC", STSong, serif);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.08em;
}

.brand-tag {
  font-size: 12px;
  color: var(--ink-tertiary, #8b8a86);
  font-weight: 500;
  letter-spacing: 0.04em;
}

.hero-title {
  display: block;
  margin-top: 28px;
  max-width: 620px;
  font-size: 38px;
  line-height: 1.35;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.05em;
}

.hero-desc {
  display: block;
  margin-top: 14px;
  max-width: 600px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--ink-secondary, #5a5956);
}

.signal-board {
  margin-top: 26px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.score-panel,
.task-panel {
  border: 1px solid var(--border-color, #e0dfdb);
  background: var(--card-bg, #fffefa);
  border-radius: var(--radius-md, 6px);
  padding: 16px;
  box-sizing: border-box;
  box-shadow: 0 2px 10px rgba(44, 43, 41, 0.035);
}
.task-panel { border-top: 3px solid var(--heritage-gold, #b8975a); }

.score-label,
.task-kicker {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--vermilion, #c23b22);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.06em;
}

.score-value {
  display: block;
  margin-top: 6px;
  font-size: 34px;
  line-height: 1;
  font-weight: 600;
  color: var(--vermilion, #c23b22);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
}

.score-pending {
  font-size: 28px;
  color: var(--ink, #2c2b29);
}

.score-bar {
  height: 7px;
  border-radius: 999px;
  background: var(--paper-deep, #efeee9);
  margin-top: 12px;
  overflow: hidden;
}

.score-fill {
  width: 0;
  height: 100%;
  border-radius: 999px;
  background: var(--vermilion, #c23b22);
}

.score-bar-pending {
  background: repeating-linear-gradient(90deg, #e0dfdb 0, #e0dfdb 10px, #efeee9 10px, #efeee9 18px);
}

.score-note {
  display: block;
  margin-top: 10px;
  font-size: 12px;
  color: var(--ink-secondary, #5a5956);
}

.task-title {
  display: block;
  margin-top: 8px;
  font-size: 17px;
  line-height: 1.35;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.04em;
}

.task-desc {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--ink-secondary, #5a5956);
}

.value-strip {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 22px;
}

.value-item {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 13px 14px;
  border-radius: var(--radius-md, 6px);
  background: var(--card-bg, #fffefa);
  border: 1px solid var(--border-color, #e0dfdb);
}

.value-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-sm, 4px);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.value-icon text {
  font-size: 22px;
}

.tone-blue { background: var(--indigo-soft, #eceefa); color: var(--indigo, #3f51b5); }
.tone-green { background: var(--sage-soft, #edf1ea); color: var(--sage, #7b8d6e); }
.tone-amber { background: var(--gold-soft, #f5efe4); color: var(--heritage-gold, #b8975a); }

.value-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.value-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.04em;
}

.value-desc {
  font-size: 12px;
  line-height: 1.45;
  color: var(--ink-secondary, #5a5956);
}

.bottom-actions {
  padding: 8px 22px calc(env(safe-area-inset-bottom, 0px) + 24px);
}

.primary-btn,
.secondary-btn {
  height: 52px;
  border-radius: var(--btn-radius, 6px);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.05em;
}

.primary-btn {
  color: #ffffff;
  background: var(--vermilion, #c23b22);
  box-shadow: 0 5px 14px rgba(194, 59, 34, 0.12);
}

.primary-btn text {
  color: #ffffff;
}

.secondary-btn {
  margin-top: 10px;
  color: var(--ink, #2c2b29);
  background: var(--paper-soft, #f5f5f0);
  border: 1px solid var(--border-color, #e0dfdb);
}

@media (max-height: 720px) {
  .hero-title { font-size: 32px; margin-top: 20px; }
  .hero-desc { font-size: 14px; }
  .signal-board { margin-top: 18px; }
  .value-strip { padding-top: 16px; gap: 8px; }
  .value-item { padding: 11px 13px; }
}
</style>
