import test from 'node:test';
import assert from 'node:assert/strict';
import { escapeHtml, parseRoute, listHash, validateBug, searchBugs, formatDate } from '../../main/resources/static/ui.js';

test('routes distinguish new, detail, edit and unknown paths', () => {
  assert.equal(parseRoute('').type, 'list');
  assert.equal(parseRoute('#/bugs/new').type, 'create');
  assert.deepEqual(parseRoute('#/bugs/42/edit'), { type: 'edit', id: '42' });
  assert.deepEqual(parseRoute('#/bugs/42'), { type: 'detail', id: '42' });
  assert.equal(parseRoute('#/bugs/nope').type, 'notFound');
  assert.equal(parseRoute('#/bugs/0').type, 'notFound');
});

test('filters survive URL encoding and reject unknown enum values', () => {
  const filter = { status: 'IN_PROGRESS', priority: 'HIGH', query: 'Ошибка & #42' };
  assert.deepEqual(parseRoute(listHash(filter)), { type: 'list', ...filter });
  assert.equal(parseRoute('#/bugs?status=UNKNOWN&priority=constructor').priority, '');
  assert.equal(parseRoute('#/bugs?status=UNKNOWN').status, '');
});

test('all API field limits and mandatory values are checked', () => {
  assert.deepEqual(validateBug({ header: 'Ошибка', priority: 'HIGH' }), {});
  assert.ok(validateBug({ header: '  ', priority: 'BAD' }).header);
  assert.ok(validateBug({ header: '  ', priority: 'BAD' }).priority);
  assert.ok(validateBug({ header: 'x'.repeat(201), priority: 'LOW' }).header);
  assert.ok(validateBug({ header: 'Bug', priority: 'LOW', steps: 'x'.repeat(10001) }).steps);
  assert.ok(validateBug({ header: 'Bug', priority: 'LOW', environment: 'x'.repeat(2001) }).environment);
});

test('user text is escaped before rendering into HTML', () => {
  assert.equal(escapeHtml('<script>"&\'</script>'), '&lt;script&gt;&quot;&amp;&#39;&lt;/script&gt;');
  assert.equal(escapeHtml(null), '');
});

test('search matches Russian text and bug numbers', () => {
  const bugs = [{ id: 12, header: 'Ошибка ВХОДА' }, { id: 3, header: 'Кнопка' }];
  assert.deepEqual(searchBugs(bugs, 'входа'), [bugs[0]]);
  assert.deepEqual(searchBugs(bugs, '#3'), [bugs[1]]);
  assert.deepEqual(searchBugs(bugs, '  '), bugs);
  assert.deepEqual(searchBugs(bugs, 'ничего'), []);
});

test('missing and invalid dates have a safe fallback', () => {
  assert.equal(formatDate(null), '—');
  assert.equal(formatDate('invalid'), '—');
  assert.match(formatDate('2026-09-23T10:00:00Z'), /2026/);
});
