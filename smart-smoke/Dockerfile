FROM m.daocloud.io/docker.io/library/node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_AMAP_KEY=
ARG VITE_AMAP_SECURITY_CODE=
ARG VITE_API_BASE_URL=
ENV VITE_AMAP_KEY=${VITE_AMAP_KEY}
ENV VITE_AMAP_SECURITY_CODE=${VITE_AMAP_SECURITY_CODE}
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

FROM m.daocloud.io/docker.io/library/nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
