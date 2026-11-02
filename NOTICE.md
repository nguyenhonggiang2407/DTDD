# Provenance and attribution

YangData implementation © 2026 Nguyễn Hồng Giang, released under the MIT License.

## Preserved reference

- Repository viewed: https://github.com/Trinhvhao/ai-engineering-hub
- Original project: https://github.com/patchy631/ai-engineering-hub
- Snapshot: 86dc1699fe036e5cd21ed7d3c57419a7fc3c844b
- Original file: rag-sql-router/tools.py
- Preserved unmodified copy: upstream/rag_sql_tools.py
- Original copyright: Copyright (c) 2024 patchy631
- License: MIT; the original license text is retained in LICENSE.

The reference demonstrates querying a CSV-backed SQLite dataset and separating database tools from the conversation layer. It is reference material and is never imported or executed by this application.

## New implementation

The Node.js server, CSV import/type inference, owned physical tables, parameterized query grammar, pagination, saved report snapshots, optimistic concurrency, workspace expiry cleanup, exports, UI, tests and container configuration are newly implemented for YangData. CSV parsing/export helpers are shared with YangQueue (Nguyễn Hồng Giang's MIT implementation).

The application deliberately implements a deterministic query builder. It has no natural-language-to-SQL agent, LLM, embedding service, external scraping, credential storage or public data sharing. It is not the complete upstream application and makes no claim to authorship of the preserved reference.

All included sample orders are fictional. Personal branding refers to Nguyễn Hồng Giang; upstream authorship and license notices remain intact.
