import { $, api, authForm, dateText, esc, eventTime, show, ticketUrl } from './api.js';

let codeToCancel = '';

async function loadTickets() {
  const tickets = await api('/api/v1/tickets/mine');
  const active = tickets.filter((ticket) => ticket.status !== 'CANCELLED'
    && ticket.eventLifecycleStatus !== 'CANCELLED');
  $('#ticket-count').textContent = `${active.length} vé còn hiệu lực · ${tickets.length - active.length} vé không còn hiệu lực`;
  $('#ticket-list').innerHTML = tickets.length
    ? tickets
        .map((ticket) => {
          const cancelled = ticket.status === 'CANCELLED';
          const eventCancelled = ticket.eventLifecycleStatus === 'CANCELLED';
          const checkedIn = ticket.status === 'CHECKED_IN';
          const eventEnded = eventTime(ticket.endsAt).getTime() <= Date.now();
          const canCancel = !cancelled && !eventCancelled && !checkedIn
            && eventTime(ticket.startsAt).getTime() > Date.now();
          const label = eventCancelled ? 'Sự kiện đã hủy'
            : cancelled ? 'Đã hủy' : checkedIn ? 'Đã check-in' : 'Chưa check-in';
          return `
            <article class="card event-card ${cancelled ? 'cancelled-ticket' : ''}">
              <span class="pill ${cancelled ? 'closed' : checkedIn ? '' : 'closed'}">${label}</span>
              <h3>${esc(ticket.eventTitle)}</h3>
              <p class="event-meta">${esc(ticket.venueName)} · ${dateText(ticket.startsAt)}</p>
              <p class="event-meta">Người tham dự: ${esc(ticket.attendeeName)}</p>
              ${cancelled ? `<p class="small muted">Đã hủy lúc ${dateText(ticket.cancelledAt)}. Mã QR không còn hiệu lực.</p>` : ''}
              ${eventCancelled ? `<p class="review-callout"><strong>Lý do hủy sự kiện:</strong> ${esc(ticket.eventCancelReason || 'Ban tổ chức chưa cập nhật.')} Mã QR không còn hiệu lực.</p>` : ''}
              ${checkedIn && !eventEnded && !eventCancelled ? '<p class="small muted">Bạn có thể đánh giá sau khi sự kiện kết thúc.</p>' : ''}
              <div class="actions">
                <a class="btn ${cancelled || eventCancelled ? 'secondary' : ''}" href="${ticketUrl(ticket.code)}">${cancelled || eventCancelled ? 'Xem lịch sử' : 'Xem vé QR'}</a>
                ${canCancel ? `<button class="btn secondary cancel-ticket" type="button" data-code="${esc(ticket.code)}" data-title="${esc(ticket.eventTitle)}">Hủy vé</button>` : ''}
                ${checkedIn && eventEnded && !eventCancelled && !ticket.feedbackSent ? `<a class="btn secondary" href="/pages/feedback.html?code=${encodeURIComponent(ticket.code)}">Đánh giá</a>` : ''}
                ${checkedIn && ticket.feedbackSent ? `<a class="btn secondary" href="/pages/feedback.html?code=${encodeURIComponent(ticket.code)}">Xem đánh giá</a>` : ''}
              </div>
            </article>`;
        })
        .join('')
    : '<div class="empty">Bạn chưa đăng ký vé nào. <a href="/#events">Khám phá sự kiện</a> để nhận vé QR miễn phí.</div>';
}

authForm(loadTickets);

$('#ticket-list').addEventListener('click', (event) => {
  const button = event.target.closest('.cancel-ticket');
  if (!button) return;
  codeToCancel = button.dataset.code;
  $('#cancel-description').textContent = `Hủy vé “${button.dataset.title}”? Mã QR sẽ không còn hiệu lực và chỗ của bạn được trả lại.`;
  $('#cancel-dialog').showModal();
});

$('#keep-ticket').addEventListener('click', () => $('#cancel-dialog').close());

$('#confirm-cancel').addEventListener('click', async () => {
  if (!codeToCancel) return;
  const button = $('#confirm-cancel');
  button.disabled = true;
  try {
    await api(`/api/v1/tickets/${encodeURIComponent(codeToCancel)}/cancel`, { method: 'POST' });
    $('#cancel-dialog').close();
    codeToCancel = '';
    await loadTickets();
    show('Đã hủy vé. Chỗ của bạn đã được trả lại cho sự kiện.');
  } catch (error) {
    $('#cancel-dialog').close();
    show(error.message, true);
    await loadTickets().catch(() => {});
  } finally {
    button.disabled = false;
  }
});
