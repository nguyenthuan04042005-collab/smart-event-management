import { $, api, authForm, dateText, esc, eventTime, show } from './api.js';

const eventId = new URLSearchParams(location.search).get('id');
let currentEvent;
let isAdmin = false;
let isOrganizer = false;
let reviewAction = '';
let staffAccounts = [];
let assignedStaff = [];

const categories = {
  TECHNOLOGY: 'Công nghệ',
  ENTERTAINMENT: 'Giải trí',
  EDUCATION: 'Giáo dục',
  OTHER: 'Khác',
};
const approvals = {
  DRAFT: 'Bản nháp',
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã công khai',
  REJECTED: 'Bị từ chối',
};

function paint(event) {
  currentEvent = event;
  document.title = `${event.title} · SEMS`;
  $('#detail-title').textContent = event.title;
  $('#detail-intro').textContent = `${event.venueName} · ${dateText(event.startsAt)}`;
  $('#detail-category').textContent = categories[event.category] || 'Khác';
  $('#detail-description').textContent = event.description || 'Chưa có mô tả.';
  $('#detail-approval').textContent = event.lifecycleStatus === 'CANCELLED'
    ? 'Sự kiện đã hủy' : approvals[event.approvalStatus] || event.approvalStatus;
  $('#detail-organizer').textContent = event.organizerName;
  $('#detail-time').textContent = `${dateText(event.startsAt)} – ${dateText(event.endsAt)}`;
  $('#detail-place').textContent = event.venueAddress
    ? `${event.venueName} · ${event.venueAddress}`
    : event.venueName;
  $('#detail-state').textContent = event.status === 'OPEN' ? 'Mở đăng ký' : 'Đóng đăng ký';
  $('#detail-cancel-row').hidden = event.lifecycleStatus !== 'CANCELLED';
  $('#detail-cancel-reason').textContent = event.cancelReason || 'Không có lý do.';
  $('#detail-capacity').textContent = `${event.registered} / ${event.capacity} vé`;
  $('#detail-banner').hidden = !event.bannerUrl;

  if (event.bannerUrl) {
    $('#detail-banner').src = event.bannerUrl;
    $('#detail-banner').alt = `Banner ${event.title}`;
    $('#detail-banner').hidden = false;
  }
  $('#detail-online-row').hidden = !event.onlineUrl;
  if (event.onlineUrl) {
    $('#detail-online').href = event.onlineUrl;
    $('#detail-online-row').hidden = false;
  }
  $('#detail-review-row').hidden = !event.reviewNote;
  if (event.reviewNote) {
    $('#detail-review-label').textContent = event.approvalStatus === 'REJECTED'
      ? 'Lý do từ chối' : event.approvalStatus === 'PENDING'
        ? 'Lý do xét lại' : 'Ghi chú xét duyệt';
    $('#detail-review').textContent = event.reviewNote;
    $('#detail-review-row').hidden = false;
  }

  $('#detail-agenda').innerHTML = event.agenda.length
    ? event.agenda.map((item) => `
      <article>
        <strong>${esc(item.title)}</strong>
        <p>${dateText(item.startsAt)} – ${dateText(item.endsAt)}${item.roomName ? ` · ${esc(item.roomName)}` : ''}</p>
      </article>`).join('')
    : '<p class="muted">Chưa có lịch trình.</p>';

  $('#detail-speakers').innerHTML = event.speakers.length
    ? event.speakers.map((speaker) => `
      <article class="management-speaker">
        ${speaker.imageUrl
          ? `<img src="${esc(speaker.imageUrl)}" alt="Ảnh ${esc(speaker.name)}" loading="lazy" />`
          : '<span class="management-speaker-avatar" aria-hidden="true">✦</span>'}
        <div>
          <strong>${esc(speaker.name)}</strong>
          ${speaker.vip ? '<span class="management-vip">Khách mời VIP</span>' : ''}
          <p>${esc(speaker.topic || 'Chưa cập nhật chủ đề')}</p>
          <small>${esc(speaker.biography || '')}</small>
        </div>
      </article>`).join('')
    : '<p class="muted">Chưa có diễn giả hoặc khách mời.</p>';

  $('#detail-content').hidden = false;
  const actions = [];
  if (isOrganizer) {
    if (event.lifecycleStatus !== 'CANCELLED')
      actions.push(`<a class="btn secondary" href="/pages/events.html?edit=${event.id}">Chỉnh sửa</a>`);
    if (['DRAFT', 'REJECTED'].includes(event.approvalStatus))
      actions.push('<button class="btn" type="button" data-review="submit">Gửi duyệt</button>');
    if (event.approvalStatus === 'PENDING')
      actions.push('<button class="btn secondary" type="button" data-review="withdraw">Rút yêu cầu duyệt</button>');
    if (event.approvalStatus === 'DRAFT' && event.ticketHistoryCount === 0)
      actions.push('<button class="btn danger" type="button" data-review="delete">Xóa bản nháp</button>');
  }
  if (isAdmin && event.approvalStatus === 'PENDING') {
    actions.push('<button class="btn" type="button" data-review="approve">Phê duyệt</button>');
    actions.push('<button class="btn danger" type="button" data-review="reject">Từ chối</button>');
  }
  if (isAdmin && ['REJECTED', 'APPROVED'].includes(event.approvalStatus)
      && event.lifecycleStatus !== 'CANCELLED') {
    const cannotReconsider = event.approvalStatus === 'APPROVED'
      && (event.registered > 0 || eventTime(event.startsAt).getTime() <= Date.now());
    actions.push(`<button class="btn secondary" type="button" data-review="reconsider" ${cannotReconsider ? 'disabled title="Sự kiện đã có vé hoặc đã bắt đầu."' : ''}>Xét lại quyết định</button>`);
    if (cannotReconsider)
      actions.push('<p class="small muted">Đã có vé hoặc đã bắt đầu: không thể thu hồi duyệt. Nếu sự kiện chưa bắt đầu và không thể tổ chức, hãy dùng Hủy sự kiện.</p>');
  }
  if ((isAdmin || isOrganizer) && event.approvalStatus === 'APPROVED'
      && event.lifecycleStatus !== 'CANCELLED' && eventTime(event.startsAt).getTime() > Date.now())
    actions.push('<button class="btn danger" type="button" data-review="cancel">Hủy sự kiện</button>');
  if (event.approvalStatus === 'APPROVED')
    actions.push(`<a class="btn secondary" href="/event.html?id=${event.id}">Trang công khai</a>`);
  $('#detail-actions').innerHTML = actions.join('');
  $('#detail-actions').hidden = false;
}

