import api from './index'

// 统一取信封 data；清洗查询参数（去掉 null/undefined/空串，空串枚举会导致 400）
const dataOf = p => p.then(r => r.data.data)
const cleanParams = obj => {
  const out = {}
  if (!obj) return out
  for (const [k, v] of Object.entries(obj)) {
    if (v !== null && v !== undefined && v !== '') out[k] = v
  }
  return out
}

/* ================= 认证 ================= */
export const authAPI = {
  login: data => api.post('/auth/login', data).then(r => r.data.data),
  register: data => api.post('/auth/register', data).then(r => r.data.data),
  // 后端无 logout 端点，JWT 由前端清除即可
  logout: () => Promise.resolve(),
  me: () => api.get('/auth/me').then(r => r.data.data)
}

/* ================= 字典：院系 / 专业 / 课程 / 章节 ================= */
export const dictAPI = {
  questionTypes: () => dataOf(api.get('/question-types')),
  difficulties: () => dataOf(api.get('/difficulties')),
  departments: () => dataOf(api.get('/departments')),
  majors: (departmentId) =>
    dataOf(api.get('/majors', { params: cleanParams({ departmentId }) })),
  courses: majorId =>
    dataOf(api.get('/courses', { params: cleanParams({ majorId }) })),
  chapters: courseId =>
    dataOf(api.get('/chapters', { params: { courseId } }))
}

/* ================= 学科（专业） ================= */
export const majorAPI = {
  list: () => dataOf(api.get('/majors')),
  get: id => dataOf(api.get('/majors/' + id)),
  create: data => dataOf(api.post('/majors', data)),
  update: (id, data) => dataOf(api.put('/majors/' + id, data)),
  remove: id => dataOf(api.delete('/majors/' + id))
}

/* ================= 课程 / 章节 CRUD（供后续页面使用） ================= */
export const courseAPI = {
  list: majorId => dataOf(api.get('/courses', { params: cleanParams({ majorId }) })),
  create: data => dataOf(api.post('/courses', data)),
  update: (id, data) => dataOf(api.put('/courses/' + id, data)),
  remove: id => dataOf(api.delete('/courses/' + id))
}

export const chapterAPI = {
  list: courseId => dataOf(api.get('/chapters', { params: { courseId } })),
  create: data => dataOf(api.post('/chapters', data)),
  update: (id, data) => dataOf(api.put('/chapters/' + id, data)),
  remove: id => dataOf(api.delete('/chapters/' + id))
}

/* ================= 题目 ================= */
export const questionAPI = {
  // 返回当前页数组（后端为 Spring Page）
  list: (params = {}) =>
    dataOf(api.get('/questions', {
      params: cleanParams({ size: 1000, ...params })
    })).then(p => p?.content || []),
  // 返回完整分页对象 { content, totalElements, totalPages, ... }
  listPage: (params = {}) =>
    dataOf(api.get('/questions', { params: cleanParams(params) })),
  count: () =>
    dataOf(api.get('/questions', { params: { page: 0, size: 1 } }))
      .then(p => p?.totalElements || 0),
  get: id => dataOf(api.get('/questions/' + id)),
  create: data => dataOf(api.post('/questions', data)),
  update: (id, data) => dataOf(api.put('/questions/' + id, data)),
  remove: id => dataOf(api.delete('/questions/' + id)),
  togglePublic: id => dataOf(api.post(`/questions/${id}/toggle_public`)),
  // AI 出题，返回 AiGeneratedQuestion 数组
  generate: data =>
    dataOf(api.post('/ai/generate', data)).then(r => r?.questions || [])
}

/* ================= 学生 / 考试（教师视角） ================= */
export const studentAPI = {
  list: () => dataOf(api.get('/students'))
}

