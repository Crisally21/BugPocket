import {
  STATUSES, PRIORITIES, FIELDS, escapeHtml as esc, parseRoute,
  listHash, validateBug, searchBugs, formatDate, safeRelatedTaskUrl
} from './ui.js';

const view = document.querySelector('#view');
const main = document.querySelector('#main');
const toast = document.querySelector('#toast');
let activeHash = location.hash || '#/bugs';
let listReturnHash = '#/bugs';
let dirty = false;
let saving = false;
let readController;
let renderVersion = 0;
let toastTimer;
const pocket = '<svg viewBox="0 0 48 48" fill="none" aria-hidden="true"><path d="M13 9h22v22a11 11 0 0 1-22 0V9Z" stroke="currentColor" stroke-width="2"/><path d="m18 23 5 5 8-9M8 16h5m22 0h5M8 30h5m22 0h5M18 5l2 4m10-4-2 4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

function notify(message) {
  clearTimeout(toastTimer);
  toast.textContent = message;
  toast.hidden = false;
  toastTimer = setTimeout(() => { toast.hidden = true; }, 4500);
}

async function api(path, { method = 'GET', body, signal } = {}) {
  const controller = new AbortController();
  const cancel = () => controller.abort();
  if (signal?.aborted) controller.abort();
  signal?.addEventListener('abort', cancel, { once: true });
  let timedOut = false;
  const timer = setTimeout(() => { timedOut = true; controller.abort(); }, 15000);
  try {
    const response = await fetch('/api/bugs' + path, {
      method,
      headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
      signal: controller.signal
    });
    let data;
    try {
      data = await response.json();
    } catch (error) {
      if (error.name === 'AbortError') throw error;
      throw new Error('Сервер вернул неожиданный ответ. Попробуй ещё раз.');
    }
    if (!response.ok) {
      const error = new Error(response.status >= 500
        ? 'На сервере произошла ошибка. Попробуй ещё раз немного позже.'
        : data.detail || 'Не удалось выполнить запрос.');
      error.status = response.status;
      error.fields = data.errors || {};
      throw error;
    }
    return data;
  } catch (error) {
    if (timedOut) throw new Error('Сервер не ответил вовремя. Проверь соединение и повтори попытку.');
    if (error.name === 'AbortError') throw error;
    if (error instanceof TypeError) throw new Error('Нет связи с сервером. Убедись, что приложение запущено, и попробуй ещё раз.');
    throw error;
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', cancel);
  }
}

function badge(status) {
  const item = STATUSES[status];
  return item ? '<span class="badge ' + item.className + '"><span class="status-dot" aria-hidden="true"></span>' + item.label + '</span>' : '—';
}

function priority(value) {
  const item = PRIORITIES[value];
  return item ? '<span class="priority ' + item.className + '"><span class="priority-bars" aria-hidden="true"><i></i><i></i><i></i></span>' + item.label + '</span>' : '—';
}

function options(items, selected, emptyLabel) {
  return (emptyLabel ? '<option value="">' + emptyLabel + '</option>' : '') +
    Object.entries(items).map(([value, item]) =>
      '<option value="' + value + '"' + (value === selected ? ' selected' : '') + '>' + item.label + '</option>').join('');
}

function breadcrumb(label, bug) {
  return '<nav class="breadcrumb" aria-label="Путь страницы"><a href="' + esc(listReturnHash) + '">← К списку</a>' +
    (bug ? '<span aria-hidden="true">/</span><a href="#/bugs/' + bug.id + '">Баг #' + bug.id + '</a>' : '') +
    '<span aria-hidden="true">/</span><span>' + esc(label) + '</span></nav>';
}

function finishRender(title) {
  document.title = title + ' — BugPocket';
  view.querySelector('h1')?.setAttribute('tabindex', '-1');
  (view.querySelector('h1') || main).focus({ preventScroll: true });
}

function showError(error) {
  const missing = error.status === 404;
  view.innerHTML = '<section class="panel empty-state"><div class="empty-art">' + pocket + '</div><h1>' +
    (missing ? 'Баг не найден' : 'Не удалось загрузить данные') + '</h1><p class="subtitle">' +
    esc(missing ? 'Возможно, этой записи уже нет. Вернись к списку и выбери другой баг.' : error.message) + '</p>' +
    (missing ? '<a class="btn btn-primary" href="' + esc(listReturnHash) + '">К списку багов</a>' :
      '<button type="button" class="btn btn-primary" id="retry">Попробовать снова</button>') + '</section>';
  view.querySelector('#retry')?.addEventListener('click', render);
  finishRender(missing ? 'Баг не найден' : 'Ошибка загрузки');
}

