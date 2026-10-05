$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

& "$scriptDir\build.cmd"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Build failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "Khởi động VKU Mail Client (Giao diện Chat-Message)..." -ForegroundColor Cyan
java "-Dfile.encoding=UTF-8" -cp "out;lib\*" MailClientFrame @args
