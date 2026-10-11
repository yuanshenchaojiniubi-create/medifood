# 构建阶段
FROM node:18-alpine AS builder

WORKDIR /app

# 复制依赖文件
COPY frontend/package.json frontend/package-lock.json ./

# 安装依赖
RUN npm ci --legacy-peer-deps

# 复制源码
COPY frontend/ ./

# 构建生产版本
RUN npm run build


# 运行阶段 - 使用nginx serving静态文件
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制nginx配置
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]