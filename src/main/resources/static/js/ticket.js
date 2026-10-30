import { $, api, params, dateText, eventTime, show, authForm, accessToken } from './api.js';

const code = params.get('code');
let loading = false;
let qrObjectUrl = null;

function clearQr() {
  $('#qr').removeAttribute('src');
  $('#download').removeAttribute('href');
  if (qrObjectUrl) URL.revokeObjectURL(qrObjectUrl);
  qrObjectUrl = null;
}

async function loadQr(ticketCode) {
  const response = await fetch(`/api/v1/tickets/${encodeURIComponent(ticketCode)}/qr`, {
    headers: { Authorization: `Bearer ${accessToken() || ''}` },
  });
  if (!response.ok) {
    if (response.status === 401) {
      location.replace(`/login.html?next=${encodeURIComponent(location.pathname + location.search)}`);
    }
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || `Không tải được mã QR (HTTP ${response.status}).`);
  }
  const blob = await response.blob();
  if (!blob.type.startsWith('image/png')) throw new Error('Dữ liệu mã QR không hợp lệ.');
  clearQr();
  qrObjectUrl = URL.createObjectURL(blob);
  $('#qr').src = qrObjectUrl;
  $('#download').href = qrObjectUrl;
  $('#download').download = `ve-qr-${ticketCode}.png`;
}

async function refresh() {
  if (loading) return;
  if (!code) return show('Thiếu mã vé. Hãy mở liên kết vé được cấp sau khi đăng ký.', true);
  loading = true;
  $('#refresh-ticket').disabled = true;
  try {
    const ticket = await api(`/api/v1/tickets/${encodeURIComponent(code)}`);
    const eventCancelled = ticket.eventLifecycleStatus === 'CANCELLED';
    $('#title').textContent = ticket.eventTitle;
    $('#name').textContent = ticket.attendeeName;
    $('#venue').textContent = ticket.venueName;
    $('#join-online').hidden = !ticket.onlineUrl || ticket.status === 'CANCELLED' || eventCancelled;
    if (ticket.onlineUrl) $('#join-online').href = ticket.onlineUrl;
    $('#time').textContent = dateText(ticket.startsAt);
    $('#code').textContent = ticket.code;
    const cancelled = ticket.status === 'CANCELLED';
    $('#state').textContent = eventCancelled ? 'Sự kiện đã hủy'
      : cancelled ? 'Đã hủy' : ticket.status === 'CHECKED_IN' ? 'Đã check-in' : 'Chưa check-in';
    $('#qr').hidden = cancelled || eventCancelled;
    $('#download').hidden = cancelled || eventCancelled;
    if (!cancelled && !eventCancelled) {
      await loadQr(code);
    } else {
      clearQr();
    }
    const eventEnded = eventTime(ticket.endsAt).getTime() <= Date.now();
    $('#feedback-link').hidden = cancelled || eventCancelled || ticket.status !== 'CHECKED_IN'
      || (!eventEnded && !ticket.feedbackSent);
    $('#feedback-link').textContent = ticket.feedbackSent ? 'Xem đánh giá của tôi' : 'Đánh giá sự kiện';
    $('#feedback-link').href = `/pages/feedback.html?code=${encodeURIComponent(code)}`;
    $('#feedback-hint').textContent = eventCancelled
      ? `Sự kiện đã hủy. Lý do: ${ticket.eventCancelReason || 'Ban tổ chức chưa cập nhật.'} Mã QR không còn hiệu lực.`
      : cancelled
      ? `Vé đã hủy lúc ${dateText(ticket.cancelledAt)}. Mã QR không còn hiệu lực.`
      : ticket.feedbackSent
      ? 'Bạn đã gửi đánh giá. Cảm ơn bạn đã tham dự!'
      : ticket.status === 'CHECKED_IN'
        ? eventEnded
          ? 'Sự kiện đã kết thúc. Hãy chia sẻ trải nghiệm của bạn qua phần đánh giá.'
          : 'Check-in thành công. Bạn có thể đánh giá sau khi sự kiện kết thúc.'
        : 'Sau khi check-in, cập nhật trạng thái để gửi đánh giá.';
  } catch (err) {
    show(err.message, true);
  } finally {
    loading = false;
    $('#refresh-ticket').disabled = false;
  }
}

$('#refresh-ticket').addEventListener('click', refresh);
window.addEventListener('focus', refresh);
window.addEventListener('pagehide', clearQr);
authForm(refresh);
