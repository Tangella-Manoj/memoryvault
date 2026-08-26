(function () {
  if (window.top !== window.self) return;

  const fab = document.createElement('button');
  fab.id = 'memoryvault-fab';
  fab.title = 'Save to MemoryVault';
  fab.textContent = '+';
  document.documentElement.appendChild(fab);

  function showToast(text) {
    const existing = document.getElementById('memoryvault-toast');
    if (existing) existing.remove();

    const toast = document.createElement('div');
    toast.id = 'memoryvault-toast';
    toast.textContent = text;
    document.documentElement.appendChild(toast);
    setTimeout(() => toast.remove(), 2600);
  }

  fab.addEventListener('click', () => {
    const selectedText = window.getSelection()?.toString().trim();

    const message = selectedText
      ? { type: 'SELECTION_SAVE', url: location.href, selectedText: selectedText.slice(0, 5000) }
      : { type: 'QUICK_SAVE', url: location.href };

    chrome.runtime.sendMessage(message, (response) => {
      if (!response) {
        showToast('Could not reach MemoryVault');
        return;
      }
      if (response.queued) {
        showToast(response.reason === 'not_logged_in' ? 'Log in via the MemoryVault icon to save' : 'Saved offline — will sync later');
      } else {
        showToast('Saved to MemoryVault');
      }
    });
  });
})();