async function loadHistory() {
  const history = await api(`/api/v1/events/${eventId}/reviews`);
  $('#review-history').innerHTML = history.length
    ? history.map((item) => `<article class="review-item">
        <strong>${esc(approvals[item.previousStatus] || item.previousStatus)} → ${esc(approvals[item.status] || item.status)}</strong>
        <span class="small muted">${esc(item.reviewerName)} · ${dateText(item.reviewedAt)}</span>
        ${item.note ? `<p>${esc(item.note)}</p>` : ''}
      </article>`).join('')
    : '<p class="muted">Chưa có quyết định xét duyệt.</p>';
  $('#review-history-section').hidden = false;
}

function paintStaff() {
  const assignedIds = new Set(assignedStaff.map((staff) => staff.id));
  const available = staffAccounts.filter((staff) => staff.active && !assignedIds.has(staff.id));
  $('#staff-select').innerHTML = available.length
    ? available.map((staff) => `<option value="${staff.id}">${esc(staff.displayName)} (${esc(staff.username)})</option>`).join('')
    : '<option value="">Không còn nhân viên để phân công</option>';
  $('#assign-staff').disabled = !available.length || currentEvent.lifecycleStatus === 'CANCELLED';
  $('#assigned-staff').innerHTML = assignedStaff.length
    ? assignedStaff.map((staff) => `<div class="assigned-staff-row">
        <span>${esc(staff.displayName)} <small class="muted">(${esc(staff.username)})</small></span>
        <button class="btn secondary unassign-staff" type="button" data-id="${staff.id}">Bỏ phân công</button>
      </div>`).join('')
    : '<p class="muted">Chưa phân công nhân viên check-in.</p>';
}

