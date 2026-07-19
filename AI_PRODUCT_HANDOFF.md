# AI Product Handoff

## 1. Current Product Positioning

CareerLoop is being refocused from an AI job-search tool collection into an AI job-readiness workbench for students and new graduates.

The product should be understood as an AI career-planning agent product, but the term "agent" must be used precisely:

- The core agent is the AI career-planning agent behind the workbench. It reads the user's target role, assessment, resume, interview, check-in, profile snapshot, and career plan, then decides the user's current stage, biggest gap, readiness, today's task, and next action.
- "小职", "小严", and "小面" are not the core agent. They are persona-based AI chat assistants that share the same chat infrastructure but differ by system prompt, tone, and use case.
- The user-facing product should emphasize "AI 给你的下一步", "今日求职任务", "求职准备度", and "我的求职计划" rather than making users understand an "Agent Hub" or multiple "agents".

The product should answer, every time the user opens it:

- What target role am I preparing for?
- Where am I currently weak?
- What is the one most important thing to do today?
- What result will I get after doing it?
- How much closer am I to being ready to apply or interview?

## 2. Current Main Loop

Target role -> user profile / assessment -> resume diagnosis -> JD matching -> today's task -> mock interview -> resume/interview feedback -> next action.

Features that do not directly support this loop should be weakened, hidden, merged, or moved below the core workbench.

Current intelligent-agent boundary:

- `CareerAgentServiceImpl` is the current backend implementation closest to the real product agent. It is currently a rule-driven orchestration service with some LLM-generated reasoning, not yet a fully autonomous planning/execution loop.
- The agent today can infer career stage, calculate readiness, surface risks, generate recommended actions, sync today's tasks, update agent state, and connect with career-plan summaries.
- It is not yet a full autonomous agent because it does not independently execute tools, observe outcomes, and iterate through multi-step plans without user action.
- The correct next product direction is to strengthen this "AI career-planning agent" as the product brain, while demoting persona chat to supporting coaching/interview surfaces.

## 3. Completed Changes

### Phase 1: Home Workbench Refactor

Completed previously.

- Replaced the old home-first structure of search + feature grid + content feed with a job-readiness workbench.
- Added a top workbench hero showing brand, user greeting, current stage, and target role.
- Added a readiness card showing readiness percentage, readiness summary, and the biggest current gap.
- Added a single primary AI task card that tells the user what to do next, why, what they get after completion, and where to go.
- Reduced core entry points to four: assessment, resume, interview, and career plan.
- Added a recent progress card showing target role, latest assessment, latest resume, and latest interview.
- Kept check-in and CDUT employment insight, but moved them into small support cards below the primary workflow.
- Kept Bilibili videos, career articles, consultations, and career cards, but moved them below the workbench as resource content.
- Removed top-of-page generic search from the first screen; resource search only appears when resource content exists or a search is active.

### Phase 2: New User Onboarding Refactor

Completed previously.

- Replaced the feature-introduction onboarding carousel with a 3-step fast setup flow.
- Collected user identity type: student, new graduate, internship seeker, or career switcher.
- Let the user choose or type a target role.
- Asked whether the user already has a resume.
- Routed users who self-report having a resume to the Resume tab and automatically opened the upload/template sheet once.
- Routed users without a resume or unsure about resume quality to assessment first.
- Kept unauthenticated setup in local pending storage and synced it after login.

### Phase 3 P0: Onboarding Persistence + Task Rules

Completed in this round.

- Added `profile_snapshot.onboarding` to persist onboarding inputs in the existing `users.profile_snapshot` JSON:
  - `identityType`
  - `hasResume`
  - `onboardingCompletedAt`
- Kept target role in the existing `profile_snapshot.preferences.targetRole`; no duplicate target-role field was added.
- Added `PUT /users/me/profile-snapshot/onboarding` for lightweight onboarding persistence.
- Updated onboarding completion and login pending-sync flows to call the new onboarding snapshot API.
- Clarified that `onboarding.hasResume` is the user's self-reported resume state, not proof that a resume exists in the system.
- Updated `CareerAgentServiceImpl.getToday()` with simple hard-coded onboarding-aware task rules:
  - internship seeker + no self-reported resume -> build first internship resume material
  - new graduate + self-reported resume but no system resume -> upload resume and match a target JD
  - career switcher -> organize transition rationale and target-role evidence
  - no target role -> choose a target role direction first
  - target role but no assessment -> complete a 5-minute career assessment
  - resume exists but no interview -> run a 10-minute mock interview
- Added light readiness dimensions in `AgentUserProfileDto.Readiness`:
  - `directionClarityPercent`
  - `resumeReadinessPercent`
  - `interviewReadinessPercent`
  - `actionContinuityPercent`
- Updated the home workbench copy mapping to surface these dimensions without rewriting the page UI.
- Fixed duplicate assessment `reLaunch()` in onboarding routing.
- Phase 3 acceptance cleanup: fixed the daily-agent task key so Chinese labels do not collapse into the same key, updates existing TODO task copy when the onboarding-aware rule changes, and dismisses stale same-day `DAILY_AGENT` TODO tasks that are no longer part of today's generated actions. This prevents old fallback tasks from covering the new onboarding-aware task on the home page.

### Phase 4: Agent Expression Convergence

Completed in this round.

- Converged the user-facing Agent concept from "Agent Hub / multiple agents / risk monitoring" toward "AI gives me the next career-prep step".
- Reworked the Agent page first screen:
  - page title is now "我的求职下一步"
  - first card is "今日行动"
  - the recommendation now explicitly shows "为什么推荐这个任务" and "完成后会得到什么"
  - "我的求职计划" is shown before diagnostic/support sections
  - "风险监控" is now "当前短板"
  - weekly review, state metrics, and event timeline remain available, but are pushed toward the bottom and renamed as recent progress/action record
