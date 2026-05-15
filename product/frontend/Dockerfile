FROM node:22-alpine AS build
WORKDIR /workspace
RUN corepack enable
COPY package.json pnpm-workspace.yaml ./
COPY frontend ./frontend
COPY apps ./apps
ARG APP_NAME
RUN pnpm install --frozen-lockfile=false
RUN pnpm --filter @minifin/${APP_NAME} build

FROM nginx:1.27-alpine
ARG APP_NAME
COPY --from=build /workspace/apps/${APP_NAME}/dist /usr/share/nginx/html
COPY deploy/nginx/spa.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
