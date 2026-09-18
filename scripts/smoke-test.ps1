# ============================================================
# 考试系统 API 冒烟测试（PowerShell，无需 Postman）
# 用法:
#   .\scripts\smoke-test.ps1
#   .\scripts\smoke-test.ps1 -BaseUrl http://localhost:8080
# 前置: 后端已启动且已执行种子数据（teacher/student1 账号）
# ============================================================
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Continue"
$script:pass = 0
$script:fail = 0
$script:results = @()

function Get-Token([string]$username, [string]$password) {
    $body = @{ username = $username; password = $password } | ConvertTo-Json -Compress
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 10
    return $r.data.token
}

function Invoke-Api {
    param(
        [string]$Name,
        [string]$Method = "GET",
        [string]$Path,
        [string]$Token,
        [object]$Body,
        [int[]]$Accept = @(200, 201)
    )
    $url = "$BaseUrl$Path"
    try {
        $params = @{ Uri = $url; Method = $Method; TimeoutSec = 15 }
        if ($Token) { $params.Headers = @{ Authorization = "Bearer $Token" } }
        if ($null -ne $Body) {
            $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
            $params.ContentType = "application/json; charset=utf-8"
        }
        $resp = Invoke-WebRequest @params -UseBasicParsing
        $code = [int]$resp.StatusCode
        $ok = $Accept -contains $code
        $payload = $resp.Content
    } catch {
        $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode.value__ } else { 0 }
        $ok = $Accept -contains $code
        $payload = $_.Exception.Message
    }
    if ($ok) { $script:pass++ } else { $script:fail++ }
    $script:results += [pscustomobject]@{ Name = $Name; Method = $Method; Path = $Path; Status = $code; Ok = $ok }
    $tag = if ($ok) { "PASS" } else { "FAIL" }
    Write-Host ("[{0}] {1} {2,-6} {3,-45} -> {4}" -f $tag, $Method, "", $Path, $code) -ForegroundColor $(if ($ok) { "Green" } else { "Red" })
    # 返回响应 JSON（成功时）供后续步骤取 id
    if ($ok -and $payload -and $payload.StartsWith("{")) {
        try { return ($payload | ConvertFrom-Json) } catch { return $null }
    }
    return $null
}

Write-Host "===== 考试系统 API 冒烟测试: $BaseUrl =====" -ForegroundColor Cyan

