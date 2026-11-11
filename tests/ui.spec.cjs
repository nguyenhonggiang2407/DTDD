const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

test("desktop job list loads and has no script errors", async ({ page }) => {
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.setViewportSize({ width: 1440, height: 1050 });
  await page.goto("/app/");
  await expect(page.locator(".job-card")).toHaveCount(6);
  await expect(page.locator("#job-count")).toHaveText("6");
  await fs.mkdir("docs/screenshots", { recursive: true });
  await page.screenshot({ path: "docs/screenshots/desktop.png", fullPage: true });
  expect(errors).toEqual([]);
});

test("manual profile, matching and application tracking work", async ({ page }) => {
  await page.goto("/app/#profiles");
  await page.getByLabel("Tên hiển thị").fill("UI Demo Student");
  await page.getByLabel("Kỹ năng, cách nhau bằng dấu phẩy").fill("Java, SQL");
  await page.getByRole("button", { name: "Lưu hồ sơ", exact: true }).click();
  await expect(page.locator(".profile-card h3")).toHaveText("UI Demo Student");
  await page.getByRole("link", { name: "Khám phá cơ hội" }).click();
  await page.getByRole("button", { name: "Sắp theo kỹ năng ↗", exact: true }).click();
  await expect(page.locator(".job-card").first().locator("h3")).toHaveText("Backend Intern");
  await page.locator(".job-card").first().getByRole("button", { name: "Lưu ứng tuyển ↗" }).click();
  await expect(page.locator("#toast")).toContainText("Đã ghi nhận");
  await page.getByRole("link", { name: "Theo dõi ứng tuyển" }).click();
  await expect(page.locator(".application-row")).toHaveCount(1);
  await expect(page.locator(".application-row h3")).toHaveText("Backend Intern");
});

test("changing or removing the active profile clears old and delayed matches", async ({ page }) => {
  const profile = name => page.locator(".profile-card").filter({ hasText: name });
  const rank = page.getByRole("button", { name: "Sắp theo kỹ năng ↗", exact: true });
  const jobs = page.getByRole("link", { name: "Khám phá cơ hội" });
  const profiles = page.getByRole("link", { name: "Hồ sơ & kỹ năng" });
  const badges = page.locator(".match-score");

  await page.goto("/app/#profiles");
  for (const [name, skills] of [["Backend student", "Java, SQL"], ["UI student", "JavaScript, HTML"]]) {
    await page.getByLabel("Tên hiển thị").fill(name);
    await page.getByLabel("Kỹ năng, cách nhau bằng dấu phẩy").fill(skills);
    await page.getByRole("button", { name: "Lưu hồ sơ", exact: true }).click();
    await expect(profile(name).getByRole("button", { name: "Đang sử dụng" })).toBeVisible();
  }
  await jobs.click();
  await rank.click();
  await expect(badges.first()).toBeVisible();
  await profiles.click();
  await profile("Backend student").getByRole("button", { name: "Sử dụng", exact: true }).click();
  await jobs.click();
  await expect(badges).toHaveCount(0);

  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await page.route("**/jobs/search", async route => {
    const response = await route.fetch();
    await gate;
    await route.fulfill({ response });
  }, { times: 1 });
  try {
    const requested = page.waitForRequest("**/jobs/search");
    await rank.click();
    await requested;
    await profiles.click();
    await profile("UI student").getByRole("button", { name: "Sử dụng", exact: true }).click();
    await profile("Backend student").getByRole("button", { name: "Sử dụng", exact: true }).click();
    await jobs.click();
    release();
    await expect(rank).toBeEnabled();
    await expect(badges).toHaveCount(0);
  } finally {
    release();
  }
  await rank.click();
  await expect(badges.first()).toBeVisible();
  await profiles.click();
  page.once("dialog", dialog => dialog.accept());
  await profile("Backend student").getByRole("button", { name: "Xóa", exact: true }).click();
  await expect(profile("UI student").getByRole("button", { name: "Đang sử dụng" })).toBeVisible();
  await jobs.click();
  await expect(badges).toHaveCount(0);
});

test("mobile layout loads without horizontal page overflow", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/app/");
  await expect(page.locator(".job-card")).toHaveCount(6);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
  expect(overflow).toBe(false);
  await fs.mkdir("docs/screenshots", { recursive: true });
  await page.screenshot({ path: "docs/screenshots/mobile.png", fullPage: true });
});
