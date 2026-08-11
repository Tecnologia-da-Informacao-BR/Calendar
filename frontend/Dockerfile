# ==========================================================
# Frontend (Angular 22 + SSR) - imagem de PRODUÇÃO
# Serve via Node/Express (dist/meu-projeto/server/server.mjs)
# ==========================================================

# ---- Etapa 1: build ----
FROM node:26-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Etapa 2: dependências apenas de produção ----
FROM node:26-alpine AS prod-deps
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# ---- Etapa 3: runtime ----
FROM node:26-alpine AS runtime
WORKDIR /app

RUN addgroup -S angular && adduser -S angular -G angular

ENV NODE_ENV=production \
    PORT=4000

COPY --from=prod-deps /app/node_modules ./node_modules
COPY --from=build /app/dist/meu-projeto ./dist/meu-projeto

USER angular
EXPOSE 4000

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD wget -qO- http://localhost:4000/ || exit 1

CMD ["node", "dist/meu-projeto/server/server.mjs"]
