$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

function Call($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    if ($null -ne $body) {
        $json = $body | ConvertTo-Json -Depth 6 -Compress
        $r = Invoke-RestMethod -Uri ($base + $path) -Method $method -Headers $headers `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 180
    } else {
        $r = Invoke-RestMethod -Uri ($base + $path) -Method $method -Headers $headers -TimeoutSec 180
    }
    $watch.Stop()
    return @{ r = $r; ms = $watch.ElapsedMilliseconds }
}

$user = Call 'POST' '/api/job/auth/login' @{ username = 'jobuser'; password = '123456' } $null
$userToken = $user.r.dataMap.token
$admin = Call 'POST' '/api/job/auth/login' @{ username = 'admin'; password = '123456' } $null
$adminToken = $admin.r.dataMap.token
Write-Output ("LOGIN: user=" + $user.r.dataMap.user.role + " admin=" + $admin.r.dataMap.user.role)

$status1 = Call 'GET' '/api/job/admin/ai/vector' $null $adminToken
Write-Output ("VECTOR STATUS RAW: " + ($status1.r.dataMap.vector | ConvertTo-Json -Compress))

$resumeBody = @{
    title = 'Milvus RAG test resume'
    candidateName = 'Test Student'
    targetPosition = 'Java Backend'
    summary = 'RAG integration verification'
    content = 'Project experience: campus trading platform order module. Used Redis cache for hot goods, tuned MySQL composite index, P99 latency reduced from 320ms to 90ms, QPS up to 1200. Skills: Java, Spring Boot, MyBatis, MySQL, Redis, Linux, Docker. Internship: backend development for 3 months.'
}
$create = Call 'POST' '/api/job/resumes' $resumeBody $userToken
Write-Output ("RESUME CREATE: code=" + $create.r.code + " id=" + $create.r.dataMap.resume.id + " elapsed=" + $create.ms + "ms")

$status2 = Call 'GET' '/api/job/admin/ai/vector' $null $adminToken
Write-Output ("VECTOR AFTER WRITE: " + ($status2.r.dataMap.vector | ConvertTo-Json -Compress))

$resumes = Call 'GET' '/api/job/resumes?page=1&size=5' $null $userToken
$positions = Call 'GET' '/api/job/positions?page=1&size=5' $null $userToken
$resumeId = $resumes.r.dataMap.page.list[0].id
$positionId = $positions.r.dataMap.page.list[0].id

$analysis = Call 'POST' '/api/job/analyses' @{ resumeId = $resumeId; positionId = $positionId } $userToken
$a = $analysis.r.dataMap.analysis
Write-Output ("ANALYSIS: code=" + $analysis.r.code + " score=" + $a.matchScore + " degraded=" + $a.degraded + " model=" + $a.model + " elapsed=" + $analysis.ms + "ms")

$reindex = Call 'POST' '/api/job/admin/ai/vector/reindex' $null $adminToken
Write-Output ("REINDEX: " + ($reindex.r.dataMap.reindexed | ConvertTo-Json -Compress))
$search = Call 'POST' '/api/job/admin/ai/vector/search' @{ query = 'Redis cache'; topK = 3 } $adminToken
Write-Output ("VECTOR SEARCH: code=" + $search.r.code + " hits=" + $search.r.dataMap.count + " elapsed=" + $search.ms + "ms")

$interview = Call 'POST' '/api/job/interviews' @{ positionId = $positionId; resumeId = $resumeId; questionCount = 2 } $userToken
Write-Output ("INTERVIEW: code=" + $interview.r.code + " questions=" + $interview.r.dataMap.session.questions.Count + " degraded=" + $interview.r.dataMap.session.degraded + " elapsed=" + $interview.ms + "ms")
