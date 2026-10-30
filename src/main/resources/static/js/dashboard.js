import { $, api, esc, show, authForm, eventTime, downloadReport, searchText, accessToken } from './api.js';

let socket;
let generation = 0;
let reconnectTimer;
let fallbackTimer;
let allTickets = [];

async function login() {
  const me = await api('/api/v1/me');
  if (!me.roles.some((r) => ['ROLE_ADMIN', 'ROLE_ORGANIZER'].includes(r.authority))) {
    throw new Error('Dashboard chỉ dành cho ban tổ chức. Nhân viên vui lòng mở trang Check-in.');
  }
  const events = await api('/api/v1/events');
  $('#event').innerHTML = events
    .map((e) => `<option value="${e.id}">${esc(e.title)}</option>`)
    .join('');
  const requestedEvent = new URLSearchParams(location.search).get('event');
  if (events.some((event) => String(event.id) === requestedEvent)) $('#event').value = requestedEvent;
  $('#workspace').hidden = false;
  if (events.length) await switchEvent();
  else {
    $('#socket-state').textContent = 'Chưa có sự kiện để theo dõi.';
    show('Chưa có sự kiện để theo dõi.');
  }
}

function paint(data) {
  $('#event-cancel-notice').hidden = data.eventLifecycleStatus !== 'CANCELLED';
  $('#event-cancel-notice').textContent = data.eventLifecycleStatus === 'CANCELLED'
    ? `Sự kiện đã hủy. Lý do: ${data.eventCancelReason || 'Ban tổ chức chưa cập nhật.'} Số liệu bên dưới được giữ để đối soát.`
    : '';
  $('#registered').textContent = data.registered;
  $('#checked').textContent = data.checkedIn;
  $('#attendance').textContent = `${data.attendanceRate}%`;
  $('#occupancy').textContent = `${data.occupancyRate}%`;
  $('#waiting-count').textContent = `${data.registered - data.checkedIn} khách chưa đến`;
  const box = $('#capacity-alert');
  box.className = 'card';
  if (data.occupancyAlert === 'RED') {
    box.textContent = 'Cảnh báo đỏ · Đã sử dụng từ 95% số chỗ của sự kiện.';
    box.classList.add('alert-red');
  } else if (data.occupancyAlert === 'YELLOW') {
    box.textContent = 'Cảnh báo vàng · Đã sử dụng từ 85% số chỗ của sự kiện.';
    box.classList.add('alert-yellow');
  } else {
    box.textContent = `Sức chứa ổn định · ${data.checkedIn} / ${data.capacity} chỗ đã có khách.`;
  }
}

function paintChart(tickets) {
  const buckets = new Map();
  tickets
    .filter((t) => t.checkedInAt)
    .forEach((ticket) => {
      const minute = Math.floor(eventTime(ticket.checkedInAt).getTime() / 300000) * 300000;
      buckets.set(minute, (buckets.get(minute) || 0) + 1);
    });
  const points = [...buckets.entries()].sort((a, b) => a[0] - b[0]).slice(-12);
  const max = Math.max(1, ...points.map((point) => point[1]));
  $('#arrival-chart').innerHTML = points.length
    ? points
        .map(([time, count]) => {
          const label = new Date(time).toLocaleTimeString('vi-VN', {
            timeZone: 'Asia/Ho_Chi_Minh',
            hour: '2-digit',
            minute: '2-digit',
          });
          return `<div class="chart-column"><strong>${count}</strong>
      <span class="chart-bar" style="height:${Math.max(4, (count / max) * 110)}px"></span>
      <span>${label}</span></div>`;
        })
        .join('')
    : '<p class="empty">Biểu đồ sẽ xuất hiện khi khách đầu tiên check-in.</p>';
}