async function render() {
  const version = ++renderVersion;
  readController?.abort();
  readController = new AbortController();
  const signal = readController.signal;
  const route = parseRoute(location.hash);
  activeHash = location.hash || '#/bugs';
  dirty = false;
  const selected = route.type === 'list' && route.status ? route.status : 'all';
  document.querySelectorAll('[data-nav]').forEach(link => {
    if (link.dataset.nav === selected) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  view.innerHTML = '<div class="loading" role="status"><span class="spinner" aria-hidden="true"></span>Загружаем данные…</div>';
  try {
    if (route.type === 'list') {
      listReturnHash = listHash(route);
      const params = new URLSearchParams();
      if (route.status) params.set('status', route.status);
      if (route.priority) params.set('priority', route.priority);
      const bugs = await api(params.size ? '?' + params : '', { signal });
      if (version !== renderVersion) return;
      renderList(bugs, route);
    } else if (route.type === 'create') {
      renderForm();
    } else if (route.type === 'detail' || route.type === 'edit') {
      const bug = await api('/' + route.id, { signal });
      if (version !== renderVersion) return;
      if (route.type === 'edit') renderForm(bug);
      else renderDetail(bug);
    } else {
      view.innerHTML = '<section class="panel empty-state"><h1>Страница не найдена</h1><p>Проверь адрес или вернись к багам.</p><a class="btn btn-primary" href="#/bugs">Все баги</a></section>';
      finishRender('Страница не найдена');
    }
  } catch (error) {
    if (error.name !== 'AbortError' && version === renderVersion) showError(error);
  }
}

function renderList(bugs, route) {
  const titles = { NEW: 'Новые баги', IN_PROGRESS: 'Баги в работе', FIXED: 'Исправленные баги' };
  view.innerHTML = '<div class="page-heading"><div><p class="eyebrow">От находки до исправления</p><h1>' +
    (titles[route.status] || 'Все баги') + '</h1><p class="subtitle">Всё, что нужно заметить, проверить и исправить.</p></div>' +
    '<a class="btn btn-primary" href="#/bugs/new"><span class="plus" aria-hidden="true">+</span>Добавить баг</a></div>' +
    '<section class="panel" aria-label="Список багов"><form class="toolbar" id="filters" role="search">' +
    '<div class="filter-field search-field"><label for="search">Поиск</label><div class="search-wrap"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="10" cy="10" r="6.5" stroke="currentColor" stroke-width="1.6"/><path d="m15 15 5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>' +
    '<input type="search" id="search" name="q" placeholder="Название или #номер бага" value="' + esc(route.query) + '" autocomplete="off"></div></div>' +
    '<div class="filter-field"><label for="filter-status">Статус</label><select id="filter-status" name="status">' + options(STATUSES, route.status, 'Все статусы') + '</select></div>' +
    '<div class="filter-field"><label for="filter-priority">Приоритет</label><select id="filter-priority" name="priority">' + options(PRIORITIES, route.priority, 'Все приоритеты') + '</select></div>' +
    '<button class="btn" type="submit">Применить</button></form>' +
    '<div class="list-meta"><span id="result-count" role="status"></span><span class="sort-note">Сначала новые</span><button class="btn btn-quiet" id="reset-filters" type="button" hidden>Сбросить</button></div><div id="list-results"></div></section>' +
    '<p class="list-tip"><span aria-hidden="true">↗</span>Хорошее описание — уже первый шаг к исправлению.</p>';
  const search = view.querySelector('#search');
  const status = view.querySelector('#filter-status');
  const prioritySelect = view.querySelector('#filter-priority');
  const updateResults = () => {
    const visible = searchBugs(bugs, search.value);
    view.querySelector('#result-count').textContent = 'Найдено багов: ' + visible.length;
    const filtered = Boolean(route.status || route.priority || search.value.trim());
    view.querySelector('#reset-filters').hidden = !filtered;
    const results = view.querySelector('#list-results');
    if (!visible.length) {
      results.innerHTML = '<div class="empty-state"><div class="empty-art">' + pocket + '</div><h2>' +
        (filtered ? 'Ничего не нашлось' : 'Здесь начнётся порядок') + '</h2><p>' +
        (filtered ? 'Попробуй другое название или убери фильтры, чтобы увидеть больше багов.' : 'Нашёл ошибку? Запиши её, пока детали свежи в памяти. Первый баг — первый шаг к исправлению.') + '</p>' +
        (filtered ? '<button class="btn" type="button" id="empty-reset">Сбросить фильтры</button>' :
          '<a class="btn btn-primary" href="#/bugs/new"><span class="plus" aria-hidden="true">+</span>Добавить первый баг</a>') + '</div>';
      results.querySelector('#empty-reset')?.addEventListener('click', reset);
    } else {
      results.innerHTML = '<div class="table-wrap"><table class="bug-table"><thead><tr><th scope="col">Баг</th><th scope="col">Статус</th><th scope="col">Приоритет</th><th scope="col">Обновлён</th></tr></thead><tbody>' +
        visible.map(bug => '<tr><td><a class="bug-title" href="#/bugs/' + bug.id + '">' + esc(bug.header) + '</a><span class="bug-number">#' + bug.id + '</span></td><td>' +
          badge(bug.status) + '</td><td>' + priority(bug.priority) + '</td><td><time class="date" datetime="' + esc(bug.updatedAt) + '">' + esc(formatDate(bug.updatedAt)) + '</time></td></tr>').join('') + '</tbody></table></div>';
    }
  };
  function reset() {
    if (location.hash === '#/bugs') {
      search.value = '';
      updateResults();
    } else location.hash = '#/bugs';
  }
  function applyFilters(event) {
    event.preventDefault();
    const hash = listHash({ status: status.value, priority: prioritySelect.value, query: search.value });
    if (location.hash === hash) render();
    else location.hash = hash;
  }
  view.querySelector('#filters').addEventListener('submit', applyFilters);
  status.addEventListener('change', applyFilters);
  prioritySelect.addEventListener('change', applyFilters);
  search.addEventListener('input', () => {
    route.query = search.value;
    listReturnHash = listHash(route);
    activeHash = listReturnHash;
    history.replaceState(null, '', listReturnHash);
    updateResults();
  });
  view.querySelector('#reset-filters').addEventListener('click', reset);
  updateResults();
  finishRender(titles[route.status] || 'Все баги');
}

function renderDetail(bug) {
  const taskUrl = safeRelatedTaskUrl(bug.relatedTaskUrl);
  view.innerHTML = breadcrumb('Баг #' + bug.id) +
    '<div class="page-heading detail-heading"><div><p class="eyebrow">Баг #' + bug.id + '</p><h1>' + esc(bug.header) + '</h1></div>' +
    '<div class="heading-actions"><a class="btn" href="#/bugs/' + bug.id + '/edit">Редактировать</a></div></div>' +
    '<div class="detail-grid"><article class="panel detail-content" aria-label="Описание бага">' +
    FIELDS.filter(field => field.name !== 'header').map(field =>
      '<section class="detail-section"><h2>' + field.label + '</h2><p class="detail-text' + (bug[field.name] ? '' : ' not-specified') + '">' +
      esc(bug[field.name] || 'Не указано') + '</p></section>').join('') +
    '<section class="detail-section"><h2>Связанная задача</h2>' +
    (taskUrl ? '<a class="related-task-link" href="' + esc(taskUrl) + '" target="_blank" rel="noopener noreferrer">' +
      esc(taskUrl) + '</a>' : '<p class="not-specified">Не указано</p>') + '</section>' +
    '</article><aside class="panel detail-aside"><h2>Детали бага</h2>' + badge(bug.status) +
    '<form id="status-form"><fieldset><label for="bug-status">Изменить статус</label><select id="bug-status" name="status">' + options(STATUSES, bug.status) +
    '</select><button class="btn btn-primary" type="submit" disabled>Сохранить статус</button></fieldset><p class="field-error" role="alert" id="status-error" hidden></p></form>' +
    '<dl><div><dt>Приоритет</dt><dd>' + priority(bug.priority) + '</dd></div><div><dt>Создан</dt><dd>' + esc(formatDate(bug.createdAt)) +
    '</dd></div><div><dt>Обновлён</dt><dd>' + esc(formatDate(bug.updatedAt)) + '</dd></div></dl></aside></div>';
  const form = view.querySelector('#status-form');
  const select = form.querySelector('select');
  const button = form.querySelector('button');
  const fieldset = form.querySelector('fieldset');
  select.addEventListener('change', () => {
    dirty = select.value !== bug.status;
    button.disabled = !dirty;
  });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (saving || select.value === bug.status) return;
    saving = true;
    fieldset.disabled = true;
    button.textContent = 'Сохраняем…';
    const errorElement = form.querySelector('#status-error');
    errorElement.hidden = true;
    try {
      const updated = await api('/' + bug.id + '/status', { method: 'PATCH', body: { status: select.value } });
      dirty = false;
      renderDetail(updated);
      notify('Статус обновлён');
    } catch (error) {
      errorElement.textContent = error.message;
      errorElement.hidden = false;
    } finally {
      saving = false;
      fieldset.disabled = false;
      button.textContent = 'Сохранить статус';
      button.disabled = select.value === bug.status;
    }
  });
  finishRender('Баг #' + bug.id);
}

