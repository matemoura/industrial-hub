FROM node:22-alpine AS build
WORKDIR /app
RUN npm install -g npm@11.5.1
COPY package*.json ./
RUN npm ci --legacy-peer-deps
COPY . .
RUN npx ng build --configuration production

FROM nginx:alpine
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY docker-entrypoint.sh /docker-msb-entrypoint.sh
RUN chmod +x /docker-msb-entrypoint.sh
EXPOSE 80
ENTRYPOINT ["/docker-msb-entrypoint.sh"]
