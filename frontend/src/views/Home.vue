<template>
  <div class="home">
    <!-- 顶部导航条 -->
    <header class="nav">
      <div class="nav-inner">
        <div class="brand" @click="go('/')">
          <span class="brand-logo">智</span>
          <span class="brand-name">智汇学场</span>
        </div>
        <nav class="nav-links">
          <a @click="scrollTo('modules')">核心模块</a>
          <a @click="scrollTo('features')">平台特性</a>
          <a @click="scrollTo('flow')">使用流程</a>
          <a @click="scrollTo('about')">关于我们</a>
        </nav>
        <div class="nav-actions">
          <ThemeToggle />
          <template v-if="auth.isLoggedIn">
            <el-button type="primary" round @click="go('/portal')">进入门户</el-button>
          </template>
          <template v-else>
            <el-button text @click="go('/login')">登录</el-button>
            <el-button type="primary" round @click="go('/register')">免费注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <!-- Hero -->
    <section class="hero">
      <div class="hero-bg">
        <span class="blob b1"></span>
        <span class="blob b2"></span>
        <span class="blob b3"></span>
        <span class="grid-overlay"></span>
      </div>
      <div class="hero-content">
        <span class="eyebrow">🚀 AI 驱动 · 教与学一体化</span>
        <h1 class="hero-title">一体化<span class="hl">智能学习平台</span></h1>
        <p class="hero-sub">考试 · 竞赛 · 悬赏 · 自学，四大场景融会贯通<br />AI 助力个性化学习，让每一次练习都看得见成长</p>
        <div class="hero-actions">
          <el-button type="primary" size="large" round @click="go(auth.isLoggedIn ? '/portal' : '/login')">
            {{ auth.isLoggedIn ? '进入学习门户' : '立即登录 / 注册' }}
          </el-button>
          <el-button size="large" round @click="scrollTo('modules')">探索四大模块</el-button>
        </div>
        <div class="hero-stats">
          <div class="stat"><b>4</b><span>核心模块</span></div>
          <div class="stat"><b>AI</b><span>智能评分</span></div>
          <div class="stat"><b>1v1</b><span>实时对战</span></div>
          <div class="stat"><b>∞</b><span>题库刷题</span></div>
        </div>
      </div>
      <div class="hero-wave"></div>
    </section>

    <!-- 数据条 -->
    <section class="strip">
      <div class="strip-inner">
        <div class="strip-item"><b>智能组卷</b><span>按章节 / 难度 / 题型一键生成</span></div>
        <div class="strip-item"><b>异步判分</b><span>主观题秒交不阻塞，AI 自动出分</span></div>
        <div class="strip-item"><b>竞技激励</b><span>段位 · 勋章 · 排行榜 · 战队</span></div>
        <div class="strip-item"><b>学情画像</b><span>错题归因与个性化复习路径</span></div>
      </div>
    </section>

    <!-- 四大模块深度介绍 -->
    <section id="modules" class="showcase">
      <h2 class="sec-title">四大核心模块</h2>
      <p class="sec-sub">登录后在门户中枢选择模块，即可进入对应工作区，各司其职又彼此贯通</p>

      <div
        class="feature-row"
        v-for="(m, i) in modules"
        :key="m.key"
        :class="{ reverse: i % 2 === 1 }"
      >
        <div class="feature-text">
          <span class="f-badge" :style="{ background: m.bg }">{{ m.icon }} {{ m.title }}</span>
          <h3>{{ m.headline }}</h3>
          <p class="f-summary">{{ m.summary }}</p>
          <ul class="points">
            <li v-for="p in m.points" :key="p.t">
              <span class="p-ic" :style="{ background: m.bg }">{{ p.icon }}</span>
              <div class="p-body"><b>{{ p.t }}</b><i>{{ p.d }}</i></div>
            </li>
          </ul>
          <el-button round @click="go('/login')">进入{{ m.title }} ›</el-button>
        </div>

        <div class="feature-vis">
          <div class="window" :style="{ '--accent': m.c1 }">
            <div class="win-bar"><i></i><i></i><i></i><span>{{ m.winTitle }}</span></div>
            <!-- 考试 -->
            <div v-if="m.key === 'exam'" class="win-body exam">
              <div class="mock-timer">⏱ 45:12</div>
              <div class="mock-q">
                <div class="mq-title">1. 下列哪项是 Spring 框架的核心特性？</div>
                <div class="mq-opt sel">A. 依赖注入 (IoC)</div>
                <div class="mq-opt">B. 手动内存管理</div>
                <div class="mq-opt">C. 强制多继承</div>
              </div>
              <div class="mock-foot"><span>客观题已自动判分</span><em>主观题 AI 评分中…</em></div>
            </div>
            <!-- 竞赛 -->
            <div v-else-if="m.key === 'competition'" class="win-body comp">
              <div class="vs">
                <div class="vs-side"><div class="ava">我</div><div class="vs-name">Student</div></div>
                <div class="vs-mid">VS</div>
                <div class="vs-side"><div class="ava b">对</div><div class="vs-name">Rank #8</div></div>
              </div>
              <div class="vs-score"><b>6</b> : <b>5</b><span>第 11 题 · 限时抢答</span></div>
              <div class="badge-row">🥇 黄金段位 · 连胜 5 场</div>
            </div>
            <!-- 悬赏 -->
            <div v-else-if="m.key === 'bounty'" class="win-body bounty">
              <div class="b-card">
                <div class="b-top"><span class="b-tag">题目征集</span><span class="b-rew">💰 50</span></div>
                <div class="b-title">征集「数据结构 · 二叉树」选择题 5 道</div>
                <div class="b-bar"><i style="width:60%"></i></div>
                <div class="b-meta">已投稿 3 / 5 · 剩余 2 天 · 采纳即入库</div>
              </div>
              <div class="b-card mini">
                <div class="b-top"><span class="b-tag g">答案征集</span><span class="b-rew">💰 30</span></div>
                <div class="b-title">求助：TCP 三次握手详解</div>
              </div>
            </div>
            <!-- 自学 -->
            <div v-else class="win-body learn">
              <div class="l-item"><span>📕 错题本</span><b>24 题待复习</b></div>
              <div class="l-item"><span>⚡ 今日刷题</span><b>35 题 · 正确率 82%</b></div>
              <div class="l-prog">
                <div class="lp-label">薄弱点：动态规划</div>
                <div class="l-bar"><i style="width:38%"></i></div>
              </div>
              <div class="l-prog">
                <div class="lp-label">已掌握：二叉树</div>
                <div class="l-bar"><i style="width:86%"></i></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 平台特性 -->
    <section id="features" class="features">
      <h2 class="sec-title">为什么选择智汇学场</h2>
      <p class="sec-sub">把复杂的考试与学习流程，做成简单、公平、有趣的一站式体验</p>
      <el-row :gutter="24">
        <el-col :xs="24" :sm="12" :md="8" v-for="f in features" :key="f.title">
          <div class="feat">
            <div class="feat-icon" :style="{ background: f.bg }">{{ f.icon }}</div>
            <div class="feat-body">
              <h4>{{ f.title }}</h4>
              <p>{{ f.desc }}</p>
            </div>
          </div>
        </el-col>
      </el-row>
    </section>

    <!-- 使用流程 -->
    <section id="flow" class="flow">
      <h2 class="sec-title">四步开启智能学习</h2>
      <p class="sec-sub">从注册到看见成长，只需几分钟</p>
      <div class="steps">
        <div class="step" v-for="(s, i) in steps" :key="s.t">
          <div class="step-no">{{ i + 1 }}</div>
          <div class="step-ic">{{ s.icon }}</div>
          <h4>{{ s.t }}</h4>
          <p>{{ s.d }}</p>
          <span v-if="i < steps.length - 1" class="step-arrow">➜</span>
        </div>
      </div>
    </section>

    <!-- 角色分区 -->
    <section class="roles">
      <div class="role-card teacher">
        <div class="role-head"><span class="role-ic">🏫</span><h3>教师</h3></div>
        <p class="role-tag">教学与命题的高效助手</p>
        <ul>
          <li>创建考试、智能组卷、一键发布</li>
          <li>人工阅卷 + AI 主观题评分双通道</li>
          <li>成绩分析、错题统计、学情画像</li>
          <li>发布悬赏征集，共建优质题库</li>
        </ul>
      </div>
      <div class="role-card student">
        <div class="role-head"><span class="role-ic">🎓</span><h3>学生</h3></div>
        <p class="role-tag">主动学习成长的进阶之路</p>
        <ul>
          <li>在线作答、限时答题、成绩查询</li>
          <li>参与竞赛对战，冲段位赢勋章</li>
          <li>投稿悬赏赚积分，帮助同学</li>
          <li>错题本 + 自由刷题 + 个性化学习计划</li>
        </ul>
      </div>
    </section>

    <!-- CTA -->
    <section class="cta">
      <div class="cta-inner">
        <h2>准备好开启智能学习之旅了吗？</h2>
        <p>注册即可体验考试、竞赛、悬赏与自主学习的完整闭环</p>
        <el-button type="primary" size="large" round @click="go(auth.isLoggedIn ? '/portal' : '/register')">
          {{ auth.isLoggedIn ? '进入门户中枢' : '免费注册，立即开始' }}
        </el-button>
      </div>
    </section>

    <!-- 页脚 -->
    <footer id="about" class="footer">
      <div class="footer-inner">
        <div class="f-col brand-col">
          <div class="brand"><span class="brand-logo">智</span><span class="brand-name">智汇学场</span></div>
          <p>一体化智能学习平台 · 考试 / 竞赛 / 悬赏 / 自学</p>
        </div>
        <div class="f-col">
          <h5>产品</h5>
          <a @click="scrollTo('modules')">核心模块</a>
          <a @click="scrollTo('features')">平台特性</a>
          <a @click="scrollTo('flow')">使用流程</a>
        </div>
        <div class="f-col">
          <h5>模块</h5>
          <a @click="go('/login')">在线考试</a>
          <a @click="go('/login')">答题竞赛</a>
          <a @click="go('/login')">征集悬赏</a>
          <a @click="go('/login')">自主学习</a>
        </div>
        <div class="f-col">
          <h5>开始</h5>
          <a @click="go('/login')">登录</a>
          <a @click="go('/register')">注册</a>
        </div>
      </div>
      <div class="footer-bottom">© 2026 智汇学场 Exam System · Spring Boot 3 + Vue 3 + Element Plus</div>
    </footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import ThemeToggle from '../components/ThemeToggle.vue'

