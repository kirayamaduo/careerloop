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
  uni.setStorageSync('zhihui_preview_home', '1');
  uni.switchTab({ url: '/pages/home/index' });
};
</script>

<style scoped>
.landing-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f6f8fb;
  color: #101828;
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
  border-radius: 14px;
  background: #155eef;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12px 24px rgba(21, 94, 239, 0.22);
}

.brand-mark-text {
  color: #ffffff;
  font-size: 22px;
  font-weight: 900;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-name {
  font-size: 20px;
  font-weight: 900;
  color: #101828;
}

.brand-tag {
  font-size: 12px;
  color: #667085;
  font-weight: 700;
}

.hero-title {
  display: block;
  margin-top: 28px;
  max-width: 620px;
  font-size: 38px;
  line-height: 1.08;
  font-weight: 900;
  color: #101828;
}

.hero-desc {
  display: block;
  margin-top: 14px;
  max-width: 600px;
  font-size: 15px;
  line-height: 1.7;
  color: #475467;
}

.signal-board {
  margin-top: 26px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.score-panel,
.task-panel {
  border: 1px solid #d9e2ef;
  background: #ffffff;
  border-radius: 18px;
  padding: 16px;
  box-sizing: border-box;
  box-shadow: 0 10px 28px rgba(16, 24, 40, 0.08);
}

.score-label,
.task-kicker {
  display: block;
  font-size: 12px;
  font-weight: 800;
  color: #667085;
}

.score-value {
  display: block;
  margin-top: 6px;
  font-size: 34px;
  line-height: 1;
  font-weight: 900;
  color: #155eef;
}

.score-pending {
  font-size: 28px;
  color: #344054;
}

.score-bar {
  height: 7px;
  border-radius: 999px;
  background: #e4eaf3;
  margin-top: 12px;
  overflow: hidden;
}

.score-fill {
  width: 0;
  height: 100%;
  border-radius: 999px;
  background: #155eef;
}

.score-bar-pending {
  background: repeating-linear-gradient(90deg, #e4eaf3 0, #e4eaf3 10px, #f0f4f9 10px, #f0f4f9 18px);
}

.score-note {
  display: block;
  margin-top: 10px;
  font-size: 12px;
  color: #475467;
}

.task-title {
  display: block;
  margin-top: 8px;
  font-size: 17px;
  line-height: 1.35;
  font-weight: 900;
  color: #101828;
}

.task-desc {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.55;
  color: #667085;
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
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e4eaf3;
}

.value-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.value-icon text {
  font-size: 22px;
}

.tone-blue { background: #dbeafe; color: #1d4ed8; }
.tone-green { background: #dcfce7; color: #15803d; }
.tone-amber { background: #fef3c7; color: #b45309; }

.value-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.value-title {
  font-size: 15px;
  font-weight: 900;
  color: #101828;
}

.value-desc {
  font-size: 12px;
  line-height: 1.45;
  color: #667085;
}

.bottom-actions {
  padding: 8px 22px calc(env(safe-area-inset-bottom, 0px) + 24px);
}

.primary-btn,
.secondary-btn {
  height: 52px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 900;
}

.primary-btn {
  color: #ffffff;
  background: #155eef;
  box-shadow: 0 12px 24px rgba(21, 94, 239, 0.24);
}

.primary-btn text {
  color: #ffffff;
}

.secondary-btn {
  margin-top: 10px;
  color: #344054;
  background: #eef3f8;
}

@media (max-height: 720px) {
  .hero-title { font-size: 32px; margin-top: 20px; }
  .hero-desc { font-size: 14px; }
  .signal-board { margin-top: 18px; }
  .value-strip { padding-top: 16px; gap: 8px; }
  .value-item { padding: 11px 13px; }
}
</style>