- Kept all existing backend Agent APIs, task data, state data, events, and plan logic unchanged.
- Updated Agent profile wording from "帮 Agent 了解你 / Agent 画像" to "完善求职偏好 / 求职信息完整度".
- Updated assistant persona wording:
  - "小职 · 职业规划 Agent" -> "小职 · 求职教练"
  - "小严 · 严格评审 Agent" -> "小严 · 严格反馈"
  - "小面 · 模拟面试 Agent" -> "小面 · 面试练习"
- Updated user profile entry from "AI 记忆" to "个性化信息" without moving the structure.
- Removed or softened user-visible "Agent Hub", "三位 AI 导师", "风险监控", "周复盘", and "AI 记忆" wording from the frontend copy. Code identifiers and CSS class names still use agent/hub/risk terms internally.

### Phase 5: Mini-Program Professional Copy Polish

Completed in this round.

- Standardized the visible product name to `CareerLoop` instead of mixed `Career Loop` / `CareerLoop` usage.
- Replaced the mini-program webview loading title from `Loading...` to `加载中`.
- Replaced long English MBTI result explanations with concise Chinese career-prep descriptions, short tags, and localized type names.
- Cleaned Chinese copy typos and temporary bilingual text, including the previous "性格倒向测评" typo and the bilingual age-confirmation sentence.
- Replaced several technical or raw error messages with user-facing wording:
  - request/network/upload failures
  - assistant history loading failure
  - interview recording, playback, camera, and microphone permission failures
- Localized interview-room status labels so the top line shows `简单 / 标准 / 挑战`, `语音`, and `中文 / 英文` instead of raw `Normal · Voice · EN`.
- Normalized ellipsis/loading copy in the locale files.
- Kept Phase 5 scoped to frontend copy and display mapping. No backend, database, recommendation, or page-architecture changes were made in this phase.
- A targeted scan now only finds internal code comments or English-locale text for the searched terms; no known Chinese mini-program screen still contains user-visible `Agent Hub`, `风险监控`, `周复盘`, `AI 记忆`, `Career Loop`, `Loading...`, or long English MBTI descriptions.

### Phase 6A: Autonomous Product Polish Kickoff

Completed in this round.

- Adopted an autonomous working mode: future changes should prioritize the user's actual mini-program experience over adding more modules. Each batch should be small, verified, and documented.
- Started with the most confusing high-visibility path: Home -> "我的求职下一步".
- Updated static `pages.json` titles and tab labels to Chinese to avoid English flashes before runtime i18n overrides:
  - 首页 / 消息 / 助手 / 简历 / 我的
  - 职业测评 / 测评结果 / 创建简历 / 面试对话 / 开始面试
- Changed the Agent detail page progress label from "准备度" to "当前阶段进度" so a `100%` stage value does not contradict "当前短板" or low resume score.
- Softened task-system wording on the Agent detail page:
  - "今日任务" -> "行动清单"
  - "拆解" -> "展开步骤"
  - "跳过" -> "暂不做"
  - "完成" -> "标记完成"
- Added frontend display normalization for backend-returned Agent task copy on the Agent page, matching the existing Home normalization. User-visible strings such as "让严格 Agent 评审简历" now render as "检查简历薄弱项".
- Cleaned additional high-signal polish issues:
  - onboarding final button loading text `保存中...` -> `保存中…`
  - assistant thinking text `正在思考...` -> `正在思考…`
  - resume opening text `正在打开...` -> `正在打开…`
  - assessment empty state no longer says "管理员尚未发布任何题库"
  - resume-template placeholder changed from a long English example to a Chinese project-experience example
  - resume-AI loading state no longer initializes as `Ready...`

### Phase 6B: Primary Entry Simplification

Completed in this round.

- Removed `消息` from the bottom tab bar so low-frequency notifications no longer compete with the core job-prep flow.
- Kept `pages/messages/index` and the notification APIs intact; the feature is now reachable from `我的 -> 系统消息`.
- Updated runtime tab-bar i18n order in `frontend/src/locales/index.ts` to match the new four-tab structure:
  - 首页
  - 助手
  - 简历
  - 我的
- Removed the message page's tab-bar badge writes. After moving messages out of the tab bar, leaving the badge at index `1` would incorrectly badge the Assistant tab.
- Softened message page copy:
  - subtitle now reads as "查看求职进展提醒和系统通知"
  - "AI" tab label is now "建议"
  - "简历 AI" is now "简历反馈"
  - "周报" is now "进展回顾"
  - "打卡连续" is now "行动提醒"
  - "AI 顾问" is now "求职建议"
- This phase intentionally did not delete the message page, notification models, or backend logic.

### Phase 6C: Home Core Entry Simplification

Completed in this round.

- Reduced the Home first-screen core entry grid from four items to three:
  - 测评
  - 简历
  - 面试
- Removed the separate `规划` tile from the Home first screen. Planning remains available through:
  - the primary "查看完整下一步" action
  - "最近进展 -> 目标岗位"
  - deeper career-route pages when needed
- Updated the "目标岗位" recent-progress row to open `我的求职下一步` instead of the map page, keeping target-role work tied to the main AI next-step flow.
- Changed the core-entry grid layout from four cramped columns to three clearer action tiles.
- Cleaned remaining high-visibility copy that made the product feel like a feature collection:
  - `技能图谱` -> `求职路线`
  - `AI 规划` -> `计划`
  - `AI 职业规划` -> `求职计划`
  - `AI 顾问` -> removed from assessment onboarding copy
  - `攻击我的职业规划` -> `严格检查我的求职计划`
  - old onboarding/login copy now emphasizes daily job-prep action instead of "一站式功能集合"

