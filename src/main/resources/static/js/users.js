import { $, api, esc, searchText, show, authForm } from './api.js';
import './password-toggle.js';

let allUsers = [];

function paintUsers() {
  const query = searchText($('#user-search').value.trim());
  const role = $('#user-role').value;
  const status = $('#user-status').value;
  const visible = allUsers.filter(
    (user) =>
      searchText(`${user.username} ${user.displayName} ${user.email || ''}`).includes(query) &&
      (role === 'all' || user.roles.includes(role)) &&
      (status === 'all' || user.active === (status === 'active')),
  );
  $('#user-count').textContent = `Hiển thị ${visible.length} / ${allUsers.length} tài khoản`;
  $('#user-list').innerHTML = visible.length
    ? visible
        .map(
      (user) => `
        <tr>
          <td>${esc(user.username)}</td>
          <td>${esc(user.displayName)}</td>
          <td>${esc(user.email || '—')}${user.email ? `<br><small class="muted">${user.emailVerified ? 'Đã xác minh' : 'Chưa xác minh'}</small>` : ''}</td>
          <td>${esc(user.roles.join(', '))}</td>
          <td>${user.active ? 'Đang hoạt động' : 'Đã khóa'}</td>
          <td>
            ${
              user.roles.includes('ADMIN')
                ? '—'
                : `
                  <button class="btn secondary toggle" data-id="${user.id}" data-active="${!user.active}">
                    ${user.active ? 'Khóa' : 'Mở khóa'}
                  </button>
                  <button class="btn secondary reset" data-id="${user.id}">
                    Đặt lại mật khẩu
                  </button>
                `
            }
          </td>
        </tr>
      `,
        )
        .join('')
    : `<tr><td colspan="6" class="empty">${allUsers.length ? 'Không tìm thấy tài khoản phù hợp.' : 'Chưa có tài khoản.'}</td></tr>`;
  document.querySelectorAll('.toggle').forEach((button) =>
    button.addEventListener('click', async () => {
      try {
        await api(`/api/v1/admin/users/${button.dataset.id}/active`, {
          method: 'PATCH',
          body: JSON.stringify({ active: button.dataset.active === 'true' }),
        });
        show('Đã cập nhật trạng thái tài khoản.');
        await refresh();
      } catch (error) {
        show(error.message, true);
      }
    }),
  );
  document.querySelectorAll('.reset').forEach((button) =>
    button.addEventListener('click', async () => {
      const password = prompt('Nhập mật khẩu mới (ít nhất 12 ký tự):');
      if (password === null) return;
      try {
        await api(`/api/v1/admin/users/${button.dataset.id}/reset-password`, {
          method: 'POST',
          body: JSON.stringify({ password }),
        });
        show('Đã đặt lại mật khẩu. Hãy chuyển mật khẩu mới cho nhân viên theo kênh riêng.');
      } catch (error) {
        show(error.message, true);
      }
    }),
  );
}

async function refresh() {
  allUsers = await api('/api/v1/admin/users');
  paintUsers();
}

authForm(async () => {
  const me = await api('/api/v1/me');
  renderProfile(me);
  $('#workspace').hidden = false;
  if (me.roles.some((role) => role.authority === 'ROLE_ADMIN')) {
    $('#staff-management').hidden = false;
    $('#users-section').hidden = false;
    await refresh();
  } else {
    document.querySelector('.hero p').textContent = 'Xem thông tin cá nhân, đổi email hoặc mật khẩu khi cần.';
  }
});

$('#user-search').addEventListener('input', paintUsers);
$('#user-role').addEventListener('change', paintUsers);
$('#user-status').addEventListener('change', paintUsers);

$('#staff-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    await api('/api/v1/admin/users', {
      method: 'POST',
      body: JSON.stringify({
        username: $('#staff-username').value.trim(),
        displayName: $('#staff-name').value.trim(),
        email: $('#staff-email').value.trim(),
        password: $('#staff-password').value,
        role: $('#staff-role').value,
      }),
    });
    $('#staff-form').reset();
    show('Đã tạo tài khoản làm việc.');
    await refresh();
  } catch (error) {
    show(error.message, true);
  }
});

function renderProfile(me) {
  $('#profile-username').textContent = me.username;
  $('#profile-name').value = me.displayName;
  $('#my-email').value = me.email || '';
  $('#my-email-state').textContent = me.email || 'Chưa cập nhật';
  $('#resend-verification').hidden = !me.email || me.emailVerified;
}

