import { $, api, attendeeAccount, dateText, esc, homeFor, registrationState, roleOf } from './api.js';

const categories = { TECHNOLOGY: 'Công nghệ', ENTERTAINMENT: 'Giải trí', EDUCATION: 'Giáo dục', OTHER: 'Khác' };
let availableEvents = [];
const session = api('/api/v1/me').catch(() => null);

async function renderSession() {
  const me = await session;
  const account = $('.public-account');
  if (!account) return;
  account.classList.remove('session-pending');
  account.removeAttribute('aria-busy');
  if (!me) return;
  const role = roleOf(me);
  const name = esc(me.displayName || me.username);
  if (role === 'ATTENDEE') {
    account.innerHTML = attendeeAccount(me, 'public-logout');
  } else {
    const current = document.body.dataset.page === 'about' ? 'about' : 'events';
    $('.attendee-nav').innerHTML = `
      <a href="/" ${current === 'events' ? 'aria-current="page"' : ''}>Sự kiện</a>
      <a href="/about.html" ${current === 'about' ? 'aria-current="page"' : ''}>Giới thiệu</a>
      <a href="${homeFor(role)}">Khu vực làm việc</a>`;
    account.innerHTML = `
      <details class="account-menu">
        <summary><span class="avatar">${esc(me.displayName?.[0] || me.username[0])}</span><span class="account-menu-name">${name}</span></summary>
        <div class="account-menu-list">
          <a href="${homeFor(role)}">Khu vực làm việc</a>
          <a href="/pages/users.html">Thông tin cá nhân</a>
          <button type="button" id="public-logout">Đăng xuất</button>
        </div>
      </details>`;
  }
  $('#public-logout').addEventListener('click', async () => {
    try {
      await api('/api/v1/auth/logout', { method: 'POST' });
      location.replace('/');
    } catch (error) {
      alert(error.message);
    }
  });
  const footerAccount = document.querySelector('.footer-grid > div:last-child');
  if (footerAccount) footerAccount.innerHTML = `<strong>Tài khoản</strong><a href="/pages/users.html">Thông tin cá nhân</a><a href="${homeFor(role)}">${role === 'ATTENDEE' ? 'Khám phá sự kiện' : 'Khu vực làm việc'}</a>`;
  const signupCta = document.querySelector('.public-cta a[href="/signup.html"]');
  if (signupCta) {
    signupCta.href = role === 'ATTENDEE' ? '/pages/my-tickets.html' : homeFor(role);
    signupCta.textContent = role === 'ATTENDEE' ? 'Xem vé của tôi →' : 'Vào khu vực làm việc →';
  }
}

renderSession();

function eventCard(event) {
  const state = registrationState(event);
  const available = Math.max(0, event.capacity - event.registered);
  return `
    <article class="public-event-card">
      ${event.bannerUrl ? `<img class="public-event-banner" src="${esc(event.bannerUrl)}" alt="Banner ${esc(event.title)}" loading="lazy" />` : ''}
      <span class="public-event-category">${categories[event.category] || 'Khác'}</span>
      <span class="event-date">${esc(dateText(event.startsAt))}</span>
      <h3>${esc(event.title)}</h3>
      <p>⌖ ${esc(event.venueName)} · ${esc(event.organizerName)}</p>
      <p class="event-excerpt">${esc(event.description || 'Xem thông tin sự kiện và đăng ký vé QR miễn phí.')}</p>
      <div class="event-card-bottom">
        <span>${state.open ? `${available} chỗ còn lại` : state.label}</span>
        <a href="/event.html?id=${encodeURIComponent(event.id)}">Xem chi tiết →</a>
      </div>
    </article>`;
}

async function loadHome() {
  const target = $('#public-events');
  try {
    availableEvents = await api('/api/v1/public/events');
    paintHome();
  } catch (error) {
    target.innerHTML = `<p class="public-empty" role="alert">${esc(error.message)}</p>`;
  }
}

function paintHome() {
  const query = $('#public-search').value.trim().toLocaleLowerCase('vi');
  const place = $('#public-place').value.trim().toLocaleLowerCase('vi');
  const category = $('#public-category').value;
  const date = $('#public-date').value;
  const filtered = availableEvents.filter((event) =>
    `${event.title} ${event.description || ''} ${event.organizerName} ${event.speakers.map((s) => s.name).join(' ')}`
      .toLocaleLowerCase('vi').includes(query)
    && `${event.venueName} ${event.venueAddress}`.toLocaleLowerCase('vi').includes(place)
    && (category === 'all' || event.category === category)
    && (!date || event.startsAt.slice(0, 10) === date));
  $('#public-count').textContent = `${filtered.length} sự kiện phù hợp`;
  $('#public-events').innerHTML = filtered.length ? filtered.map(eventCard).join('')
    : '<p class="public-empty">Không tìm thấy sự kiện phù hợp. Hãy thử bộ lọc khác.</p>';
}