### Phase 6D: Career Route Page Repositioning

Completed in this round.

- Kept `pages/map/index` as a secondary page, but repositioned it as a long-horizon "求职路线" detail page rather than a primary feature hub.
- Changed the default tab from route nodes to `求职计划`, because users who enter this page without a specific route-card context should first see their plan, not a skill-map backend.
- If the page is opened with `pathId`, it still opens the route-node view so Home/resource cards can deep-link into a specific route.
- Renamed the two tabs:
  - `路线` -> `路线节点`
  - `计划` -> `求职计划`
- Removed backend-like or debug-like display details:
  - plan card no longer shows raw `v{version}`
  - route stage label no longer shows `L{level}`
  - estimated effort now displays as `约 n 小时` instead of `~n h`
  - empty node description no longer says `Description coming soon.`
- Updated plan copy:
  - `重新生成` -> `更新计划`
  - `规划生成完成` -> `计划已更新`
  - empty state now explains that the route helps organize next-stage job-prep actions

### Phase 6E: Assistant Page Scenario Convergence

Completed in this round.

- Kept the existing assistant chat backend and persona keys unchanged, but changed the user-facing expression from "three named AI assistants" toward three job-prep scenarios.
- Assistant tab chips now show:
  - `教练`
  - `反馈`
  - `练习`
- Assistant mode names now show:
  - `求职教练`
  - `严格反馈`
  - `面试练习`
- Removed visible `小职 / 小严 / 小面` wording from the Assistant page and Assistant history list.
- Rewrote the assistant welcome copy so each mode explains when to use it:
  - unclear next step -> 求职教练
  - existing resume/plan/answer -> 严格反馈
  - interview warm-up and follow-up -> 面试练习
- Removed the separate explanatory note block when it contains no useful content, reducing first-screen density.
- Replaced the raw chat error message that exposed `{msg}` with a simpler user-facing retry message.
- Updated older onboarding/history copy that still referenced the named personas.

### Phase 6F: Resume Workflow Copy Convergence

Completed in this round.

- Kept the existing resume pages and APIs unchanged, but changed the user-facing copy from a file/tool hub toward a coherent "make resume application-ready" workflow.
- Resume tab copy now reads as:
  - `我的简历`
  - `上传、诊断并优化到可投递状态`
  - `基础简历`
  - `针对岗位优化后的简历`
- Removed high-visibility "简历中心 / 简历管理 / 简历库 / AI 定制简历" wording from the Chinese mini-program copy.
- Resume diagnosis page now reads as `简历匹配诊断`, focused on checking whether a resume matches a pasted target JD.
- CTA wording changed from generic/AI-heavy language toward workflow actions:
  - `开始分析` -> `开始诊断`
  - `生成定制简历` -> `按 JD 优化简历`
  - `前往简历库` -> `回到我的简历`
  - Home resume CTA -> `匹配诊断`
- Tailored resumes are shown as `针对岗位优化后的简历`, with `JD` badges instead of `AI` badges.
- Onboarding copy for users who already have a resume now routes them to `我的简历` for upload or matching diagnosis.

### Phase 6G: Interview Practice Flow Copy Convergence

Completed in this round.

- Kept the existing interview pages and APIs unchanged, but changed the user-facing copy toward a single practice loop: choose role -> practice -> get feedback -> improve next time.
- `pages/interview/index` no longer displays a generic `加载中` bridge. It now says `准备面试练习 / 正在进入练习设置…` before redirecting to setup.
- Interview setup copy now reads as `开始面试练习`, with guidance around role, practice mode, difficulty, and follow-up.
- Removed high-visibility "AI 面试官 / AI 评分报告" wording from the Chinese mini-program copy.
- Interview history copy now reads as practice records:
  - `面试练习记录`
  - `复盘每次练习的分数、问题和下一步改进方向`
  - empty state tells users they will get scores and improvement suggestions after one 10-minute practice
- Interview report copy now reads as `练习反馈`, not a generic report:
  - loading text says it is organizing answer performance
  - fallback summary is localized Chinese
  - typo-like copy around sharing questions was cleaned
  - bottom CTA returns to `面试练习`
- The voice room end confirmation now says ending will generate `本次练习反馈`, not an "AI scoring report".

### Phase 6H: Profile / Career Assets Copy Convergence

Completed in this round.

- Kept the existing Profile/User pages and account APIs unchanged, but changed the Chinese mini-program copy so the page feels like career-prep asset management instead of a generic account/settings center.
- `我的` page subtitle now reads `管理求职资产、偏好和记录`.
- Stats now read as:
  - `练习次数`
  - `简历份数`
- Main group label changed from `我的资产` to `求职资产`.
- Menu entries now read:
  - `我的简历`
  - `测评记录`
  - `面试练习记录`
  - `求职偏好资料`
  - `系统消息`
- Settings grouping changed from `外观与无障碍` to `使用偏好`.
- Legal/support grouping changed from `法律与支持` to `支持与安全`.
- Profile edit modal now reads `完善基本信息`, and save success says `基本信息已保存`.
- `用户记忆/个性化信息` surface was renamed to `求职偏好资料`, with copy explaining that these details help produce more relevant career-prep suggestions.
- Removed user-visible "AI 已知 / AI 提取 / 个性化信息" wording from the Chinese Profile and preference-info surfaces.

### Phase 7: Web-Parity Visual Flatten (national final)

Completed on 2026-07-19, driven by reviewer feedback: unify style with the web
platform, remove rounded corners and card top accent lines, use a serif
(Source Han Serif / Noto Serif SC family) for headings.

