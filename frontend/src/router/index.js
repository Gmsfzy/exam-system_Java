import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

// meta 说明：
//   public  - 未登录也可访问（首页 / 登录 / 注册）
//   bare    - 不套用模块工作区侧边栏外壳（首页 / 登录 / 门户中枢）
//   auth    - 仅要求登录（师生同权）
//   role    - 限定角色
//   module  - 所属模块分组：exam / learning / competition / bounty（决定专属侧边栏）
const routes = [
  // ① 公开首页（落地页）
  { path: '/', component: () => import('../views/Home.vue'), meta: { public: true, bare: true } },
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true, bare: true } },
  { path: '/register', component: () => import('../views/Login.vue'), meta: { public: true, bare: true } },
  // ② 登录后：四模块中枢门户
  { path: '/portal', component: () => import('../views/Portal.vue'), meta: { auth: true, bare: true } },

  // ③ 模块一：在线考试
  { path: '/dashboard', component: () => import('../views/Dashboard.vue'), meta: { role: 'teacher', module: 'exam' } },
  { path: '/exams', component: () => import('../views/Exams.vue'), meta: { role: 'teacher', module: 'exam' } },
  { path: '/exams/:id/manage', component: () => import('../views/ExamManage.vue'), meta: { role: 'teacher', module: 'exam' } },
  { path: '/questions', component: () => import('../views/Questions.vue'), meta: { role: 'teacher', module: 'exam' } },
  { path: '/majors', component: () => import('../views/Majors.vue'), meta: { role: 'teacher', module: 'exam' } },
  { path: '/grading', component: () => import('../views/Grading.vue'), meta: { role: 'teacher', module: 'exam' } },
  { path: '/results', component: () => import('../views/Results.vue'), meta: { role: 'teacher', module: 'exam' } },
  { path: '/results/:id', component: () => import('../views/ResultDetail.vue'), meta: { role: 'teacher', module: 'exam' } },
  { path: '/my-exams', component: () => import('../views/MyExams.vue'), meta: { role: 'student', module: 'exam' } },
  { path: '/take/:examId', component: () => import('../views/TakeExam.vue'), meta: { role: 'student', module: 'exam' } },
  { path: '/my-results', component: () => import('../views/MyResults.vue'), meta: { role: 'student', module: 'exam' } },
  { path: '/my-results/:id', component: () => import('../views/ResultDetail.vue'), meta: { role: 'student', module: 'exam' } },

  // 模块二：自主学习（师生同权）
  { path: '/learning', component: () => import('../views/learning/LearningHome.vue'), meta: { auth: true, module: 'learning' } },
  { path: '/learning/wrong-notebook', component: () => import('../views/learning/WrongNotebook.vue'), meta: { auth: true, module: 'learning' } },
  { path: '/learning/practice', component: () => import('../views/learning/PracticeList.vue'), meta: { auth: true, module: 'learning' } },
  { path: '/learning/practice/:id', component: () => import('../views/learning/PracticeDetail.vue'), meta: { auth: true, module: 'learning' } },
  { path: '/learning/plans', component: () => import('../views/learning/StudyPlan.vue'), meta: { auth: true, module: 'learning' } },
  { path: '/learning/report', component: () => import('../views/learning/StudyReport.vue'), meta: { auth: true, module: 'learning' } },

  // 模块三：答题竞赛 + 竞技化
  { path: '/competition', component: () => import('../views/competition/CompetitionHome.vue'), meta: { auth: true, module: 'competition' } },
  { path: '/competition/plaza', component: () => import('../views/competition/CompetitionPlaza.vue'), meta: { auth: true, module: 'competition' } },
  { path: '/competition/manage', component: () => import('../views/competition/CompetitionManage.vue'), meta: { role: 'teacher', module: 'competition' } },
  { path: '/competition/manage/:id', component: () => import('../views/competition/CompetitionManage.vue'), meta: { role: 'teacher', module: 'competition' } },
  { path: '/competition/take/:id', component: () => import('../views/competition/CompetitionTake.vue'), meta: { role: 'student', module: 'competition' } },
  { path: '/competition/:id/leaderboard', component: () => import('../views/competition/CompetitionLeaderboard.vue'), meta: { auth: true, module: 'competition' } },
  { path: '/competition/pk', component: () => import('../views/competition/PkLobby.vue'), meta: { role: 'student', module: 'competition' } },
  { path: '/competition/pk/:battleId', component: () => import('../views/competition/PkTake.vue'), meta: { role: 'student', module: 'competition' } },
  { path: '/rank', component: () => import('../views/competition/RankCenter.vue'), meta: { auth: true, module: 'competition' } },
  { path: '/team', component: () => import('../views/competition/TeamCenter.vue'), meta: { auth: true, module: 'competition' } },
  { path: '/profile/study', component: () => import('../views/competition/StudyProfile.vue'), meta: { auth: true, module: 'competition' } },

  // 模块四：征集悬赏（师生同权）
  { path: '/bounty', component: () => import('../views/bounty/BountyPlaza.vue'), meta: { auth: true, module: 'bounty' } },
  { path: '/bounty/publish', component: () => import('../views/bounty/BountyPublish.vue'), meta: { auth: true, module: 'bounty' } },
  { path: '/bounty/mine', component: () => import('../views/bounty/BountyMine.vue'), meta: { auth: true, module: 'bounty' } },
  { path: '/bounty/:id', component: () => import('../views/bounty/BountyDetail.vue'), meta: { auth: true, module: 'bounty' } },

  { path: '/:pathMatch(.*)*', redirect: '/portal' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  const auth = useAuthStore()

  // 公开页：已登录用户访问首页/登录/注册时，直接带入门户
  if (to.meta.public) {
    if (auth.isLoggedIn && (to.path === '/' || to.path === '/login' || to.path === '/register')) {
      next('/portal'); return
    }
    next(); return
  }

  // 需登录
  if (!auth.isLoggedIn) { next('/login'); return }

  // 角色不符：回门户重新选模块
  if (to.meta.role && auth.currentUser?.role !== to.meta.role) {
    next('/portal'); return
  }
  next()
})

export default router
