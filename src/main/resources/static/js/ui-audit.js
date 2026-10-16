/**
 * BookMind Nexus - Frontend UI Audit
 * Runs on every page to detect DOM anomalies, auth state mismatches,
 * duplicate elements, and broken button assignments.
 */
(function () {
    'use strict';

    const AUDIT_URL = '/ui/audit';
    const MAX_ANOMALIES = 50;

    function runAudit() {
        const anomalies = [];
        const pagePath = window.location.pathname;
        const userState = detectUserState();

        anomalies.push(...checkDuplicateIds());
        anomalies.push(...checkAuthStateConsistency(userState));
        anomalies.push(...checkBrokenLinks());
        anomalies.push(...checkMissingFormLabels());
        anomalies.push(...checkOrphanedElements());
        anomalies.push(...checkButtonAssignments());

        const stats = collectDomStats();

        const report = {
            pagePath: pagePath,
            userState: userState,
            anomalies: anomalies.slice(0, MAX_ANOMALIES),
            domStats: stats
        };

        sendReport(report);
    }

    function detectUserState() {
        const indicators = document.querySelectorAll('[href="/app"], [href="/outputs"], .user-greeting');
        const loginLinks = document.querySelectorAll('[href="/login"], [href="/register"]');
        const visibleIndicators = Array.from(indicators).filter(el => isVisible(el));
        const visibleLoginLinks = Array.from(loginLinks).filter(el => isVisible(el));

        if (visibleIndicators.length > 0 && visibleLoginLinks.length === 0) {
            return 'logged_in';
        }
        return 'anonymous';
    }

    function isVisible(el) {
        if (!el) return false;
        const style = window.getComputedStyle(el);
        if (style.display === 'none') return false;
        if (style.visibility === 'hidden') return false;
        if (parseFloat(style.opacity) === 0) return false;
        if (el.hasAttribute('hidden')) return false;
        return true;
    }

    function checkDuplicateIds() {
        const results = [];
        const ids = {};
        const allElements = document.querySelectorAll('[id]');
        allElements.forEach(el => {
            const id = el.getAttribute('id');
            if (!id) return;
            if (ids[id]) {
                results.push({
                    type: 'duplicate_id',
                    severity: 'error',
                    description: 'Duplicate element ID: ' + id,
                    selector: '#' + id,
                    expected: 'Unique ID',
                    actual: 'Found ' + (ids[id] + 1) + ' elements with id=' + id
                });
            }
            ids[id] = (ids[id] || 0) + 1;
        });
        return results;
    }

    function checkAuthStateConsistency(userState) {
        const results = [];
        const isLoggedIn = userState === 'logged_in';

        if (isLoggedIn) {
            const loginLinks = document.querySelectorAll('a[href="/login"], a[href="/register"]');
            loginLinks.forEach(link => {
                if (isVisible(link)) {
                    results.push({
                        type: 'auth_state_mismatch',
                        severity: 'error',
                        description: 'Login/Register link visible while user is logged in',
                        selector: getSelector(link),
                        expected: 'hidden',
                        actual: 'visible'
                    });
                }
            });
        } else {
            const appLinks = document.querySelectorAll('a[href="/app"], a[href="/outputs"]');
            appLinks.forEach(link => {
                if (isVisible(link)) {
                    results.push({
                        type: 'auth_state_mismatch',
                        severity: 'error',
                        description: 'App/Outputs link visible while user is logged out',
                        selector: getSelector(link),
                        expected: 'hidden',
                        actual: 'visible'
                    });
                }
            });
        }
        return results;
    }

    function checkBrokenLinks() {
        const results = [];
        const links = document.querySelectorAll('a[href]');
        links.forEach(link => {
            const href = link.getAttribute('href');
            if (!href || href.startsWith('#') || href.startsWith('javascript:') || href.startsWith('http')) {
                return;
            }
            results.push({
                type: 'internal_link',
                severity: 'info',
                description: 'Internal link found',
                selector: getSelector(link),
                expected: null,
                actual: href
            });
        });
        return results.slice(0, 10);
    }

    function checkMissingFormLabels() {
        const results = [];
        const inputs = document.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), textarea, select');
        inputs.forEach(input => {
            const id = input.getAttribute('id');
            let hasLabel = false;
            if (id) {
                hasLabel = document.querySelector('label[for="' + id + '"]') !== null;
            }
            if (!hasLabel && input.closest('label') === null) {
                results.push({
                    type: 'missing_label',
                    severity: 'warning',
                    description: 'Form input missing associated label',
                    selector: getSelector(input),
                    expected: '<label> element',
                    actual: 'no label found'
                });
            }
        });
        return results;
    }

    function checkOrphanedElements() {
        const results = [];
        const emptyDivs = document.querySelectorAll('div:empty, span:empty, p:empty');
        emptyDivs.forEach(el => {
            if (el.children.length === 0 && !el.textContent.trim()) {
                results.push({
                    type: 'empty_element',
                    severity: 'info',
                    description: 'Empty container element detected',
                    selector: getSelector(el),
                    expected: 'content or removal',
                    actual: 'empty'
                });
            }
        });
        return results.slice(0, 10);
    }

    function checkButtonAssignments() {
        const results = [];
        const buttons = document.querySelectorAll('button, input[type="submit"]');
        buttons.forEach(btn => {
            const text = (btn.textContent || btn.value || '').trim();
            const onclick = btn.getAttribute('onclick') || '';
            const form = btn.closest('form');
            if (!text && !onclick && !form) {
                results.push({
                    type: 'orphaned_button',
                    severity: 'warning',
                    description: 'Button with no text and no action',
                    selector: getSelector(btn),
                    expected: 'text or onclick',
                    actual: 'empty'
                });
            }
        });
        return results.slice(0, 10);
    }

    function getSelector(el) {
        if (el.id) return '#' + el.id;
        const tag = el.tagName.toLowerCase();
        const cls = el.className && typeof el.className === 'string' ? '.' + el.className.trim().split(/\s+/).join('.') : '';
        const parent = el.parentElement;
        const parentSelector = parent ? getSelector(parent) + ' > ' : '';
        return parentSelector + tag + cls;
    }

    function collectDomStats() {
        return {
            totalElements: document.querySelectorAll('*').length,
            totalIds: document.querySelectorAll('[id]').length,
            duplicateIds: (document.querySelectorAll('[id]').length - new Set(Array.from(document.querySelectorAll('[id]')).map(el => el.id)).size),
            visibleLinks: document.querySelectorAll('a:not([href="#"]):not([href^="javascript"])').length,
            forms: document.querySelectorAll('form').length,
            buttons: document.querySelectorAll('button, input[type="submit"]').length
        };
    }

    function sendReport(report) {
        if (navigator.sendBeacon) {
            const blob = new Blob([JSON.stringify(report)], { type: 'application/json' });
            navigator.sendBeacon(AUDIT_URL, blob);
        } else {
            fetch(AUDIT_URL, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(report)
            }).catch(() => {});
        }
    }

    function fixLanguageToggle() {
        const currentLang = document.documentElement.lang || 'en';
        const isArabic = currentLang === 'ar';
        const targetLang = isArabic ? 'en' : 'ar';
        const toggleText = isArabic ? '🌐 EN' : '🌐 عربي';
        const currentPath = window.location.pathname;

        document.querySelectorAll('a[href*="language/switch"]').forEach(link => {
            const href = link.getAttribute('href');
            if (href && href.includes('language/switch')) {
                const newHref = href.replace(/lang=[^&]*/, 'lang=' + targetLang);
                link.setAttribute('href', newHref);
                link.textContent = toggleText;
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            runAudit();
            fixLanguageToggle();
        });
    } else {
        runAudit();
        fixLanguageToggle();
    }
})();