- `style-library/styles/tokens.css`: all radius tokens (`--radius-*`,
  `--btn-radius`) are now `0`; legacy `--gradient-*` aliases resolve to flat
  competition-palette colours; `--font-serif` now prefers
  `Noto Serif SC / Source Han Serif SC` before `Songti SC`.
- `uni.scss`: `$uni-border-radius-sm/base/lg` set to `0`.
- `style-library/styles/competition.css`:
  - global flatten layer using element selectors (WXSS has no `*` selector)
    that zeroes `border-radius` on `view/text/button/image/input/...`, plus an
    H5-only universal flatten;
  - explicit circle exceptions: spinners, `pulse-ring`, `record-pulse`,
    `record-btn`, interview-room `avatar-face`/`avatar-halo`/`eye`,
    `profile-score-ring(-inner)`;
  - every `border-top: 3px solid <accent>` card accent replaced with a
    1px `--border-color` hairline (web cards have no coloured top line);
  - vermilion glow shadows removed; login hero radial washes flattened to
    plain paper; readiness/agent progress fills are flat vermilion;
  - home resource cover placeholders (`cover-tone-*`, `cover-topic-*`)
    remapped from candy gradients to paper-palette soft tones;
  - serif coverage extended to all page/section/card/modal title classes and
    primary buttons/form labels collected across every page.
- Hard-coded `border-top: 3px/4px` accents inside page scoped styles were
  rewritten to the same 1px hairline.
- Checks: `npm run type-check` passed; `npm run build:mp-weixin` passed;
  H5 visual pass over landing/login/home/assistant/resume/user confirmed the
  flat print look with circles preserved only where intended.
- Note: true webfont loading (`wx.loadFontFace`) is not wired; the serif stack
  relies on device fonts (iOS `Songti SC`, desktop DevTools Noto/Source Han if
  installed). If the team wants pixel-identical serif on Android, serve a
  subsetted `NotoSerifSC` from `api.careerloop.top` and call
  `wx.loadFontFace` at launch in a later round.

## 4. Unfinished Issues

- Home copy is currently hardcoded in Chinese in `frontend/src/pages/home/index.vue`; if English mode remains a product requirement, these strings should be moved into locale files.
- The target runtime is WeChat Mini Program. H5 can be used only as a quick visual preview; acceptance should use `build:mp-weixin` and Weixin DevTools.
- The readiness score now has light dimensions, but the UI still shows a single main readiness card. A later round can expose the four dimensions more explicitly.
- Resource content is only visually downgraded, not moved to a separate page yet.
- Full backend `mvn test` currently fails because existing Flyway migrations use MySQL syntax that H2 rejects in the test profile (`V2__sprint5_additions.sql`). Reproduction command: `cd backend && mvn test`. Observed failure: Flyway fails at `ALTER TABLE users ADD COLUMN ... DATETIME NULL COMMENT ...`, which is MySQL-style syntax not accepted by H2 in the current test setup. This is not caused by Phase 3 logic; backend compile/package with skipped tests and the focused controller test pass.
- A small follow-up fix could add an H2-compatible test migration/profile or refactor the affected migration into H2-compatible statements for tests. Do not make a broad production migration rewrite without first deciding the migration compatibility strategy.
- Backend-generated action labels can still contain the internal word "Agent" in a few cases because Phase 4 intentionally did not modify backend logic. If this appears in Weixin DevTools, handle it in a later narrow backend copy pass or add a frontend display-label mapper.
- English locale strings still intentionally contain English product copy. Phase 5 focused on the Chinese WeChat Mini Program experience and did not fully re-review English mode for product tone.
- Some internal code identifiers, CSS classes, and comments still include words like `agent`, `hub`, `risk`, `debug`, or `demo`. These are not currently user-facing and were left unchanged to avoid unnecessary churn.

## 5. Known Risks

- There are many pre-existing uncommitted changes outside the files touched in this round. Do not revert or overwrite them without explicit instruction.
- `/pages/home/index.vue` still carries older CSS for legacy home sections. Some is still used by resource sections; cleanup should be done carefully in a later phase.
- The home page depends on several best-effort APIs: homepage feed, check-in, CDUT insight, agent bundle, and profile snapshot. Failures should not block the main workbench.
- Guest users and users without backend data must still see a useful first task. Current fallback is assessment-first.
- `onboarding.hasResume` is only the user's self-reported state from onboarding. Resume existence for system diagnosis still comes from `profile_snapshot.resume` / uploaded resume records.
- Pending onboarding sync after login is intentionally idempotent: successful sync removes `career_onboarding_pending`; failed sync keeps it for retry. A repeated successful PUT should merge the same snapshot fields without creating duplicate target-role fields.
- Home uses backend `agentTasks` before local fallback tasks. After Phase 3 cleanup, same-day stale `DAILY_AGENT` TODO tasks are dismissed when they are not in the regenerated onboarding-aware actions, so old fallback-like daily tasks should not dominate the first home task.

## 6. Next Recommended Round

Phase 6I completed on 2026-05-13: global Chinese mini-program copy consistency sweep.

Completed:

- Scanned high-risk user-visible copy across `frontend/src/pages`, `frontend/src/locales/zh-CN.ts`, and `frontend/src/pages.json` for legacy/debug/product-internal wording including loading placeholders, `Agent Hub`, multi-agent wording, risk/weekly-review wording, memory-management wording, `Career Loop`, obvious typos, raw error labels, and backend/admin/demo-style expressions.
- Fixed the visible typo `邮符1` -> `邮箱`.
- Removed mixed formal address in assistant-history retention copy and made it read as user-facing continuity context, not "AI memory".
- Replaced visible login loading ellipses with a cleaner Chinese UI loading glyph.
- Stopped assistant request failures from surfacing raw `Unknown error` style content.
- Stopped interview opening-question failure from exposing `AI service error`.
- Changed resume-template subtitle away from "AI optimization/export tool" wording toward "generate a PDF resume that can continue into diagnosis".
- Re-scanned the same high-risk terms after fixes. The targeted user-visible risk terms now return no matches in pages, Chinese locale, or mini-program route config.

