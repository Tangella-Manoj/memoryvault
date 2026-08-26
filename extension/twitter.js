(function () {
  const host = location.hostname;
  if (!host.includes('twitter.com') && !host.includes('x.com')) return;
  if (!location.pathname.includes('/bookmarks')) return;

  const syncedUrls = new Set();
  let syncedCount = 0;

  function extractBookmarks() {
    const tweets = document.querySelectorAll('article[data-testid="tweet"]');
    const found = [];

    tweets.forEach((article) => {
      const statusLink = article.querySelector('a[href*="/status/"]');
      if (!statusLink) return;
      const url = statusLink.href.split('?')[0];
      if (syncedUrls.has(url)) return;

      const authorEl = article.querySelector('[data-testid="User-Name"]');
      const textEl = article.querySelector('[data-testid="tweetText"]');

      found.push({
        url,
        author: authorEl ? authorEl.textContent.trim() : null,
        text: textEl ? textEl.textContent.trim() : null,
      });
      syncedUrls.add(url);
    });

    return found;
  }

  async function syncNewBookmarks() {
    const bookmarks = extractBookmarks();
    if (bookmarks.length === 0) return;

    for (const bookmark of bookmarks) {
      try {
        const response = await chrome.runtime.sendMessage({
          type: 'QUICK_SAVE',
          url: bookmark.url,
          source: 'TWITTER',
        });
        if (response && !response.queued) {
          syncedCount++;
          chrome.runtime.sendMessage({ type: 'SET_BADGE', text: String(syncedCount) });
        }
      } catch {
        // Best-effort per bookmark.
      }
    }
  }

  syncNewBookmarks();

  // Bookmarks page is a virtualized/infinite-scroll timeline — new tweets mount as
  // DOM nodes with no navigation event, so MutationObserver is required here too.
  const observer = new MutationObserver(() => {
    syncNewBookmarks();
  });
  observer.observe(document.body, { childList: true, subtree: true });
})();
