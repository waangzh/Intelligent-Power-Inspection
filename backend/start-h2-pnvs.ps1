# H2 演示启动，并启用号码认证真发短信（读取 application.yml 中的 AccessKey）
$env:SPRING_PROFILES_ACTIVE = "test"
$env:APP_SMS_MODE = "pnvs"
mvn spring-boot:test-run
