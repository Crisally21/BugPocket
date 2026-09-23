export const STATUSES = {
  NEW: { label: 'Новый', className: 'new' },
  IN_PROGRESS: { label: 'В работе', className: 'progress' },
  FIXED: { label: 'Исправлен', className: 'fixed' }
};

export const PRIORITIES = {
  LOW: { label: 'Низкий', className: 'low' },
  MEDIUM: { label: 'Средний', className: 'medium' },
  HIGH: { label: 'Высокий', className: 'high' }
};

export const FIELDS = [
  { name: 'header', label: 'Название бага', max: 200, required: true },
  { name: 'steps', label: 'Шаги воспроизведения', max: 10000 },
  { name: 'actualResult', label: 'Фактический результат', max: 10000 },
  { name: 'expectedResult', label: 'Ожидаемый результат', max: 10000 },
  { name: 'environment', label: 'Окружение', max: 2000 }
];

export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[char]);
}

export function parseRoute(hash) {
  const [path, query = ''] = (hash.replace(/^#/, '') || '/bugs').split('?');
  const params = new URLSearchParams(query);
  if (path === '/bugs' || path === '/') {
    return {
      type: 'list',
      status: Object.hasOwn(STATUSES, params.get('status')) ? params.get('status') : '',
      priority: Object.hasOwn(PRIORITIES, params.get('priority')) ? params.get('priority') : '',
      query: params.get('q') || ''
    };
  }
  if (path === '/bugs/new') return { type: 'create' };
  const match = path.match(/^\/bugs\/([1-9]\d*)(\/edit)?$/);
  if (match) return { type: match[2] ? 'edit' : 'detail', id: match[1] };
  return { type: 'notFound' };
}

export function listHash({ status = '', priority = '', query = '' } = {}) {
  const params = new URLSearchParams();
  if (Object.hasOwn(STATUSES, status)) params.set('status', status);
  if (Object.hasOwn(PRIORITIES, priority)) params.set('priority', priority);
  if (query.trim()) params.set('q', query.trim());
  return '#/bugs' + (params.size ? '?' + params.toString() : '');
}

export function validateBug(values) {
  const errors = {};
  for (const field of FIELDS) {
    const value = String(values[field.name] ?? '');
    if (field.required && !value.trim()) errors[field.name] = 'Укажи название бага.';
    else if (value.length > field.max) errors[field.name] = 'Не больше ' + field.max + ' символов.';
  }
  if (!Object.hasOwn(PRIORITIES, values.priority)) errors.priority = 'Выбери приоритет.';
  return errors;
}

export function searchBugs(bugs, query) {
  const normalized = query.trim().toLocaleLowerCase('ru');
  if (!normalized) return bugs;
  return bugs.filter(bug => bug.header.toLocaleLowerCase('ru').includes(normalized)
    || ('#' + bug.id).includes(normalized));
}

export function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
  }).format(date);
}

export function safeRelatedTaskUrl(value) {
  if (typeof value !== 'string' || !/^https?:\/\//i.test(value)) return null;
  try {
    const url = new URL(value);
    return (url.protocol === 'http:' || url.protocol === 'https:') && url.hostname ? value : null;
  } catch {
    return null;
  }
}
