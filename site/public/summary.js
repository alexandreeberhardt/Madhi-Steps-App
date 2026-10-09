// All inputs come from the same public snapshot, including the reference date.
export function totalDistance(points) {
  const radians = (degrees) => degrees * Math.PI / 180;
  let total = 0;
  for (let index = 1; index < points.length; index += 1) {
    const a = points[index - 1];
    const b = points[index];
    const haversine = Math.sin(radians(b.latitude - a.latitude) / 2) ** 2
      + Math.cos(radians(a.latitude)) * Math.cos(radians(b.latitude))
      * Math.sin(radians(b.longitude - a.longitude) / 2) ** 2;
    const bounded = Math.min(1, Math.max(0, haversine));
    total += 2 * 6371000 * Math.atan2(Math.sqrt(bounded), Math.sqrt(1 - bounded));
  }
  return total;
}

export function tripDays(snapshot) {
  if (snapshot.trip.startedAt === null) return null;
  const start = new Date(snapshot.trip.startedAt);
  const end = new Date(snapshot.trip.endedAt ?? snapshot.cutoffAt);
  const calendarDay = (value) => Date.UTC(value.getFullYear(), value.getMonth(), value.getDate());
  return Math.max(1, Math.round((calendarDay(end) - calendarDay(start)) / 86400000) + 1);
}

export function formatDistance(metres) {
  if (!Number.isFinite(metres)) return '-';
  if (metres < 1000) return `${Math.round(metres).toLocaleString('fr-FR')} m`;
  return `${(metres / 1000).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} km`;
}
