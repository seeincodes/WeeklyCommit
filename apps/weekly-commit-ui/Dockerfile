# Multi-stage Dockerfile for the weekly-commit-ui frontend (demo deploy).
#
# Stage 1: install yarn deps + run `vite build` with VITE_DEMO_MODE=true so the
#          devAuth shim survives tree-shaking and the resulting static bundle
#          mints JWTs in-browser against the e2e-profile backend.
# Stage 2: serve `dist/` from nginx with a SPA-fallback route + sensible cache
#          headers for the federated chunks.

# ---------- Stage 1: build ----------
FROM node:20-alpine AS build
WORKDIR /workspace

# Corepack ships with Node 20 but is opt-in; activate it so the project's
# pinned Yarn 4.5.0 (declared in root package.json `packageManager`) takes over
# the `yarn` shim. Without this, the system yarn (1.x) fails the gate.
RUN corepack enable && corepack prepare yarn@4.5.0 --activate

# Copy only the descriptors first so `yarn install` caches across iterative
# rebuilds where only source changes.
COPY package.json yarn.lock .yarnrc.yml nx.json tsconfig.base.json ./
COPY .yarn ./.yarn
COPY apps/weekly-commit-ui/package.json ./apps/weekly-commit-ui/
COPY libs/rtk-api-client/package.json ./libs/rtk-api-client/
COPY libs/contracts/package.json ./libs/contracts/
COPY libs/ui-components/package.json ./libs/ui-components/

RUN yarn install --immutable

# Now bring in the rest of the source.
COPY libs ./libs
COPY apps/weekly-commit-ui ./apps/weekly-commit-ui

# VITE_DEMO_MODE keeps the devAuth shim in the bundle (see src/main.tsx).
# VITE_API_BASE_URL is the URL the bundled fetch calls hit; passed in via
# build-arg by docker-compose.demo.yml so the same Dockerfile can produce
# bundles for localhost-compose and AWS-deployed origins.
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_DEMO_MODE=true
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_DEMO_MODE=${VITE_DEMO_MODE}

RUN yarn workspace @wc/weekly-commit-ui run build

# ---------- Stage 2: runtime ----------
FROM nginx:1.27-alpine AS runtime

# nginx default config doesn't fall back to /index.html for SPA routes (HashRouter
# uses fragment-style URLs so it would actually be fine, but keeping the fallback
# means swapping to BrowserRouter in the host shell later doesn't need a config
# touch). Also disables the default access log to keep the demo task quiet.
COPY apps/weekly-commit-ui/nginx-demo.conf /etc/nginx/conf.d/default.conf

# The Vite build output lands in `dist/` under the workspace path.
COPY --from=build /workspace/apps/weekly-commit-ui/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1/ > /dev/null || exit 1
