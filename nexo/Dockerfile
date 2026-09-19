# Etapa 1: Compilar o projeto Angular
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# Etapa 2: Servir os arquivos compilados no Nginx
FROM nginx:alpine
COPY --from=build /app/dist/nexo/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]