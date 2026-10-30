import { $, api, homeFor, roleOf } from './api.js';
import './password-toggle.js';

const message = (text, error = true) => {
  const box = $('#auth-message');
  box.textContent = text;
  box.hidden = false;
  box.classList.toggle('error', error);
};

const next = new URLSearchParams(location.search).get('next');
const target = next?.startsWith('/pages/') && !next.startsWith('//') ? next : null;
if (target && $('#signup-link')) $('#signup-link').href = `/signup.html?next=${encodeURIComponent(target)}`;
if (target && $('#login-link')) $('#login-link').href = `/login.html?next=${encodeURIComponent(target)}`;

api('/api/v1/me')
  .then((me) => location.replace(target || homeFor(roleOf(me))))
  .catch((error) => {
    if (error.status !== 401) message(error.message);
  });

if (new URLSearchParams(location.search).has('registered'))
  message('Tài khoản đã được tạo. Hãy đăng nhập.', false);
if (new URLSearchParams(location.search).has('passwordChanged')) {
  message('Mật khẩu đã thay đổi. Hãy đăng nhập lại.', false);
}

$('#login-form')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  button.disabled = true;
  try {
    const me = await api('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        username: $('#username').value.trim(),
        password: $('#password').value,
      }),
    });
    location.replace(target || homeFor(roleOf(me)));
  } catch (error) {
    $('#password').value = '';
    message(error.message);
    button.disabled = false;
  }
});

$('#signup-form')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  if ($('#password').value !== $('#confirm-password').value) {
    message('Hai mật khẩu chưa khớp.');
    $('#confirm-password').focus();
    return;
  }
  const button = event.submitter;
  button.disabled = true;
  try {
    const result = await api('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        username: $('#username').value.trim(),
        displayName: $('#display-name').value.trim(),
        email: $('#email').value.trim(),
        password: $('#password').value,
      }),
    });
    if (result.verificationRequired) {
      sessionStorage.setItem('sems-signup-request', result.requestId);
      sessionStorage.setItem('sems-signup-email', $('#email').value.trim());
      showSignupCode(result.requestId, $('#email').value.trim());
      message(result.message, false);
      button.disabled = false;
      return;
    }
    const redirect = new URLSearchParams({ registered: '1' });
    if (target) redirect.set('next', target);
    location.replace(`/login.html?${redirect}`);
  } catch (error) {
    message(error.message);
    button.disabled = false;
  }
});

function showSignupCode(requestId, email) {
  if (!$('#signup-code-form') || !requestId) return;
  $('#signup-form').hidden = true;
  $('#signup-code-form').hidden = false;
  $('#signup-code-address').textContent = email;
  $('#signup-code').focus();
}

if ($('#signup-code-form'))
  showSignupCode(sessionStorage.getItem('sems-signup-request'), sessionStorage.getItem('sems-signup-email'));

$('#signup-code-form')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  button.disabled = true;
  try {
    await api('/api/v1/auth/confirm-registration', {
      method: 'POST',
      body: JSON.stringify({
        requestId: sessionStorage.getItem('sems-signup-request'),
        code: $('#signup-code').value.trim(),
      }),
    });
    sessionStorage.removeItem('sems-signup-request');
    sessionStorage.removeItem('sems-signup-email');
    const redirect = new URLSearchParams({ registered: '1' });
    if (target) redirect.set('next', target);
    location.replace(`/login.html?${redirect}`);
  } catch (error) {
    message(error.message);
    button.disabled = false;
  }
});

$('#signup-resend')?.addEventListener('click', async (event) => {
  const button = event.currentTarget;
  button.disabled = true;
  try {
    const result = await api('/api/v1/auth/resend-registration-code', {
      method: 'POST',
      body: JSON.stringify({ requestId: sessionStorage.getItem('sems-signup-request') }),
    });
    message(result.message, false);
  } catch (error) {
    message(error.message);
  } finally {
    button.disabled = false;
  }
});

$('#signup-restart')?.addEventListener('click', () => {
  sessionStorage.removeItem('sems-signup-request');
  sessionStorage.removeItem('sems-signup-email');
  $('#signup-code-form').hidden = true;
  $('#signup-form').hidden = false;
  $('#email').focus();
});
