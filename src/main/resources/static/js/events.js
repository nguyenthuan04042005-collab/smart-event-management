import { $, api, esc, dateText, eventUrl, show, authForm, registrationState } from './api.js';

let events = [];
let venues = [];
let isAdmin = false;
let isOrganizer = false;
let bannerUrl = null;
let previewObjectUrl = null;

function updateBannerPreview() {
  if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl);
  const selected = $('#banner-file').files[0];
  previewObjectUrl = selected ? URL.createObjectURL(selected) : null;
  const source = previewObjectUrl || bannerUrl;
  $('#banner-preview').hidden = !source;
  $('#remove-banner').hidden = !source;
  $('#remove-banner').textContent = selected ? 'Bỏ ảnh đã chọn' : 'Xóa ảnh banner';
  if (source) $('#banner-preview').src = source;
  else $('#banner-preview').removeAttribute('src');
}

const categories = { TECHNOLOGY: 'Công nghệ', ENTERTAINMENT: 'Giải trí', EDUCATION: 'Giáo dục', OTHER: 'Khác' };
const approvals = { DRAFT: 'Bản nháp', PENDING: 'Chờ duyệt', APPROVED: 'Đã công khai', REJECTED: 'Bị từ chối' };

function modeChanged() {
  const online = $('#event-mode').value === 'online';
  $('#online-field').hidden = !online;
  $('#venue').required = !online;
  $('#online-url').required = online;
  $('#venue').disabled = online;
  venueHint();
}

function addAgenda(item = {}) {
  const row = document.createElement('div');
  row.className = 'editor-row agenda-row';
  row.innerHTML = `<div class="row"><div><label>Tên phiên</label><input class="agenda-title" maxlength="180" required value="${esc(item.title || '')}" /></div><div><label>Phòng / sân khấu</label><input class="agenda-room" maxlength="160" value="${esc(item.roomName || '')}" /></div></div><div class="row"><div><label>Bắt đầu</label><input class="agenda-start" type="datetime-local" required value="${esc(item.startsAt?.slice(0,16) || '')}" /></div><div><label>Kết thúc</label><input class="agenda-end" type="datetime-local" required value="${esc(item.endsAt?.slice(0,16) || '')}" /></div></div><button type="button" class="btn secondary remove-row">Xóa phiên</button>`;
  $('#agenda-editor').append(row);
}

function addSpeaker(item = {}) {
  const row = document.createElement('div');
  row.className = 'editor-row speaker-row';
  row.innerHTML = `<label>Họ tên</label><input class="speaker-name" maxlength="120" required value="${esc(item.name || '')}" /><label>Chủ đề trình bày</label><input class="speaker-topic" maxlength="180" value="${esc(item.topic || '')}" /><label>Tiểu sử</label><textarea class="speaker-bio" maxlength="500">${esc(item.biography || '')}</textarea><label>URL ảnh</label><input class="speaker-image" type="text" maxlength="500" value="${esc(item.imageUrl || '')}" /><label class="inline-check"><input class="speaker-vip" type="checkbox" ${item.vip ? 'checked' : ''} /> Khách mời VIP</label><button type="button" class="btn secondary remove-row">Xóa người</button>`;
  $('#speaker-editor').append(row);
}

