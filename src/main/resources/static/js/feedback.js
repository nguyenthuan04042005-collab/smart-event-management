import { $, api, params, esc, dateText, eventTime, show, authForm, downloadReport } from './api.js';

const STAR_COLORS = { 5: '#16a34a', 4: '#2563eb', 3: '#f59e0b', 2: '#f97316', 1: '#dc2626' };
let summaryVersion = 0;
let listVersion = 0;
let currentPage = 0;
let searchTimer;
let totalFeedback = 0;

function paintFeedback(data) {
  const first = data.total ? data.page * data.pageSize + 1 : 0;
  const last = data.page * data.pageSize + data.items.length;
  $('#feedback-count').textContent = data.total
    ? `Hiển thị ${first}–${last} / ${data.total} đánh giá`
    : totalFeedback ? 'Không có đánh giá phù hợp với bộ lọc.' : 'Chưa có đánh giá.';
  $('#feedback-list').innerHTML = data.items.length
    ? data.items.map((item) => `
      <article class="feedback-item">
        <div class="feedback-item-head">
          <strong>${esc(item.attendeeName)}</strong>
          <span class="feedback-stars" aria-label="${item.rating} trên 5 sao">${'★'.repeat(item.rating)}${'☆'.repeat(5 - item.rating)}</span>
        </div>
        <span class="muted small">${dateText(item.createdAt)}</span>
        <p>${esc(item.comment || 'Không có nhận xét.')}</p>
      </article>`).join('')
    : `<p class="empty">${totalFeedback ? 'Không tìm thấy đánh giá phù hợp.' : 'Chưa có phản hồi.'}</p>`;
  const pages = Math.ceil(data.total / data.pageSize);
  $('#feedback-pages').hidden = pages <= 1;
  $('#feedback-prev').disabled = data.page === 0;
  $('#feedback-next').disabled = data.page + 1 >= pages;
  $('#feedback-page-label').textContent = `Trang ${data.page + 1} / ${pages}`;
}

function paintRatings(data) {
  $('#rating-chart').innerHTML = data.ratings.map(({ rating, count, percentage }) => `
    <div class="rating-row">
      <span>${rating} ★</span>
      <div class="rating-track"><div class="rating-fill" style="width:${percentage}%;background:${STAR_COLORS[rating]}"></div></div>
      <strong>${count} lượt · ${percentage}%</strong>
    </div>`).join('');
  $('#rating-legend').innerHTML = data.ratings.map(({ rating, count, percentage }) => `
    <div class="rating-legend-item"><span class="rating-swatch" style="background:${STAR_COLORS[rating]}"></span>
      <span>${rating} sao</span><strong>${count} lượt (${percentage}%)</strong></div>`).join('');
  const segments = [];
  let start = 0;
  for (const { rating, count } of data.ratings) {
    const end = start + (data.count ? count / data.count * 100 : 0);
    segments.push(`${STAR_COLORS[rating]} ${start}% ${end}%`);
    start = end;
  }
  $('#rating-pie').style.background = data.count
    ? `conic-gradient(${segments.join(', ')})` : '#e2e8f0';
  $('#rating-pie').innerHTML = `<span>${data.count}<small>phản hồi</small></span>`;
  $('#rating-pie').setAttribute('aria-label',
    `Phân bố đánh giá: ${data.ratings.map((item) => `${item.rating} sao ${item.percentage}%`).join(', ')}`);
}