function paintRegistrations() {
  const query = searchText($('#registration-search').value.trim());
  const status = $('#registration-status').value;
  const visible = allTickets.filter((ticket) => {
    const searchable = searchText(`${ticket.attendeeName} ${ticket.attendeeEmail} ${ticket.attendeeOrganization || ''} ${ticket.attendeePositionTitle || ''} ${ticket.code}`);
    return searchable.includes(query) && (status === 'all' || ticket.status === status);
  });
  $('#registration-count').textContent = `Hiển thị ${visible.length} / ${allTickets.length} khách đăng ký`;
  $('#registrations').innerHTML = visible.length
    ? visible.map((ticket) => `
      <tr>
        <td>${esc(ticket.attendeeName)}</td>
        <td>${esc(ticket.attendeeEmail)}</td>
        <td>${esc(ticket.attendeeOrganization || '—')}</td>
        <td>${esc(ticket.attendeePositionTitle || '—')}</td>
        <td><code>${esc(ticket.code)}</code></td>
        <td><span class="pill ${ticket.status === 'CHECKED_IN' ? '' : 'closed'}">${ticket.status === 'CHECKED_IN' ? 'Đã đến' : 'Chưa đến'}</span></td>
      </tr>`).join('')
    : `<tr><td colspan="6" class="empty">${allTickets.length ? 'Không tìm thấy khách phù hợp.' : 'Chưa có khách đăng ký sự kiện này.'}</td></tr>`;
}

async function refresh(version = generation) {
  const id = $('#event').value;
  if (!id) return;
  const [stats, tickets] = await Promise.all([
    api(`/api/v1/events/${id}/dashboard`),
    api(`/api/v1/events/${id}/registrations`),
  ]);
  if (version !== generation || id !== $('#event').value) return;
  paint(stats);
  paintChart(tickets);
  allTickets = tickets;
  paintRegistrations();
}

function connect(version) {
  if (version !== generation) return;
  const id = $('#event').value;
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const current = new WebSocket(`${protocol}//${location.host}/ws/dashboard/${id}`,
    ['sems-jwt', accessToken()]);
  socket = current;
  current.onopen = () => {
    if (version !== generation) return current.close();
    clearInterval(fallbackTimer);
    $('#socket-state').classList.add('connected');
    $('#socket-state').textContent = 'Trực tiếp · Số liệu tự động cập nhật';
    refresh(version).catch((err) => show(err.message, true));
  };
  current.onmessage = () => {
    if (version === generation) refresh(version).catch((err) => show(err.message, true));
  };
  current.onclose = () => {
    if (version !== generation) return;
    $('#socket-state').classList.remove('connected');
    $('#socket-state').textContent = 'Đang kết nối lại · Tạm làm mới mỗi 5 giây';
    clearInterval(fallbackTimer);
    fallbackTimer = setInterval(
      () => refresh(version).catch((err) => show(err.message, true)),
      5000,
    );
    reconnectTimer = setTimeout(() => connect(version), 5000);
  };
}

async function switchEvent() {
  const version = ++generation;
  clearTimeout(reconnectTimer);
  clearInterval(fallbackTimer);
  if (socket) socket.close();
  $('#socket-state').classList.remove('connected');
  $('#socket-state').textContent = 'Đang kết nối...';
  await refresh(version);
  if (version === generation && $('#event').value) connect(version);
}

authForm(login);
$('#event').addEventListener('change', () => switchEvent().catch((err) => show(err.message, true)));
$('#registration-search').addEventListener('input', paintRegistrations);
$('#registration-status').addEventListener('change', paintRegistrations);
document.querySelectorAll('.export-report').forEach((button) => {
  button.addEventListener('click', async () => {
    if (!$('#event').value) return show('Hãy chọn sự kiện trước khi tải báo cáo.', true);
    button.disabled = true;
    try {
      await downloadReport($('#event').value, button.dataset.type);
    } catch (err) {
      show(err.message, true);
    } finally {
      button.disabled = false;
    }
  });
});
window.addEventListener('pagehide', () => {
  generation++;
  clearTimeout(reconnectTimer);
  clearInterval(fallbackTimer);
  socket?.close();
});
