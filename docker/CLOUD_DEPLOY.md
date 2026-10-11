# MediFood Circle 云服务器部署指南

## 服务器要求

| 配置 | 推荐 |
|------|------|
| CPU | 4核+
| 内存 | 8GB+
| 系统 | Ubuntu 22.04 LTS |
| 带宽 | 5M+ |
| 数据盘 | 50GB+ |

## 快速部署步骤

### 1. 安装Docker和Docker Compose

```bash
# 安装Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# 安装Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### 2. 上传项目文件

```bash
# 在服务器创建项目目录
sudo mkdir -p /opt/medifood
cd /opt/medifood

# 上传整个项目（或使用git clone）
# 推荐使用 git clone
git clone <你的仓库地址> .
```

### 3. 修改Docker配置

```bash
cd /opt/medifood/docker
```

编辑 `.env` 文件，根据需要修改：
- MySQL密码
- Redis配置
- RabbitMQ账号

### 4. 启动服务

```bash
# 构建并启动所有服务
docker-compose up -d --build

# 查看状态
docker-compose ps
```

### 5. 开放防火墙端口

```bash
# 开放端口
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp
sudo ufw enable
```

## 公网访问

服务启动后，访问：`http://你的服务器IP:8080`

## 可选：配置域名和HTTPS

### 1. 配置域名

在域名商处添加A记录：
- 主机记录：`@` 或 `www`
- 记录值：`你的服务器IP`

### 2. 配置HTTPS（使用Let's Encrypt）

```bash
# 安装certbot
sudo apt install certbot python3-certbot-nginx

# 获取免费证书
sudo certbot --nginx -d yourdomain.com -d www.yourdomain.com

# 自动续期
sudo certbot renew --dry-run
```

## 常用命令

```bash
# 查看日志
docker-compose logs -f

# 重启服务
docker-compose restart

# 停止服务
docker-compose down

# 更新后重新部署
git pull
docker-compose up -d --build
```

## 数据持久化

所有数据存储在Docker卷中，重启不会丢失：
```bash
# 查看卷
docker volume ls | grep medifood
```

## 备份数据

```bash
# 备份数据库
docker exec medifood-mysql mysqldump -uroot -proot123 medifood_circle > backup.sql

# 备份Redis
docker exec medifood-redis redis-cli SAVE
docker cp medifood-redis:/data/dump.rdb ./redis-backup.rdb
```

## 常见问题

1. **端口被占用**：检查其他服务是否占用了80/8080端口
2. **内存不足**：增加swap或升级服务器配置
3. **数据库连接失败**：等待MySQL完全启动后再访问