function paint() {
  $('#total-events').textContent = events.length;
  $('#open-events').textContent = events.filter((e) => registrationState(e).open).length;
  $('#total-tickets').textContent = events.reduce((sum, e) => sum + e.registered, 0);
  const query = $('#search').value.trim().toLocaleLowerCase('vi');
  const filter = $('#filter').value;
  const category = $('#category-filter').value;
  const visible = events.filter((event) => {
    const matches = `${event.title} ${event.venueName} ${event.description} ${event.organizerName}`.toLocaleLowerCase('vi').includes(query);
    const open = registrationState(event).open;
    return matches && (category === 'all' || event.category === category)
      && (filter === 'all' || (filter === 'open' ? open : !open));
  });
  $('#event-count').textContent = `${visible.length} sự kiện`;
  $('#event-list').innerHTML = visible.length
    ? visible
        .map((e) => {
          const state = registrationState(e);
          return `
      <article class="card event-card">
        ${e.bannerUrl ? `<img class="event-card-banner" src="${esc(e.bannerUrl)}" alt="Banner ${esc(e.title)}" loading="lazy" />` : ''}
        <span class="pill ${state.open ? '' : 'closed'}">${state.label}</span>
        ${e.lifecycleStatus === 'CANCELLED' && e.cancelReason ? `<p class="review-callout"><strong>Lý do hủy:</strong> ${esc(e.cancelReason)}</p>` : ''}
        ${isAdmin || isOrganizer ? `<span class="small muted">${approvals[e.approvalStatus]} · ${esc(e.organizerName)}</span>` : ''}
        <h3>${esc(e.title)}</h3>
        <p class="event-meta">${categories[e.category] || 'Khác'}</p>
        <p class="event-meta">${esc(e.venueName)}</p>
        <p class="event-meta">${dateText(e.startsAt)}</p>
        <p class="event-description">${esc(e.description || 'Khám phá sự kiện và đăng ký nhận vé QR miễn phí.')}</p>
        ${isOrganizer && e.approvalStatus === 'REJECTED' && e.reviewNote ? `<p class="review-callout"><strong>Lý do từ chối:</strong> ${esc(e.reviewNote)}</p>` : ''}
        <p class="small muted">${e.registered} / ${e.capacity} vé đã phát hành</p>
        <progress class="progress" value="${e.registered}" max="${e.capacity}" aria-label="Số vé đã phát hành"></progress>
        <div class="actions">
          ${isAdmin || isOrganizer ? '' : `<a class="btn" href="${eventUrl(e.id)}">${state.open ? 'Xem & đăng ký' : 'Xem chi tiết'}</a>`}
          ${isAdmin || isOrganizer ? `<a class="btn secondary" href="/pages/event-detail.html?id=${encodeURIComponent(e.id)}">Xem chi tiết</a>` : ''}
          ${isOrganizer && e.lifecycleStatus !== 'CANCELLED' ? `<button class="btn secondary edit" data-id="${e.id}">Chỉnh sửa</button>` : ''}
          ${isOrganizer && ['DRAFT', 'REJECTED'].includes(e.approvalStatus) ? `<button class="btn submit-review" data-id="${e.id}">Gửi duyệt</button>` : ''}
          ${isAdmin && e.approvalStatus === 'PENDING' ? `<span class="small muted">Mở chi tiết để duyệt nội dung</span>` : ''}
        </div>
      </article>
    `;
        })
        .join('')
    : `<p class="empty">${isOrganizer ? 'Chưa có sự kiện phù hợp. Hãy đổi bộ lọc hoặc tạo sự kiện mới.' : 'Chưa có sự kiện phù hợp. Hãy thử bộ lọc khác hoặc quay lại sau.'}</p>`;
  document
    .querySelectorAll('.edit')
    .forEach((button) => button.addEventListener('click', () => edit(Number(button.dataset.id))));
  document.querySelectorAll('.submit-review').forEach((button) => button.addEventListener('click', async () => {
    const id = Number(button.dataset.id);
    button.disabled = true;
    try {
      await api(`/api/v1/events/${id}/submit`, { method: 'POST' });
      show('Đã gửi sự kiện để quản trị viên duyệt.');
      await render();
    } catch (error) {
      show(error.message, true);
      button.disabled = false;
    }
  }));
}

async function render() {
  events = (await api('/api/v1/events')).sort((a, b) => b.id - a.id);
  paint();
}

function venueHint() {
  if ($('#event-mode').value === 'online') {
    $('#capacity').removeAttribute('max');
    $('#venue-help').textContent = 'Số vé tối đa của sự kiện trực tuyến do ban tổ chức quyết định.';
    return;
  }
  const venue = venues.find((v) => v.id === Number($('#venue').value));
  if (!venue) {
    $('#capacity').removeAttribute('max');
    return;
  }
  $('#capacity').max = venue.capacity;
  $('#venue-help').textContent =
    `Sức chứa địa điểm: ${venue.capacity} người. Số vé phải từ 1 đến ${venue.capacity}.`;
}

