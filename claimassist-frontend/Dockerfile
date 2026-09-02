# Multi-stage build: build React app with Node, serve with nginx
# Accept VITE_API_BASE_URL as a build-arg so the production bundle is built
# with the correct API base URL (required because Vite embeds env vars at build time).
FROM node:18-alpine AS builder
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
WORKDIR /app
COPY package*.json ./
RUN npm ci --silent
COPY . .
# Ensure the build uses the provided VITE_API_BASE_URL
RUN if [ -n "${VITE_API_BASE_URL}" ]; then echo "VITE_API_BASE_URL=${VITE_API_BASE_URL}" > .env.production; fi
RUN npm run build

FROM nginx:stable-alpine
# Remove default config and use custom nginx.conf
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
# Healthcheck endpoint (optional)
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 CMD ["/bin/sh","-c","[ -f /usr/share/nginx/html/index.html ] || exit 1"]
EXPOSE 80
CMD ["nginx","-g","daemon off;"]
