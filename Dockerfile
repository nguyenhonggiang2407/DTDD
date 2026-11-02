FROM node:24-bookworm-slim
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts
COPY lib ./lib
COPY apps ./apps
COPY public ./public
COPY server.mjs ./
RUN mkdir -p /app/data && chown node:node /app/data
ENV NODE_ENV=production YANGLAB_BIND=0.0.0.0 PORT=3100
USER node
EXPOSE 3100
HEALTHCHECK --interval=30s --timeout=3s CMD node -e "fetch('http://127.0.0.1:3100/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "--experimental-sqlite", "server.mjs"]
