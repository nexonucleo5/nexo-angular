# Etapa 1: Compilar o projeto Angular
FROM node:18-alpine AS build
WORKDIR /app

# Copia arquivos de dependência
COPY package*.json ./

# Instala todas as dependências do projeto
RUN npm install --legacy-peer-deps

# Copia o restante do código-fonte
COPY . .

# Aumenta a memória do Node e desativa telemetria
ENV NODE_OPTIONS="--max-old-space-size=4096"
ENV NG_CLI_ANALYTICS=false

# Roda o script de build oficial do seu package.json
RUN npm run build -- --configuration=production

# Etapa 2: Servir os arquivos compilados no Nginx
FROM nginx:alpine
# Copia o resultado do build (pega tudo dentro de dist, independente da subpasta)
COPY --from=build /app/dist/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]