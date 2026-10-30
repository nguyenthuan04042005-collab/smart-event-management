import { $, api } from './api.js';
import './password-toggle.js';

function message(text, error = false) {
  const box = $('#auth-message');
  box.textContent = text;
  box.classList.toggle('error', error);
  box.hidden = false;
}

const token = new URLSearchParams(location.search).get('token');
if (location.pathname.endsWith('/verify-email.html')) {
  history.replaceState(null, '', '/verify-email.html');
  if (!token) message('Liên kết xác minh không hợp lệ.', true);
  else api('/api/v1/auth/verify-email', { method: 'POST', body: JSON.stringify({ token }) })
    .then((result) => message(result.message))
    .catch((error) => message(error.message, true));
}

if (location.pathname.endsWith('/reset-password.html')) {
  history.replaceState(null, '', '/reset-password.html');
  if (!token) {
    $('#reset-form').hidden = true;
    message('Liên kết đặt lại mật khẩu không hợp lệ.', true);
  }
  $('#reset-form')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    if ($('#new-password').value !== $('#confirm-password').value) {
      message('Hai mật khẩu chưa khớp.', true);
      return;
    }
    const button = event.submitter;
    button.disabled = true;
    try {
      const result = await api('/api/v1/auth/reset-password', {
        method: 'POST',
        body: JSON.stringify({ token, newPassword: $('#new-password').value }),
      });
      $('#reset-form').hidden = true;
      message(result.message);
    } catch (error) {
      message(error.message, true);
      button.disabled = false;
    }
  });
}

$('#forgot-form')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  button.disabled = true;
  try {
    const result = await api('/api/v1/auth/forgot-password', {
      method: 'POST', body: JSON.stringify({ email: $('#email').value.trim() }),
    });
    message(result.message);
  } catch (error) {
    message(error.message, true);
  } finally {
    button.disabled = false;
  }
});
