export function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  const pad = part => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** A fixed UTC+08:00 timestamp regardless of the editor's local timezone. */
export function formatBeijingTime(value = new Date()) {
  const instant = new Date(value);
  if (Number.isNaN(instant.getTime())) return '';
  const date = new Date(instant.getTime() + 8 * 60 * 60 * 1000);
  const pad = part => String(part).padStart(2, '0');
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}（北京时间）`;
}
