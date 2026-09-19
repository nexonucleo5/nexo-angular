# Etapa 1: Compilar o projeto Angular
FROM node:18-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm install --legacy-peer-deps

COPY . .

ENV NODE_OPTIONS="--max-old-space-size=4096"
ENV NG_CLI_ANALYTICS=false

# Roda o build e, se der erro, exibe o log detalhado no console do Docker
RUN npm run build -- --configuration=production || (cat /tmp/* /root/.npm/_logs/* 2>/dev/null; exit 1)

# Etapa 2: Servir no Nginx
FROM nginx:alpine
COPY --from=build /app/dist/nexo/browser/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]