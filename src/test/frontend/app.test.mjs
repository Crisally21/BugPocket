import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';

const root = new URL('../../main/resources/static/', import.meta.url);
const html = await readFile(new URL('index.html', root), 'utf8');
const ui = await readFile(new URL('ui.js', root), 'utf8');
const app = await readFile(new URL('app.js', root), 'utf8');
// jsdom does not execute ES modules; preserve application logic and remove only module syntax.
const script = ui.replace(/^export /gm, '') + '\nconst esc = escapeHtml;\n' +
  app.replace(/^import \{[\s\S]*?\} from '\.\/ui.js';/, '');

export const bug = {
  id: 1, header: 'Ошибка входа', steps: 'Открыть форму\nНажать «Войти»',
  actualResult: 'Ошибка 500', expectedResult: 'Личный кабинет',
  environment: 'Firefox', priority: 'HIGH', status: 'NEW',
  createdAt: '2026-09-23T10:00:00Z', updatedAt: '2026-09-23T10:00:00Z'
};

export async function until(condition, message = 'condition', timeout = 4000) {
  const start = Date.now();
  while (!condition()) {
    if (Date.now() - start > timeout) throw new Error('Timed out waiting for ' + message);
    await new Promise(resolve => setTimeout(resolve, 10));
  }
}

export function setup(t, { hash = '#/bugs', fetch, base = 'http://localhost:8081/' } = {}) {
  const dom = new JSDOM(html, { url: base + hash, runScripts: 'outside-only', pretendToBeVisual: true });
  t.after(() => dom.window.close());
  dom.window.fetch = fetch;
  // Use Node signals when bridging jsdom to Node fetch.
  dom.window.AbortController = globalThis.AbortController;
  dom.window.AbortSignal = globalThis.AbortSignal;
  dom.window.confirm = () => true;
  dom.window.eval(script);
  const document = dom.window.document;
  const input = (name, value) => {
    const element = document.querySelector('[name="' + name + '"]');
    assert.ok(element, 'input exists: ' + name);
    element.value = value;
    element.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
    element.dispatchEvent(new dom.window.Event('change', { bubbles: true }));
  };
  const submit = selector => {
    document.querySelector(selector).dispatchEvent(new dom.window.Event('submit', { bubbles: true, cancelable: true }));
  };
  const navigate = async (hash, selector) => {
    dom.window.location.hash = hash;
    if (selector) await until(() => document.querySelector(selector), selector);
  };
  return { dom, document, input, submit, navigate };
}

function reply(data, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => data };
}

test('empty list, create form validation and successful save', async t => {
  const requests = [];
  let saved;
  const { document, input, submit, navigate } = setup(t, { fetch: async (path, options) => {
    requests.push([path, options.method]);
    if (options.method === 'POST') {
      saved = { ...bug, ...JSON.parse(options.body) };
      return reply(saved, 201);
    }
    return reply(path === '/api/bugs/1' ? saved : []);
  }});
  await until(() => document.querySelector('.empty-state'));
  assert.match(document.querySelector('.empty-state').textContent, /Добавить первый баг/);
  await navigate('#/bugs/new', '#bug-form');
  input('header', '   ');
  submit('#bug-form');
  assert.equal(document.querySelector('#header').getAttribute('aria-invalid'), 'true');
  assert.equal(requests.filter(([, method]) => method === 'POST').length, 0);
  input('header', '  Новый баг  ');
  input('steps', 'Открыть страницу');
  submit('#bug-form');
  await until(() => document.querySelector('#status-form'));
  assert.equal(saved.header, 'Новый баг');
  assert.equal(saved.steps, 'Открыть страницу');
  assert.match(document.querySelector('h1').textContent, /Новый баг/);
});

test('failed save preserves input and allows retry without duplicate submissions', async t => {
  let calls = 0;
  let release;
  const { document, input, submit } = setup(t, { hash: '#/bugs/new', fetch: async () => {
    calls++;
    return new Promise(resolve => { release = () => resolve(reply({ detail: 'Проверь поля', errors: { header: 'Название занято' } }, 400)); });
  }});
  input('header', 'Сохранить мой текст');
  input('steps', 'Первая строка\nВторая строка');
  submit('#bug-form');
  submit('#bug-form');
  assert.equal(calls, 1);
  assert.equal(document.querySelector('fieldset').disabled, true);
  release();
  await until(() => !document.querySelector('fieldset').disabled);
  assert.equal(document.querySelector('#header').value, 'Сохранить мой текст');
  assert.equal(document.querySelector('#steps').value, 'Первая строка\nВторая строка');
  assert.equal(document.querySelector('#header-error').textContent, 'Название занято');
  assert.equal(document.querySelector('#form-error').hidden, false);
});

