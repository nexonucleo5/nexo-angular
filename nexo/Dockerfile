# Etapa 1: Compilar o projeto Angular
FROM node:18-alpine AS build
WORKDIR /app

# Instala o Angular CLI globalmente
RUN npm install -g @angular/cli@17

# Copia arquivos de dependência
COPY package*.json ./

# Instala todas as dependências do projeto
RUN npm install --legacy-peer-deps

# Copia o restante do código-fonte
COPY . .

# Desativa telemetria e define limite de memória do Node
ENV NODE_OPTIONS="--max-old-space-size=4096"
ENV NG_CLI_ANALYTICS=false

# Compila o projeto usando o CLI instalado
RUN ng build --configuration production

# Etapa 2: Servir os arquivos compilados no Nginx
FROM nginx:alpine
# Copia o resultado do build da Etapa 1 para o servidor web
COPY --from=build /app/dist/nexo/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]