// Cùng một thao tác xem/ẩn mật khẩu cho các biểu mẫu đăng nhập và tài khoản.
document.querySelectorAll('input[type="password"]').forEach((input) => {
  const field = document.createElement('div');
  field.className = 'password-field';
  input.parentNode.insertBefore(field, input);
  field.append(input);

  const toggle = document.createElement('button');
  toggle.type = 'button';
  toggle.className = 'password-toggle';
  toggle.setAttribute('aria-label', 'Hiện mật khẩu');
  toggle.setAttribute('aria-pressed', 'false');
  toggle.setAttribute('aria-controls', input.id);
  toggle.innerHTML = `
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor"
         stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
      <path class="eye-slash" d="M3 21 21 3" />
    </svg>`;
  field.append(toggle);

  function setVisible(visible) {
    input.type = visible ? 'text' : 'password';
    toggle.setAttribute('aria-label', visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
    toggle.setAttribute('aria-pressed', String(visible));
    toggle.classList.toggle('is-visible', visible);
  }

  toggle.addEventListener('click', () => setVisible(input.type === 'password'));
  input.form?.addEventListener('reset', () => setVisible(false));
});
