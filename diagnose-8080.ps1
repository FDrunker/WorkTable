# ============================================================
# 8080 端口占用诊断与释放
#
# 用法：右键「使用 PowerShell 运行」，或在终端执行
#       powershell -ExecutionPolicy Bypass -File .\diagnose-8080.ps1
#
# 若定位到的进程属于其他用户或更高权限，需要用「管理员身份」运行本脚本。
# ============================================================

$ErrorActionPreference = 'Continue'

Write-Host "===== 1. 谁在监听 8080 =====" -ForegroundColor Cyan
$listeners = netstat -ano | Select-String ':8080\s' | Select-String 'LISTENING'
if (-not $listeners) {
    Write-Host "  当前没有进程监听 8080，端口已释放。" -ForegroundColor Green
    exit 0
}

$pids = @()
foreach ($line in $listeners) {
    Write-Host "  $($line.Line.Trim())"
    $pids += ($line.Line.Trim() -split '\s+')[-1]
}
$pids = $pids | Sort-Object -Unique

Write-Host ""
Write-Host "===== 2. 进程详情 =====" -ForegroundColor Cyan
foreach ($procId in $pids) {
    $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if ($p) {
        Write-Host "  PID   : $procId"
        Write-Host "  名称  : $($p.ProcessName)"
        Write-Host "  路径  : $($p.Path)"
        Write-Host "  启动  : $($p.StartTime)"
        Write-Host "  时长  : $([math]::Round(((Get-Date) - $p.StartTime).TotalMinutes,1)) 分钟"
        try {
            $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$procId").CommandLine
            Write-Host "  命令行: $cmd"
        } catch {
            Write-Host "  命令行: 无法读取（权限不足）" -ForegroundColor Yellow
        }
    } else {
        Write-Host "  PID $procId : 对当前用户不可见" -ForegroundColor Yellow
        Write-Host "         说明它由其他用户或更高权限启动，需用管理员身份处理。" -ForegroundColor Yellow
    }
    Write-Host ""
}

Write-Host "===== 3. 服务 / 计划任务中是否有指向 8080 的 =====" -ForegroundColor Cyan
$svc = Get-CimInstance Win32_Service -ErrorAction SilentlyContinue | Where-Object { $_.PathName -match '8080' }
if ($svc) { $svc | Select-Object Name, State, PathName | Format-List } else { Write-Host "  无匹配的 Windows 服务" }

$task = Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { ($_.Actions.Arguments -join ' ') -match '8080' }
if ($task) { $task | Select-Object TaskName, State | Format-Table } else { Write-Host "  无匹配的计划任务" }

Write-Host ""
Write-Host "===== 4. 是否为本项目残留的后端进程 =====" -ForegroundColor Cyan
$java = Get-Process -Name java, javaw -ErrorAction SilentlyContinue
if ($java) {
    Write-Host "  当前 java 进程："
    $java | Select-Object Id, ProcessName, StartTime, Path | Format-Table -AutoSize
    Write-Host "  提示：若其中某个是 ruoyi-admin 且并非本次要启动的实例，先结束它。" -ForegroundColor Yellow
} else {
    Write-Host "  当前没有 java 进程（说明占 8080 的不是本项目后端）"
}

Write-Host ""
Write-Host "===== 5. 处理方式 =====" -ForegroundColor Cyan
Write-Host "  方式 A：结束占用进程（需管理员权限，仅确认是本项目残留时使用）"
foreach ($procId in $pids) {
    Write-Host "      Stop-Process -Id $procId -Force" -ForegroundColor Yellow
}
Write-Host ""
Write-Host "  方式 B：改用其他端口启动后端，不改任何文件："
Write-Host "      cd Backend" -ForegroundColor Yellow
Write-Host "      mvn -pl ruoyi-admin spring-boot:run -Dspring-boot.run.arguments=--server.port=8090" -ForegroundColor Yellow
Write-Host "      或打包后：java -jar ruoyi-admin/target/ruoyi-admin.jar --server.port=8090" -ForegroundColor Yellow
Write-Host ""
Write-Host "  注意：换端口后前端代理也要跟着改（见下方提示）。" -ForegroundColor Yellow
