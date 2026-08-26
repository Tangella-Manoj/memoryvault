(function () {
  const host = location.hostname;
  const isGoogle = host.includes('google.');
  const isBing = host.includes('bing.com');
  const isDuckDuckGo = host.includes('duckduckgo.com');
  if (!isGoogle && !isBing && !isDuckDuckGo) return;

  const CONTEXT_SCORE_THRESHOLD = 0.4;
  const DEBOUNCE_MS = 600;
  const DISMISS_KEY = 'memoryvault_overlay_dismissed';

  function findSearchInput() {
    if (isGoogle) return document.querySelector('textarea[name="q"], input[name="q"]');
    if (isBing) return document.querySelector('input#sb_form_q');
    if (isDuckDuckGo) return document.querySelector('textarea#searchbox_input, input#search_form_input, textarea[name="q"], input[name="q"]');
    return null;
  }

  function platformIcon(source) {
    const icons = {
      INSTAGRAM: '\u{1F4F7}', TWITTER: '\u{1F426}', YOUTUBE: '\u{25B6}️',
      EMAIL: '✉️', CHROME_EXTENSION: '\u{1F310}', WEB: '\u{1F310}', BULK_IMPORT: '\u{1F4E5}',
    };
    return icons[source] || '\u{1F310}';
  }

  function buildOverlay(results) {
    const existing = document.getElementById('memoryvault-search-overlay');
    if (existing) existing.remove();

    const panel = document.createElement('div');
    panel.id = 'memoryvault-search-overlay';
    panel.innerHTML = `
      <div id="mv-overlay-header">
        <span>From your MemoryVault</span>
        <button id="mv-overlay-close" aria-label="Dismiss">×</button>
      </div>
      <div id="mv-overlay-items"></div>
    `;

    const itemsContainer = panel.querySelector('#mv-overlay-items');
    results.slice(0, 3).forEach((r) => {
      const item = document.createElement('a');
      item.href = r.url;
      item.target = '_blank';
      item.rel = 'noreferrer';
      item.className = 'mv-overlay-item';
      item.innerHTML = `
        <div class="mv-overlay-item-top">
          <span class="mv-overlay-icon">${platformIcon(r.platform)}</span>
          <span class="mv-overlay-days">${r.days_since_saved}d ago</span>
        </div>
        <div class="mv-overlay-title">${escapeHtml(r.title || r.url)}</div>
        ${r.summary ? `<div class="mv-overlay-summary">${escapeHtml(truncate(r.summary, 100))}</div>` : ''}
      `;
      itemsContainer.appendChild(item);
    });

    document.documentElement.appendChild(panel);
    panel.querySelector('#mv-overlay-close').addEventListener('click', () => {
      panel.remove();
      sessionStorage.setItem(DISMISS_KEY, '1');
    });
  }

  function escapeHtml(s) {
    const div = document.createElement('div');
    div.textContent = s;
    return div.innerHTML;
  }

  function truncate(s, max) {
    return s.length > max ? s.slice(0, max) + '…' : s;
  }

  function removeOverlay() {
    const existing = document.getElementById('memoryvault-search-overlay');
    if (existing) existing.remove();
  }

  async function runSearch(query) {
    if (sessionStorage.getItem(DISMISS_KEY) === '1') return;
    if (!query || query.trim().length < 3) {
      removeOverlay();
      return;
    }

    const response = await chrome.runtime.sendMessage({ type: 'CONTEXT_SEARCH', query });
    if (!response || !response.ok || !Array.isArray(response.data)) {
      removeOverlay();
      return;
    }

    const relevant = response.data.filter((r) => r.context_score > CONTEXT_SCORE_THRESHOLD);
    if (relevant.length === 0) {
      removeOverlay();
      return;
    }

    buildOverlay(relevant);
  }

  let debounceTimer = null;
  function onInput(event) {
    clearTimeout(debounceTimer);
    const query = event.target.value;
    debounceTimer = setTimeout(() => runSearch(query), DEBOUNCE_MS);
  }

  function attach() {
    const input = findSearchInput();
    if (!input || input.dataset.memoryvaultAttached) return;
    input.dataset.memoryvaultAttached = '1';
    input.addEventListener('input', onInput);
    // Search-engine result pages often already have a query in the box on load.
    if (input.value) runSearch(input.value);
  }

  attach();
  // Google/Bing/DDG are largely client-rendered for suggestions and results — the
  // input element itself is usually present immediately, but attach defensively in
  // case it mounts after this script runs.
  const observer = new MutationObserver(attach);
  observer.observe(document.documentElement, { childList: true, subtree: true });
})();