$('#profile-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  button.disabled = true;
  try {
    await api('/api/v1/account/profile', {
      method: 'POST',
      body: JSON.stringify({ displayName: $('#profile-name').value.trim() }),
    });
    const me = await api('/api/v1/me');
    renderProfile(me);
    for (const label of document.querySelectorAll('.account-menu-name, .sidebar-foot strong, .topbar-user > span:not(.topbar-avatar)')) {
      label.textContent = me.displayName;
    }
    for (const avatar of document.querySelectorAll('.account-menu .avatar, .sidebar-foot .avatar, .topbar-avatar')) {
      avatar.textContent = me.displayName[0];
    }
    show('Đã cập nhật họ tên.');
  } catch (error) {
    show(error.message, true);
  } finally {
    button.disabled = false;
  }
});

$('#change-email-toggle').addEventListener('click', () => {
  const open = $('#email-form').hidden;
  $('#email-form').hidden = !open;
  $('#change-email-toggle').setAttribute('aria-expanded', String(open));
  if (open) $('#my-email').focus();
});

$('#email-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  button.disabled = true;
  try {
    const result = await api('/api/v1/account/email', { method: 'POST', body: JSON.stringify({
      currentPassword: $('#email-password').value,
      email: $('#my-email').value.trim(),
    }) });
    $('#email-password').value = '';
    $('#email-form').hidden = true;
    $('#change-email-toggle').setAttribute('aria-expanded', 'false');
    if (result.verificationRequired) {
      sessionStorage.setItem('sems-email-change-request', result.requestId);
      sessionStorage.setItem('sems-email-change-address', $('#my-email').value.trim());
      showEmailCode(result.requestId, $('#my-email').value.trim());
      show(result.message);
    } else {
      renderProfile(await api('/api/v1/me'));
      show(result.message);
      if ($('#users-section').hidden === false) await refresh();
    }
  } catch (error) {
    show(error.message, true);
  } finally {
    button.disabled = false;
  }
});

function showEmailCode(requestId, email) {
  if (!requestId) return;
  $('#email-form').hidden = true;
  $('#email-code-form').hidden = false;
  $('#email-code-address').textContent = email;
  $('#email-code').focus();
}

showEmailCode(sessionStorage.getItem('sems-email-change-request'),
  sessionStorage.getItem('sems-email-change-address'));

$('#email-code-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  button.disabled = true;
  try {
    const result = await api('/api/v1/account/confirm-email-change', {
      method: 'POST',
      body: JSON.stringify({
        requestId: sessionStorage.getItem('sems-email-change-request'),
        code: $('#email-code').value.trim(),
      }),
    });
    sessionStorage.removeItem('sems-email-change-request');
    sessionStorage.removeItem('sems-email-change-address');
    $('#email-code-form').hidden = true;
    $('#email-code').value = '';
    renderProfile(await api('/api/v1/me'));
    if ($('#users-section').hidden === false) await refresh();
    show(result.message);
  } catch (error) {
    show(error.message, true);
  } finally {
    button.disabled = false;
  }
});

$('#email-code-resend').addEventListener('click', async (event) => {
  const button = event.currentTarget;
  button.disabled = true;
  try {
    const result = await api('/api/v1/account/resend-email-change-code', {
      method: 'POST',
      body: JSON.stringify({ requestId: sessionStorage.getItem('sems-email-change-request') }),
    });
    show(result.message);
  } catch (error) {
    show(error.message, true);
  } finally {
    button.disabled = false;
  }
});

$('#email-code-cancel').addEventListener('click', () => {
  sessionStorage.removeItem('sems-email-change-request');
  sessionStorage.removeItem('sems-email-change-address');
  $('#email-code-form').hidden = true;
  $('#email-code').value = '';
  show('Đã hủy thao tác đổi email. Email hiện tại vẫn được giữ nguyên.');
});

$('#resend-verification').addEventListener('click', async (event) => {
  const button = event.currentTarget;
  button.disabled = true;
  try {
    const result = await api('/api/v1/account/resend-verification', { method: 'POST' });
    show(result.message);
  } catch (error) {
    show(error.message, true);
  } finally {
    button.disabled = false;
  }
});

$('#password-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    await api('/api/v1/account/change-password', {
      method: 'POST',
      body: JSON.stringify({
        currentPassword: $('#current-password').value,
        newPassword: $('#new-password').value,
      }),
    });
    $('#password-form').reset();
    location.replace('/login.html?passwordChanged=1');
  } catch (error) {
    show(error.message, true);
  }
});
