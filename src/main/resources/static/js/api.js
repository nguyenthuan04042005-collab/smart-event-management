export const $ = (selector) => document.querySelector(selector);
const tokenKey = 'sems-access-token';
export const accessToken = () => sessionStorage.getItem(tokenKey);
export const params = new URLSearchParams(location.search);
export const eventUrl = (id) => `/event.html?id=${encodeURIComponent(id)}`;
export const ticketUrl = (code) => `/pages/ticket.html?code=${encodeURIComponent(code)}`;
export const esc = (text) =>
  String(text ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c],
  );

export const searchText = (value) => String(value ?? '')
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '')
  .toLocaleLowerCase('vi')
  .replaceAll('đ', 'd');

// API trả giờ địa phương Việt Nam không kèm offset.
export const eventTime = (value) => new Date(`${value}+07:00`);
export const dateText = (value) =>
  value
    ? eventTime(value).toLocaleString('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
        hour: '2-digit',
        minute: '2-digit',
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
      })
    : '—';

export function registrationState(event) {
  if (event.lifecycleStatus === 'CANCELLED')
    return { open: false, label: 'Sự kiện đã hủy' };
  if (event.approvalStatus && event.approvalStatus !== 'APPROVED')
    return { open: false, label: 'Chưa công khai' };
  const cutoff = eventTime(event.endsAt).getTime() - 15 * 60 * 1000;
  if (Date.now() >= cutoff) return { open: false, label: 'Đã hết giờ đăng ký' };
  if (event.registered >= event.capacity) return { open: false, label: 'Đã hết vé' };
  if (event.status !== 'OPEN') return { open: false, label: 'Đóng đăng ký' };
  return { open: true, label: 'Mở đăng ký' };
}

export function show(message, error = false) {
  const box = $('#status');
  if (!box) return;
  box.replaceChildren();
  box.setAttribute('role', error ? 'alert' : 'status');
  box.setAttribute('aria-live', error ? 'assertive' : 'polite');
  const text = document.createElement('span');
  text.className = 'status-message';
  text.textContent = message;
  const close = document.createElement('button');
  close.type = 'button';
  close.className = 'status-close';
  close.setAttribute('aria-label', 'Đóng thông báo');
  close.textContent = '×';
  close.addEventListener('click', () => {
    box.className = 'status';
  });
  box.append(text, close);
  box.className = `status show${error ? ' error' : ''}`;
}

export async function api(path, options = {}) {
  const headers = { Accept: 'application/json', ...(options.headers || {}) };
  if (options.body && !(options.body instanceof FormData) && !headers['Content-Type'])
    headers['Content-Type'] = 'application/json';
  const publicRequest = path.startsWith('/api/v1/public/') ||
    (path.startsWith('/api/v1/auth/') && path !== '/api/v1/auth/logout');
  if (accessToken() && !publicRequest) headers.Authorization = `Bearer ${accessToken()}`;
  try {
    const response = await fetch(path, { ...options, headers });
    const contentType = response.headers.get('content-type') || '';
    const body = contentType.includes('json') ? await response.json() : null;
    if (!response.ok) {
      if (response.status === 401 && path !== '/api/v1/auth/login')
        sessionStorage.removeItem(tokenKey);
      if (response.status === 401 && location.pathname.startsWith('/pages/')) {
        location.replace(`/login.html?next=${encodeURIComponent(location.pathname + location.search)}`);
      }
      const error = new Error(
        body?.message ||
          (response.status === 401
            ? 'Cần đăng nhập.'
            : `Không thực hiện được thao tác (HTTP ${response.status}).`),
      );
      error.status = response.status;
      throw error;
    }
    if (path === '/api/v1/auth/login' && body?.token)
      sessionStorage.setItem(tokenKey, body.token);
    if (path === '/api/v1/auth/logout')
      sessionStorage.removeItem(tokenKey);
    return body;
  } catch (error) {
    if (error instanceof TypeError)
      throw new Error('Không kết nối được máy chủ. Hãy kiểm tra kết nối rồi thử lại.');
    throw error;
  }
}

