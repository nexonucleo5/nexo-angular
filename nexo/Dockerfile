FROM nginx:alpine
COPY dist/nexo/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]