# Etapa 1: Compilar o projeto Angular
FROM node:18-alpine AS build
WORKDIR /app

# Copia dependências
COPY package*.json ./
RUN npm install --legacy-peer-deps

# Copia o código-fonte
COPY . .

# Aumenta memória e desativa telemetria + tratamento rígido de Warnings
ENV NODE_OPTIONS="--max-old-space-size=4096"
ENV NG_CLI_ANALYTICS=false

# Compila o projeto no modo produção
RUN npm run build -- --configuration=production

# Etapa 2: Servir os arquivos no Nginx
FROM nginx:alpine
# Copia especificamente da pasta 'browser' gerada pelo Angular
COPY --from=build /app/dist/nexo/browser/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]