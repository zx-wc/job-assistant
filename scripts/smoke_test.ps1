$ErrorActionPreference = 'Stop'
$base = if ($args.Count -ge 1) { $args[0] } else { 'http://127.0.0.1:8080' }
$payloads = Get-Content -Path (Join-Path $PSScriptRoot 'smoke_payloads.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$results = @()

function Call($name, $method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    try {
        if ($null -ne $body) {
            $json = $body | ConvertTo-Json -Depth 6 -Compress
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
            $r = Invoke-RestMethod -Uri ($base + $path) -Method $method -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes -TimeoutSec 120
        } else {
            $r = Invoke-RestMethod -Uri ($base + $path) -Method $method -Headers $headers -TimeoutSec 120
        }
        $script:results += [pscustomobject]@{ Step = $name; Code = $r.code; Msg = $r.msg }
        return $r
    } catch {
        $script:results += [pscustomobject]@{ Step = $name; Code = 'EXCEPTION'; Msg = $_.Exception.Message }
        return $null
    }
}

$ping = Call 'ping' 'GET' '/api/job/auth/ping' $null $null
Write-Output ('PING: ' + $ping.dataMap.service + ' / ' + $ping.dataMap.status)

$unauth = Call 'unauthorized' 'GET' '/api/job/resumes' $null $null
Write-Output ('UNAUTHORIZED CODE: ' + $unauth.code)

$login = Call 'login' 'POST' '/api/job/auth/login' @{ username = 'jobuser'; password = '123456' } $null
$token = $login.dataMap.token
Write-Output ('LOGIN: user=' + $login.dataMap.user.username + ' role=' + $login.dataMap.user.role + ' token=' + $token.Substring(0, 8) + '***')

$resumes = Call 'resume-page' 'GET' '/api/job/resumes?page=1&size=5' $null $token
$resumeId = $resumes.dataMap.page.list[0].id
Write-Output ('RESUMES: total=' + $resumes.dataMap.page.total + ' firstId=' + $resumeId)

$positions = Call 'position-page' 'GET' '/api/job/positions?page=1&size=5' $null $token
$positionId = $positions.dataMap.page.list[0].id
Write-Output ('POSITIONS: total=' + $positions.dataMap.page.total + ' firstId=' + $positionId)

$newResume = Call 'resume-create' 'POST' '/api/job/resumes' $payloads.resume $token
$newResumeId = $newResume.dataMap.resume.id
$masked = $newResume.dataMap.resume.content.Contains('138****5678')
Write-Output ('RESUME CREATE: id=' + $newResumeId + ' masked=' + $masked)

$versions = Call 'resume-versions' 'GET' "/api/job/resumes/$newResumeId/versions" $null $token
Write-Output ('VERSIONS: count=' + $versions.dataMap.versions.Count)

$analysis = Call 'analysis' 'POST' '/api/job/analyses' @{ resumeId = $resumeId; positionId = $positionId } $token
$analysisVo = $analysis.dataMap.analysis
Write-Output ('ANALYSIS: id=' + $analysisVo.id + ' score=' + $analysisVo.matchScore + ' degraded=' + $analysisVo.degraded + ' model=' + $analysisVo.model)
Write-Output ('ANALYSIS: strengths=' + $analysisVo.strengths.Count + ' suggestions=' + $analysisVo.suggestions.Count + ' dims=' + $analysisVo.dimensionScores.PSObject.Properties.Count)

$interview = Call 'interview-start' 'POST' '/api/job/interviews' @{ positionId = $positionId; resumeId = $resumeId; difficulty = 'NORMAL'; questionCount = 3 } $token
$session = $interview.dataMap.session
Write-Output ('INTERVIEW START: id=' + $session.id + ' questions=' + $session.questions.Count + ' degraded=' + $session.degraded)

$answer = Call 'interview-answer' 'POST' ("/api/job/interviews/" + $session.id + "/answers") @{ questionNo = 1; answer = $payloads.answer } $token
$result = $answer.dataMap.result
Write-Output ('ANSWER 1: score=' + $result.score + ' degraded=' + $result.degraded + ' allAnswered=' + $result.allAnswered)

$follow = Call 'interview-followup' 'POST' ("/api/job/interviews/" + $session.id + "/follow-up") @{ questionNo = 1 } $token
Write-Output ('FOLLOW-UP: questions=' + $follow.dataMap.session.questions.Count)

$finish = Call 'interview-finish' 'POST' ("/api/job/interviews/" + $session.id + "/finish") $null $token
$report = $finish.dataMap.report
Write-Output ('REPORT: overall=' + $report.overallScore + ' degraded=' + $report.degraded + ' model=' + $report.model + ' dims=' + $report.dimensionScores.PSObject.Properties.Count)

$dash = Call 'dashboard' 'GET' '/api/job/dashboard' $null $token
$d = $dash.dataMap.dashboard
Write-Output ('DASHBOARD: resumes=' + $d.resumeCount + ' positions=' + $d.positionCount + ' analyses=' + $d.analysisCount + ' interviews=' + $d.interviewCount + ' ongoing=' + $d.ongoingInterviewCount)

$forbidden = Call 'forbidden' 'GET' '/api/job/admin/ai/stats' $null $token
Write-Output ('FORBIDDEN CODE: ' + $forbidden.code)

$adminLogin = Call 'admin-login' 'POST' '/api/job/auth/login' @{ username = 'admin'; password = '123456' } $null
$adminToken = $adminLogin.dataMap.token
Write-Output ('ADMIN LOGIN: role=' + $adminLogin.dataMap.user.role)

$stats = Call 'admin-stats' 'GET' '/api/job/admin/ai/stats' $null $adminToken
$s = $stats.dataMap.stats
Write-Output ('AI STATS: todayTotal=' + $s.todayTotal + ' fail=' + $s.todayFail + ' degraded=' + $s.todayDegraded + ' available=' + $s.available + ' byScene=' + $s.byScene.Count)

$config = Call 'admin-config' 'GET' '/api/job/admin/ai/config' $null $adminToken
Write-Output ('AI CONFIG: model=' + $config.dataMap.config.model + ' key=' + $config.dataMap.config.apiKeyMasked + ' timeoutMs=' + $config.dataMap.config.timeoutMs + ' retries=' + $config.dataMap.config.maxRetries)

$scenes = Call 'admin-scenes' 'GET' '/api/job/admin/ai/scenes' $null $adminToken
Write-Output ('AI SCENES: count=' + $scenes.dataMap.scenes.Count + ' first=' + $scenes.dataMap.scenes[0].code + ' placeholders=' + ($scenes.dataMap.scenes[0].placeholders -join ','))

$logs = Call 'admin-logs' 'GET' '/api/job/admin/ai/logs?page=1&size=5' $null $adminToken
Write-Output ('AI LOGS: total=' + $logs.dataMap.page.total + ' latestScene=' + $logs.dataMap.page.list[0].scene + ' success=' + $logs.dataMap.page.list[0].success)

$page = Invoke-WebRequest -Uri ($base + '/job/index.html') -TimeoutSec 30
Write-Output ('STATIC PAGE: status=' + $page.StatusCode + ' bytes=' + $page.RawContentLength)
$js = Invoke-WebRequest -Uri ($base + '/job/js/app.js') -TimeoutSec 30
Write-Output ('APP JS: status=' + $js.StatusCode + ' bytes=' + $js.RawContentLength)
$css = Invoke-WebRequest -Uri ($base + '/job/css/job.css') -TimeoutSec 30
Write-Output ('APP CSS: status=' + $css.StatusCode + ' bytes=' + $css.RawContentLength)

$logout = Call 'logout' 'POST' '/api/job/auth/logout' $null $token
$afterLogout = Call 'after-logout' 'GET' '/api/job/resumes' $null $token
Write-Output ('LOGOUT: code=' + $logout.code + ' afterLogoutCode=' + $afterLogout.code)

Write-Output ''
Write-Output '===== STEP SUMMARY ====='
$results | ForEach-Object { '{0,-20} {1,-10} {2}' -f $_.Step, $_.Code, $_.Msg }
