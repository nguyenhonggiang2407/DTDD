FROM node:24-bookworm-slim
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts
COPY lib ./lib
COPY public ./public
COPY server.mjs ./
RUN mkdir -p /app/data && chown node:node /app/data
ENV NODE_ENV=production YANGQUEUE_BIND=0.0.0.0 PORT=3120
USER node
EXPOSE 3120
HEALTHCHECK --interval=30s --timeout=3s CMD node -e "fetch('http://127.0.0.1:3120/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "--experimental-sqlite", "server.mjs"]
