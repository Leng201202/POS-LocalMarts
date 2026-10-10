(function () {
    'use strict';
    // Integer cents avoid floating-point rounding in this temporary client-side preview.
    function cents(value, signed) {
        const pattern = signed ? /^-?\d+(?:\.\d{1,2})?$/ : /^\d+(?:\.\d{1,2})?$/;
        if (!pattern.test(value)) return null;
        const negative = value.startsWith('-');
        const [whole, fraction = ''] = value.replace(/^-/, '').split('.');
        const result = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
        return negative ? -result : result;
    }
    function reconcile(real, system) {
        const actual = cents(real, false), expected = cents(system, true);
        if (actual === null || expected === null) return null;
        const delta = actual - expected, absolute = delta < 0n ? -delta : delta;
        return {
            difference: (delta < 0n ? '-' : '') + (absolute / 100n) + '.' + String(absolute % 100n).padStart(2, '0'),
            status: delta > 0n ? 'OVER' : delta < 0n ? 'SHORT' : 'BALANCED'
        };
    }
    function weeklyRange(value) {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return '';
        const date = new Date(value + 'T00:00:00Z');
        if (Number.isNaN(date.getTime())) return '';
        const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        const format = day => day.getUTCDate() + ' ' + months[day.getUTCMonth()] + ' ' + day.getUTCFullYear();
        const end = format(date);
        date.setUTCDate(date.getUTCDate() - 6);
        return format(date) + ' - ' + end + ' (7 days)';
    }
    if (typeof module !== 'undefined' && module.exports) module.exports = {reconcile, weeklyRange};
    if (typeof document === 'undefined') return;
    const cash = document.getElementById('real-world-cash');
    if (cash) cash.addEventListener('input', function () {
        const result = reconcile(cash.value, document.getElementById('system-cash').dataset.amount);
        const invalid = cash.value !== '' && result === null;
        cash.setCustomValidity(invalid ? 'Enter a nonnegative amount with at most two decimals.' : '');
        cash.setAttribute('aria-invalid', String(invalid));
        document.getElementById('cash-error').textContent = cash.validationMessage;
        document.getElementById('cash-difference').textContent = result ? result.difference : '—';
        const status = document.getElementById('cash-status');
        status.textContent = result ? result.status : 'WAITING FOR COUNT';
        const statusClass = !result ? 'text-bg-secondary'
            : result.status === 'BALANCED' ? 'text-bg-success'
                : result.status === 'OVER' ? 'text-bg-warning' : 'text-bg-danger';
        status.className = 'badge difference-status ' + statusClass;
    });
    const form = document.getElementById('report-period-form');
    if (form) {
        const type = document.getElementById('type'), date = document.getElementById('date');
        const start = document.getElementById('startDate'), end = document.getElementById('endDate');
        function update() {
            const custom = type.value === 'CUSTOM';
            const weekly = type.value === 'WEEKLY';
            document.getElementById('report-date-label').textContent = weekly ? 'Weekly report end date' : 'Report date';
            document.getElementById('weekly-date-help').hidden = !weekly;
            document.getElementById('single-date').hidden = custom;
            document.getElementById('custom-dates').hidden = !custom;
            date.disabled = custom; date.required = !custom;
            start.disabled = end.disabled = !custom; start.required = end.required = custom;
            end.min = start.value;
            document.getElementById('period-range').textContent = 'Reporting period: ' + (custom
                ? (start.value || 'Start date') + ' – ' + (end.value || 'End date')
                : type.value === 'WEEKLY' ? weeklyRange(date.value) : date.value);
        }
        form.addEventListener('input', update);
        form.addEventListener('change', update);
        update();
    }
}());
