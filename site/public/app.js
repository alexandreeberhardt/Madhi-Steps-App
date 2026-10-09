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
const dateFormat = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
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
    rechercheEnCours: false, adresseDesactivee: false,
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
  element('titre-voyage').textContent = snapshot.trip.name;
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
    `Période du ${date(bounds.from)} au ${date(bounds.to)}.`
    + (data.sampled ? ' Tracé simplifié.' : '')
    + (points.length === 0 ? ' Aucun point sur cette période.' : '')
    + (snapshot.trip.endedAt ? ` Voyage terminé le ${date(snapshot.trip.endedAt)}.` : '');
  try {
    if (map === null) map = creerCarte(element('carte'), { surClicPoint: selectPoint });
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
    rechercheEnCours: false,
  });
  if (!visible) return;
  element('resume-distance').textContent = formatDistance(summary.totalMetres);
  element('resume-aujourdhui').textContent = formatDistance(summary.todayMetres);
  element('resume-jours').textContent = days === 1 ? '1er jour' : `${days.toLocaleString('fr-FR')}ème jour`;
  element('resume-depart').textContent = new Intl.DateTimeFormat('fr-FR', {
    day: '2-digit', month: '2-digit', year: '2-digit',
  }).format(new Date(snapshot.trip.startedAt));
}

function renderStatus() {
  const empty = data?.snapshot.latest == null;
  const central = element('message-central');
  central.hidden = !empty;
  const text = error
    ? 'Le voyage est momentanément indisponible. Réessaie dans quelques instants.'
    : busy && data === null
      ? 'Chargement du voyage…'
      : 'Les premières positions du voyage apparaîtront ici.';
  element('empty-text').textContent = text;
  element('empty-title').textContent = error ? 'Voyage indisponible' : 'Le voyage de Madhi';
  central.classList.toggle('chargement', busy && data === null);
  const stale = data !== null && publicationStale(data.snapshot);
  const notice = element('publication-status');
  notice.hidden = !error && !stale;
  notice.textContent = error && data !== null
    ? 'Actualisation impossible. Les dernières données chargées restent affichées.'
    : stale ? 'Le site n’a pas été actualisé récemment. Les dernières données disponibles restent affichées.' : '';
  element('retry').hidden = !error;
  element('retry').disabled = busy;
  element('refresh-state').textContent = busy && data !== null ? 'Actualisation…' : '';
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
