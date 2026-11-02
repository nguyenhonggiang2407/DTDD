# Provenance & attribution

YangQueue is maintained and branded for **Nguyễn Hồng Giang**
([nguyenhonggiang2407](https://github.com/nguyenhonggiang2407)).

The starting reference is the MIT-licensed
[motia-content-creation](https://github.com/Trinhvhao/ai-engineering-hub/tree/86dc1699fe036e5cd21ed7d3c57419a7fc3c844b/motia-content-creation)
example in **patchy631/ai-engineering-hub**, accessed through the Trinhvhao fork.
Inspected snapshot: **86dc1699fe036e5cd21ed7d3c57419a7fc3c844b**.

The original **Copyright (c) 2024 patchy631** and the complete MIT terms remain
in LICENSE. Reference files retained in upstream/ are unmodified in substance:
api.step.py, scrape.step.py and generate-linkedin.step.py. They are not imported,
executed or packaged in the runtime container.

YangQueue adopts the asynchronous acceptance DTO idea (requestId/status) and
API → event → processing-step structure. It replaces scraping, model generation
and social scheduling with bounded local data processors. The SQLite queue,
idempotency ledger, lease fencing, retry policy, worker management, private
workspace HTTP layer, UI and tests are new implementations. This is not a
claim that Giang wrote the original Motia example, its framework or model work.

No upstream research material, private data, logos, screenshots, credentials
or model weights are redistributed. All demo jobs are fictional.

The YangQueue-specific code is Copyright (c) 2026 Nguyễn Hồng Giang and is
distributed under the MIT terms in LICENSE alongside the retained upstream notice.