Latest checks:

- `npm run type-check` in `frontend`: passed.
- `npm run build:mp-weixin` in `frontend`: passed. Weixin DevTools import path: `frontend/dist/build/mp-weixin`.

Phase 6J: continue autonomous mini-program validation + deeper consistency cleanup.

Phase 6J started on 2026-05-13: backend task-source copy cleanup + old-data display normalization.

Completed:

- Updated `CareerAgentServiceImpl.getToday()` source labels so newly generated daily tasks no longer say things like "让严格 Agent 评审简历", "AI 面试官", "AI 助手", "AI 生成简历", "职业规划", or "技能图谱" in user-visible task copy.
- Updated backend risk/plan/task descriptions to use "求职计划", "行动连续性", "任务不存在或已失效", and "长期求职计划" instead of engineering/internal Agent phrasing.
- Added a lightweight frontend display normalization utility at `frontend/src/utils/displayText.ts`:
  - normalizes stale cached task strings that may still contain Agent/AI-assistant/skill-map/career-plan wording
  - maps common old English target-role labels such as `Entrepreneur` to Chinese display labels without changing stored profile data
- Wired the normalization into Home and Agent pages so target-role labels and cached tasks display more cleanly.
- Updated Chinese locale action/copy strings around chat actions and resume generation so the core path reads as求职教练/简历生成/目标方向, not a visible AI-module demo.

Latest Phase 6J checks:

- Targeted residual scan for source/UI strings including `让严格`, `AI 助手`, `AI 面试官`, `Agent 无法`, `Agent 现在`, `近期 Agent`, `职业规划`, `技能图谱`, `AI 生成`, `AI 正在`, `Unknown error`, and `AI service error`: only intentional normalization/legal-copy references remain.
- `npm run type-check` in `frontend`: passed.
- `npm run build:mp-weixin` in `frontend`: passed.
- `mvn -DskipTests package` in `backend`: passed.

Phase 6K completed on 2026-05-13: Agent page first-screen hierarchy cleanup.

Completed:

- Refined `frontend/src/pages/agent/index.vue` so the first card owns the primary "今日行动":
  - added a clear `开始今日行动` CTA
  - added a direct `标记完成` action on the primary card
  - hid the lower generic action chips whenever a real primary task exists
- Changed the lower task list from duplicating the first task into `后续行动`:
  - `secondaryTasks` excludes the current primary task
  - count and empty state now refer only to follow-up actions
  - empty copy tells the user to finish today's action first, instead of looking like a task-management backend
- Added dark-mode coverage for the new primary action buttons.

Latest Phase 6K checks:

- `npm run type-check` in `frontend`: passed.
- `npm run build:mp-weixin` in `frontend`: passed.

Phase 6L completed on 2026-05-14: Home -> Next Step task loop alignment.

Completed:

- Added `frontend/src/utils/taskDisplay.ts` as a small shared display mapper for task title, description, outcome, CTA, and target route.
- Wired Home and Agent pages to the same task outcome/target logic so the home card and "我的求职下一步" page no longer describe the same task differently.
- Home now only treats `TODO` tasks as the primary task. Completed tasks are filtered out and no longer reappear as the home primary card just because the backend returns them for the day.
- Agent page also filters today tasks to `TODO` before computing the primary and secondary tasks.
- Fixed Agent page navigation for tab pages (`/pages/assistant/index`, `/pages/resume/index`, `/pages/user/index`, `/pages/home/index`) by using `switchTab` instead of `navigateTo`.
- Completing a task from Home or Agent now removes it from the visible task list, refreshes relevant state, and shows `已完成，下一步已更新`.

Latest Phase 6L checks:

- `npm run type-check` in `frontend`: passed.
- `npm run build:mp-weixin` in `frontend`: passed.

Phase 6M completed on 2026-05-14: empty/error state hardening for Home and Next Step.

Completed:

- Added a resilient fallback first card on `frontend/src/pages/agent/index.vue` when the next-step APIs fail or no `agentToday` is available:
  - default action is `先完成一次职业测评`
  - includes reason and outcome copy, so the page still feels actionable instead of blank
- Added a non-raw `loadError` message for the Agent page when both today-task endpoints fail.
- Hardened navigation in Home and Agent pages:
  - empty routes now show `暂时无法打开，请稍后重试`
  - tab pages use `switchTab`
  - navigation failures show a user-readable toast instead of silently failing
  - removed stale `/pages/messages/index` from Home tab-route switching because messages is no longer a tab
- Home resource loading now uses the localized fallback `首页内容加载失败，请检查网络后重试` instead of surfacing raw request errors.
- Home and Agent task completion/plan failures no longer prioritize backend raw error strings in visible toasts.

Latest Phase 6M checks:

- Residual scan for raw error strings and fragile navigation patterns in Home/Agent pages: no matches.
- `npm run type-check` in `frontend`: passed.
- `npm run build:mp-weixin` in `frontend`: passed.

Phase 6N completed on 2026-05-14: Weixin DevTools assisted validation + route hardening.

Completed:

- Opened the current `frontend/dist/build/mp-weixin` project in Weixin DevTools.
- Verified the current simulator was using the built mini-program output.
- Observed Home first screen in the simulator:
  - product headline and readiness card render
  - home task no longer shows `严格 Agent`
  - old English role `Entrepreneur` displays as `创业/创新方向`
