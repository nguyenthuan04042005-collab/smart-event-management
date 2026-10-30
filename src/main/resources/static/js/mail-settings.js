import { $, api, authForm, show, esc, dateText, searchText } from './api.js';
import './password-toggle.js';

let deliveries = [];
let mailJobs = [];
const messageTypes = {
  TICKET: 'Vé QR', EVENT_CANCELLED: 'Hủy sự kiện', SURVEY_INVITATION: 'Mời đánh giá',
  VERIFY_EMAIL: 'Xác minh tài khoản', CONFIRMATION_CODE: 'Mã xác minh',
  PASSWORD_RESET: 'Đặt lại mật khẩu', PASSWORD_CHANGED: 'Đổi mật khẩu',
  SMTP_TEST: 'Email thử',
};
const deliveryStates = {
  PENDING: 'Chưa rõ kết quả', SMTP_ACCEPTED: 'SMTP đã nhận', FAILED: 'Gửi thất bại',
};
const errorReasons = {
  RETRYABLE_FAILURE: 'Lỗi kết nối tạm thời',
  PERMANENT_FAILURE: 'Địa chỉ bị SMTP từ chối hoặc lỗi không thể tự thử lại',
  CONFIG_FAILURE: 'Cấu hình hoặc xác thực SMTP chưa đúng',
};

function paintDeliveries() {
  const term = searchText($('#delivery-search').value.trim());
  const status = $('#delivery-status').value;
  const found = deliveries.filter((item) =>
    (status === 'all' || item.status === status) &&
    searchText(`${item.recipient} ${messageTypes[item.messageType] || item.messageType}`).includes(term));
  $('#delivery-count').textContent = `${found.length} / ${deliveries.length} lần gửi gần nhất`;
  $('#delivery-rows').innerHTML = found.length ? found.map((item) => `
    <tr>
      <td>${esc(dateText(item.createdAt))}</td>
      <td>${esc(messageTypes[item.messageType] || item.messageType)}</td>
      <td>${esc(item.recipient)}</td>
      <td>${esc(deliveryStates[item.status] || item.status)}</td>
      <td>${esc(errorReasons[item.errorCode] || (item.status === 'SMTP_ACCEPTED'
        ? 'Chưa xác nhận đã phát tới hộp thư' : '—'))}</td>
    </tr>`).join('') : '<tr><td colspan="5">Chưa có lần gửi phù hợp.</td></tr>';
}

async function loadDeliveries() {
  deliveries = await api('/api/v1/admin/mail-settings/deliveries');
  paintDeliveries();
  mailJobs = await api('/api/v1/admin/mail-settings/jobs');
  paintJobs();
}

function paintJobs() {
  const jobs = mailJobs.filter((item) =>
    ($('#job-type').value === 'all' || item.messageType === $('#job-type').value) &&
    ($('#job-status').value === 'all' || item.status === $('#job-status').value));
  const jobStates = {
    PENDING: 'Chờ gửi / thử lại', PAUSED: 'Tạm dừng — kiểm tra SMTP',
    SMTP_ACCEPTED: 'SMTP đã nhận', FAILED: 'Gửi thất bại', SKIPPED: 'Bỏ qua',
  };
  $('#mail-job-rows').innerHTML = jobs.length ? jobs.map((item) => `
    <tr>
      <td>${esc(messageTypes[item.messageType] || item.messageType)}</td>
      <td>${esc(item.eventTitle)}</td><td>${esc(item.recipient)}</td>
      <td>${esc(item.ticketCode)}</td><td>${esc(jobStates[item.status] || item.status)}</td>
      <td>${item.attempts} / 3</td>
      <td>${item.nextAttemptAt ? esc(dateText(item.nextAttemptAt)) : '—'}</td>
    </tr>`).join('') : '<tr><td colspan="7">Chưa có công việc phù hợp.</td></tr>';
}

function render(settings, password = '') {
  $('#mail-enabled').checked = settings.enabled;
  $('#mail-host').value = settings.host || 'smtp.gmail.com';
  $('#mail-port').value = settings.port || 587;
  $('#mail-user').value = settings.username || '';
  $('#mail-from').value = settings.fromAddress || '';
  $('#mail-password').value = password;
  $('#test-address').value ||= settings.fromAddress || '';
}

async function savedPassword(settings) {
  if (!settings.passwordConfigured) return '';
  const result = await api('/api/v1/admin/mail-settings/password');
  return result.password;
}

authForm(async () => {
  const settings = await api('/api/v1/admin/mail-settings');
  render(settings, await savedPassword(settings));
  await loadDeliveries();
});

$('#delivery-search').addEventListener('input', paintDeliveries);
$('#delivery-status').addEventListener('change', paintDeliveries);
$('#job-type').addEventListener('change', paintJobs);
$('#job-status').addEventListener('change', paintJobs);
$('#refresh-deliveries').addEventListener('click', () =>
  loadDeliveries().catch((error) => show(error.message, true)));

$('#settings-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  button.disabled = true;
  try {
    const typedPassword = $('#mail-password').value;
    const settings = await api('/api/v1/admin/mail-settings', {
      method: 'PUT',
      body: JSON.stringify({
        enabled: $('#mail-enabled').checked,
        host: $('#mail-host').value.trim(),
        port: Number($('#mail-port').value),
        username: $('#mail-user').value.trim(),
        fromAddress: $('#mail-from').value.trim(),
        password: typedPassword,
      }),
    });
    render(settings, typedPassword || await savedPassword(settings));
    show('Đã lưu cấu hình email.');
  } catch (error) {
    show(error.message, true);
  } finally {
    button.disabled = false;
  }
});

$('#test-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  button.disabled = true;
  try {
    const result = await api('/api/v1/admin/mail-settings/test', {
      method: 'POST', body: JSON.stringify({ address: $('#test-address').value.trim() }),
    });
    show(result.message);
    await loadDeliveries();
  } catch (error) {
    show(error.message, true);
    await loadDeliveries().catch(() => {});
  } finally {
    button.disabled = false;
  }
});
