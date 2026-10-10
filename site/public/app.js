import { createPublicSource, PERIODS, publicationStale } from './data.js';
import {
  creerCarte, afficherDernierePosition, afficherFond, afficherTrajet,
  ajusterVue, rafraichirTaille, montrerBulle, fermerBulle,
} from '../components/map.js';
import { rendrePaysActuel } from '../components/current-country.js';
import { formatDistance, tripDays } from './summary.js';
import { contenuBulle } from '../components/point-bubble.js';

const source = createPublicSource();
const element = (id) => document.getElementById(id);
const dateFormat = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' });
const date = (value) => dateFormat.format(new Date(value));
let period = 'TODAY';
let displayedPeriod = null;
let displayedVersion = null;
let data = null;
let map = null;
let sequence = 0;
let busy = false;
let error = false;

for (const item of PERIODS) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'bouton-periode';
  button.textContent = item.label;
  button.dataset.period = item.id;
  button.addEventListener('click', () => {
    if (period === item.id && !error) return;
    period = item.id;
    load();
  });
  element('periodes').append(button);
}
element('recentrer').addEventListener('click', () => { if (map) ajusterVue(map); });
element('retry').addEventListener('click', load);

function selectPoint(point) {
  if (!point) { fermerBulle(map); return; }
  const latestPlace = point.id === data.snapshot.latest?.id ? data.snapshot.latestPlace : null;
  montrerBulle(map, point, contenuBulle({
    point, maintenant: new Date(), adresse: latestPlace?.address ?? null,
    rechercheEnCours: false, adresseDesactivee: false, locale: 'en-GB',
  }));
}

async function load() {
  const current = ++sequence;
  const requestedPeriod = period;
  busy = true;
  renderStatus();
  try {
    const result = await source.load(requestedPeriod);
    if (sequence !== current) return;
    const changed = displayedVersion !== result.version || displayedPeriod !== requestedPeriod;
    data = result;
    displayedPeriod = requestedPeriod;
    displayedVersion = result.version;
    error = false;
    renderData(changed);
  } catch (cause) {
    if (sequence !== current) return;
    error = true;
    // Keep the previous map and its actual selected period on a failed request.
    if (displayedPeriod !== null) period = displayedPeriod;
  } finally {
    if (sequence === current) {
      busy = false;
      renderStatus();
    }
  }
}

function renderData(changed) {
  const { snapshot, bounds, points, overview } = data;
  const latest = snapshot.latest;
  element('titre-voyage').textContent = 'Travel 2026';
  renderSummary();
  element('carte').hidden = latest === null;
  element('position').hidden = latest === null;
  element('recentrer').hidden = latest === null;
  element('periodes').hidden = latest === null;
  element('couverture').hidden = latest === null;
  if (latest === null) return;
  element('position-date').textContent = date(latest.recordedAt);
  const place = snapshot.latestPlace;
  element('position-lieu').textContent = place?.address
    ?? `${latest.latitude.toFixed(5)}, ${latest.longitude.toFixed(5)}`;
  element('couverture').textContent =
    `Period: ${date(bounds.from)} to ${date(bounds.to)}.`
    + (data.sampled ? ' Route simplified.' : '')
    + (points.length === 0 ? ' No points in this period.' : '')
    + (snapshot.trip.endedAt ? ` Trip ended on ${date(snapshot.trip.endedAt)}.` : '');
  try {
    if (map === null) map = creerCarte(element('carte'), { surClicPoint: selectPoint, locale: 'en' });
    rafraichirTaille(map);
    if (changed) {
      fermerBulle(map);
      afficherFond(map, displayedPeriod === 'ALL' ? [] : overview);
      afficherDernierePosition(map, latest, selectPoint);
      afficherTrajet(map, points);
      ajusterVue(map);
    }
    element('map-error').hidden = true;
  } catch (cause) {
    element('map-error').hidden = false;
  }
}

function renderSummary() {
  const { snapshot, summary } = data;
  const days = tripDays(snapshot);
  const visible = days !== null && snapshot.latest !== null;
  element('resume-voyage').hidden = !visible;
  rendrePaysActuel(element('pays-actuel'), {
    visible,
    pays: snapshot.latestPlace?.country ?? null,
    codePays: snapshot.latestPlace?.countryCode ?? null,
    rechercheEnCours: false, locale: 'en',
  });
  if (!visible) return;
  element('resume-distance').textContent = formatDistance(summary.totalMetres);
  element('resume-aujourdhui').textContent = formatDistance(summary.todayMetres);
  element('resume-jours').textContent = days.toLocaleString('en-GB', { maximumFractionDigits: 0 });
  element('resume-depart').textContent = new Intl.DateTimeFormat('en-GB', {
    day: '2-digit', month: '2-digit', year: '2-digit',
  }).format(new Date(snapshot.trip.startedAt));
}

function renderStatus() {
  const empty = data?.snapshot.latest == null;
  const central = element('message-central');
  central.hidden = !empty;
  const text = error
    ? 'The trip is temporarily unavailable. Please try again shortly.'
    : busy && data === null
      ? 'Loading trip…'
      : 'The first trip locations will appear here.';
  element('empty-text').textContent = text;
  element('empty-title').textContent = error ? 'Trip unavailable' : 'Travel 2026';
  central.classList.toggle('chargement', busy && data === null);
  const stale = data !== null && publicationStale(data.snapshot);
  const notice = element('publication-status');
  notice.hidden = !error && !stale;
  notice.textContent = error && data !== null
    ? 'Could not refresh. The last loaded data is still shown.'
    : stale ? 'This site has not been updated recently. The latest available data is still shown.' : '';
  element('retry').hidden = !error;
  element('retry').disabled = busy;
  element('refresh-state').textContent = busy && data !== null ? 'Refreshing…' : '';
  for (const button of element('periodes').children) {
    button.setAttribute('aria-pressed', String(button.dataset.period === period));
  }
  element('periodes').setAttribute('aria-busy', String(busy));
}

setInterval(() => { if (document.visibilityState === 'visible') load(); }, 5 * 60 * 1000);
setInterval(() => { if (document.visibilityState === 'visible') renderStatus(); }, 60 * 1000);
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') load();
});
load();
