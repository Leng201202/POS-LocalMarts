const {test} = require('node:test');
const assert = require('node:assert/strict');
const {reconcile, weeklyRange} = require('../../main/resources/static/js/cashier-report.js');
test('cash difference: over, short, balanced and negative System Cash', () => {
    assert.deepEqual(reconcile('100', '95.00'), {difference:'5.00', status:'OVER'});
    assert.deepEqual(reconcile('90', '95.00'), {difference:'-5.00', status:'SHORT'});
    assert.deepEqual(reconcile('95.00', '95.00'), {difference:'0.00', status:'BALANCED'});
    assert.deepEqual(reconcile('0', '-72.50'), {difference:'72.50', status:'OVER'});
    assert.deepEqual(reconcile('0.30','0.10'), {difference:'0.20', status:'OVER'});
});
test('rejects invalid real cash and avoids Number precision loss', () => {
    for (const value of ['', '-1', '1.001', '1e3', 'abc', 'NaN', '1,000', ' ']) assert.equal(reconcile(value, '0'), null);
    assert.deepEqual(reconcile('9007199254740993.01', '9007199254740993.00'), {difference:'0.01',status:'OVER'});
});
test('weekly range ends on selected date and includes exactly seven calendar dates', () => {
    assert.equal(weeklyRange('2026-09-30'),'24 Sep 2026 - 30 Sep 2026 (7 days)');
    assert.equal(weeklyRange('2026-10-02'),'26 Sep 2026 - 2 Oct 2026 (7 days)');
    assert.equal(weeklyRange('2026-01-02'),'27 Dec 2025 - 2 Jan 2026 (7 days)');
    assert.equal(weeklyRange('2024-03-02'),'25 Feb 2024 - 2 Mar 2024 (7 days)');
});
test('date changes update Weekly wording and range; Daily and Custom retain their fields', () => {
    const elements = Object.fromEntries(['report-period-form', 'type', 'date', 'startDate', 'endDate',
        'single-date', 'custom-dates', 'report-date-label', 'weekly-date-help', 'period-range'].map(id => [id, {}]));
    const listeners = {};
    elements['report-period-form'].addEventListener = (event, callback) => { listeners[event] = callback; };
    elements.type.value = 'WEEKLY'; elements.date.value = '2026-09-30';
    elements.startDate.value = '2026-09-01'; elements.endDate.value = '2026-09-12';
    require('node:vm').runInNewContext(require('node:fs').readFileSync(
        require('node:path').join(__dirname, '../../main/resources/static/js/cashier-report.js'), 'utf8'), {
        document: {getElementById: id => elements[id] || null}
    });
    assert.equal(elements['report-date-label'].textContent, 'Weekly report end date');
    assert.equal(elements['weekly-date-help'].hidden, false);
    assert.equal(elements['period-range'].textContent, 'Reporting period: 24 Sep 2026 - 30 Sep 2026 (7 days)');
    elements.date.value = '2026-10-02'; listeners.input();
    assert.equal(elements['period-range'].textContent, 'Reporting period: 26 Sep 2026 - 2 Oct 2026 (7 days)');
    elements.type.value = 'DAILY'; listeners.change();
    assert.equal(elements['report-date-label'].textContent, 'Report date');
    assert.equal(elements['weekly-date-help'].hidden, true);
    assert.equal(elements['period-range'].textContent, 'Reporting period: 2026-10-02');
    elements.type.value = 'CUSTOM'; listeners.change();
    assert.equal(elements.date.disabled, true);
    assert.equal(elements.startDate.disabled, false);
    assert.equal(elements.endDate.disabled, false);
    assert.equal(elements['period-range'].textContent, 'Reporting period: 2026-09-01 – 2026-09-12');
});
