import { $, api, params, dateText, show, ticketUrl, registrationState, authForm } from './api.js';
const id = Number(params.get('event'));

authForm(async (me) => {
  $('#full-name').value = me.displayName || '';
  $('#ticket-email').textContent = me.email || '';
  if (!me.emailVerified) {
    $('#register-form').hidden = true;
    return show('Hãy xác minh email tài khoản trước khi đăng ký vé.', true);
  }
  if (!id) return show('Thiếu mã sự kiện.', true);
  await api(`/api/v1/events/${id}`)
    .then((e) => {
      $('#event-title').textContent = e.title;
      $('#event-description').textContent = e.description || '';
      $('#event-venue').textContent = `${e.venueName} – ${e.venueAddress}`;
      $('#event-time').textContent = `${dateText(e.startsAt)} đến ${dateText(e.endsAt)}`;
      $('#event-count').textContent = `${e.registered}/${e.capacity}`;
      const state = registrationState(e);
      $('#event-state').textContent = state.label;
      if (!state.open) {
        $('#register-form').hidden = true;
        $('#event-state').classList.add('closed');
      }
    })
    .catch((err) => show(err.message, true));
});

$('#register-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const submit = e.submitter;
  if (submit) submit.disabled = true;
  try {
    const ticket = await api(`/api/v1/events/${id}/registrations`, {
      method: 'POST',
      body: JSON.stringify({
        fullName: $('#full-name').value,
        phone: $('#phone').value,
        organization: $('#organization').value.trim(),
        positionTitle: $('#position-title').value.trim(),
      }),
    });
    location.href = ticketUrl(ticket.code);
  } catch (err) {
    show(err.message, true);
  } finally {
    if (submit) submit.disabled = false;
  }
});
