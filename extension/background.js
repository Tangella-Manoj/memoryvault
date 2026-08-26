// Points at the local dev backend by default. For a production-packed build, change this to
// 'https://api.stacknode.dev/api' (already allow-listed in manifest.json's host_permissions
// and in the backend's CORS config) and repack — see README "Deploying to production".
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
    : { url: entry.url, source: entry.source };

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

async function contextSearch(query, token) {
  const res = await fetch(`${API_BASE}/chrome/search?q=${encodeURIComponent(query)}`, {
    headers: { 'X-Chrome-Token': token },
  });
  if (!res.ok) {
    throw new Error(`Search failed with status ${res.status}`);
  }
  const body = await res.json();
  return body.data;
}

async function setBadge(text) {
  try {
    await chrome.action.setBadgeText({ text: text || '' });
    await chrome.action.setBadgeBackgroundColor({ color: '#2d6a6a' });
  } catch {
    // Badge API can be unavailable in some contexts; never let this break a save.
  }
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
      case 'SET_BADGE': {
        await setBadge(message.text);
        sendResponse({ ok: true });
        break;
      }
      case 'CONTEXT_SEARCH': {
        const token = await getStoredToken();
        if (!token) {
          sendResponse({ ok: false, error: 'not_logged_in' });
          break;
        }
        try {
          const data = await contextSearch(message.query, token);
          sendResponse({ ok: true, data });
        } catch (err) {
          sendResponse({ ok: false, error: err.message });
        }
        break;
      }
      default:
        sendResponse({ ok: false, error: 'Unknown message type' });
    }
  })();
  return true;
});

chrome.runtime.onStartup?.addListener(flushOfflineQueue);