- Clicked `查看完整下一步` and verified navigation to `pages/agent/index`.
- Observed Agent page first screen in the simulator:
  - title reads `我的求职下一步`
  - primary card reads as `今日行动`
  - primary CTA is `开始今日行动`
  - lower section is `后续行动`, not a duplicate backend-like task dashboard
- Automated route audit confirmed every literal `/pages/...` reference found in `frontend/src/pages` and `frontend/src/utils` exists in `pages.json`.
- Hardened high-frequency navigation:
  - Home article/resource in-app links now route through the same safe `navTo` helper.
  - Home consultation in-app links now route through the same safe `navTo` helper.
  - User page `navTo` now handles tab pages with `switchTab` and shows a readable toast on navigation failure.
- Fixed a real Weixin DevTools runtime error:
  - root cause: `useTheme().refresh()` called `setTabBarStyle` from non-tab pages such as `pages/agent/index`
  - fix: `frontend/src/utils/theme.ts` now only applies native tab-bar style when the current route is an actual tab page
- Polished issues seen during DevTools validation:
  - `职业计划` display text now normalizes to `求职计划`
  - Agent missing-signal chips and weekly-review highlights now pass through display normalization
  - chat/task outcome for `CHAT` tasks now says `一条更明确的修改方向和行动建议` instead of a generic progress line

Latest Phase 6N checks:

- Literal route audit: passed, no missing `/pages/...` references.
- High-risk Chinese copy scan: only intentional code/status-normalization references remain.
- `npm run type-check` in `frontend`: passed.
- `npm run build:mp-weixin` in `frontend`: passed.
- `mvn -DskipTests package` in `backend`: passed.

Phase 6O completed on 2026-05-14: backend test-suite stabilization.

Completed:

- Reproduced the backend full-suite failure with `cd backend && mvn test`.
- Confirmed the first blocker was test-only H2/Flyway compatibility, not a Phase 3/6 product regression:
  - production migration `V2__sprint5_additions.sql` uses MySQL-style multi-column `ALTER TABLE ... COMMENT ...`
  - H2 cannot parse that DDL even in MySQL mode
  - test profile already uses Hibernate `create-drop`, so Flyway is unnecessary for these tests
- Disabled Flyway only in `backend/src/test/resources/application-test.yml`; production migration behavior is unchanged.
- Fixed two `@WebMvcTest` slice gaps exposed after Flyway was disabled:
  - `AuthControllerTest` now mocks `AuthInterceptor`, avoiding a real `UserRepository` dependency inside the MVC slice
  - `CareerControllerTest` now mocks `CareerPlanService`, matching the current controller constructor

Latest Phase 6O checks:

- `mvn test` in `backend`: passed, 126 tests, 0 failures, 0 errors.
- `mvn -DskipTests package` in `backend`: passed.
- `npm run type-check` in `frontend`: passed.
- `npm run build:mp-weixin` in `frontend`: passed.

Phase 6P completed on 2026-05-14: focused regression tests for onboarding-driven next actions.

Completed:

- Extended `backend/src/test/java/com/group1/career/controller/UserControllerTest.java` with a contract test for `PUT /users/me/profile-snapshot/onboarding`.
  - verifies `identityType`, `hasResume`, and `onboardingCompletedAt` are passed to `profile_snapshot.onboarding`
  - verifies `targetRole` is written through `profile_snapshot.preferences.targetRole`, not duplicated in onboarding
  - verifies target-role changes trigger plan regeneration and agent profile refresh
- Added `backend/src/test/java/com/group1/career/service/CareerAgentServiceImplTest.java` to lock the three Phase 3 P0 task rules:
  - `internship_seeker + hasResume=no + no system resume` -> `INTERNSHIP_RESUME_BOOTSTRAP`, headline `建立第一版实习简历素材`
  - `new_graduate + hasResume=yes + no system resume` -> `GRADUATE_RESUME_UPLOAD`, headline `上传简历并匹配一个目标 JD`
  - `career_switcher` -> `CAREER_SWITCH_POSITIONING`, headline `整理转岗理由和目标岗位证据`

Latest Phase 6P checks:

- `mvn -Dtest=UserControllerTest,CareerAgentServiceImplTest test` in `backend`: passed, 5 tests.
- `mvn test` in `backend`: passed, 130 tests, 0 failures, 0 errors.
- `mvn -DskipTests package` in `backend`: passed.
- `npm run type-check` in `frontend`: passed.
- `npm run build:mp-weixin` in `frontend`: passed.

Phase 6Q completed on 2026-05-14: real-user Weixin DevTools pass for the Home -> next-action loop.

Completed:

- Rebuilt the mini-program and validated the current logged-in Home screen in Weixin DevTools as a real user.
- Fixed the main task selection bug that made secondary `CHAT` actions appear as the primary Home task.
  - root cause: daily tasks are returned by creation time, while the real primary action is the first backend `today.actions` item
  - fix: `frontend/src/utils/taskDisplay.ts` now exposes `selectPrimaryTask()` and matches tasks against backend actions by type, target, and source
  - Home and `我的求职下一步` now choose the same primary task
- Fixed the duplicate-feeling Agent page state:
  - primary card now aligns with the backend top action, e.g. `投递前先优化你的简历`
  - `后续行动` no longer promotes the actual main task as a secondary checklist item
- Made Home and Agent readiness display use the same user-facing readiness source where available.
  - Agent detail now shows `求职准备度 {n}%` from `agentProfile.readiness.overallPercent` before falling back to `today.progressPercent`
  - avoids the observed split where Home showed 70% while Agent detail could show 100%