function paintTrend(data) {
  const points = data.trend;
  $('#trend-period').textContent = data.trendInterval === 'HOUR'
    ? 'Gom theo giờ gửi đánh giá' : 'Gom theo ngày gửi đánh giá';
  if (!points.length) {
    $('#feedback-trend').innerHTML = '<p class="empty">Chưa có phản hồi để vẽ xu hướng.</p>';
    $('#feedback-trend').setAttribute('aria-label', 'Chưa có dữ liệu xu hướng');
    return;
  }
  const width = Math.max(480, points.length * 88 + 70);
  const baseline = 150;
  const maxCount = Math.max(...points.map((point) => point.count));
  const labels = points.map((point) => eventTime(point.period).toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    ...(data.trendInterval === 'HOUR'
      ? { hour: '2-digit', day: '2-digit', month: '2-digit' }
      : { day: '2-digit', month: '2-digit' }),
  }));
  const line = points.map((point, index) =>
    `${55 + index * 88},${baseline - point.averageRating / 5 * 105}`).join(' ');
  $('#feedback-trend').innerHTML = `<svg viewBox="0 0 ${width} 195" width="${width}" height="195" aria-hidden="true">
    <line x1="30" y1="${baseline}" x2="${width - 18}" y2="${baseline}" stroke="#cbd5e1" />
    ${points.map((point, index) => {
      const x = 55 + index * 88;
      const barHeight = point.count / maxCount * 105;
      return `<rect x="${x - 16}" y="${baseline - barHeight}" width="32" height="${barHeight}" rx="6" fill="#fda4af" />
        <text x="${x}" y="${baseline - barHeight - 6}" text-anchor="middle" class="trend-value">${point.count}</text>`;
    }).join('')}
    <polyline points="${line}" fill="none" stroke="#b91c1c" stroke-width="3" stroke-linejoin="round" />
    ${points.map((point, index) => {
      const x = 55 + index * 88;
      const y = baseline - point.averageRating / 5 * 105;
      return `<circle cx="${x}" cy="${y}" r="5" fill="#b91c1c" stroke="white" stroke-width="2" />
        <text x="${x}" y="${y - 12}" text-anchor="middle" class="trend-average">${point.averageRating}</text>
        <text x="${x}" y="181" text-anchor="middle" class="trend-label">${esc(labels[index])}</text>`;
    }).join('')}
  </svg>`;
  $('#feedback-trend').setAttribute('aria-label', points.map((point, index) =>
    `${labels[index]}: ${point.count} phản hồi, trung bình ${point.averageRating} trên 5`).join('; '));
}

async function loadMyTickets(preferredCode = '') {
  const [tickets, history] = await Promise.all([
    api('/api/v1/tickets/mine'), api('/api/v1/feedback/mine'),
  ]);
  const eligible = tickets.filter((ticket) => ticket.status === 'CHECKED_IN'
    && ticket.eventLifecycleStatus !== 'CANCELLED' && !ticket.feedbackSent
    && eventTime(ticket.endsAt).getTime() <= Date.now());
  $('#code').innerHTML = eligible.length
    ? eligible
        .map((ticket) => `<option value="${esc(ticket.code)}">${esc(ticket.eventTitle)} · ${dateText(ticket.startsAt)} · ${esc(ticket.attendeeName)}</option>`)
        .join('')
    : '<option value="">Chưa có vé có thể đánh giá</option>';
  if (eligible.some((ticket) => ticket.code === preferredCode)) $('#code').value = preferredCode;
  $('#feedback-form button').disabled = !eligible.length;
  $('#ticket-hint').textContent = eligible.length
    ? 'Chỉ hiển thị vé đã check-in của sự kiện đã kết thúc và chưa được đánh giá.'
    : 'Chưa có vé phù hợp. Bạn có thể đánh giá sau khi sự kiện kết thúc và đã check-in.';
  $('#my-feedback-list').innerHTML = history.length
    ? history.map((item) => `<article class="feedback-item" ${item.ticketCode === preferredCode ? 'data-selected="true"' : ''}>
        <div class="feedback-item-head"><strong>${esc(item.eventTitle)}</strong>
        <span class="feedback-stars">${'★'.repeat(item.rating)}${'☆'.repeat(5 - item.rating)}</span></div>
        <span class="small muted">${dateText(item.createdAt)}</span>
        <p>${esc(item.comment || 'Không có nhận xét.')}</p>
      </article>`).join('')
    : '<p class="muted">Bạn chưa gửi đánh giá nào.</p>';
}