function newEvent() {
  $('#event-form').reset();
  $('#event-id').value = '';
  bannerUrl = null;
  updateBannerPreview();
  for (const option of $('#venue').options) {
    option.disabled = !venues.find((venue) => venue.id === Number(option.value))?.active;
  }
  $('#venue').value = String(venues.find((venue) => venue.active)?.id || '');
  $('#agenda-editor').replaceChildren();
  $('#speaker-editor').replaceChildren();
  $('#editor-title').textContent = 'Tạo sự kiện';
  const now = Date.now();
  $('#starts').value = new Date(now + 7 * 3600000).toISOString().slice(0, 16);
  $('#ends').value = new Date(now + 9 * 3600000).toISOString().slice(0, 16);
  modeChanged();
  venueHint();
}

function edit(id) {
  const e = events.find((item) => item.id === id);
  if (!e) return;
  $('#editor-title').textContent = 'Chỉnh sửa sự kiện';
  $('#event-id').value = id;
  $('#title').value = e.title;
  $('#description').value = e.description || '';
  $('#category').value = e.category || 'OTHER';
  bannerUrl = e.bannerUrl || null;
  $('#banner-file').value = '';
  updateBannerPreview();
  $('#event-mode').value = e.onlineUrl ? 'online' : 'offline';
  $('#online-url').value = e.onlineUrl || '';
  modeChanged();
  const selectedVenue = [...$('#venue').options].find((option) => Number(option.value) === e.venueId);
  if (selectedVenue) selectedVenue.disabled = false;
  $('#venue').value = e.venueId;
  $('#starts').value = e.startsAt.slice(0, 16);
  $('#ends').value = e.endsAt.slice(0, 16);
  $('#capacity').value = e.capacity;
  $('#event-status').value = e.status;
  $('#agenda-editor').replaceChildren();
  $('#speaker-editor').replaceChildren();
  e.agenda.forEach(addAgenda);
  e.speakers.forEach(addSpeaker);
  venueHint();
  $('#editor').scrollIntoView({ behavior: 'smooth', block: 'start' });
  $('#title').focus({ preventScroll: true });
}

async function ready(me) {
  isAdmin = me.roles.some((r) => r.authority === 'ROLE_ADMIN');
  isOrganizer = me.roles.some((r) => r.authority === 'ROLE_ORGANIZER');
  if (isOrganizer) {
    venues = await api('/api/v1/venues');
    $('#venue').innerHTML = venues
      .map((v) => `<option value="${v.id}" ${v.active ? '' : 'disabled'}>${esc(v.name)}${v.active ? '' : ' (ngừng sử dụng)'}</option>`)
      .join('');
    $('#editor').hidden = false;
    $('#create-event-shortcut').hidden = false;
    $('#manage-venues').hidden = false;
    newEvent();
  } else if (!isAdmin) {
    document.querySelector('.events-layout').classList.add('visitor-layout');
    document.querySelector('.hero h1').textContent = 'Khám phá sự kiện sắp tới.';
    document.querySelector('.hero p').textContent =
      'Chọn sự kiện, đăng ký vé miễn phí và dùng mã QR để check-in tại cổng.';
  }
  await render();
  const editId = Number(new URLSearchParams(location.search).get('edit'));
  if (isOrganizer && editId && events.some((event) => event.id === editId)) edit(editId);
}

