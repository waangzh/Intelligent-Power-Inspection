# 真实后端：MySQL + 阿里云号码认证真发短信（不要用 SPRING_PROFILES_ACTIVE=test / test-run）
$ErrorActionPreference = "Stop"

$javaHome = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$mavenBin = "D:\apache-maven-3.9.9\bin"
if (Test-Path $javaHome) { $env:JAVA_HOME = $javaHome }
if (Test-Path $mavenBin) { $env:Path = "$mavenBin;" + $env:Path }

$appYml = Join-Path $PSScriptRoot "src\main\resources\application.yml"
if (-not (Test-Path $appYml)) {
  Write-Host "未找到 application.yml，请先复制并编辑：" -ForegroundColor Yellow
  Write-Host "  copy src\main\resources\application.example.yml src\main\resources\application.yml"
  Write-Host "参考 application.local.example.yml 填写 MySQL 与阿里云 PNVS 参数。"
  exit 1
}

$raw = Get-Content $appYml -Raw
if ($raw -match 'YOUR_MYSQL_PASSWORD|YOUR_ALIYUN_ACCESS_KEY_ID|YOUR_PNVS_SIGN_NAME') {
  Write-Host "请编辑 src\main\resources\application.yml，替换 YOUR_* 占位符后再启动。" -ForegroundColor Yellow
  exit 1
}

# 显式禁用 mock，避免环境变量遗留 test profile
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
$env:APP_SMS_MODE = "pnvs"

Set-Location $PSScriptRoot
Write-Host "启动 MySQL 真实后端（短信 mode=pnvs）..." -ForegroundColor Cyan
mvn spring-boot:run
