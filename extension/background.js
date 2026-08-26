const API_BASE = 'http://localhost:8091/api';

async function getStoredToken() {
  const { chromeToken, chromeTokenExpiresAt } = await chrome.storage.local.get([
    'chromeToken',
    'chromeTokenExpiresAt',
  ]);
  if (!chromeToken) return null;
  if (chromeTokenExpiresAt && Date.now() > chromeTokenExpiresAt) return null;
  return chromeToken;
}

async function login(email, password) {
  const loginRes = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const loginBody = await loginRes.json();
  if (loginBody.status !== 'SUCCESS') {
    throw new Error(loginBody.message || 'Login failed');
  }

  const sessionRes = await fetch(`${API_BASE}/chrome/session`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${loginBody.data.accessToken}` },
  });
  const sessionBody = await sessionRes.json();
  if (sessionBody.status !== 'SUCCESS') {
    throw new Error(sessionBody.message || 'Could not create extension session');
  }

  await chrome.storage.local.set({
    chromeToken: sessionBody.data.chromeToken,
    chromeTokenExpiresAt: Date.now() + 29 * 24 * 60 * 60 * 1000,
    userEmail: loginBody.data.email,
    userDisplayName: loginBody.data.displayName,
  });

  return { email: loginBody.data.email, displayName: loginBody.data.displayName };
}

async function logout() {
  await chrome.storage.local.remove(['chromeToken', 'chromeTokenExpiresAt', 'userEmail', 'userDisplayName']);
}

async function queueOffline(entry) {
  const { offlineQueue = [] } = await chrome.storage.local.get('offlineQueue');
  offlineQueue.push(entry);
  await chrome.storage.local.set({ offlineQueue });
}

async function flushOfflineQueue() {
  const token = await getStoredToken();
  if (!token) return;

  const { offlineQueue = [] } = await chrome.storage.local.get('offlineQueue');
  if (offlineQueue.length === 0) return;

  const remaining = [];
  for (const entry of offlineQueue) {
    try {
      await callSaveEndpoint(entry, token);
    } catch {
      remaining.push(entry);
    }
  }
  await chrome.storage.local.set({ offlineQueue: remaining });
}

async function callSaveEndpoint(entry, token) {
  const path = entry.type === 'SELECTION_SAVE' ? '/chrome/save/selection' : '/chrome/save/quick';
  const body = entry.type === 'SELECTION_SAVE'
    ? { url: entry.url, selectedText: entry.selectedText }
    : { url: entry.url };

  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Chrome-Token': token },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error(`Save failed with status ${res.status}`);
  }
  return res.json();
}

async function saveItem(entry) {
  const token = await getStoredToken();
  if (!token) {
    await queueOffline(entry);
    return { queued: true, reason: 'not_logged_in' };
  }

  try {
    const result = await callSaveEndpoint(entry, token);
    return { queued: false, data: result.data };
  } catch {
    await queueOffline(entry);
    return { queued: true, reason: 'network_or_server_error' };
  }
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  (async () => {
    switch (message.type) {
      case 'LOGIN': {
        try {
          const user = await login(message.email, message.password);
          sendResponse({ ok: true, user });
        } catch (err) {
          sendResponse({ ok: false, error: err.message });
        }
        break;
      }
      case 'LOGOUT': {
        await logout();
        sendResponse({ ok: true });
        break;
      }
      case 'GET_SESSION': {
        await flushOfflineQueue();
        const token = await getStoredToken();
        const { userEmail, userDisplayName, offlineQueue = [] } = await chrome.storage.local.get([
          'userEmail',
          'userDisplayName',
          'offlineQueue',
        ]);
        sendResponse({ loggedIn: Boolean(token), userEmail, userDisplayName, pendingCount: offlineQueue.length });
        break;
      }
      case 'QUICK_SAVE':
      case 'SELECTION_SAVE': {
        await flushOfflineQueue();
        const result = await saveItem(message);
        sendResponse(result);
        break;
      }
      default:
        sendResponse({ ok: false, error: 'Unknown message type' });
    }
  })();
  return true;
});

chrome.runtime.onStartup?.addListener(flushOfflineQueue);