authForm(ready);
$('#search').addEventListener('input', paint);
$('#filter').addEventListener('change', paint);
$('#category-filter').addEventListener('change', paint);
$('#event-mode').addEventListener('change', modeChanged);
$('#add-agenda').addEventListener('click', () => addAgenda());
$('#add-speaker').addEventListener('click', () => addSpeaker());
for (const id of ['agenda-editor', 'speaker-editor']) {
  $(`#${id}`).addEventListener('click', (event) => {
    if (event.target.classList.contains('remove-row')) event.target.closest('.editor-row').remove();
  });
}
$('#venue').addEventListener('change', venueHint);
$('#new-event').addEventListener('click', newEvent);
$('#banner-file').addEventListener('change', () => {
  const file = $('#banner-file').files[0];
  if (file && (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 5 * 1024 * 1024)) {
    $('#banner-file').value = '';
    show('Chọn ảnh PNG hoặc JPEG tối đa 5 MB.', true);
  }
  updateBannerPreview();
});
$('#remove-banner').addEventListener('click', async () => {
  if ($('#banner-file').files.length) {
    $('#banner-file').value = '';
    updateBannerPreview();
    return;
  }
  const id = $('#event-id').value;
  if (!id) {
    bannerUrl = null;
    updateBannerPreview();
    return;
  }
  try {
    await api(`/api/v1/events/${id}/banner`, { method: 'DELETE' });
    bannerUrl = null;
    updateBannerPreview();
    show('Đã xóa ảnh banner. Sự kiện cần được gửi duyệt lại.');
    await render();
  } catch (error) {
    show(error.message, true);
  }
});
$('#create-event-shortcut').addEventListener('click', () => {
  newEvent();
  $('#editor').scrollIntoView({ behavior: 'smooth', block: 'start' });
  $('#title').focus({ preventScroll: true });
});

$('#event-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = $('#event-id').value;
  const submit = e.submitter;
  const body = {
    title: $('#title').value.trim(),
    description: $('#description').value.trim(),
    venueId: $('#event-mode').value === 'online' ? null : Number($('#venue').value),
    onlineUrl: $('#event-mode').value === 'online' ? $('#online-url').value.trim() : null,
    bannerUrl,
    category: $('#category').value,
    agenda: [...document.querySelectorAll('.agenda-row')].map((row) => ({
      title: row.querySelector('.agenda-title').value.trim(),
      roomName: row.querySelector('.agenda-room').value.trim(),
      startsAt: row.querySelector('.agenda-start').value,
      endsAt: row.querySelector('.agenda-end').value,
    })),
    speakers: [...document.querySelectorAll('.speaker-row')].map((row) => ({
      name: row.querySelector('.speaker-name').value.trim(),
      topic: row.querySelector('.speaker-topic').value.trim(),
      biography: row.querySelector('.speaker-bio').value.trim(),
      imageUrl: row.querySelector('.speaker-image').value.trim() || null,
      vip: row.querySelector('.speaker-vip').checked,
    })),
    startsAt: $('#starts').value,
    endsAt: $('#ends').value,
    capacity: Number($('#capacity').value),
    status: $('#event-status').value,
  };
  if (!body.title) return show('Vui lòng nhập tên sự kiện.', true);
  if (new Date(body.endsAt) - new Date(body.startsAt) < 30 * 60000) {
    $('#ends').focus();
    return show('Giờ kết thúc phải cách giờ bắt đầu ít nhất 30 phút.', true);
  }
  if (submit) submit.disabled = true;
  let metadataSaved = false;
  try {
    const saved = await api(id ? `/api/v1/events/${id}` : '/api/v1/events', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(body),
    });
    metadataSaved = true;
    $('#event-id').value = saved.id;
    if ($('#banner-file').files.length) {
      const data = new FormData();
      data.append('file', $('#banner-file').files[0]);
      const withBanner = await api(`/api/v1/events/${saved.id}/banner`, {
        method: 'POST', body: data,
      });
      bannerUrl = withBanner.bannerUrl;
      $('#banner-file').value = '';
      updateBannerPreview();
    }
    $('#event-id').value = saved.id;
    $('#editor-title').textContent = 'Chỉnh sửa sự kiện';
    show(id
      ? saved.approvalStatus === 'DRAFT'
        ? 'Đã lưu nội dung. Sự kiện đang là bản nháp; hãy gửi duyệt lại để công khai.'
        : 'Đã cập nhật trạng thái đăng ký của sự kiện.'
      : 'Đã lưu bản nháp. Hãy gửi duyệt để công khai.');
    await render();
  } catch (err) {
    show(metadataSaved ? `Thông tin sự kiện đã lưu, nhưng ảnh banner chưa tải được: ${err.message}`
      : err.message, true);
  } finally {
    if (submit) submit.disabled = false;
  }
});