$('#feedback-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const submit = e.submitter;
  if (submit) submit.disabled = true;
  try {
    const code = $('#code').value.trim();
    await api(`/api/v1/tickets/${encodeURIComponent(code)}/feedback`, {
      method: 'POST',
      body: JSON.stringify({ rating: Number($('#rating').value), comment: $('#comment').value }),
    });
    show('Cảm ơn bạn đã đánh giá sự kiện.');
    $('#feedback-form').reset();
    await loadMyTickets();
  } catch (err) {
    show(err.message, true);
  } finally {
    if (submit) submit.disabled = !$('#code').value;
  }
});

async function ready(me) {
  if (!me.roles.some((r) => ['ROLE_ADMIN', 'ROLE_ORGANIZER'].includes(r.authority))) {
    $('#summary').parentElement.hidden = true;
    document.querySelector('.hero p').textContent =
      'Chọn vé đã check-in của bạn để chấm điểm và chia sẻ trải nghiệm.';
    await loadMyTickets(params.get('code') || '');
    return;
  }
  $('#feedback-entry').hidden = true;
  const events = await api('/api/v1/events');
  $('#event').innerHTML = events
    .map((e) => `<option value="${e.id}">${esc(e.title)}</option>`)
    .join('');
  $('#summary').hidden = false;
  await refresh();
}

async function refresh() {
  if ($('#summary').hidden || !$('#event').value) return;
  const version = ++summaryVersion;
  const eventId = $('#event').value;
  const data = await api(`/api/v1/events/${eventId}/feedback`, {}, true);
  if (version !== summaryVersion || eventId !== $('#event').value) return;
  $('#count').textContent = data.count;
  $('#average').textContent = data.averageRating;
  totalFeedback = data.count;
  paintRatings(data);
  paintTrend(data);
  await refreshItems();
}

async function refreshItems() {
  const eventId = $('#event').value;
  if (!eventId) return;
  const version = ++listVersion;
  const query = $('#feedback-search').value.trim();
  const rating = $('#feedback-rating').value === 'all' ? 0 : Number($('#feedback-rating').value);
  const data = await api(`/api/v1/events/${eventId}/feedback/items?query=${encodeURIComponent(query)}&rating=${rating}&page=${currentPage}`);
  if (version !== listVersion || eventId !== $('#event').value) return;
  paintFeedback(data);
}

authForm(ready);
$('#event').addEventListener('change', () => {
  currentPage = 0;
  refresh().catch((err) => show(err.message, true));
});
$('#feedback-search').addEventListener('input', () => {
  currentPage = 0;
  listVersion++;
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => refreshItems().catch((err) => show(err.message, true)), 300);
});
$('#feedback-rating').addEventListener('change', () => {
  currentPage = 0;
  refreshItems().catch((err) => show(err.message, true));
});
$('#feedback-prev').addEventListener('click', () => {
  currentPage--;
  refreshItems().catch((err) => show(err.message, true));
});
$('#feedback-next').addEventListener('click', () => {
  currentPage++;
  refreshItems().catch((err) => show(err.message, true));
});
$('#export-feedback').addEventListener('click', async () => {
  if (!$('#event').value) return show('Hãy chọn sự kiện trước khi tải báo cáo.', true);
  $('#export-feedback').disabled = true;
  try {
    await downloadReport($('#event').value, 'feedback');
  } catch (err) {
    show(err.message, true);
  } finally {
    $('#export-feedback').disabled = false;
  }
});
$('#export-feedback-excel').addEventListener('click', async () => {
  if (!$('#event').value) return show('Hãy chọn sự kiện trước khi tải báo cáo.', true);
  $('#export-feedback-excel').disabled = true;
  try {
    await downloadReport($('#event').value, 'feedback', 'xlsx');
  } catch (err) {
    show(err.message, true);
  } finally {
    $('#export-feedback-excel').disabled = false;
  }
});
