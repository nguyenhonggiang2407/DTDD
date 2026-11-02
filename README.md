# YangData

Private CSV datasets and a parameterized SQLite report builder by **Nguyễn Hồng Giang (Yang)**.

This branch is an independent application. Development checkpoint: backend implementation and automated checks; interface and browser verification are being completed.

## Source and scope

The reference is the MIT-licensed [rag-sql-router](https://github.com/Trinhvhao/ai-engineering-hub/tree/86dc1699fe036e5cd21ed7d3c57419a7fc3c844b/rag-sql-router) example from ai-engineering-hub, originally by patchy631. The original reference source and MIT license are preserved in upstream/ and LICENSE. See NOTICE.md for the precise distinction between reference material and this implementation.

The new Node.js runtime uses a typed query builder and parameter binding. It does not execute free-form SQL or call an LLM. No external API key is required.

## Development

Requires Node.js 24. Run:

~~~sh
npm ci
npm test
npm start
~~~

The server listens on 127.0.0.1:3130. Workspace sessions are private to the current browser and expire after seven days. CSV values and data stay in this server's SQLite database.
