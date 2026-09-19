# Etapa 1: Compilar o projeto Angular
FROM node:18-alpine AS build
WORKDIR /app

# Copia arquivos de dependência
COPY package*.json ./

# Instala dependências (ci é mais rápido e seguro em contêineres)
RUN npm ci || npm install

# Copia o restante do código
COPY . .

# Aumenta o limite de memória do Node para o build (2GB) e desativa telemetria do Angular
ENV NODE_OPTIONS="--max-old-space-size=2048"
ENV NG_CLI_ANALYTICS=false

# Compila o projeto em modo de produção
RUN npx ng build --configuration production

# Etapa 2: Servir os arquivos compilados no Nginx
FROM nginx:alpine
COPY --from=build /app/dist/nexo/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]