- Added lightweight display normalization for stale/user-visible technical strings:
  - `EMOTIONPROBLEM` -> `情绪与压力应对`
  - repeated `_tailored` suffixes are hidden in resume titles
  - selected career-path names/descriptions are rendered in Chinese
  - `h5(html5)+css3+移动端前端` video wording is softened to `HTML/CSS 与移动端前端`
- Validated the primary Home CTA:
  - `完善简历` opens `pages/resume-ai/index`
  - the route does not incorrectly send the user to chat for the resume-improvement task

Latest Phase 6Q checks:

- `npm run type-check` in `frontend`: passed.
- `npm run build:mp-weixin` in `frontend`: passed.
- Weixin DevTools visual check:
  - Home primary card shows `投递前先优化你的简历`
  - Home recent assessment shows `情绪与压力应对`
  - Home recent resume hides `_tailored_tailored`
  - Career path descriptions at the bottom render in Chinese
  - `完善简历` opens `pages/resume-ai/index`

Known Phase 6Q residual risks:

- DevTools debugger still shows warnings and an intermittent simulator error counter; no build/type-check failure was reproduced in this pass. Inspect console details in a separate pass if it blocks interaction.
- Home lower content still includes live/feed-style article titles and some broad AI-industry news. This is below the core workbench and not currently blocking the task loop, but it can still dilute the "AI 求职准备工作台" feel.
- Onboarding pending local storage remains visible in this existing test account. The Phase 3 sync logic is covered by tests, but a fresh-storage login pass is still needed before release.

Phase 6R completed on 2026-05-14: onboarding pending-sync hardening.

Completed:

- Investigated the existing logged-in DevTools account and confirmed `career_onboarding_pending` can remain in local storage after login if the original pending sync failed.
- Added `frontend/src/utils/onboardingSync.ts` as the single frontend helper for:
  - reading `career_onboarding_pending`
  - retrying `PUT /users/me/profile-snapshot/onboarding`
  - clearing pending storage only after a successful backend sync
- Updated login to reuse the shared helper instead of carrying a duplicate inline pending-sync implementation.
- Updated `App.vue` to silently retry pending onboarding sync on real-account launch.
  - guest sessions still do not sync
  - failures keep pending storage for the next retry
  - startup retry is silent, so users do not see a network toast just because backend/profile sync is temporarily unavailable
- Extended `updateOnboardingApi()` with an optional `silent` flag so background sync can fail quietly while foreground onboarding/login behavior remains explicit.

Latest Phase 6R checks:

- `npm run type-check` in `frontend`: passed.
- `npm run build:mp-weixin` in `frontend`: passed.
- `mvn test` in `backend`: passed, 130 tests, 0 failures, 0 errors.
- Weixin DevTools observed state:
  - Home still renders normally after rebuild.
  - Earlier note incorrectly treated the DevTools pass as if it depended on a local backend. The production mini-program build uses the server API, not localhost.

Remaining Phase 6R manual check:

- Deploy the backend code that contains `PUT /users/me/profile-snapshot/onboarding`, relaunch/import the mini-program, and confirm `career_onboarding_pending` disappears from Storage after the silent startup retry succeeds.

Phase 6S completed on 2026-05-14: server-backend correction and pending-sync edge fix.

Completed:

- Rechecked the actual runtime configuration:
  - `frontend/.env.production` points to `https://api.careerloop.top`
  - `frontend/dist/build/mp-weixin/utils/request.js` is built with `https://api.careerloop.top`
  - `https://api.careerloop.top/api/agent/today` is reachable and returns the expected unauthorized envelope without a token
- Verified in Weixin DevTools that the imported project is `frontend/dist/build/mp-weixin`.
- Verified the current logged-in home screen still renders through the server-backed production build:
  - Home primary task shows `投递前先优化你的简历`
  - primary CTA is `完善简历`
  - `查看完整下一步` opens `pages/agent/index`
  - Agent detail first screen is `我的求职下一步 / 今日行动`, with `为什么推荐这个任务` and `完成后会得到什么`
- Fixed a real pending-sync edge case:
  - real account login/register/WeChat login now removes stale `isGuest`
  - `isGuest()` now only returns true when `isGuest === true` and `userId === -1`
  - this prevents a previous guest session from blocking `syncPendingOnboarding()` after the user signs into a real account

Latest Phase 6S checks:

- `npm run type-check` in `frontend`: passed.
- `npm run build:mp-weixin` in `frontend`: passed.
- `mvn test` in `backend`: passed, 130 tests, 0 failures, 0 errors.
- Weixin DevTools after rebuild: 0 compile/debugger errors in the main panel; storage still shows the existing account's `career_onboarding_pending`, so server-side endpoint/version or request outcome still needs deployment/log verification.

Known Phase 6S residual risk:

- The server domain is reachable, but the current production server may not yet be running the backend code that contains the new onboarding endpoint/handler. Because unauthenticated requests are stopped by auth first, a no-token curl can only prove reachability/auth behavior, not the authenticated handler result.
- The existing simulator account still has old local pending onboarding data. This should clear only after a successful authenticated `PUT /users/me/profile-snapshot/onboarding`; do not manually clear it as a substitute for verifying the real server sync.

Remaining manual Weixin DevTools checks:

- Clear storage and run first-install flow: consent -> onboarding -> login/guest -> home.
- Validate pending onboarding sync after real login with both `hasResume=yes` and `hasResume=no`.
- Run the three Phase 3 task paths with fresh users/data:
  - 找实习 + 无简历
  - 应届/准应届 + 自报有简历但系统无简历记录
  - 转方向
- Tap every home first-screen entry on device: 今日行动 CTA, 查看完整下一步, 标记完成, 测评, 简历, 面试, 我的头像.
- Validate microphone/camera permission flows on a real device; simulator warnings for permissions are expected.