const router = useRouter()
const auth = useAuthStore()

const modules = [
  {
    key: 'exam', icon: '📝', title: '在线考试', winTitle: '考试作答 · 数据结构',
    headline: '从命题到判分，考试全流程自动化',
    summary: '教师智能组卷、一键发布，学生在线限时作答；客观题即时判分、主观题 AI 异步评分，成绩完成后自动公布。',
    bg: 'linear-gradient(135deg,#6d5efc,#4f7cff)', c1: '#6d5efc',
    points: [
      { icon: '🗂️', t: '智能组卷', d: '按章节 / 难度 / 题型一键抽题生成试卷' },
      { icon: '🤖', t: 'AI 主观题评分', d: '异步判分不阻塞，完成后自动公布成绩' },
      { icon: '🛡️', t: '防作弊监考', d: '切屏监测与超时自动交卷，公平公正' },
      { icon: '📊', t: '成绩分析', d: '及格率、分数分布与错题统计一目了然' }
    ]
  },
  {
    key: 'competition', icon: '🏆', title: '答题竞赛', winTitle: '1v1 实时对战',
    headline: '把答题变成一场热血的竞技',
    summary: '限时竞赛、1v1 实时对战同步作答，段位、勋章与排行榜激励每一次进步，还能组队征战战队赛。',
    bg: 'linear-gradient(135deg,#f0993a,#f5c04e)', c1: '#f0993a',
    points: [
      { icon: '⌛', t: '限时竞赛', d: '倒计时抢答，紧张刺激的答题 showdown' },
      { icon: '⚔️', t: '1v1 实时对战', d: 'WebSocket 推送，与对手同步作答' },
      { icon: '🎖️', t: '段位勋章', d: '对战晋级、解锁成就，见证实力成长' },
      { icon: '👥', t: '排行榜 · 战队', d: '个人榜与战队榜，同伴互相激励' }
    ]
  },
  {
    key: 'bounty', icon: '💰', title: '征集悬赏', winTitle: '悬赏广场',
    headline: '众包共建，让好题与好答案流动起来',
    summary: '发布题目或答案征集悬赏，师生众包投稿，采纳即入库并赚取积分，形成优质内容共建的正向循环。',
    bg: 'linear-gradient(135deg,#22b573,#5fd08a)', c1: '#22b573',
    points: [
      { icon: '📢', t: '发布征集', d: '题目 / 答案悬赏，设置积分奖励' },
      { icon: '✍️', t: '众包投稿', d: '人人可参与，贡献优质内容' },
      { icon: '✅', t: '采纳入库', d: '优质投稿采纳后直接进入题库' },
      { icon: '💰', t: '积分激励', d: '赚积分、攒荣誉，共建共享' }
    ]
  },
  {
    key: 'learning', icon: '📚', title: '自主学习', winTitle: '学习中心',
    headline: '因材施教的个性化学习闭环',
    summary: '自动错题本、自由刷题、个性化学习计划与学情画像，精准定位薄弱点，规划专属复习路径。',
    bg: 'linear-gradient(135deg,#0ea5b7,#38c7d4)', c1: '#0ea5b7',
    points: [
      { icon: '📕', t: '错题本', d: '自动归集错题，针对性反复巩固' },
      { icon: '⚡', t: '自由刷题', d: '按章节 / 难度随时开练，即做即判' },
      { icon: '🗓️', t: '学习计划', d: '个性化复习路径，目标清晰可执行' },
      { icon: '📈', t: '学情画像', d: '薄弱点分析与学习报告，看见成长' }
    ]
  }
]

