export const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const date = parseDate(value);
  if (!date) {
    return '-';
  }
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

const parseDate = (value?: string | null) => {
  const text = String(value || '').trim();
  if (!text) return null;

  const direct = new Date(text);
  if (!Number.isNaN(direct.getTime())) {
    return direct;
  }

  const localMatch = /^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[ T](\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?/.exec(text);
  if (!localMatch) return null;
  const [, year, month, day, hour = '0', minute = '0', second = '0'] = localMatch;
  const local = new Date(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute), Number(second));
  return Number.isNaN(local.getTime()) ? null : local;
};

const formatUnit = (value: number, unit: string) => `${value}${unit}前`;

export const formatRelativeTime = (value?: string | null) => {
  if (!value) {
    return '未知';
  }
  const date = parseDate(value);
  if (!date) {
    return value;
  }
  const now = Date.now();
  const diffMs = now - date.getTime();
  if (diffMs < 0) {
    return '刚刚';
  }
  const diffSeconds = Math.floor(diffMs / 1000);
  if (diffSeconds < 60) {
    return '刚刚';
  }
  const minutes = Math.floor(diffSeconds / 60);
  if (minutes < 60) {
    return formatUnit(minutes, '分钟');
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return formatUnit(hours, '小时');
  }
  const days = Math.floor(hours / 24);
  if (days < 30) {
    return formatUnit(days, '天');
  }
  const months = Math.floor(days / 30);
  if (months < 12) {
    return `${months}个月前`;
  }
  const years = Math.floor(months / 12);
  return formatUnit(years, '年');
};

export const formatRecentTime = (value?: string | null, relativeDays = 7) => {
  if (!value) {
    return '未知';
  }
  const date = parseDate(value);
  if (!date) {
    return value;
  }
  const diffMs = Date.now() - date.getTime();
  if (diffMs > relativeDays * 24 * 60 * 60 * 1000) {
    return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`;
  }
  return formatRelativeTime(value);
};
