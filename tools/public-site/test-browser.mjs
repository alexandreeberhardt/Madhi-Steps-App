import assert from 'node:assert/strict';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.PUBLIC_SITE_URL || 'http://127.0.0.1:8114';
const browser = await chromium.launch({ headless: true });
try {
  for (const viewport of [{ width: 390, height: 844 }, { width: 1440, height: 900 }]) {
    const page = await browser.newPage({ viewport });
    const errors = [];
    const requests = [];
    page.on('pageerror', (error) => errors.push(error.message));
    page.on('request', (request) => requests.push(request.url()));
    await page.goto(base);
    await page.locator('#resume-voyage').waitFor();
    assert.doesNotMatch(await page.locator('body').innerText(), /décalage|retard|diffusé|semaine plus tard|publiée?s?/i);
    assert.match(await page.locator('#pays-actuel').innerText(), /Pays actuel/);
    assert.match(await page.locator('#resume-distance').innerText(), /km|m/);
    assert.match(await page.locator('#resume-aujourdhui').innerText(), /km|m/);
    assert.match(await page.locator('#resume-jours').innerText(), /jour/);
    const totalBefore = await page.locator('#resume-distance').innerText();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    if (await page.locator('#carte').isVisible()) {
      await page.getByRole('button', { name: 'Tout le voyage', exact: true }).click();
      await page.waitForFunction(() => document.querySelector('#periodes').getAttribute('aria-busy') === 'false');
      await page.getByRole('button', { name: '24 h', exact: true }).click();
      await page.waitForFunction(() => document.querySelector('#periodes').getAttribute('aria-busy') === 'false');
      assert.equal(await page.locator('#resume-distance').innerText(), totalBefore);
      await page.getByRole('button', { name: 'Recentrer', exact: true }).click();
      await page.locator('.leaflet-marker-icon').click();
      await page.locator('.bulle-coordonnees').waitFor();
      assert.equal(await page.locator('#map-error').isVisible(), false);
    }
    assert.deepEqual(errors, []);
    assert.ok(requests.every((url) => url.startsWith(base) || url.startsWith('https://tile.openstreetmap.org/')));
    assert.ok(!requests.some((url) => /\/api\//.test(url)));
    await page.screenshot({ path: `/tmp/madhi-public-${viewport.width}.png`, fullPage: true });
    // Refresh failure must preserve the previously loaded data.
    await page.route('**/data/manifest.json', (route) => route.fulfill({ status: 503, body: '' }));
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    await page.locator('#retry').waitFor();
    assert.match(await page.locator('#publication-status').innerText(), /dernières données/);
    await page.unroute('**/data/manifest.json');
    await page.getByRole('button', { name: 'Réessayer', exact: true }).click();
    await page.locator('#retry').waitFor({ state: 'hidden' });
    await page.close();
  }
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  await page.route('**/data/manifest.json', (route) => route.fulfill({ status: 503, body: '' }));
  await page.goto(base);
  await page.locator('#retry').waitFor();
  assert.match(await page.locator('#empty-title').innerText(), /indisponible/);
  assert.equal(await page.locator('#carte').isVisible(), false);
  await page.close();
  const emptyPage = await browser.newPage({ viewport: { width: 390, height: 844 } });
  await emptyPage.route('**/snapshot.json', async (route) => {
    const response = await route.fetch();
    const snapshot = await response.json();
    const generated = Date.now() - 4 * 3600000;
    Object.assign(snapshot, {
      generatedAt: new Date(generated).toISOString(),
      cutoffAt: new Date(generated - 168 * 3600000).toISOString(),
      trip: { name: 'Voyage', startedAt: null, endedAt: null },
      latest: null, latestPlace: null, totalPoints: 0, days: [],
    });
    await route.fulfill({ json: snapshot });
  });
  await emptyPage.route('**/overview.json', (route) => route.fulfill({ json: [] }));
  await emptyPage.goto(base);
  await emptyPage.locator('#publication-status').waitFor();
  assert.match(await emptyPage.locator('#empty-text').innerText(), /premières positions/);
  assert.match(await emptyPage.locator('#publication-status').innerText(), /pas été actualisé récemment/);
  assert.equal(await emptyPage.locator('#carte').isVisible(), false);
  assert.equal(await emptyPage.locator('#resume-voyage').isVisible(), false);
  await emptyPage.close();
  console.log('Browser checks passed: mobile, desktop, periods, popup, request isolation, failure and recovery.');
} finally {
  await browser.close();
}