async function loadStaff() {
  if (!isAdmin) return;
  const [users, assigned] = await Promise.all([
    api('/api/v1/admin/users'), api(`/api/v1/admin/events/${eventId}/staff`),
  ]);
  staffAccounts = users.filter((user) => user.roles.includes('STAFF'));
  assignedStaff = assigned;
  paintStaff();
  $('#staff-section').hidden = false;
}

async function reload() {
  paint(await api(`/api/v1/events/${eventId}`));
  const [history, staff] = await Promise.allSettled([loadHistory(), loadStaff()]);
  if (history.status === 'rejected') {
    $('#review-history').textContent = `Không tải được lịch sử xét duyệt: ${history.reason.message}`;
    $('#review-history-section').hidden = false;
  }
  if (staff.status === 'rejected') {
    $('#assigned-staff').textContent = `Không tải được danh sách nhân viên: ${staff.reason.message}`;
    $('#staff-section').hidden = false;
    $('#assign-staff').disabled = true;
  }
}

function openReview(action) {
  reviewAction = action;
  const titles = {
    approve: 'Phê duyệt sự kiện', reject: 'Từ chối sự kiện',
    reconsider: 'Xét lại quyết định',
    withdraw: 'Rút yêu cầu duyệt', delete: 'Xóa bản nháp', cancel: 'Hủy sự kiện',
  };
  const descriptions = {
    approve: 'Sự kiện sẽ xuất hiện công khai và có thể nhận đăng ký nếu đang mở.',
    reject: 'Ban tổ chức sẽ thấy lý do từ chối và có thể chỉnh sửa để gửi lại.',
    reconsider: 'Sự kiện trở lại trạng thái chờ duyệt. Ghi rõ lý do sửa quyết định trước đó.',
    withdraw: 'Sự kiện sẽ trở lại bản nháp để bạn chỉnh sửa và gửi duyệt sau.',
    delete: 'Bản nháp sẽ được ẩn khỏi danh sách. Thao tác này không thể hoàn tác.',
    cancel: 'Đăng ký và check-in sẽ dừng. Vé đã cấp vẫn được giữ trong lịch sử và khách sẽ thấy lý do hủy. Nếu đã cấu hình email, hệ thống sẽ gửi thông báo.',
  };
  $('#review-dialog-title').textContent = titles[action];
  $('#review-dialog-description').textContent = `${currentEvent.title}. ${descriptions[action]}`;
  $('#review-note').value = '';
  const needsNote = ['reject', 'reconsider', 'cancel'].includes(action);
  $('#review-note').required = needsNote;
  $('#review-note').hidden = !needsNote && action !== 'approve';
  $('#review-note-label').hidden = $('#review-note').hidden;
  $('#review-note-label').textContent = action === 'reject' ? 'Lý do từ chối'
    : action === 'reconsider' ? 'Lý do xét lại' : action === 'cancel' ? 'Lý do hủy'
      : 'Ghi chú (không bắt buộc)';
  $('#confirm-review').textContent = titles[action];
  $('#review-dialog').showModal();
}

$('#detail-actions').addEventListener('click', async (event) => {
  const button = event.target.closest('[data-review]');
  if (!button) return;
  const action = button.dataset.review;
  if (action !== 'submit') return openReview(action);
  button.disabled = true;
  try {
    await api(`/api/v1/events/${eventId}/submit`, { method: 'POST' });
    await reload();
    show('Đã gửi sự kiện để quản trị viên duyệt.');
  } catch (error) { show(error.message, true); }
  finally { button.disabled = false; }
});