Suggested scope:

- Import `frontend/dist/build/mp-weixin` into Weixin DevTools and validate onboarding -> login -> resume upload sheet -> home task flow.
- Validate Agent page copy in Weixin DevTools: the first screen should read as "我的求职下一步 / 今日行动", not as an Agent Hub.
- Validate the updated Agent detail page specifically:
  - the first card should feel like a "today action detail", not a task management backend
  - the progress label should read as stage progress, not overall readiness
  - backend-generated task labels should not expose "严格 Agent"
- Manually validate the three Phase 3 paths in Weixin DevTools:
  - internship seeker + no self-reported resume -> home today's task says to build first internship resume material
  - new graduate + self-reported resume + no system resume record -> home today's task says to upload resume and match a JD
  - career switcher -> home today's task says to organize transition rationale and target-role evidence
- Scan the mini-program UI for any remaining user-visible "Agent Hub", "多智能体", "风险监控", "周复盘", or "AI 记忆" wording that may be returned dynamically by backend-generated action labels.
- Validate Phase 5 copy in real Weixin DevTools screens:
  - consent/login/onboarding loading and button copy
  - assessment result MBTI descriptions
  - interview room permission/recording/playback errors
  - assistant history empty/error states
  - home task card and Agent page first screen
- Add explicit UI affordance for the four readiness dimensions if the product wants users to see why the percentage changed.
- Consider whether `career_switcher` should continue to take priority over assessment/resume gaps after the first transition-positioning task is completed.
- If old user data still displays English target-role strings such as `Entrepreneur`, add a narrow display-label mapper or profile-data normalization path rather than changing the core onboarding schema.
- Continue reducing page-entry overload. `消息` has moved under `我的`, Home now has three core action tiles, `求职路线/map` is now a secondary long-horizon route page, Assistant now reads as three coaching modes, Resume now reads as a single application-readiness workflow, Interview now reads as a practice-feedback loop, and Profile now reads as career asset/preference management. Next likely targets are consistency sweeps across secondary pages, then backend/source-of-truth issues such as readiness contradictions and backend-generated Agent wording.
- Validate the four-tab bottom bar in Weixin DevTools. Confirm there is no stale notification badge on the Assistant tab after messages moved out of the tab bar.

## 7. Important File Paths

- `frontend/src/pages/home/index.vue`: main home workbench and current Phase 1 implementation.
- `frontend/src/api/agent.ts`: API client for the core career-planning agent bundle, readiness, tasks, profile, plan, and risk DTOs.
- `backend/src/main/java/com/group1/career/service/impl/CareerAgentServiceImpl.java`: current backend implementation of the AI career-planning agent logic.
- `frontend/src/pages/assistant/index.vue`: persona chat surface for "小职", "小严", and "小面"; this is not the core agent.
- `frontend/src/pages/agent/index.vue`: user-facing "我的求职下一步" page backed by the core career-planning Agent APIs; keep the user-facing wording focused on today's action and career plan.
- `frontend/src/pages/agent/profile.vue`: "完善求职偏好" page for extra user inputs that improve plan/task relevance.
- `backend/src/main/java/com/group1/career/config/AiPersonas.java`: persona prompt definitions for the three chat assistants.
- `frontend/src/api/user.ts`: profile snapshot API and target role preference API.
- `frontend/src/pages/onboarding/index.vue`: fast setup onboarding flow and local pending setup writer.
- `frontend/src/pages/login/index.vue`: pending onboarding sync after login.
- `frontend/src/pages/assessment/result.vue`: currently writes suggested target role into profile snapshot.
- `frontend/src/pages/resume-ai/index.vue`: resume/JD diagnosis and tailored resume path.
- `frontend/src/pages/interview/start.vue`: mock interview entry and target-role prefill.
- `frontend/src/pages/map/index.vue`: skill map and AI career plan.

## 8. Logic Not To Break

- `navTo()` must use `switchTab()` for tab roots and `navigateTo()` for non-tab pages.
- Guest mode uses `userId === -1`; auth-required APIs must not hard-crash guest sessions.
- Home API loading is intentionally best-effort. Failure of feed/check-in/CDUT/agent/profile-snapshot should not prevent the home page from rendering.
- Agent tasks can be completed from home via `completeAgentTaskApi`; keep the task list state in sync after completion.
- Profile snapshot is the cross-tool source for target role, recent assessment, resume score, and interview score.
- Onboarding inputs live in `profile_snapshot.onboarding`; `onboarding.hasResume` is self-reported and must not be treated as an uploaded resume record.
- Do not describe "小职", "小严", and "小面" as separate agents unless their implementation changes substantially. They are persona chat assistants.
- The core product agent is the AI career-planning agent that powers readiness, gaps, tasks, and next actions.
- User-facing copy should avoid making users understand "Agent Hub", "multi-agent", "risk monitoring", "weekly review center", or "memory management" as core concepts. Keep those as implementation/support concepts unless intentionally reintroduced.

## 9. Latest Checks

- `npm run type-check` in `frontend`: passed.
- `npm run build:mp-weixin` in `frontend`: passed. Weixin DevTools import path: `frontend/dist/build/mp-weixin`.
- `mvn -DskipTests package` in `backend`: passed.
- `mvn -Dtest=UserControllerTest,CareerAgentServiceImplTest test` in `backend`: passed.
- `mvn test` in `backend`: passed, 130 tests, 0 failures, 0 errors.
- Test-profile note: Flyway is disabled in `backend/src/test/resources/application-test.yml` because tests use Hibernate `create-drop` with H2, while production migrations include MySQL-specific DDL that H2 cannot parse. Production Flyway migrations remain enabled outside the test profile.