# 0. 登录拿 token
try {
    $teacher = Get-Token "teacher" "123123"
    $student = Get-Token "student1" "123123"
    Write-Host "[PASS] 获取 teacher / student1 token" -ForegroundColor Green
    $script:pass += 2
} catch {
    Write-Host "[FATAL] 登录失败，请确认后端已启动并完成种子数据: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 1. 认证
Invoke-Api -Name "当前用户me" -Path "/api/auth/me" -Token $teacher | Out-Null

# 2. 公共字典
Invoke-Api -Name "题型字典" -Path "/api/question-types" | Out-Null
Invoke-Api -Name "难度字典" -Path "/api/difficulties" | Out-Null

# 3. 院系/专业/课程/章节
Invoke-Api -Name "院系列表" -Path "/api/departments" -Token $teacher | Out-Null
Invoke-Api -Name "专业列表" -Path "/api/majors?departmentId=1" -Token $teacher | Out-Null
Invoke-Api -Name "专业详情" -Path "/api/majors/1" -Token $teacher | Out-Null
Invoke-Api -Name "课程列表" -Path "/api/courses?majorId=1" -Token $teacher | Out-Null
Invoke-Api -Name "章节列表" -Path "/api/chapters?courseId=1" -Token $teacher | Out-Null
Invoke-Api -Name "章节详情" -Path "/api/chapters/1" -Token $teacher | Out-Null

# 4. 题库
Invoke-Api -Name "题目分页" -Path "/api/questions?page=0&size=10" -Token $teacher | Out-Null
Invoke-Api -Name "题目详情" -Path "/api/questions/1" -Token $teacher | Out-Null
$newQ = Invoke-Api -Name "新建题目" -Method POST -Path "/api/questions" -Token $teacher -Body @{
    content = "冒烟测试-栈的特点"; options = @("A. FIFO","B. LIFO"); answer = "B"
    analysis = "LIFO"; knowledge = "栈"; type = "single_choice"; difficulty = "easy"
    isPublic = $true; majorId = 1; courseId = 1; chapterId = 2
}
$qid = $newQ.data.id
if ($qid) {
    Invoke-Api -Name "更新题目" -Method PUT -Path "/api/questions/$qid" -Token $teacher -Body @{
        content = "冒烟测试-队列特点"; options = @("A. FIFO","B. LIFO"); answer = "A"
        type = "single_choice"; difficulty = "easy"; isPublic = $false
        majorId = 1; courseId = 1; chapterId = 2
    } | Out-Null
    Invoke-Api -Name "切换公开" -Method POST -Path "/api/questions/$qid/toggle_public" -Token $teacher | Out-Null
}

# 5. 考试管理（教师）
$newE = Invoke-Api -Name "创建考试" -Method POST -Path "/api/exams" -Token $teacher -Body @{
    title = "冒烟测试考试"; description = "smoke"; duration = 60
    startTime = "2026-09-20 09:00:00"; endTime = "2026-09-20 11:00:00"
}
$eid = $newE.data.id
Invoke-Api -Name "考试列表" -Path "/api/exams" -Token $teacher | Out-Null
if ($eid) {
    Invoke-Api -Name "考试详情" -Path "/api/exams/$eid" -Token $teacher | Out-Null
    Invoke-Api -Name "更新考试" -Method PUT -Path "/api/exams/$eid" -Token $teacher -Body @{
        title = "冒烟测试考试-改"; duration = 90
        startTime = "2026-09-20 09:00:00"; endTime = "2026-09-20 11:00:00"
    } | Out-Null
    Invoke-Api -Name "添加题目" -Method POST -Path "/api/exams/$eid/add_questions" -Token $teacher -Body @{ questionIds = @(1, 2) } | Out-Null
    Invoke-Api -Name "考试题目列表" -Path "/api/exams/$eid/questions" -Token $teacher | Out-Null
    Invoke-Api -Name "移除题目" -Method POST -Path "/api/exams/$eid/remove_question/2" -Token $teacher | Out-Null
    Invoke-Api -Name "生成邀请" -Method POST -Path "/api/exams/$eid/generate_invitation" -Token $teacher | Out-Null
    Invoke-Api -Name "邀请学生" -Method POST -Path "/api/exams/$eid/invite" -Token $teacher -Body @{ studentIds = @(3) } | Out-Null
    Invoke-Api -Name "考试学生列表" -Path "/api/exams/$eid/students" -Token $teacher | Out-Null
}
Invoke-Api -Name "种子考试题目" -Path "/api/exams/1/questions" -Token $teacher | Out-Null
Invoke-Api -Name "种子考试学生" -Path "/api/exams/1/students" -Token $teacher | Out-Null

# 6. 学生侧
Invoke-Api -Name "全部学生(教师)" -Path "/api/students" -Token $teacher | Out-Null
Invoke-Api -Name "学生-我的考试" -Path "/api/exams" -Token $student | Out-Null
Invoke-Api -Name "学生-邀请码加入" -Method POST -Path "/api/exam/join/TEST1234" -Token $student -Accept @(200, 400, 409) | Out-Null
Invoke-Api -Name "学生-开始考试" -Method POST -Path "/api/exam/1/start" -Token $student -Accept @(200, 400) | Out-Null
Invoke-Api -Name "学生-拉取试卷" -Path "/api/exam/1/take" -Token $student -Accept @(200, 400) | Out-Null
Invoke-Api -Name "学生-保存答案" -Method POST -Path "/api/exam/1/save_answer" -Token $student -Accept @(200, 400) -Body @{
    questionId = 1; studentAnswer = "A"
} | Out-Null
Invoke-Api -Name "学生-切屏上报" -Method POST -Path "/api/exam/1/report_switch" -Token $student -Accept @(200, 400) | Out-Null
Invoke-Api -Name "学生-交卷" -Method POST -Path "/api/exam/1/submit" -Token $student -Accept @(200, 400) | Out-Null
Invoke-Api -Name "学生-考试报告" -Path "/api/exam/1/report" -Token $student -Accept @(200, 400, 404) | Out-Null

# 7. 成绩（允许 400/404：种子考试未必已交卷）
Invoke-Api -Name "学生-我的成绩" -Path "/api/results/me" -Token $student | Out-Null
Invoke-Api -Name "教师-考试成绩" -Path "/api/results/exam/1" -Token $teacher -Accept @(200, 400, 403) | Out-Null
Invoke-Api -Name "教师-待批改列表" -Path "/api/results/grading/1" -Token $teacher -Accept @(200, 400) | Out-Null
Invoke-Api -Name "教师-成绩分析" -Path "/api/results/analysis/1" -Token $teacher -Accept @(200, 400) | Out-Null

# 8. 通知
Invoke-Api -Name "通知列表" -Path "/api/notifications" -Token $student | Out-Null
Invoke-Api -Name "通知未读数" -Path "/api/notifications/unread" -Token $student | Out-Null
Invoke-Api -Name "全部已读" -Method PUT -Path "/api/notifications/read_all" -Token $student | Out-Null

# 9. 日志
Invoke-Api -Name "上报日志" -Method POST -Path "/api/logs" -Token $teacher -Body @{
    level = "INFO"; source = "smoke-test"; component = "TakeExam"; message = "smoke-test"; meta = @{ }
} | Out-Null

# 10. 权限负向：学生创建考试应 403
Invoke-Api -Name "[负向]学生建考试(应403)" -Method POST -Path "/api/exams" -Token $student -Accept @(403) -Body @{
    title = "hacker"; duration = 30
} | Out-Null
# 11. 未认证访问应 401
Invoke-Api -Name "[负向]无token查考试(应401)" -Path "/api/exams" -Accept @(401) | Out-Null

# ============================================================
# v4.0 新增模块：自主学习 / 竞赛竞技化 / 征集悬赏
# ============================================================

# 12. 自主学习（师生同权）
Invoke-Api -Name "自学-报告概览" -Path "/api/learning/report/overview" -Token $student | Out-Null
Invoke-Api -Name "自学-错题本" -Path "/api/learning/wrong-records" -Token $student | Out-Null
Invoke-Api -Name "自学-学习计划" -Path "/api/learning/plans" -Token $student | Out-Null
Invoke-Api -Name "自学-刷题历史" -Path "/api/learning/practice/history" -Token $student | Out-Null

# 13. 竞技化：段位 / 勋章 / 画像
Invoke-Api -Name "竞技-我的段位" -Path "/api/rank/me" -Token $student | Out-Null
Invoke-Api -Name "竞技-赛季列表" -Path "/api/rank/seasons" -Token $student | Out-Null
Invoke-Api -Name "竞技-我的勋章" -Path "/api/badges/me" -Token $student | Out-Null
Invoke-Api -Name "竞技-学情画像" -Path "/api/profile/study" -Token $student | Out-Null

# 14. 竞赛：广场 / 管理（教师）
Invoke-Api -Name "竞赛-大厅" -Path "/api/competitions/lobby" -Token $student | Out-Null
Invoke-Api -Name "竞赛-管理列表" -Path "/api/competitions" -Token $teacher | Out-Null
$newC = Invoke-Api -Name "竞赛-创建" -Method POST -Path "/api/competitions" -Token $teacher -Body @{
    title = "冒烟测试竞赛"; description = "smoke-comp"; duration = 10; drawCount = 0
    startTime = "2026-09-20 09:00:00"; endTime = "2026-09-20 11:00:00"
}
$cid = $newC.data.id
if ($cid) {
    Invoke-Api -Name "竞赛-详情" -Path "/api/competitions/$cid" -Token $teacher | Out-Null
    Invoke-Api -Name "竞赛-选题快照" -Method POST -Path "/api/competitions/$cid/questions" -Token $teacher -Body @{ questionIds = @(1) } | Out-Null
    Invoke-Api -Name "竞赛-发布" -Method POST -Path "/api/competitions/$cid/publish" -Token $teacher -Accept @(200, 400) | Out-Null
    Invoke-Api -Name "竞赛-删除" -Method DELETE -Path "/api/competitions/$cid" -Token $teacher -Accept @(200, 400) | Out-Null
}
Invoke-Api -Name "竞赛-PK大厅" -Path "/api/pk/lobby" -Token $student | Out-Null

# 15. 战队
Invoke-Api -Name "战队-列表" -Path "/api/teams" -Token $student | Out-Null
Invoke-Api -Name "战队-我的" -Path "/api/teams/mine" -Token $student -Accept @(200, 404) | Out-Null

# 16. 征集悬赏
Invoke-Api -Name "悬赏-广场" -Path "/api/bounties" -Token $student | Out-Null
$newB = Invoke-Api -Name "悬赏-发布题目征集" -Method POST -Path "/api/bounties" -Token $teacher -Body @{
    bountyType = "question"; title = "冒烟测试征集"; description = "smoke"; rewardPoints = 15; majorId = 1
}
$bid = $newB.data.id
if ($bid) {
    Invoke-Api -Name "悬赏-详情" -Path "/api/bounties/$bid" -Token $student | Out-Null
    Invoke-Api -Name "悬赏-投稿" -Method POST -Path "/api/bounties/$bid/submissions" -Token $student -Accept @(200, 409) -Body @{
        qContent = "冒烟投稿题干"; qAnswer = "A"; qOptions = @("A. 对", "B. 错")
    } | Out-Null
    Invoke-Api -Name "悬赏-我发布的" -Path "/api/bounties/mine" -Token $teacher | Out-Null
    Invoke-Api -Name "悬赏-我的投稿" -Path "/api/bounties/my-submissions" -Token $student | Out-Null
}

# 清理
if ($qid) { Invoke-Api -Name "删除题目" -Method DELETE -Path "/api/questions/$qid" -Token $teacher | Out-Null }
if ($eid) { Invoke-Api -Name "删除考试" -Method DELETE -Path "/api/exams/$eid" -Token $teacher -Accept @(200, 400) | Out-Null }

Write-Host "`n===== 结果汇总 =====" -ForegroundColor Cyan
Write-Host ("PASS: {0}    FAIL: {1}    TOTAL: {2}" -f $script:pass, $script:fail, ($script:pass + $script:fail)) -ForegroundColor Yellow
if ($script:fail -gt 0) {
    Write-Host "`n失败用例:" -ForegroundColor Red
    $script:results | Where-Object { -not $_.Ok } | Format-Table -AutoSize
    exit 1
}
exit 0
