(function () {
  if (!location.hostname.includes('instagram.com')) return;
  if (!/\/saved\/?/.test(location.pathname) && !location.search.includes('saved')) return;

  const syncedUrls = new Set();
  let syncedCount = 0;

  function extractPosts() {
    // Instagram saved-collection grid: anchors linking to /p/ (post) or /reel/ (reel).
    const anchors = document.querySelectorAll('a[href*="/p/"], a[href*="/reel/"]');
    const posts = [];

    anchors.forEach((a) => {
      const href = a.href.split('?')[0];
      if (syncedUrls.has(href)) return;

      const img = a.querySelector('img');
      const isReel = href.includes('/reel/');
      // Instagram marks video posts with a small video/reel icon (svg[aria-label*="Reel"/"Clip"])
      // inside the same tile; absence defaults to an image post.
      const hasVideoIcon = !!a.querySelector('svg[aria-label*="Reel" i], svg[aria-label*="Clip" i], svg[aria-label*="Video" i]');

      posts.push({
        url: href,
        thumbnailUrl: img ? img.src : null,
        contentType: isReel || hasVideoIcon ? 'VIDEO' : 'IMAGE',
      });
      syncedUrls.add(href);
    });

    return posts;
  }

  async function syncNewPosts() {
    const posts = extractPosts();
    if (posts.length === 0) return;

    for (const post of posts) {
      try {
        const response = await chrome.runtime.sendMessage({
          type: 'QUICK_SAVE',
          url: post.url,
          source: 'INSTAGRAM',
        });
        if (response && !response.queued) {
          syncedCount++;
          chrome.runtime.sendMessage({ type: 'SET_BADGE', text: String(syncedCount) });
        }
      } catch {
        // Best-effort — a single failed post never blocks the rest of the batch.
      }
    }
  }

  // Initial grid is already in the DOM by document_idle; sync it immediately.
  syncNewPosts();

  // Instagram's saved grid renders more tiles as the user scrolls (infinite scroll),
  // entirely via client-side DOM mutation with no full navigation — MutationObserver
  // is the only reliable way to catch that without polling.
  const observer = new MutationObserver(() => {
    syncNewPosts();
  });
  observer.observe(document.body, { childList: true, subtree: true });
})();