function renderForm(bug) {
  const editing = Boolean(bug);
  const title = editing ? 'Редактирование бага' : 'Новый баг';
  const returnHash = editing ? '#/bugs/' + bug.id : listReturnHash;
  const values = bug || { priority: 'MEDIUM' };
  const placeholders = {
    header: 'Например: кнопка «Сохранить» не реагирует на нажатие',
    steps: '1. Открыть страницу…\n2. Нажать…\n3. Проверить…',
    actualResult: 'Что произошло на самом деле?',
    expectedResult: 'Как это должно работать?',
    environment: 'Например: Windows 11, Firefox 130'
  };
  const fieldHtml = name => {
    const field = FIELDS.find(item => item.name === name);
    const attrs = ' id="' + name + '" name="' + name + '" maxlength="' + field.max + '" aria-describedby="' + name + '-error"' +
      (field.required ? ' required' : '') + ' placeholder="' + esc(placeholders[name]) + '"';
    return '<div class="field"><label for="' + name + '">' + field.label +
      (field.required ? ' <span class="required" aria-hidden="true">*</span>' : '') + '</label>' +
      (name === 'header' || name === 'environment'
        ? '<input type="text"' + attrs + ' value="' + esc(values[name]) + '">'
        : '<textarea rows="4"' + attrs + '>' + esc(values[name]) + '</textarea>') +
      '<p class="field-error" id="' + name + '-error" hidden></p>' +
      (name === 'header' ? '<p class="field-hint">Коротко и конкретно · до 200 символов</p>' : '') + '</div>';
  };
  view.innerHTML = breadcrumb(title, bug) +
    '<div class="page-heading"><div><p class="eyebrow">' + (editing ? 'Уточним детали' : 'Поймаем ещё одну ошибку') + '</p><h1>' + title +
    '</h1><p class="subtitle">Чем понятнее описание, тем проще найти решение.</p></div></div>' +
    '<div class="form-layout"><form id="bug-form" class="panel form-panel" novalidate><div id="form-error" class="alert" role="alert" hidden></div><fieldset>' +
    '<h2 class="form-section-title">Основная информация</h2>' + fieldHtml('header') +
    '<div class="field"><label for="priority">Приоритет <span class="required" aria-hidden="true">*</span></label><select id="priority" name="priority" required aria-describedby="priority-error">' +
    options(PRIORITIES, values.priority) + '</select><p class="field-error" id="priority-error" hidden></p></div>' +
    '<h2 class="form-section-title">Как воспроизвести</h2>' + fieldHtml('steps') +
    '<div class="form-row">' + fieldHtml('actualResult') + fieldHtml('expectedResult') + '</div>' + fieldHtml('environment') +
    '<div class="field"><label for="relatedTaskUrl">Связанная задача</label>' +
    '<input type="text" inputmode="url" id="relatedTaskUrl" name="relatedTaskUrl" value="' + esc(values.relatedTaskUrl) +
    '" aria-describedby="relatedTaskUrl-error relatedTaskUrl-hint" placeholder="tracker.company.local/TASK-123">' +
    '<p class="field-error" id="relatedTaskUrl-error" hidden></p>' +
    '<p class="field-hint" id="relatedTaskUrl-hint">Необязательно. Если схема не указана, добавим https://.</p></div>' +
    '<div class="form-actions"><a class="btn btn-quiet" href="' + esc(returnHash) + '">Отмена</a><button class="btn btn-primary" type="submit">' +
    (editing ? 'Сохранить изменения' : 'Создать баг') + '</button></div></fieldset></form>' +
    '<aside class="form-aside"><p class="aside-label">Небольшая подсказка</p><h2>Хороший баг-репорт</h2><p>Одна запись — одна ошибка. Так проще следить за исправлением и ничего не потерять.</p>' +
    '<p>Опиши шаги так, чтобы другой человек смог повторить их без дополнительных вопросов.</p><p>Обязательны только название и приоритет. Остальные детали можно добавить позже.</p>' +
    '<p>Новый баг получит статус <strong>«Новый»</strong>. Изменить его можно в карточке.</p></aside></div>';
  const form = view.querySelector('#bug-form');
  const fieldset = form.querySelector('fieldset');
  const button = form.querySelector('button[type="submit"]');
  const errorBox = form.querySelector('#form-error');
  const readValues = () => Object.fromEntries(new FormData(form));
  const initialValues = JSON.stringify(readValues());
  function showFields(errors) {
    for (const name of [...FIELDS.map(field => field.name), 'priority', 'relatedTaskUrl']) {
      const input = form.elements.namedItem(name);
      const message = form.querySelector('#' + name + '-error');
      const error = errors[name];
      message.textContent = error || '';
      message.hidden = !error;
      if (error) input.setAttribute('aria-invalid', 'true');
      else input.removeAttribute('aria-invalid');
    }
  }
  form.addEventListener('input', event => {
    dirty = JSON.stringify(readValues()) !== initialValues;
    const name = event.target.name;
    if (name && form.querySelector('#' + name + '-error')) {
      event.target.removeAttribute('aria-invalid');
      form.querySelector('#' + name + '-error').hidden = true;
    }
  });
  form.addEventListener('change', () => { dirty = JSON.stringify(readValues()) !== initialValues; });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (saving) return;
    const data = readValues();
    const errors = validateBug(data);
    showFields(errors);
    errorBox.hidden = true;
    if (Object.keys(errors).length) {
      form.querySelector('[aria-invalid="true"]')?.focus();
      return;
    }
    data.header = data.header.trim();
    saving = true;
    fieldset.disabled = true;
    const originalLabel = button.textContent;
    button.textContent = 'Сохраняем…';
    form.setAttribute('aria-busy', 'true');
    try {
      const saved = await api(editing ? '/' + bug.id : '', { method: editing ? 'PUT' : 'POST', body: data });
      dirty = false;
      saving = false;
      location.hash = '#/bugs/' + saved.id;
      notify(editing ? 'Изменения сохранены' : 'Баг #' + saved.id + ' создан');
    } catch (error) {
      showFields(error.fields || {});
      errorBox.textContent = error.message;
      errorBox.hidden = false;
      // Enable inputs before focusing an invalid field.
      fieldset.disabled = false;
      const firstInvalid = form.querySelector('[aria-invalid="true"]');
      if (firstInvalid) firstInvalid.focus();
      else { errorBox.tabIndex = -1; errorBox.focus(); }
    } finally {
      saving = false;
      fieldset.disabled = false;
      button.textContent = originalLabel;
      form.removeAttribute('aria-busy');
    }
  });
  finishRender(title);
}

window.addEventListener('hashchange', () => {
  if (saving || (dirty && !window.confirm('Есть несохранённые изменения. Уйти со страницы?'))) {
    history.replaceState(null, '', activeHash);
    if (saving) notify('Дождись завершения сохранения.');
    return;
  }
  dirty = false;
  render();
});

window.addEventListener('beforeunload', event => {
  if (dirty || saving) {
    event.preventDefault();
    event.returnValue = '';
  }
});

document.querySelector('.skip-link').addEventListener('click', event => {
  event.preventDefault();
  main.focus();
});

if (!location.hash) history.replaceState(null, '', '#/bugs');
render();
