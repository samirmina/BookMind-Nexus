/**
 * BookMind Nexus - Collapsible sections for AI responses
 * Toggles visibility of long AI-generated content sections.
 */
function toggleCollapse(header) {
    const content = header.nextElementSibling;
    const section = header.parentElement;
    const isOpen = content.classList.contains('open');

    if (isOpen) {
        content.classList.remove('open');
        header.classList.remove('expanded');
        header.setAttribute('aria-expanded', 'false');
    } else {
        content.classList.add('open');
        header.classList.add('expanded');
        header.setAttribute('aria-expanded', 'true');
        if (section && section.classList.contains('collapsible-section')) {
            section.classList.add('has-content');
        }
    }
}
