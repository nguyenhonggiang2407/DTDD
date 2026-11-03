# Provenance

YangHooks implementation © 2026 Nguyễn Hồng Giang · MIT.

Reference: Trinhvhao/ai-engineering-hub, fork of patchy631/ai-engineering-hub, snapshot 86dc1699fe036e5cd21ed7d3c57419a7fc3c844b. Original file motia-content-creation/api.step.py is preserved unmodified in upstream/api.step.py. Original copyright © 2024 patchy631 and the full MIT license remain in LICENSE.

The reference demonstrates an API step that accepts a request, emits an event and returns requestId/status. YangHooks reuses that small acceptance-response concept. Its HMAC protocol, replay checks, local HTTP sender/CLI, private workspace, SQLite storage, key rotation, UI, tests and container are new implementations. No Motia pipeline, scraping, model, embedding or social posting runs here.

HTTP session/body helper patterns are adapted from Nguyễn Hồng Giang's own MIT YangData implementation. All sample data is fictional. The preserved upstream source is reference material and is not executed.