$('#close-review').addEventListener('click', () => $('#review-dialog').close());
$('#review-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const note = $('#review-note').value.trim();
  if (['reject', 'reconsider', 'cancel'].includes(reviewAction) && !note)
    return show('Cần nhập lý do trước khi xác nhận.', true);
  const button = $('#confirm-review');
  button.disabled = true;
  try {
    const action = reviewAction;
    if (action === 'delete') {
      await api(`/api/v1/events/${eventId}`, { method: 'DELETE' });
      location.replace('/pages/events.html');
      return;
    }
    if (action === 'withdraw')
      await api(`/api/v1/events/${eventId}/withdraw`, { method: 'POST' });
    else if (action === 'cancel')
      await api(`/api/v1/events/${eventId}/cancel`, {
        method: 'POST', body: JSON.stringify({ reason: note }),
      });
    else {
      const reconsider = action === 'reconsider';
      await api(`/api/v1/admin/events/${eventId}/${reconsider ? 'reconsider' : 'review'}`, {
        method: 'POST',
        body: JSON.stringify(reconsider ? { reason: note } : {
          decision: action === 'approve' ? 'APPROVED' : 'REJECTED', note,
        }),
      });
    }
    $('#review-dialog').close();
    await reload();
    show(action === 'withdraw' ? 'Đã rút yêu cầu duyệt.'
      : action === 'cancel' ? 'Đã hủy sự kiện. Khách có thể xem lý do trong Vé của tôi.'
      : action === 'reconsider' ? 'Đã đưa sự kiện về trạng thái chờ duyệt.'
      : action === 'approve' ? 'Đã phê duyệt sự kiện.' : 'Đã từ chối và lưu lý do.');
  } catch (error) { show(error.message, true); }
  finally { button.disabled = false; }
});

$('#assign-staff').addEventListener('click', async () => {
  const staffId = $('#staff-select').value;
  if (!staffId) return;
  const button = $('#assign-staff');
  button.disabled = true;
  try {
    assignedStaff = await api(`/api/v1/admin/events/${eventId}/staff/${staffId}`, { method: 'POST' });
    paintStaff();
    show('Đã phân công nhân viên cho sự kiện.');
  } catch (error) { show(error.message, true); }
  finally { button.disabled = !$('#staff-select').value || currentEvent.lifecycleStatus === 'CANCELLED'; }
});

$('#assigned-staff').addEventListener('click', async (event) => {
  const button = event.target.closest('.unassign-staff');
  if (!button) return;
  button.disabled = true;
  try {
    await api(`/api/v1/admin/events/${eventId}/staff/${button.dataset.id}`, { method: 'DELETE' });
    assignedStaff = assignedStaff.filter((staff) => staff.id !== Number(button.dataset.id));
    paintStaff();
    show('Đã bỏ phân công nhân viên.');
  } catch (error) { show(error.message, true); button.disabled = false; }
});

authForm(async (me) => {
  if (!eventId || !/^\d+$/.test(eventId)) {
    $('#detail-title').textContent = 'Không tìm thấy sự kiện';
    $('#detail-intro').textContent = 'Vui lòng chọn một sự kiện từ danh sách.';
    return;
  }
  isAdmin = me.roles.some((role) => role.authority === 'ROLE_ADMIN');
  isOrganizer = me.roles.some((role) => role.authority === 'ROLE_ORGANIZER');
  if (!isAdmin && !isOrganizer) {
    location.replace('/pages/events.html');
    return;
  }
  try {
    await reload();
  } catch (error) {
    $('#detail-title').textContent = 'Không tải được sự kiện';
    $('#detail-intro').textContent = error.message;
  }
});
