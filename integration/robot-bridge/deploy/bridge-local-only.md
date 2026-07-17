# Robot Bridge 管理接口仅本机访问

## 正式拓扑

```text
Spring Boot -> http://127.0.0.1:8001/bridge/v1
Jetson      -> https://example.com/robot-api/v1
Browser     -> https://example.com/api/v1
```

Nginx 只代理 `/robot-api/`。不要添加公开 `/bridge/` location，不要让浏览器持有 `BRIDGE_API_TOKEN`，不要把 8001 暴露到安全组或公网监听。

## Spring 配置

```text
ROBOT_BRIDGE_BASE_URL=http://127.0.0.1:8001
ROBOT_BRIDGE_ADMIN_TOKEN=<server-only>
```

Spring 在服务端添加 Bearer；前端继续收 Spring `ApiResponse`。

## 临时管理：SSH tunnel

推荐从运维机建立回环 tunnel：

```bash
ssh -L 18001:127.0.0.1:8001 example.com
```

然后在本机请求：

```bash
curl -H 'Authorization: Bearer token-placeholder' \
  http://127.0.0.1:18001/bridge/v1/health
```

SSH 密码只在交互提示输入；禁止 sshpass、命令行密码、私钥入库和 `StrictHostKeyChecking=no`。

## 不推荐的临时公网管理

只有无法使用 SSH tunnel 且经过安全审批时，才创建单独、默认不启用的 Nginx 配置，并同时满足：

- 仅允许明确的运维来源 IP allowlist；其余 `deny all`。
- 强制 HTTPS。
- 仍要求 `BRIDGE_API_TOKEN`。
- 独立 access log，日志不记录 Authorization。
- 设置短期变更窗口，联调结束立即删除并 `nginx -t && reload`。

示意配置只用于临时审批场景：

```nginx
location /bridge/ {
    allow <approved-admin-ip>;
    deny all;
    proxy_pass http://127.0.0.1:8001/bridge/;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

正式环境不保留该 location。
