"use strict";
const state = { jobs: [], profiles: [], selected: null, scores: new Map(), ai: false };
const $ = (selector) => document.querySelector(selector);
let workspacePromise;
let toastTimer;
async function workspace() {
  if (!workspacePromise) workspacePromise = fetch("/workspace", { credentials: "same-origin" }).then(async response => {
    if (!response.ok) throw new Error("Không thể mở không gian riêng.");
    return response.json();
  }).catch(error => { workspacePromise = null; throw error; });
  return workspacePromise;
}
async function api(path, options = {}) {
  const session = await workspace();
  const headers = new Headers(options.headers);
  const method = options.method || "GET";
  if (method !== "GET") headers.set("X-CSRF-Token", session.csrf_token);
  const response = await fetch(path, { ...options, headers, credentials: "same-origin" });
  const result = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401) workspacePromise = null;
    throw new Error(typeof result.detail === "string" ? result.detail : "Yêu cầu chưa hợp lệ. Hãy kiểm tra dữ liệu.");
  }
  return result;
}
function node(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}
function toast(message) {
  $("#toast").textContent = message;
  $("#toast").hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { $("#toast").hidden = true; }, 6000);
}
function empty(text) { return node("div", "empty", text); }
function currentProfile() { return state.profiles.find(profile => profile.id === state.selected); }
function selectProfile(id) {
  state.selected = id;
  sessionStorage.setItem("yangcareer.selectedCV", String(id));
  state.scores.clear();
  renderProfiles();
}
async function refreshProfiles() {
  state.profiles = await api("/cvs");
  if (!state.profiles.some(row => row.id === state.selected)) {
    state.selected = state.profiles[0]?.id || null;
  }
  $("#cv-count").textContent = String(state.profiles.length);
  renderProfiles();
}
function renderJobs() {
  const query = $("#search").value.trim().toLocaleLowerCase();
  const location = $("#location").value;
  const jobs = state.jobs.filter(job => {
    const text = [job.job_title, job.name, job.skills, job.job_description].join(" ").toLocaleLowerCase();
    return (!query || text.includes(query)) && (!location || job.work_location === location);
  });
  jobs.sort((a, b) => (state.scores.get(b.id) ?? -1) - (state.scores.get(a.id) ?? -1));
  const grid = $("#jobs-grid");
  grid.replaceChildren();
  $("#results-count").textContent = jobs.length + " cơ hội phù hợp với bộ lọc";
  if (!jobs.length) { grid.append(empty("Chưa có công việc phù hợp. Thử đổi từ khóa hoặc địa điểm.")); return; }
  for (const job of jobs) {
    const card = node("article", "job-card");
    const top = node("div", "job-top");
    top.append(node("span", "company-icon", job.name.split(" ").map(word => word[0]).slice(0, 2).join("")));
    const company = node("div");
    company.append(node("div", "company-name", job.name), node("div", "job-type", job.work_type));
    top.append(company);
    if (state.scores.has(job.id)) top.append(node("span", "match-score", Math.round(state.scores.get(job.id) * 100) + "% kỹ năng"));
    card.append(top, node("h3", "", job.job_title), node("div", "job-location", job.work_location + " · " + job.experience),
                node("p", "job-description", job.job_description));
    const chips = node("div", "chips");
    for (const skill of (job.skills || "").split(";").filter(Boolean).slice(0, 5)) chips.append(node("span", "chip", skill));
    card.append(chips);
    const bottom = node("div", "job-bottom");
    bottom.append(node("span", "job-salary", job.salary));
    const button = node("button", "button secondary", "Lưu ứng tuyển ↗");
    button.type = "button";
    button.addEventListener("click", async () => {
      if (!currentProfile()) { locationHash("profiles"); toast("Tạo hồ sơ kỹ năng trước khi lưu ứng tuyển."); return; }
      button.disabled = true;
      try {
        await api("/apply", { method: "POST", headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ cv_id: state.selected, job_id: job.id, status: "applied" }) });
        toast("Đã ghi nhận cho hồ sơ " + currentProfile().cv_info.name + ". Chưa gửi đến công ty.");
      } catch (error) { toast(error.message); } finally { button.disabled = false; }
    });
    bottom.append(button); card.append(bottom); grid.append(card);
  }
}
function renderProfiles() {
  const list = $("#profiles-list");
  list.replaceChildren();
  if (!state.profiles.length) { list.append(empty("Bạn chưa có hồ sơ. Tạo hồ sơ kỹ năng để bắt đầu.")); return; }
  for (const profile of state.profiles) {
    const card = node("article", "profile-card" + (profile.id === state.selected ? " selected" : ""));
    const details = node("div");
    details.append(node("h3", "", profile.cv_info.name || profile.filename),
                   node("p", "", (profile.cv_info.skills || []).join(" · ")));
    card.append(node("span", "avatar", String(profile.cv_info.name || "CV").charAt(0).toUpperCase()), details);
    const actions = node("div", "actions");
    const use = node("button", "button secondary", profile.id === state.selected ? "Đang sử dụng" : "Sử dụng");
    use.type = "button"; use.disabled = profile.id === state.selected;
    use.addEventListener("click", () => { selectProfile(profile.id); toast("Đã chọn hồ sơ."); });
    const remove = node("button", "button secondary danger", "Xóa");
    remove.type = "button";
    remove.addEventListener("click", async () => {
      if (!window.confirm("Xóa hồ sơ và lịch sử ứng tuyển liên quan?")) return;
      remove.disabled = true;
      try {
        await api("/delete-cv", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ file_id: profile.id }) });
        await refreshProfiles(); toast("Đã xóa hồ sơ.");
      } catch (error) { toast(error.message); remove.disabled = false; }
    });
    actions.append(use, remove); card.append(actions); list.append(card);
  }
}
async function renderApplications() {
  const list = $("#applications-list"); list.replaceChildren(empty("Đang tải lịch sử ứng tuyển…"));
  try {
    const rows = (await Promise.all(state.profiles.map(async profile => {
      const data = await api("/applications/" + profile.id);
      return data.applications.map(row => ({ ...row, profileName: profile.cv_info.name || profile.filename }));
    }))).flat();
    list.replaceChildren();
    if (!rows.length) { list.append(empty("Chưa có lần ứng tuyển nào. Chọn hồ sơ rồi lưu một công việc để thử.")); return; }
    rows.sort((a, b) => b.applied_at.localeCompare(a.applied_at));
    for (const row of rows) {
      const item = node("article", "application-row"), detail = node("div");
      detail.append(node("h3", "", row.job_title), node("p", "", row.profileName + " · " + row.work_location));
      const time = node("p", "", new Date(row.applied_at.replace(" ", "T") + (row.applied_at.includes("Z") ? "" : "Z")).toLocaleDateString("vi-VN"));
      detail.append(time); item.append(node("span", "company-icon", "CV"), detail,
        node("span", "status", ({ applied: "Đã ghi nhận", pending: "Đang chờ", accepted: "Đã nhận", rejected: "Đã đóng" })[row.status] || row.status));
      list.append(item);
    }
  } catch (error) { list.replaceChildren(empty(error.message)); }
}
function locationHash(view) { window.location.hash = view; }
function route() {
  const requested = window.location.hash.slice(1), view = ["profiles", "tracker", "about"].includes(requested) ? requested : "jobs";
  for (const element of document.querySelectorAll(".view")) element.hidden = element.id !== "view-" + view;
  for (const link of document.querySelectorAll("[data-view]")) link.classList.toggle("active", link.dataset.view === view);
  $("#view-label").textContent = ({ jobs: "KHÁM PHÁ", profiles: "HỒ SƠ", tracker: "ỨNG TUYỂN", about: "VỀ DỰ ÁN" })[view];
  if (view === "tracker") renderApplications();
}
$("#search").addEventListener("input", renderJobs);
$("#location").addEventListener("change", renderJobs);
$("#rank-button").addEventListener("click", async event => {
  if (!currentProfile()) { locationHash("profiles"); toast("Hãy tạo hoặc chọn một hồ sơ trước."); return; }
  const button = event.currentTarget; button.disabled = true;
  try {
    const result = await api("/jobs/search", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ cv_id: state.selected, limit: 100, offset: 0 }) });
    state.scores = new Map(result.jobs.map(job => [job.job_id, job.match_score || 0]));
    renderJobs(); toast("Đã sắp theo kỹ năng của " + currentProfile().cv_info.name + ". Đây không phải dự đoán kết quả tuyển dụng.");
  } catch (error) { toast(error.message); } finally { button.disabled = false; }
});
$("#manual-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = event.currentTarget, button = form.querySelector("button"), data = new FormData(form);
  const skills = String(data.get("skills")).split(/[,;]/).map(value => value.trim()).filter(Boolean);
  if (!skills.length) { toast("Nhập ít nhất một kỹ năng."); return; }
  button.disabled = true;
  try {
    const result = await api("/cvs/manual", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: String(data.get("name")).trim(), skills, career_objective: data.get("objective") }) });
    await refreshProfiles(); selectProfile(result.cv_id); form.reset(); toast("Đã lưu hồ sơ kỹ năng.");
  } catch (error) { toast(error.message); } finally { button.disabled = false; }
});
$("#pdf-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = event.currentTarget, button = $("#upload-button"), file = form.elements.pdf.files[0];
  if (!file || file.size > 10 * 1024 * 1024) { toast("Chọn file PDF tối đa 10 MB."); return; }
  if (!form.elements.consent.checked) { toast("Cần đồng ý gửi nội dung CV đến dịch vụ đã cấu hình."); return; }
  const data = new FormData(); data.append("file", file);
  button.disabled = true; button.textContent = "Đang phân tích…";
  try {
    const result = await api("/upload-cv", { method: "POST", body: data });
    await refreshProfiles(); selectProfile(result.cv_id); form.reset(); toast("Đã lưu CV. Hãy xem lại các kỹ năng được trích xuất.");
  } catch (error) { toast(error.message); } finally { button.disabled = !state.ai; button.textContent = "Gửi CV để phân tích"; }
});
window.addEventListener("hashchange", route);
async function start() {
  state.selected = Number(sessionStorage.getItem("yangcareer.selectedCV")) || null;
  try {
    const [data, health] = await Promise.all([api("/jobs?limit=1000"), api("/health")]);
    state.jobs = data.jobs; state.ai = health.ai_configured;
    $("#job-count").textContent = data.total;
    $("#ai-state").textContent = state.ai ? "Đã cấu hình" : "Kỹ năng";
    $("#ai-caption").textContent = state.ai ? "Có thể yêu cầu phân tích CV PDF" : "Tạo hồ sơ thủ công để dùng thử";
    $("#upload-button").disabled = !state.ai;
    $("#upload-note").textContent = state.ai ? "Nội dung được xử lý qua Gemini khi bạn gửi." : "Chưa cấu hình dịch vụ. Tạo hồ sơ kỹ năng ở bên cạnh để dùng thử.";
    for (const place of [...new Set(state.jobs.map(row => row.work_location))]) {
      const option = node("option", "", place); option.value = place; $("#location").append(option);
    }
    await refreshProfiles(); renderJobs(); route();
  } catch (error) { $("#load-error").textContent = error.message; $("#load-error").hidden = false; }
}
route();
start();
