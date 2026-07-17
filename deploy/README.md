# 平台服务器部署

生产入口固定为：Web/API/STOMP 使用 `https://waang.top:443`，机器人设备 API 使用 `https://waang.top:8000/robot-api/`。Bridge 管理面 `127.0.0.1:8001/bridge/v1` 不向浏览器公开。

部署前备份当前 Nginx、systemd 和环境文件，并记录回滚版本：

```bash
sudo cp -a /www/server/panel/vhost/nginx/waang.top.conf /www/server/panel/vhost/nginx/waang.top.conf.bak
sudo cp -a /etc/systemd/system/power-inspection-backend.service /etc/systemd/system/power-inspection-backend.service.bak
sudo cp -a /etc/power-inspection/backend.env /etc/power-inspection/backend.env.bak
readlink -f /opt/power-inspection/current
```

安装时把后端 jar 和前端 `dist/` 放到版本化 release，并原子切换 `/opt/power-inspection/current`；真实凭据只写 `/etc/power-inspection/backend.env`，权限为 `root:powerinspection 0640`。已在聊天、仓库或日志出现过的 SSH/API 凭据必须在部署前轮换。

```bash
sudo install -m 0644 deploy/systemd/power-inspection-backend.service /etc/systemd/system/
sudo install -m 0644 deploy/nginx/waang.top.conf.example /www/server/panel/vhost/nginx/waang.top.conf
sudo systemctl daemon-reload
sudo systemctl enable --now power-inspection-backend
sudo /www/server/nginx/sbin/nginx -t && sudo systemctl reload nginx
```

无运动验收：

```bash
curl -fsS http://127.0.0.1:8080/api/v1/health
curl -fsS https://waang.top/api/v1/health
sudo systemctl status power-inspection-backend robot-bridge --no-pager
sudo journalctl -u power-inspection-backend -n 200 --no-pager
ss -lntp | grep -E ':8080|:8001'
```

预期 8080/8001 仅监听回环；登录 API 可用；机器人继续 heartbeat。Nginx 公网 `/bridge/v1/health` 必须为 404。systemd 日志由 journald 归档，使用 `journalctl -u power-inspection-backend` 查询。

回滚时停止新服务，恢复三份 `.bak`，把 `/opt/power-inspection/current` 切回旧 release，执行 `daemon-reload`、`nginx -t` 后重启。回滚判据为本机/公网 health、登录和机器人 heartbeat 恢复。