export async function downloadReport(eventId, type, format = 'csv') {
  const suffix = format === 'xlsx' ? `${type}.xlsx` : type;
  const response = await fetch(`/api/v1/events/${eventId}/reports/${suffix}`, {
    headers: { Authorization: `Bearer ${accessToken() || ''}` },
  });
  if (!response.ok) {
    if (response.status === 401) {
      location.replace('/login.html');
      throw new Error('Cần đăng nhập lại.');
    }
    throw new Error('Không tải được báo cáo. Hãy thử lại.');
  }
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a');
  link.href = url;
  link.download = `event-${eventId}-${type}.${format}`;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}

const menus = {
  ADMIN: [
    ['dashboard', '▦', 'Dashboard'],
    ['events', '▤', 'Sự kiện'],
    ['venues', '⌖', 'Địa điểm'],
    ['feedback', '★', 'Phản hồi'],
    ['users', '◉', 'Tài khoản'],
    ['mail-settings', '✉', 'Cấu hình email'],
  ],
  ORGANIZER: [
    ['events', '▤', 'Sự kiện của tôi'],
    ['venues', '⌖', 'Địa điểm'],
    ['dashboard', '▦', 'Dashboard'],
    ['feedback', '★', 'Phản hồi'],
    ['users', '◉', 'Tài khoản'],
  ],
  STAFF: [
    ['checkin', '▣', 'Check-in'],
    ['users', '◉', 'Tài khoản'],
  ],
  ATTENDEE: [
    ['my-tickets', '▣', 'Vé của tôi'],
    ['feedback', '★', 'Đánh giá'],
    ['users', '◉', 'Thông tin cá nhân'],
  ],
};

const access = {
  events: ['ADMIN', 'ORGANIZER', 'ATTENDEE'],
  'event-detail': ['ADMIN', 'ORGANIZER'],
  venues: ['ADMIN', 'ORGANIZER'],
  dashboard: ['ADMIN', 'ORGANIZER'],
  checkin: ['STAFF'],
  feedback: ['ADMIN', 'ORGANIZER', 'ATTENDEE'],
  register: ['ATTENDEE'],
  ticket: ['ADMIN', 'ATTENDEE'],
  'my-tickets': ['ATTENDEE'],
  users: ['ADMIN', 'ORGANIZER', 'STAFF', 'ATTENDEE'],
  'mail-settings': ['ADMIN'],
};

export const roleOf = (me) =>
  ['ADMIN', 'ORGANIZER', 'STAFF', 'ATTENDEE'].find((role) =>
    me.roles.some((item) => item.authority === `ROLE_${role}`),
  );

export const homeFor = (role) =>
  role === 'ADMIN'
    ? '/pages/dashboard.html'
    : role === 'ORGANIZER'
      ? '/pages/events.html'
      : role === 'STAFF'
      ? '/pages/checkin.html'
      : '/';

export function attendeeAccount(me, logoutId = 'global-logout') {
  const name = esc(me.displayName || me.username);
  const initial = esc(me.displayName?.[0] || me.username[0]);
  return `
    <details class="account-menu">
      <summary><span class="avatar">${initial}</span><span class="account-menu-name">${name}</span></summary>
      <div class="account-menu-list">
        <a href="/pages/users.html">Thông tin cá nhân</a>
        <a href="/pages/my-tickets.html">Vé của tôi</a>
        <a href="/pages/feedback.html">Đánh giá của tôi</a>
        <button type="button" id="${logoutId}">Đăng xuất</button>
      </div>
    </details>`;
}

