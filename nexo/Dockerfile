# Etapa 1: Compilar o projeto Angular
FROM node:20-alpine AS build
WORKDIR /app

# Copia dependências
COPY package*.json ./
RUN npm install --legacy-peer-deps

# Copia o código-fonte
COPY . .

# Desativa telemetria e limita a memória do Node a 2GB (evita estouro de RAM no Linux)
ENV NG_CLI_ANALYTICS=false
ENV NODE_OPTIONS="--max-old-space-size=2048"

# Compila diretamente com o CLI local
RUN npx ng build --configuration=production

# Etapa 2: Servir os arquivos no Nginx
FROM nginx:alpine
# Copia a pasta gerada pelo Angular 17+
COPY --from=build /app/dist/nexo/browser/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]