export const examAPI = {
  list: () => dataOf(api.get('/exams')),
  get: id => dataOf(api.get('/exams/' + id)),
  create: data => dataOf(api.post('/exams', data)),
  update: (id, data) => dataOf(api.put('/exams/' + id, data)),
  remove: id => dataOf(api.delete('/exams/' + id)),
  publish: id => dataOf(api.post(`/exams/${id}/publish`)),
  end: id => dataOf(api.post(`/exams/${id}/end`)),
  // 题目
  examQuestions: id => dataOf(api.get(`/exams/${id}/questions`)),
  addQuestions: (id, questionIds) =>
    dataOf(api.post(`/exams/${id}/add_questions`, { questionIds })),
  removeQuestion: (id, questionId) =>
    dataOf(api.post(`/exams/${id}/remove_question/${questionId}`)),
  // 考生
  examStudents: id => dataOf(api.get(`/exams/${id}/students`)),
  invite: (id, studentIds) =>
    dataOf(api.post(`/exams/${id}/invite`, { studentIds })),
  removeStudent: (id, studentId) =>
    dataOf(api.post(`/exams/${id}/remove_student/${studentId}`)),
  // 邀请码 / 智能组卷
  invitation: id => dataOf(api.post(`/exams/${id}/generate_invitation`)),
  smartComposition: (id, data) =>
    dataOf(api.post(`/exams/${id}/smart_composition`, data)),
  // 学生输入邀请码加入
  joinByCode: code => dataOf(api.post(`/exam/join/${encodeURIComponent(code)}`))
}

/* ================= 学生考试流程 ================= */
export const takeAPI = {
  start: examId => dataOf(api.post(`/exam/${examId}/start`)),
  take: examId => dataOf(api.get(`/exam/${examId}/take`)),
  saveAnswer: (examId, questionId, studentAnswer) =>
    dataOf(api.post(`/exam/${examId}/save_answer`, { questionId, studentAnswer })),
  submit: examId => dataOf(api.post(`/exam/${examId}/submit`)),
  reportSwitch: examId => dataOf(api.post(`/exam/${examId}/report_switch`)),
  report: examId => dataOf(api.get(`/exam/${examId}/report`))
}

/* ================= 成绩 / 阅卷 / 分析 ================= */
export const resultAPI = {
  myResults: () => dataOf(api.get('/results/me')),
  examResults: examId => dataOf(api.get(`/results/exam/${examId}`)),
  get: resultId => dataOf(api.get('/results/' + resultId)),
  gradingList: examId => dataOf(api.get(`/results/grading/${examId}`)),
  gradingDetail: (examId, studentId) =>
    dataOf(api.get(`/results/grading/${examId}/${studentId}`)),
  manualGrade: (examId, studentId, data) =>
    dataOf(api.post(`/results/grading/${examId}/${studentId}`, data)),
  analysis: examId => dataOf(api.get(`/results/analysis/${examId}`))
}

/* ================= 通知 / 日志 / 上传（当前页面未使用，预留） ================= */
export const notificationAPI = {
  list: () => dataOf(api.get('/notifications')),
  unread: () => dataOf(api.get('/notifications/unread')),
  read: id => dataOf(api.put(`/notifications/${id}/read`)),
  readAll: () => dataOf(api.put('/notifications/read_all')),
  remove: id => dataOf(api.delete('/notifications/' + id)),
  clearAll: () => dataOf(api.delete('/notifications/clear_all'))
}

/* ================= 自主学习（v4.0，师生同权） ================= */
export const learningAPI = {
  // 错题本
  wrongRecords: (params = {}) =>
    dataOf(api.get('/learning/wrong-records', { params: cleanParams(params) })),
  masterWrongRecord: id => dataOf(api.post(`/learning/wrong-records/${id}/master`)),
  deleteWrongRecord: id => dataOf(api.delete('/learning/wrong-records/' + id)),
  // 自由刷题
  practiceStart: data => dataOf(api.post('/learning/practice/start', data)),
  practiceDetail: id => dataOf(api.get('/learning/practice/' + id)),
  practiceAnswer: (id, data) => dataOf(api.post(`/learning/practice/${id}/answer`, data)),
  practiceSubmit: id => dataOf(api.post(`/learning/practice/${id}/submit`)),
  practiceDelete: id => dataOf(api.delete('/learning/practice/' + id)),
  practiceHistory: () => dataOf(api.get('/learning/practice/history')),
  // 学习计划
  plans: (params = {}) =>
    dataOf(api.get('/learning/plans', { params: cleanParams(params) })),
  createPlan: data => dataOf(api.post('/learning/plans', data)),
  getPlan: id => dataOf(api.get('/learning/plans/' + id)),
  updatePlan: (id, data) => dataOf(api.put('/learning/plans/' + id, data)),
  togglePausePlan: id => dataOf(api.post(`/learning/plans/${id}/pause`)),
  deletePlan: id => dataOf(api.delete('/learning/plans/' + id)),
  // 学习报告
  reportOverview: () => dataOf(api.get('/learning/report/overview')),
  reportDaily: days =>
    dataOf(api.get('/learning/report/daily', { params: cleanParams({ days }) })),
  // AI 答疑
  ask: data => dataOf(api.post('/learning/ask', data))
}