test('edit sends all existing fields and retains the current status', async t => {
  let updated = { ...bug, status: 'IN_PROGRESS' };
  let payload;
  const { document, input, submit } = setup(t, { hash: '#/bugs/1/edit', fetch: async (path, options) => {
    if (options.method === 'PUT') {
      payload = JSON.parse(options.body);
      updated = { ...updated, ...payload };
    }
    return reply(updated);
  }});
  await until(() => document.querySelector('#bug-form'));
  assert.equal(document.querySelector('#steps').value, bug.steps);
  input('header', 'Уточнение');
  submit('#bug-form');
  await until(() => document.querySelector('#status-form'));
  assert.equal(payload.actualResult, bug.actualResult);
  assert.equal(payload.expectedResult, bug.expectedResult);
  assert.equal(payload.environment, bug.environment);
  assert.equal(payload.status, undefined);
  assert.equal(document.querySelector('#bug-status').value, 'IN_PROGRESS');
});

test('status changes are explicitly saved and API errors stay visible', async t => {
  let current = { ...bug };
  let fail = true;
  const { document, input, submit } = setup(t, { hash: '#/bugs/1', fetch: async (path, options) => {
    if (options.method === 'PATCH') {
      if (fail) return reply({}, 500);
      current = { ...current, ...JSON.parse(options.body) };
    }
    return reply(current);
  }});
  await until(() => document.querySelector('#status-form'));
  input('status', 'FIXED');
  submit('#status-form');
  await until(() => !document.querySelector('#status-error').hidden);
  assert.equal(document.querySelector('#bug-status').value, 'FIXED');
  assert.equal(document.querySelector('#status-form fieldset').disabled, false);
  fail = false;
  submit('#status-form');
  await until(() => document.querySelector('.badge.fixed'));
  assert.equal(document.querySelector('#status-form button').disabled, true);
});

test('filter requests reach the API, search escapes user content, reset clears filters', async t => {
  const requests = [];
  const unsafe = { ...bug, header: '<img src=x onerror=alert(1)> Ошибка' };
  const { document, input, dom } = setup(t, { hash: '#/bugs?status=NEW&priority=HIGH', fetch: async path => {
    requests.push(path);
    return reply([unsafe]);
  }});
  await until(() => document.querySelector('.bug-title'));
  assert.equal(requests[0], '/api/bugs?status=NEW&priority=HIGH');
  assert.equal(document.querySelector('.bug-title img'), null);
  assert.equal(document.querySelector('.bug-title').textContent, unsafe.header);
  input('q', 'нет совпадений');
  assert.match(document.querySelector('#list-results').textContent, /Ничего не нашлось/);
  assert.match(dom.window.location.hash, /q=/);
  document.querySelector('#empty-reset').click();
  await until(() => requests.includes('/api/bugs'));
  await until(() => document.querySelector('.bug-title'));
  assert.equal(document.querySelector('#search').value, '');
});

test('unsaved changes can cancel navigation and preserve the form', async t => {
  const { dom, document, input } = setup(t, { hash: '#/bugs/new', fetch: async () => reply([]) });
  input('header', 'Не потерять');
  let prompted = false;
  dom.window.confirm = () => { prompted = true; return false; };
  dom.window.location.hash = '#/bugs';
  await until(() => prompted);
  assert.equal(dom.window.location.hash, '#/bugs/new');
  assert.equal(document.querySelector('#header').value, 'Не потерять');
});

test('loading error offers retry and missing bug offers a way back', async t => {
  let fail = true;
  const { document, navigate } = setup(t, { fetch: async path => {
    if (path === '/api/bugs/999') return reply({ detail: 'Не найден' }, 404);
    if (fail) return reply({}, 503);
    return reply([]);
  }});
  await until(() => document.querySelector('#retry'));
  fail = false;
  document.querySelector('#retry').click();
  await until(() => document.querySelector('#filters'));
  await navigate('#/bugs/999');
  await until(() => document.querySelector('h1')?.textContent === 'Баг не найден');
  assert.ok(document.querySelector('a[href="#/bugs"]'));
});

test('a late list response cannot overwrite a newer page', async t => {
  let release;
  const { document, navigate } = setup(t, { fetch: async () =>
    new Promise(resolve => { release = () => resolve(reply([bug])); })
  });
  await navigate('#/bugs/new', '#bug-form');
  release();
  await new Promise(resolve => setTimeout(resolve, 20));
  assert.ok(document.querySelector('#bug-form'));
  assert.equal(document.querySelector('h1').textContent, 'Новый баг');
});

