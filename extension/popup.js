const loggedOutView = document.getElementById('loggedOutView');
const loggedInView = document.getElementById('loggedInView');
const loginError = document.getElementById('loginError');

function send(message) {
  return new Promise((resolve) => chrome.runtime.sendMessage(message, resolve));
}

async function render() {
  const session = await send({ type: 'GET_SESSION' });
  if (session.loggedIn) {
    loggedOutView.style.display = 'none';
    loggedInView.style.display = 'block';
    document.getElementById('whoami').textContent = `Logged in as ${session.userDisplayName ?? session.userEmail}`;
    document.getElementById('pending').textContent =
      session.pendingCount > 0 ? `${session.pendingCount} item(s) waiting to sync` : '';
  } else {
    loggedOutView.style.display = 'block';
    loggedInView.style.display = 'none';
  }
}

document.getElementById('loginBtn').addEventListener('click', async () => {
  loginError.textContent = '';
  const email = document.getElementById('email').value.trim();
  const password = document.getElementById('password').value;
  const result = await send({ type: 'LOGIN', email, password });
  if (result.ok) {
    render();
  } else {
    loginError.textContent = result.error;
  }
});

document.getElementById('logoutBtn').addEventListener('click', async () => {
  await send({ type: 'LOGOUT' });
  render();
});

document.getElementById('saveBtn').addEventListener('click', async () => {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  const btn = document.getElementById('saveBtn');
  btn.textContent = 'Saving…';
  btn.disabled = true;

  const result = await send({ type: 'QUICK_SAVE', url: tab.url });
  btn.textContent = result.queued ? 'Queued (offline)' : 'Saved!';
  setTimeout(() => {
    btn.textContent = 'Save this page';
    btn.disabled = false;
  }, 1400);
});

render();