const features = [
  { icon: '🤖', title: 'AI 智能评分', desc: '主观题异步 AI 判分，秒级交卷不阻塞，成绩完成后自动公布。', bg: 'linear-gradient(135deg,#6d5efc,#4f7cff)' },
  { icon: '⚔️', title: '竞技化激励', desc: '段位、勋章、排行榜、实时对战，让学习像打游戏一样上瘾。', bg: 'linear-gradient(135deg,#f0993a,#f5c04e)' },
  { icon: '📊', title: '学情画像', desc: '错题归因、学习报告与薄弱点分析，个性化推荐复习路径。', bg: 'linear-gradient(135deg,#0ea5b7,#38c7d4)' },
  { icon: '💡', title: '众包共建', desc: '悬赏征集优质题目，师生共建题库，采纳即入库。', bg: 'linear-gradient(135deg,#22b573,#5fd08a)' },
  { icon: '🔒', title: '防作弊监考', desc: '切屏监测与自动交卷，保障考试公平公正。', bg: 'linear-gradient(135deg,#ef5b5b,#f78989)' },
  { icon: '🖥️', title: '多端一致', desc: '师生同权门户，考试、竞赛、悬赏、自学一站式切换。', bg: 'linear-gradient(135deg,#7c5cff,#a78bfa)' }
]