const liveBase = process.env.BUGPOCKET_TEST_URL;
test('UI creates, edits, changes status and filters against the running Spring application',
  { skip: !liveBase }, async t => {
  const { document, input, submit, navigate } = setup(t, {
    hash: '#/bugs/new',
    base: liveBase,
    fetch: (path, options) => globalThis.fetch(new URL(path, liveBase), options)
  });
  const marker = 'UI integration ' + Date.now();
  input('header', marker);
  input('steps', 'Первый шаг\nВторой шаг');
  input('priority', 'HIGH');
  submit('#bug-form');
  await until(() => document.querySelector('#status-form'), 'created detail', 10000);
  const id = document.querySelector('.eyebrow').textContent.match(/\d+/)[0];
  await navigate('#/bugs/' + id + '/edit', '#bug-form');
  input('expectedResult', 'Работает');
  submit('#bug-form');
  await until(() => document.querySelector('#status-form'));
  assert.match(document.querySelector('.detail-content').textContent, /Работает/);
  input('status', 'FIXED');
  submit('#status-form');
  await until(() => document.querySelector('.badge.fixed'));
  await navigate('#/bugs?status=FIXED&priority=HIGH', '#filters');
  input('q', marker);
  assert.equal(document.querySelectorAll('.bug-title').length, 1);
  assert.equal(document.querySelector('.bug-title').textContent, marker);
  const page = await globalThis.fetch(new URL('/', liveBase));
  assert.equal(page.status, 200);
  assert.match(await page.text(), /BugPocket/);
  for (const asset of ['styles.css', 'app.js', 'ui.js', 'favicon.svg']) {
    const response = await globalThis.fetch(new URL('/' + asset, liveBase));
    assert.equal(response.status, 200, asset + ' should be served');
  }
});

test('related task URL is submitted, normalized by the server and safely linked', async t => {
  let saved;
  let payload;
  const { document, input, submit } = setup(t, { hash: '#/bugs/new', fetch: async (path, options) => {
    if (options.method === 'POST') {
      payload = JSON.parse(options.body);
      saved = { ...bug, ...payload, relatedTaskUrl: 'https://tracker.company.local/TASK-123?q=%22' };
      return reply(saved, 201);
    }
    return reply(saved);
  }});
  input('header', 'Со ссылкой');
  input('relatedTaskUrl', 'tracker.company.local/TASK-123?q=%22');
  submit('#bug-form');
  await until(() => document.querySelector('.related-task-link'));
  assert.equal(payload.relatedTaskUrl, 'tracker.company.local/TASK-123?q=%22');
  const link = document.querySelector('.related-task-link');
  assert.equal(link.getAttribute('href'), saved.relatedTaskUrl);
  assert.equal(link.textContent, saved.relatedTaskUrl);
  assert.equal(link.getAttribute('rel'), 'noopener noreferrer');
});

test('related URL editing preserves dirty state, displays backend error and clears URL', async t => {
  let current = { ...bug, relatedTaskUrl: 'https://tracker.invalid/ONE' };
  let fail = true;
  let payload;
  const { dom, document, input, submit } = setup(t, { hash: '#/bugs/1/edit', fetch: async (path, options) => {
    if (options.method === 'PUT') {
      payload = JSON.parse(options.body);
      if (fail) return reply({ detail: 'Проверь поля', errors: { relatedTaskUrl: 'Некорректная ссылка' } }, 400);
      current = { ...current, ...payload, relatedTaskUrl: payload.relatedTaskUrl || null };
    }
    return reply(current);
  }});
  await until(() => document.querySelector('#relatedTaskUrl'));
  assert.equal(document.querySelector('#relatedTaskUrl').value, current.relatedTaskUrl);
  input('relatedTaskUrl', 'invalid');
  let prompted = false;
  dom.window.confirm = () => { prompted = true; return false; };
  dom.window.location.hash = '#/bugs';
  await until(() => prompted);
  assert.equal(document.querySelector('#relatedTaskUrl').value, 'invalid');
  submit('#bug-form');
  await until(() => document.querySelector('#relatedTaskUrl').getAttribute('aria-invalid') === 'true');
  assert.equal(document.querySelector('#relatedTaskUrl-error').textContent, 'Некорректная ссылка');
  fail = false;
  input('relatedTaskUrl', '');
  submit('#bug-form');
  await until(() => document.querySelector('#status-form'));
  assert.equal(payload.relatedTaskUrl, '');
  assert.equal(document.querySelector('.related-task-link'), null);
});

test('unsafe related task scheme never becomes a clickable link', async t => {
  const { document } = setup(t, { hash: '#/bugs/1', fetch: async () =>
    reply({ ...bug, relatedTaskUrl: 'javascript:alert(1)' }) });
  await until(() => document.querySelector('#status-form'));
  assert.equal(document.querySelector('.related-task-link'), null);
});
