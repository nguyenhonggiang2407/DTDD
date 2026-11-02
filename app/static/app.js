"use strict";

const $ = (selector) => document.querySelector(selector);
const state = { csrf: "", documents: [], favorites: false, results: null, preview: null, deleting: null };
let toastTimer;
let searchSequence = 0;

function node(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}

function action(label, className, text, handler) {
  const button = node("button", className, text);
  button.type = "button";
  button.setAttribute("aria-label", label);
  button.addEventListener("click", handler);
  return button;
}

function error(message = "") {
  $("#error").textContent = message;
  $("#error").hidden = !message;
}

function toast(message) {
  clearTimeout(toastTimer);
  $("#toast").textContent = message;
  $("#toast").hidden = false;
  toastTimer = setTimeout(() => { $("#toast").hidden = true; }, 4500);
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set("X-CSRF-Token", state.csrf);
  if (options.body && !(options.body instanceof FormData)) headers.set("Content-Type", "application/json");
  const response = await fetch(path, { ...options, headers, credentials: "same-origin" });
  if (!response.ok) {
    let message = "Không thể hoàn tất. Vui lòng thử lại.";
    try { message = (await response.json()).detail || message; } catch { /* Generic server error. */ }
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.json();
}

async function perform(task) {
  error();
  try { await task(); } catch (err) { error(err.message || "Mất kết nối với máy chủ."); }
}

function formatBytes(bytes) {
  return bytes < 1024 ? `${bytes} B` : bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function renderEmpty(title, description, sample = false) {
  const empty = node("div", "empty");
  empty.append(node("span", "empty-symbol", "▤"), node("h3", "", title), node("p", "", description));
  if (sample) empty.append(action("Thử bộ tài liệu mẫu", "button secondary", "Thử bộ tài liệu mẫu →", loadDemo));
  return empty;
}

function renderDocuments() {
  const format = $("#file-filter").value;
  const docs = state.documents.filter(doc => (!state.favorites || doc.favorite) && (format === "all" || doc.kind === format));
  $("#documents").replaceChildren();
  $("#library-count").textContent = docs.length;
  $("#library-title").firstChild.textContent = state.favorites ? "Tài liệu đã ghim " : "Thư viện tài liệu ";
  $("#library-description").textContent = state.favorites ? "Những tài liệu bạn muốn quay lại thường xuyên." : "Một nơi cho những điều bạn muốn ghi nhớ.";
  if (!docs.length) {
    $("#documents").append(renderEmpty(
      state.favorites ? "Chưa có tài liệu đã ghim" : format !== "all" ? "Chưa có định dạng này" : "Bắt đầu từ một tài liệu.",
      state.favorites ? "Nhấn ngôi sao bên cạnh tài liệu để lưu vào đây." : "Thêm tài liệu của bạn hoặc khám phá 3 tài liệu mẫu được biên soạn sẵn.",
      !state.documents.length,
    ));
    return;
  }
  for (const doc of docs) {
    const card = node("article", "document-card");
    card.dataset.documentId = doc.id;
    const icon = node("span", `file-icon ${doc.kind}`, doc.kind.toUpperCase());
    icon.setAttribute("aria-hidden", "true");
    const info = node("div", "document-info");
    const name = action(`Mở ${doc.name}`, "document-name", doc.name, () => perform(() => preview(doc.id, 1)));
    const metadata = node("div", "document-meta");
    metadata.append(node("span", "", `${doc.page_count} trang`), node("span", "meta-dot", "●"),
      node("span", "", `${doc.chunk_count} đoạn`), node("span", "meta-dot", "●"), node("span", "", formatBytes(doc.size)));
    info.append(name, metadata);
    const actions = node("div", "document-actions");
    const favorite = action(`${doc.favorite ? "Bỏ ghim" : "Ghim"} ${doc.name}`, `icon-button${doc.favorite ? " pinned" : ""}`, doc.favorite ? "★" : "☆", () => perform(async () => {
      await api(`/api/documents/${doc.id}`, { method: "PATCH", body: JSON.stringify({ favorite: !doc.favorite }) });
      await refresh();
      if (state.results && state.favorites) await search(state.results.query);
      toast(doc.favorite ? "Đã bỏ ghim tài liệu." : "Đã ghim tài liệu.");
    }));
    favorite.setAttribute("aria-pressed", Boolean(doc.favorite));
    const remove = action(`Xóa ${doc.name}`, "icon-button delete-button", "×", () => {
      state.deleting = doc;
      $("#delete-name").textContent = doc.name;
      $("#delete-dialog").showModal();
    });
    actions.append(favorite, remove);
    card.append(icon, info, actions);
    $("#documents").append(card);
  }
}

async function refresh() {
  const result = await api("/api/documents");
  state.documents = result.documents;
  $("#stat-documents").textContent = result.stats.documents;
  $("#stat-pages").textContent = result.stats.pages;
  $("#stat-favorites").textContent = result.stats.favorites;
  $("#nav-total").textContent = result.stats.documents;
  $("#nav-favorites").textContent = result.stats.favorites;
  renderDocuments();
}

async function loadDemo() {
  const buttons = [$("#sample-sidebar"), ...document.querySelectorAll('.empty button')];
  buttons.forEach(button => { button.disabled = true; });
  await perform(async () => {
    const result = await api("/api/demo", { method: "POST" });
    await refresh();
    toast(result.added ? `Đã thêm ${result.added} tài liệu mẫu. Thử tìm “làm việc từ xa”.` : "Bộ tài liệu mẫu đã có trong thư viện.");
  });
  buttons.forEach(button => { button.disabled = false; });
}

function normalize(text) {
  return text.toLowerCase().replace(/đ/g, "d").normalize("NFD").replace(/[\u0300-\u036f]/g, "");
}

function highlighted(text, terms) {
  const fragment = document.createDocumentFragment();
  const words = /[\p{L}\p{N}]+/gu;
  let last = 0;
  for (const match of text.matchAll(words)) {
    fragment.append(document.createTextNode(text.slice(last, match.index)));
    fragment.append(terms.some(term => normalize(match[0]).startsWith(term)) ? node("mark", "", match[0]) : document.createTextNode(match[0]));
    last = match.index + match[0].length;
  }
  fragment.append(document.createTextNode(text.slice(last)));
  return fragment;
}

function renderResults(result) {
  $("#results-section").hidden = false;
  $("#results-title").textContent = `“${result.query}”`;
  $("#results-summary").textContent = `${result.hits.length} đoạn trích · ${result.message}`;
  $("#export-results").disabled = !result.hits.length;
  $("#results").replaceChildren();
  if (!result.hits.length) {
    $("#results").append(renderEmpty("Chưa tìm thấy nội dung phù hợp.", "Thử từ khóa ngắn hơn, kiểm tra định dạng đang lọc hoặc thêm tài liệu liên quan.", !state.documents.length));
    return;
  }
  for (const hit of result.hits) {
    const card = node("article", "result-card");
    const top = node("div", "result-top");
    top.append(node("span", "citation-badge", `[${hit.citation}]`), node("span", "result-source", hit.name), node("span", "page-badge", `Trang ${hit.page}`));
    const quote = node("blockquote");
    quote.append(highlighted(hit.text, result.terms));
    const bottom = node("div", "result-bottom");
    bottom.append(action(`Mở nguồn ${hit.name} trang ${hit.page}`, "", "Đọc trong tài liệu ↗", () => perform(() => preview(hit.document_id, hit.page))),
      action(`Sao chép trích dẫn ${hit.citation}`, "", "Sao chép trích dẫn", () => perform(async () => {
        await copyText(`${hit.text}\n\n[${hit.citation}] ${hit.name} · Trang ${hit.page}`);
      })));
    card.append(top, quote, bottom);
    $("#results").append(card);
  }
}

async function search(query) {
  const sequence = ++searchSequence;
  const button = $("#search-form button");
  $("#search-input").value = query;
  button.disabled = true;
  $("#results").setAttribute("aria-busy", "true");
  try {
    const result = await api("/api/search", { method: "POST", body: JSON.stringify({ query, favorites: state.favorites }) });
    if (sequence !== searchSequence) return;
    state.results = result;
    renderResults(result);
  } finally {
    if (sequence === searchSequence) {
      button.disabled = false;
      $("#results").removeAttribute("aria-busy");
    }
  }
}

async function preview(documentId, page) {
  const data = await api(`/api/documents/${documentId}/pages/${page}`);
  state.preview = data;
  $("#preview-title").textContent = data.document.name;
  $("#preview-text").textContent = data.text || "Trang này không có văn bản có thể trích xuất.";
  $("#preview-page-label").textContent = `Trang ${data.number} / ${data.document.page_count}`;
  $("#previous-page").disabled = data.number <= 1;
  $("#next-page").disabled = data.number >= data.document.page_count;
  if (!$("#preview-dialog").open) $("#preview-dialog").showModal();
  $("#preview-text").scrollTop = 0;
}

async function copyText(text) {
  if (!navigator.clipboard) throw new Error("Trình duyệt cần HTTPS để sao chép. Bạn có thể chọn văn bản và sao chép thủ công.");
  await navigator.clipboard.writeText(text);
  toast("Đã sao chép kèm nguồn.");
}

function setView(favorites) {
  state.favorites = favorites;
  for (const [id, selected] of [["#library-view", !favorites], ["#favorites-view", favorites]]) {
    $(id).classList.toggle("active", selected);
    if (selected) $(id).setAttribute("aria-current", "page");
    else $(id).removeAttribute("aria-current");
  }
  $("#breadcrumb-current").textContent = favorites ? "Đã ghim" : "Thư viện";
  renderDocuments();
  if (state.results) perform(() => search(state.results.query));
}

$("#library-view").addEventListener("click", () => setView(false));
$("#favorites-view").addEventListener("click", () => setView(true));
$("#file-filter").addEventListener("change", renderDocuments);
$("#sample-sidebar").addEventListener("click", loadDemo);
$("#open-upload").addEventListener("click", () => {
  $("#upload-message").textContent = "";
  $("#upload-dialog").showModal();
});
document.querySelectorAll("[data-close]").forEach(button => button.addEventListener("click", () => $("#" + button.dataset.close).close()));
document.querySelectorAll("[data-query]").forEach(button => button.addEventListener("click", () => perform(() => search(button.dataset.query))));
$("#search-form").addEventListener("submit", event => { event.preventDefault(); perform(() => search($("#search-input").value.trim())); });
$("#clear-results").addEventListener("click", () => {
  ++searchSequence;
  $("#search-form button").disabled = false;
  state.results = null;
  $("#results-section").hidden = true;
  $("#search-input").value = "";
  $("#search-input").focus();
});
$("#file-input").addEventListener("change", () => {
  $("#selected-files").textContent = Array.from($("#file-input").files).map(file => `${file.name} (${formatBytes(file.size)})`).join(" · ");
});
$("#upload-form").addEventListener("submit", async event => {
  event.preventDefault();
  const files = Array.from($("#file-input").files);
  const submit = $("#upload-submit");
  submit.disabled = true;
  $("#file-input").disabled = true;
  let added = 0;
  const failures = [];
  try {
    for (const [i, file] of files.entries()) {
      $("#upload-message").textContent = `Đang xử lý tài liệu ${i + 1}/${files.length}…`;
      try {
        if (file.size > 5 * 1024 * 1024) throw new Error("Tệp vượt quá 5 MB.");
        const data = new FormData();
        data.append("file", file);
        await api("/api/documents", { method: "POST", body: data });
        added += 1;
      } catch (err) { failures.push(`${file.name}: ${err.message}`); }
    }
    await refresh();
    if (state.results) await search(state.results.query);
    if (!failures.length) {
      $("#upload-dialog").close();
      $("#upload-form").reset();
      $("#selected-files").textContent = "";
      toast(`Đã thêm ${added} tài liệu vào thư viện.`);
    } else {
      $("#upload-message").textContent = `Đã thêm ${added}/${files.length} tài liệu. ${failures.join(" · ")}`;
    }
  } catch (err) { $("#upload-message").textContent = err.message; }
  finally { submit.disabled = false; $("#file-input").disabled = false; }
});
$("#confirm-delete").addEventListener("click", async () => {
  if (!state.deleting) return;
  const button = $("#confirm-delete");
  button.disabled = true;
  await perform(async () => {
    await api(`/api/documents/${state.deleting.id}`, { method: "DELETE" });
    $("#delete-dialog").close();
    state.deleting = null;
    await refresh();
    if (state.results) await search(state.results.query);
    toast("Đã xóa tài liệu và chỉ mục tìm kiếm.");
  });
  button.disabled = false;
});
$("#previous-page").addEventListener("click", () => perform(() => preview(state.preview.document.id, state.preview.number - 1)));
$("#next-page").addEventListener("click", () => perform(() => preview(state.preview.document.id, state.preview.number + 1)));
$("#copy-page").addEventListener("click", () => perform(() => copyText(`${state.preview.text}\n\n${state.preview.document.name} · Trang ${state.preview.number}`)));
$("#export-results").addEventListener("click", () => perform(async () => {
  if (!state.results) return;
  const response = await fetch("/api/export", {
    method: "POST", credentials: "same-origin",
    headers: { "X-CSRF-Token": state.csrf, "Content-Type": "application/json" },
    body: JSON.stringify({ query: state.results.query, favorites: state.favorites }),
  });
  if (!response.ok) throw new Error("Không xuất được ghi chú. Hãy thử tìm kiếm lại.");
  const url = URL.createObjectURL(await response.blob());
  const link = node("a");
  link.href = url;
  link.download = "yangdocs-notes.md";
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  toast("Đã xuất ghi chú kèm nguồn.");
}));
document.addEventListener("keydown", event => {
  if (event.key === "/" && !/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName) && !document.querySelector("dialog[open]")) {
    event.preventDefault();
    $("#search-input").focus();
  }
});

perform(async () => {
  const session = await api("/api/session");
  state.csrf = session.csrf;
  await refresh();
});