const steps = [
  { icon: '🔑', t: '注册登录', d: '创建师生账号，安全登录' },
  { icon: '🧭', t: '选择模块', d: '进入门户中枢，挑选目标模块' },
  { icon: '🎯', t: '学习作答', d: '考试 / 竞赛 / 悬赏 / 刷题' },
  { icon: '📈', t: '看见成长', d: '成绩、段位与学情画像反馈' }
]

function go(to) { router.push(to) }
function scrollTo(id) {
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth' })
}
</script>

<style scoped>
.home { min-height: 100vh; background: var(--canvas); color: var(--ink); }

/* ===== 顶部导航 ===== */
.nav { position: sticky; top: 0; z-index: 100; background: var(--topbar-bg); backdrop-filter: blur(12px); border-bottom: 1px solid var(--topbar-bd); }
.nav-inner { max-width: 1200px; margin: 0 auto; height: 66px; display: flex; align-items: center; justify-content: space-between; padding: 0 24px; }
.brand { display: flex; align-items: center; cursor: pointer; }
.brand-logo { width: 36px; height: 36px; border-radius: 10px; background: var(--brand-grad); color: #fff; font-weight: 800; font-size: 18px; display: flex; align-items: center; justify-content: center; margin-right: 10px; box-shadow: 0 6px 16px rgba(91,107,255,.32); }
.brand-name { font-size: 20px; font-weight: 800; letter-spacing: 1px; background: var(--brand-grad); -webkit-background-clip: text; background-clip: text; color: transparent; }
.nav-links { display: flex; gap: 30px; }
.nav-links a { cursor: pointer; color: var(--ink-2); font-size: 15px; font-weight: 500; transition: color .2s; }
.nav-links a:hover { color: var(--el-color-primary); }
.nav-actions { display: flex; align-items: center; gap: 8px; }

/* ===== Hero ===== */
.hero { position: relative; overflow: hidden; }
.hero-bg { position: absolute; inset: 0; background: linear-gradient(135deg,#2a2d5a 0%,#4f46b8 45%,#22c1c3 130%); }
.blob { position: absolute; border-radius: 50%; filter: blur(60px); opacity: .5; }
.b1 { width: 420px; height: 420px; background: #6d5efc; top: -120px; left: -60px; animation: float 12s ease-in-out infinite; }
.b2 { width: 380px; height: 380px; background: #22c1c3; bottom: -140px; right: 8%; animation: float 14s ease-in-out infinite reverse; }
.b3 { width: 300px; height: 300px; background: #a78bfa; top: 20%; right: -80px; animation: float 16s ease-in-out infinite; }
@keyframes float { 0%,100% { transform: translate(0,0) scale(1); } 50% { transform: translate(30px,-30px) scale(1.08); } }
.grid-overlay { position: absolute; inset: 0; background-image: linear-gradient(rgba(255,255,255,.06) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.06) 1px,transparent 1px); background-size: 44px 44px; mask-image: radial-gradient(circle at 50% 40%,#000,transparent 75%); }
.hero-content { position: relative; max-width: 1000px; margin: 0 auto; padding: 104px 24px 120px; text-align: center; color: #fff; }
.eyebrow { display: inline-block; padding: 6px 16px; border-radius: 999px; background: rgba(255,255,255,.16); backdrop-filter: blur(6px); font-size: 13px; letter-spacing: .5px; margin-bottom: 22px; border: 1px solid rgba(255,255,255,.22); }
.hero-title { font-size: 56px; margin: 0; letter-spacing: 2px; font-weight: 800; line-height: 1.15; }
.hero-title .hl { background: linear-gradient(90deg,#ffe27a,#7ef0d6); -webkit-background-clip: text; background-clip: text; color: transparent; }
.hero-sub { font-size: 18px; margin: 22px 0 36px; opacity: .94; line-height: 1.8; }
.hero-actions { display: flex; gap: 14px; justify-content: center; }
.hero-stats { display: flex; justify-content: center; gap: 20px; margin-top: 56px; flex-wrap: wrap; }
.stat { min-width: 120px; padding: 16px 24px; border-radius: 16px; background: rgba(255,255,255,.1); border: 1px solid rgba(255,255,255,.16); backdrop-filter: blur(6px); display: flex; flex-direction: column; }
.stat b { font-size: 30px; }
.stat span { font-size: 13px; opacity: .82; margin-top: 4px; }
.hero-wave { position: absolute; bottom: -1px; left: 0; right: 0; height: 70px; background: var(--surface); clip-path: ellipse(60% 100% at 50% 100%); }

/* ===== 数据条 ===== */
.strip { background: var(--surface); padding: 8px 24px 4px; }
.strip-inner { max-width: 1120px; margin: -40px auto 0; position: relative; z-index: 5; display: grid; grid-template-columns: repeat(auto-fit,minmax(220px,1fr)); gap: 16px; background: var(--surface); border: 1px solid var(--line-2); border-radius: 18px; padding: 24px 28px; box-shadow: 0 20px 44px rgba(24,32,64,.10); }
.strip-item { display: flex; flex-direction: column; gap: 4px; padding-left: 14px; border-left: 3px solid; border-image: var(--brand-grad) 1; }
.strip-item b { font-size: 16px; font-weight: 800; color: var(--ink); }
.strip-item span { font-size: 13px; color: var(--muted); }

/* ===== 通用区块标题 ===== */
.sec-title { text-align: center; font-size: 32px; margin: 0 0 10px; font-weight: 800; letter-spacing: .5px; }
.sec-sub { text-align: center; color: var(--muted); margin: 0 auto 48px; max-width: 640px; line-height: 1.7; }

/* ===== 模块深度介绍 ===== */
.showcase { max-width: 1180px; margin: 0 auto; padding: 84px 24px 20px; }
.feature-row { display: flex; align-items: center; gap: 56px; margin-bottom: 84px; }
.feature-row.reverse { flex-direction: row-reverse; }
.feature-text { flex: 1; min-width: 0; }
.f-badge { display: inline-flex; align-items: center; gap: 6px; color: #fff; font-weight: 700; font-size: 14px; padding: 6px 16px; border-radius: 999px; box-shadow: 0 8px 20px rgba(24,32,64,.14); }
.feature-text h3 { font-size: 28px; margin: 18px 0 12px; font-weight: 800; line-height: 1.35; }
.f-summary { color: var(--ink-2); font-size: 15px; line-height: 1.85; margin: 0 0 22px; }
.points { list-style: none; padding: 0; margin: 0 0 26px; display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.points li { display: flex; gap: 12px; align-items: flex-start; }
.p-ic { flex: none; width: 38px; height: 38px; border-radius: 11px; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 18px; box-shadow: 0 6px 14px rgba(24,32,64,.14); }
.p-body { display: flex; flex-direction: column; }
.p-body b { font-size: 15px; font-weight: 700; color: var(--ink); }
.p-body i { font-style: normal; font-size: 13px; color: var(--muted); line-height: 1.55; margin-top: 2px; }

/* 界面模拟窗 */
.feature-vis { flex: 1; min-width: 0; display: flex; justify-content: center; }
.window { width: 100%; max-width: 420px; background: var(--surface); border-radius: 18px; box-shadow: 0 24px 60px rgba(24,32,64,.16); border: 1px solid var(--line-2); overflow: hidden; transition: transform .3s, box-shadow .3s; }
.window:hover { transform: translateY(-6px); box-shadow: 0 32px 70px rgba(24,32,64,.22); }
.win-bar { display: flex; align-items: center; gap: 7px; padding: 13px 16px; background: var(--panel-2); border-bottom: 1px solid var(--line-2); }
.win-bar i { width: 11px; height: 11px; border-radius: 50%; background: #dfe3ec; }
.win-bar i:nth-child(1) { background: #ff6b6b; }
.win-bar i:nth-child(2) { background: #f5c04e; }
.win-bar i:nth-child(3) { background: #4ecb71; }
.win-bar span { margin-left: 8px; font-size: 12px; color: var(--muted); font-weight: 600; }
.win-body { padding: 22px; min-height: 260px; }

/* exam mock */
.exam { position: relative; }
.mock-timer { position: absolute; top: 16px; right: 16px; background: var(--accent); color: #fff; font-size: 12px; font-weight: 700; padding: 4px 12px; border-radius: 999px; }
.mq-title { font-size: 15px; font-weight: 700; margin-bottom: 14px; }
.mq-opt { padding: 11px 14px; border: 1px solid var(--line-2); border-radius: 10px; margin-bottom: 9px; font-size: 13px; color: var(--ink-2); background: var(--surface-2); }
.mq-opt.sel { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 10%, var(--surface)); color: var(--accent); font-weight: 700; }
.mock-foot { display: flex; justify-content: space-between; margin-top: 14px; font-size: 12px; }
.mock-foot span { color: #21a95a; }
.mock-foot em { font-style: normal; color: var(--muted); }

/* competition mock */
.comp { display: flex; flex-direction: column; align-items: center; gap: 16px; }
.vs { display: flex; align-items: center; gap: 26px; width: 100%; justify-content: center; }
.vs-side { text-align: center; }
.ava { width: 58px; height: 58px; border-radius: 50%; background: var(--brand-grad); color: #fff; font-weight: 800; display: flex; align-items: center; justify-content: center; margin: 0 auto 6px; }
.ava.b { background: linear-gradient(135deg,#f0993a,#f5c04e); }
.vs-name { font-size: 13px; color: var(--ink-2); font-weight: 600; }
.vs-mid { font-size: 26px; font-weight: 900; color: #c3cbdf; }
.vs-score { font-size: 26px; font-weight: 900; color: var(--ink); display: flex; flex-direction: column; align-items: center; gap: 2px; }
.vs-score b { color: var(--accent); }
.vs-score span { font-size: 12px; color: var(--muted); font-weight: 500; }
.badge-row { background: color-mix(in srgb, var(--accent) 12%, var(--surface)); color: var(--accent); font-weight: 700; font-size: 13px; padding: 8px 18px; border-radius: 999px; }

/* bounty mock */
.b-card { border: 1px solid var(--line-2); border-radius: 12px; padding: 14px 16px; margin-bottom: 12px; background: var(--surface-2); }
.b-card.mini { padding: 12px 16px; }
.b-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.b-tag { font-size: 11px; font-weight: 700; color: var(--accent); background: color-mix(in srgb, var(--accent) 12%, var(--surface)); padding: 3px 10px; border-radius: 999px; }
.b-tag.g { color: #4f7cff; background: #eaf0ff; }
.b-rew { font-size: 14px; font-weight: 800; color: #e08a1e; }
.b-title { font-size: 14px; font-weight: 600; color: var(--ink); }
.b-bar { height: 6px; border-radius: 4px; background: var(--line-2); margin: 10px 0 6px; overflow: hidden; }
.b-bar i { display: block; height: 100%; background: var(--accent); border-radius: 4px; }
.b-meta { font-size: 12px; color: var(--muted); }

/* learning mock */
.l-item { display: flex; justify-content: space-between; align-items: center; padding: 12px 14px; border: 1px solid var(--line-2); border-radius: 10px; margin-bottom: 10px; background: var(--surface-2); font-size: 14px; }
.l-item b { color: var(--accent); }
.l-prog { margin-top: 14px; }
.lp-label { font-size: 13px; color: var(--ink-2); margin-bottom: 6px; font-weight: 600; }
.l-bar { height: 8px; border-radius: 5px; background: var(--line-2); overflow: hidden; }
.l-bar i { display: block; height: 100%; background: var(--accent); border-radius: 5px; }

/* ===== 特性 ===== */
.features { max-width: 1180px; margin: 0 auto; padding: 40px 24px 84px; }
.features .el-row { width: 100%; }
.feat { display: flex; gap: 16px; padding: 26px 24px; border: 1px solid var(--line-2); border-radius: 16px; margin-bottom: 22px; background: var(--surface); transition: transform .25s, box-shadow .25s; height: calc(100% - 22px); }
.feat:hover { transform: translateY(-6px); box-shadow: 0 18px 40px rgba(24,32,64,.12); }
.feat-icon { flex: none; width: 52px; height: 52px; border-radius: 14px; color: #fff; font-size: 26px; display: flex; align-items: center; justify-content: center; box-shadow: 0 10px 22px rgba(24,32,64,.16); }
.feat-body h4 { margin: 4px 0 8px; font-size: 17px; font-weight: 800; }
.feat-body p { margin: 0; color: var(--muted); font-size: 13.5px; line-height: 1.7; }

/* ===== 使用流程 ===== */
.flow { background: linear-gradient(180deg, var(--canvas), var(--surface)); padding: 76px 24px; }
.steps { max-width: 1080px; margin: 0 auto; display: grid; grid-template-columns: repeat(4,1fr); gap: 20px; }
.step { position: relative; text-align: center; padding: 30px 18px; background: var(--surface); border: 1px solid var(--line-2); border-radius: 18px; box-shadow: 0 10px 26px rgba(24,32,64,.06); }
.step-no { position: absolute; top: 16px; left: 16px; width: 26px; height: 26px; border-radius: 50%; background: var(--brand-grad); color: #fff; font-size: 13px; font-weight: 800; display: flex; align-items: center; justify-content: center; }
.step-ic { font-size: 40px; margin: 8px 0 12px; }
.step h4 { margin: 0 0 6px; font-size: 17px; font-weight: 800; }
.step p { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.6; }
.step-arrow { position: absolute; right: -18px; top: 50%; transform: translateY(-50%); color: #c3cbdf; font-size: 20px; z-index: 2; }

/* ===== 角色分区 ===== */
.roles { max-width: 1080px; margin: 0 auto; padding: 76px 24px; display: grid; grid-template-columns: 1fr 1fr; gap: 28px; }
.role-card { border-radius: 22px; padding: 36px 34px; color: #fff; box-shadow: 0 20px 46px rgba(24,32,64,.16); position: relative; overflow: hidden; }
.role-card.teacher { background: linear-gradient(135deg,#6d5efc,#4f7cff); }
.role-card.student { background: linear-gradient(135deg,#0ea5b7,#22b573); }
.role-card::after { content: ''; position: absolute; right: -50px; bottom: -50px; width: 180px; height: 180px; border-radius: 50%; background: rgba(255,255,255,.12); }
.role-head { display: flex; align-items: center; gap: 12px; }
.role-ic { font-size: 40px; }
.role-head h3 { margin: 0; font-size: 26px; font-weight: 800; }
.role-tag { margin: 6px 0 20px; opacity: .9; font-size: 14px; }
.role-card ul { list-style: none; padding: 0; margin: 0; position: relative; z-index: 1; }
.role-card li { padding: 9px 0 9px 28px; position: relative; font-size: 14.5px; line-height: 1.5; border-bottom: 1px solid rgba(255,255,255,.14); }
.role-card li::before { content: '✓'; position: absolute; left: 0; font-weight: 800; }
.role-card li:last-child { border-bottom: none; }

/* ===== CTA ===== */
.cta { padding: 20px 24px 84px; }
.cta-inner { max-width: 1000px; margin: 0 auto; text-align: center; padding: 60px 40px; border-radius: 26px; background: linear-gradient(135deg,#2a2d5a,#4f46b8 55%,#4f7cff); color: #fff; box-shadow: 0 24px 60px rgba(79,70,184,.32); position: relative; overflow: hidden; }
.cta-inner::before { content: ''; position: absolute; width: 300px; height: 300px; border-radius: 50%; background: radial-gradient(circle,rgba(34,193,195,.5),transparent 70%); top: -120px; right: -60px; }
.cta-inner h2 { font-size: 32px; margin: 0 0 12px; font-weight: 800; position: relative; }
.cta-inner p { margin: 0 0 28px; opacity: .92; font-size: 16px; position: relative; }
.cta-inner .el-button { position: relative; }

/* ===== 页脚 ===== */
.footer { background: #1a1e30; color: #c3cbdf; }
.footer-inner { max-width: 1180px; margin: 0 auto; padding: 56px 24px 32px; display: grid; grid-template-columns: 2fr 1fr 1fr 1fr; gap: 32px; }
.brand-col .brand-name { color: #fff; -webkit-text-fill-color: #fff; }
.brand-col p { margin: 14px 0 0; font-size: 13.5px; line-height: 1.7; color: var(--muted); max-width: 280px; }
.f-col h5 { color: #fff; font-size: 15px; margin: 4px 0 16px; font-weight: 700; }
.f-col a { display: block; cursor: pointer; color: var(--muted); font-size: 14px; margin-bottom: 12px; transition: color .2s; }
.f-col a:hover { color: #fff; }
.footer-bottom { border-top: 1px solid rgba(255,255,255,.08); text-align: center; padding: 22px; font-size: 13px; color: #6b7488; }

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .feature-row, .feature-row.reverse { flex-direction: column; gap: 30px; }
  .points { grid-template-columns: 1fr; }
  .steps { grid-template-columns: 1fr 1fr; }
  .step-arrow { display: none; }
  .roles { grid-template-columns: 1fr; }
  .footer-inner { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 768px) {
  .nav-links { display: none; }
  .hero-title { font-size: 34px; }
  .hero-content { padding: 72px 20px 96px; }
  .steps { grid-template-columns: 1fr; }
  .footer-inner { grid-template-columns: 1fr; }
}
</style>