async function loadEvent() {
  const id = new URLSearchParams(location.search).get('id');
  if (!id || !/^\d+$/.test(id)) {
    $('#event-title').textContent = 'Không tìm thấy sự kiện';
    $('#event-intro').textContent = 'Hãy trở lại danh sách sự kiện để chọn chương trình khác.';
    $('.event-info-card').hidden = true;
    return;
  }
  try {
    const event = await api(`/api/v1/public/events/${encodeURIComponent(id)}`);
    const state = registrationState(event);
    document.title = `${event.title} · SEMS`;
    $('#event-title').textContent = event.title;
    $('#event-intro').textContent = `${event.venueName} · ${dateText(event.startsAt)}`;
    $('#event-description').textContent = event.description || 'Ban tổ chức chưa cập nhật mô tả chi tiết.';
    $('#event-category').textContent = categories[event.category] || 'Khác';
    $('#event-organizer').textContent = event.organizerName;
    if (event.bannerUrl) {
      $('#event-banner').src = event.bannerUrl;
      $('#event-banner').hidden = false;
    }
    $('#event-agenda').innerHTML = event.agenda.length
      ? event.agenda.map((item) => `<article><strong>${esc(item.title)}</strong><p>${dateText(item.startsAt)} – ${dateText(item.endsAt)}${item.roomName ? ` · ${esc(item.roomName)}` : ''}</p></article>`).join('')
      : '<p>Ban tổ chức chưa cập nhật lịch trình.</p>';
    $('#event-speakers').innerHTML = event.speakers.length
      ? event.speakers.map((speaker) => `<article class="speaker-card">${speaker.imageUrl ? `<img src="${esc(speaker.imageUrl)}" alt="Ảnh ${esc(speaker.name)}" loading="lazy" />` : '<span class="speaker-avatar" aria-hidden="true">✦</span>'}<div><strong>${esc(speaker.name)}</strong>${speaker.vip ? '<span class="vip-tag">Khách mời VIP</span>' : ''}<p>${esc(speaker.topic || '')}</p><small>${esc(speaker.biography || '')}</small></div></article>`).join('')
      : '<p>Ban tổ chức chưa cập nhật diễn giả.</p>';
    $('#event-time').textContent = `${dateText(event.startsAt)} – ${dateText(event.endsAt)}`;
    $('#event-place').textContent = `${event.venueName} · ${event.venueAddress}`;
    $('#event-capacity').textContent = `${event.registered} / ${event.capacity} vé đã đăng ký`;
    $('#event-state').textContent = state.label;
    if (event.lifecycleStatus === 'CANCELLED')
      $('#event-description').textContent = `${event.description || ''}\n\nSỰ KIỆN ĐÃ HỦY. Lý do: ${event.cancelReason || 'Ban tổ chức chưa cập nhật.'}`;
    $('#event-state').classList.toggle('closed', !state.open);
    const next = `/pages/register.html?event=${encodeURIComponent(event.id)}`;
    const me = await session;
    const role = me ? roleOf(me) : null;
    const myTickets = role === 'ATTENDEE'
      ? await api('/api/v1/tickets/mine').catch(() => [])
      : [];
    const ownedTicket = myTickets.find((ticket) =>
      ticket.eventId === Number(id) && ticket.status !== 'CANCELLED');
    $('#event-register').href = role === 'ATTENDEE'
      ? ownedTicket
        ? `/pages/ticket.html?code=${encodeURIComponent(ownedTicket.code)}`
        : next
      : role ? homeFor(role) : `/login.html?next=${encodeURIComponent(next)}`;
    $('#event-register').textContent = ownedTicket
      ? 'Xem vé của tôi →'
      : role === 'ATTENDEE'
      ? 'Đăng ký vé miễn phí →'
      : role ? 'Vào khu vực làm việc →' : 'Đăng nhập để đăng ký →';
    $('#event-signup').href = `/signup.html?next=${encodeURIComponent(next)}`;
    $('.event-info-note').hidden = Boolean(me);
    if (!state.open && !ownedTicket) {
      $('#event-register').textContent = state.label;
      $('#event-register').removeAttribute('href');
      $('#event-register').setAttribute('aria-disabled', 'true');
      $('.event-info-note').hidden = true;
    }
  } catch (error) {
    $('#event-title').textContent = 'Không tải được sự kiện';
    $('#event-intro').textContent = error.message;
    $('.event-info-card').hidden = true;
  }
}

if (document.body.dataset.page === 'home') {
  for (const id of ['public-search', 'public-category', 'public-date', 'public-place']) {
    $(`#${id}`).addEventListener('input', paintHome);
  }
  loadHome();
}
if (document.body.dataset.page === 'event') loadEvent();
