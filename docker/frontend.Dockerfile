# syntax=docker/dockerfile:1
ARG NODE_IMAGE=docker.m.daocloud.io/library/node:22-alpine

FROM ${NODE_IMAGE} AS dashboard-build

WORKDIR /workspace/dashboard
ENV NODE_OPTIONS=--max-old-space-size=768

ARG VITE_API_PAYLOAD_SECRET=
ARG BLOG_ADMIN_BASE_PATH=/admin
ENV VITE_API_PAYLOAD_SECRET=${VITE_API_PAYLOAD_SECRET}
ENV BLOG_ADMIN_BASE_PATH=${BLOG_ADMIN_BASE_PATH}

COPY dashboard/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY dashboard/ ./
RUN npm run build

FROM ${NODE_IMAGE} AS frontend-build

WORKDIR /workspace

ARG BLOG_BACKEND_ORIGIN=http://backend:8080
ARG NUXT_PUBLIC_API_PAYLOAD_SECRET=
ARG BLOG_ADMIN_BASE_PATH=/admin
ENV BLOG_BACKEND_ORIGIN=${BLOG_BACKEND_ORIGIN}
ENV NUXT_PUBLIC_API_PAYLOAD_SECRET=${NUXT_PUBLIC_API_PAYLOAD_SECRET}
ENV BLOG_ADMIN_BASE_PATH=${BLOG_ADMIN_BASE_PATH}

COPY frontend/package*.json frontend/
RUN --mount=type=cache,target=/root/.npm cd frontend && npm ci --ignore-scripts --no-audit --no-fund

COPY frontend/ frontend/
COPY --from=dashboard-build /workspace/dashboard/dist dashboard/dist

# Cap Node heap during Nuxt build to avoid OOM on small VPS hosts.
ENV NODE_OPTIONS=--max-old-space-size=1024
RUN cd frontend && npm run build

FROM ${NODE_IMAGE}

WORKDIR /app

ENV NODE_ENV=production \
    NODE_OPTIONS=--max-old-space-size=160 \
    NITRO_HOST=0.0.0.0 \
    NITRO_PORT=3256 \
    BLOG_BACKEND_ORIGIN=http://backend:8080 \
    BLOG_ADMIN_BASE_PATH=/admin

COPY --chown=node:node --from=frontend-build /workspace/frontend/.output ./.output
USER node

EXPOSE 3256
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=4 CMD node -e "fetch('http://127.0.0.1:3256/').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", ".output/server/index.mjs"]