export function attendeeHeader(me, activePage = '', logoutId = 'global-logout') {
  const links = [
    ['events', '/', 'Sự kiện'],
    ['tickets', '/pages/my-tickets.html', 'Vé của tôi'],
    ['feedback', '/pages/feedback.html', 'Đánh giá'],
    ['about', '/about.html', 'Giới thiệu'],
  ].map(([key, href, label]) =>
    `<a href="${href}" ${activePage === key ? 'aria-current="page"' : ''}>${label}</a>`).join('');
  return `
    <div class="attendee-header-inner">
      <a class="attendee-brand" href="/" aria-label="SEMS - Trang chủ">
        <span class="brand-mark">S</span><span>SEMS<small>SMART EVENT MANAGEMENT SYSTEM</small></span>
      </a>
      <nav class="attendee-nav" aria-label="Điều hướng người tham dự">${links}</nav>
      <div class="attendee-account">${attendeeAccount(me, logoutId)}</div>
    </div>`;
}

function renderShell(me, role, page) {
  const labels = { ADMIN: 'Quản trị viên', ORGANIZER: 'Ban tổ chức', STAFF: 'Nhân viên check-in', ATTENDEE: 'Người tham dự' };
  const name = esc(me.displayName || me.username);
  const activePage = page === 'event-detail' ? 'events' : page;
  const links = menus[role]
    .map(([key, icon, label]) =>
      `<a href="/pages/${key}.html" class="${activePage === key ? 'active' : ''}" ${activePage === key ? 'aria-current="page"' : ''}><span class="nav-icon" aria-hidden="true">${icon}</span>${label}</a>`,
    )
    .join('');
  if (role === 'ATTENDEE') {
    document.body.classList.add('attendee-workspace');
    const active = page === 'my-tickets' || page === 'ticket' ? 'tickets'
      : page === 'feedback' ? 'feedback'
      : page === 'register' ? 'events' : '';
    $('header').className = 'attendee-header';
    $('header').innerHTML = attendeeHeader(me, active);
  } else {
    $('header').innerHTML = `
    <a class="brand" href="${homeFor(role)}"><span class="brand-mark">S</span><span>SEMS<small>SMART EVENT<br />MANAGEMENT SYSTEM</small></span></a>
    <div class="nav-caption">KHÔNG GIAN LÀM VIỆC</div>
    <nav aria-label="Điều hướng chính">${links}</nav>
    <div class="sidebar-foot"><span class="avatar">${esc(me.displayName?.[0] || me.username[0])}</span><div><strong>${name}</strong><small>${labels[role]}</small></div></div>`;
    const top = document.createElement('div');
    top.className = 'topbar';
    top.innerHTML = `<div><span class="topbar-breadcrumb">SEMS / ${labels[role]}</span><strong>${document.title.split('·')[0].trim()}</strong></div><div class="topbar-user"><span class="topbar-avatar">${esc(me.displayName?.[0] || me.username[0])}</span><span>${name}</span><button type="button" class="logout-button" id="global-logout">Đăng xuất</button></div>`;
    $('main').prepend(top);
  }
  const footer = document.createElement('footer');
  footer.className = 'app-footer';
  footer.innerHTML = `<span>© 2026 SEMS · Smart Event Management System</span><span><a href="/">Trang chủ</a><a href="/about.html">Giới thiệu</a></span>`;
  $('main').append(footer);
  $('#global-logout').addEventListener('click', async () => {
    try {
      await api('/api/v1/auth/logout', { method: 'POST' });
      location.replace('/login.html');
    } catch (error) {
      show(error.message, true);
    }
  });
}

export async function authForm(onReady = async () => {}) {
  try {
    const me = await api('/api/v1/me');
    const role = roleOf(me);
    const page = location.pathname.split('/').pop().replace('.html', '');
    if (!role) {
      location.replace('/login.html');
      return;
    }
    if (role === 'ATTENDEE' && page === 'events') {
      location.replace('/');
      return;
    }
    if (!access[page]?.includes(role)) {
      location.replace(homeFor(role));
      return;
    }
    renderShell(me, role, page);
    document.body.dataset.role = role;
    await onReady(me);
    document.body.classList.add('ready');
  } catch (error) {
    if (error.status === 401) return;
    document.body.classList.add('ready');
    show(error.message, true);
  }
}
