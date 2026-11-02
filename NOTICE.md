# Attribution & provenance

YangDocs is maintained and branded for **Nguyễn Hồng Giang**
([nguyenhonggiang2407](https://github.com/nguyenhonggiang2407)).

## Licensed starting point

- Original project: [patchy631/ai-engineering-hub](https://github.com/patchy631/ai-engineering-hub).
- Source encountered through: [Trinhvhao/ai-engineering-hub](https://github.com/Trinhvhao/ai-engineering-hub), a fork of that project.
- Inspected snapshot: `86dc1699fe036e5cd21ed7d3c57419a7fc3c844b`.
- Example: [`simple-rag-workflow/workflow.py`](https://github.com/Trinhvhao/ai-engineering-hub/blob/86dc1699fe036e5cd21ed7d3c57419a7fc3c844b/simple-rag-workflow/workflow.py).
- Original copyright: **Copyright (c) 2024 patchy631**. The complete original MIT license is preserved in [LICENSE](LICENSE).
- An unmodified copy of the example is retained in [upstream/workflow.py](upstream/workflow.py). It is a reference, not imported by the application or included in the runtime dependencies.

## Changes in this repository

YangDocs reworks the ingest → retrieve → synthesize architecture into
ingest → scoped keyword retrieval → exact, page-addressable evidence. It adds
FastAPI, persistent SQLite FTS5, Vietnamese normalization, per-workspace document
ownership, bounded uploads, CSRF and Origin checks, favorites, source preview,
Markdown export, a responsive interface, tests and container instructions.

The runtime implementation and UI were newly developed for this derivative.
This is not a claim that Giang authored the upstream example or its model work.
The original author's name and license are intentionally kept in attribution.
No upstream research PDFs, scraped data, private contact details, screenshots,
branding, or model weights are redistributed. All example Markdown documents
in this repository are fictional teaching material created for YangDocs.

New YangDocs-specific code: Copyright (c) 2026 Nguyễn Hồng Giang, distributed
under the MIT terms in LICENSE, in addition to the retained upstream notice.