/* ================= 竞赛：管理（教师） ================= */
export const competitionAdminAPI = {
  list: () => dataOf(api.get('/competitions')),
  create: data => dataOf(api.post('/competitions', data)),
  detail: id => dataOf(api.get('/competitions/' + id)),
  update: (id, data) => dataOf(api.put('/competitions/' + id, data)),
  remove: id => dataOf(api.delete('/competitions/' + id)),
  addQuestions: (id, questionIds) =>
    dataOf(api.post(`/competitions/${id}/questions`, { questionIds })),
  removeQuestion: (id, cqId) => dataOf(api.delete(`/competitions/${id}/questions/${cqId}`)),
  publish: id => dataOf(api.post(`/competitions/${id}/publish`)),
  end: id => dataOf(api.post(`/competitions/${id}/end`)),
  participants: id => dataOf(api.get(`/competitions/${id}/participants`))
}

/* ================= 竞赛：限时赛参赛（学生） ================= */
export const competitionAPI = {
  lobby: () => dataOf(api.get('/competitions/lobby')),
  leaderboard: id => dataOf(api.get(`/competitions/${id}/leaderboard`)),
  join: id => dataOf(api.post(`/competitions/${id}/join`)),
  start: id => dataOf(api.post(`/competitions/${id}/start`)),
  play: id => dataOf(api.get(`/competitions/${id}/play`)),
  answer: (id, data) => dataOf(api.post(`/competitions/${id}/answer`, data)),
  finish: id => dataOf(api.post(`/competitions/${id}/finish`))
}

/* ================= 竞赛：1v1 PK（学生） ================= */
export const pkAPI = {
  create: (competitionId, data) => dataOf(api.post(`/competitions/${competitionId}/pk`, data || {})),
  lobby: () => dataOf(api.get('/pk/lobby')),
  accept: battleId => dataOf(api.post(`/pk/${battleId}/accept`)),
  state: battleId => dataOf(api.get(`/pk/${battleId}/state`)),
  answer: (battleId, data) => dataOf(api.post(`/pk/${battleId}/answer`, data)),
  finish: battleId => dataOf(api.post(`/pk/${battleId}/finish`))
}

/* ================= 竞技化：段位 / 勋章 / 画像 / 战队 ================= */
export const gamificationAPI = {
  rankMe: () => dataOf(api.get('/rank/me')),
  seasons: () => dataOf(api.get('/rank/seasons')),
  seasonRank: season => dataOf(api.get('/rank/season/' + season)),
  archives: () => dataOf(api.get('/rank/archives')),
  badges: () => dataOf(api.get('/badges/me')),
  myProfile: () => dataOf(api.get('/profile/study')),
  studentProfile: id => dataOf(api.get('/profile/study/' + id))
}

export const teamAPI = {
  list: () => dataOf(api.get('/teams')),
  create: data => dataOf(api.post('/teams', data)),
  mine: () => dataOf(api.get('/teams/mine')),
  join: id => dataOf(api.post(`/teams/${id}/join`)),
  leave: id => dataOf(api.post(`/teams/${id}/leave`)),
  transfer: (id, userId) => dataOf(api.post(`/teams/${id}/transfer`, { userId })),
  disband: id => dataOf(api.delete('/teams/' + id))
}

/* ================= 征集悬赏（师生同权，v4.0） ================= */
export const bountyAPI = {
  plaza: params => dataOf(api.get('/bounties', { params: cleanParams(params) })),
  publish: data => dataOf(api.post('/bounties', data)),
  mine: () => dataOf(api.get('/bounties/mine')),
  mySubmissions: () => dataOf(api.get('/bounties/my-submissions')),
  detail: id => dataOf(api.get('/bounties/' + id)),
  submissions: id => dataOf(api.get(`/bounties/${id}/submissions`)),
  submit: (id, data) => dataOf(api.post(`/bounties/${id}/submissions`, data)),
  accept: sid => dataOf(api.post(`/submissions/${sid}/accept`)),
  reject: (sid, reviewComment) => dataOf(api.post(`/submissions/${sid}/reject`, { reviewComment }))
}

export const logAPI = {
  report: data => api.post('/logs', data),
  reportVue: data => api.post('/logs/vue', data)
}

export const uploadAPI = {
  upload: formData =>
    dataOf(api.post('/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }))
}
