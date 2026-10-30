import { $, api, authForm, esc, show } from './api.js';

let venues = [];
let venueToDelete = null;
let isAdmin = false;

function render() {
  $('#venue-count').textContent = `${venues.length} địa điểm`;
  $('#venue-list').innerHTML = venues.length
    ? venues
        .map(
          (venue) => `
          <article class="card venue-item">
            <div>
              <span class="pill ${venue.eventCount || !venue.active ? 'closed' : ''}">${venue.active ? venue.eventCount ? `${venue.eventCount} sự kiện` : 'Chưa sử dụng' : 'Ngừng sử dụng'}</span>
              <h3>${esc(venue.name)}</h3>
              <p class="muted">${esc(venue.address)}</p>
              ${isAdmin ? `<p class="small muted">Ban tổ chức: ${esc(venue.organizerName)}</p>` : ''}
              <p class="small muted">Sức chứa tối đa: <strong>${venue.capacity} người</strong></p>
              ${venue.approvedEventCount ? '<p class="small muted">Tên và địa chỉ được giữ cố định vì có sự kiện đã duyệt.</p>' : ''}
            </div>
            ${isAdmin ? '' : `<div class="actions">
              <button class="btn secondary edit-venue" type="button" data-id="${venue.id}">Chỉnh sửa</button>
              <button class="btn secondary toggle-venue" type="button" data-id="${venue.id}">${venue.active ? 'Ngừng sử dụng' : 'Dùng lại'}</button>
              ${venue.eventCount ? '' : `<button class="btn danger delete-venue" type="button" data-id="${venue.id}">Xóa</button>`}
            </div>`}
          </article>`,
        )
        .join('')
    : `<p class="empty">${isAdmin ? 'Chưa có địa điểm nào.' : 'Chưa có địa điểm. Thêm địa điểm đầu tiên để tạo sự kiện.'}</p>`;
}

async function loadVenues() {
  venues = await api('/api/v1/venues');
  render();
}

function newVenue() {
  $('#venue-form').reset();
  $('#venue-id').value = '';
  $('#venue-editor-title').textContent = 'Thêm địa điểm';
  $('#venue-name').readOnly = false;
  $('#venue-address').readOnly = false;
  $('#venue-help').textContent = 'Số vé của mỗi sự kiện không được vượt quá sức chứa địa điểm.';
}

function editVenue(id) {
  const venue = venues.find((item) => item.id === id);
  if (!venue) return;
  $('#venue-id').value = venue.id;
  $('#venue-name').value = venue.name;
  $('#venue-address').value = venue.address;
  $('#venue-capacity').value = venue.capacity;
  $('#venue-editor-title').textContent = 'Chỉnh sửa địa điểm';
  const fixed = venue.approvedEventCount > 0;
  $('#venue-name').readOnly = fixed;
  $('#venue-address').readOnly = fixed;
  $('#venue-help').textContent = fixed
    ? 'Địa điểm đã gắn với sự kiện được duyệt: chỉ có thể chỉnh sức chứa.'
    : 'Số vé của mỗi sự kiện không được vượt quá sức chứa địa điểm.';
  $('.venue-editor').scrollIntoView({ behavior: 'smooth', block: 'start' });
  $('#venue-name').focus({ preventScroll: true });
}

authForm(async (me) => {
  isAdmin = me.roles.some((role) => role.authority === 'ROLE_ADMIN');
  if (isAdmin) {
    $('.venue-editor').hidden = true;
    $('.venues-layout').classList.add('admin-view');
    document.querySelector('.hero p').textContent =
      'Danh sách địa điểm do các ban tổ chức quản lý. Admin kiểm tra thông tin khi duyệt sự kiện.';
  }
  await loadVenues();
});

$('#new-venue').addEventListener('click', newVenue);

$('#venue-list').addEventListener('click', (event) => {
  const edit = event.target.closest('.edit-venue');
  if (edit) return editVenue(Number(edit.dataset.id));
  const toggle = event.target.closest('.toggle-venue');
  if (toggle) {
    const venue = venues.find((item) => item.id === Number(toggle.dataset.id));
    if (!venue) return;
    toggle.disabled = true;
    api(`/api/v1/venues/${venue.id}/active`, {
      method: 'PATCH', body: JSON.stringify({ active: !venue.active }),
    }).then(async () => {
      await loadVenues();
      show(venue.active ? 'Đã ngừng sử dụng địa điểm. Các sự kiện cũ vẫn giữ thông tin.'
        : 'Địa điểm đã có thể chọn cho sự kiện mới.');
    }).catch((error) => { show(error.message, true); toggle.disabled = false; });
    return;
  }
  const remove = event.target.closest('.delete-venue');
  if (!remove) return;
  venueToDelete = venues.find((item) => item.id === Number(remove.dataset.id));
  if (!venueToDelete) return;
  $('#delete-venue-description').textContent = `Xóa “${venueToDelete.name}”? Thao tác này chỉ thực hiện được nếu địa điểm chưa được dùng cho sự kiện.`;
  $('#delete-venue-dialog').showModal();
});

$('#keep-venue').addEventListener('click', () => $('#delete-venue-dialog').close());

$('#confirm-delete-venue').addEventListener('click', async () => {
  if (!venueToDelete) return;
  const button = $('#confirm-delete-venue');
  button.disabled = true;
  try {
    await api(`/api/v1/venues/${venueToDelete.id}`, { method: 'DELETE' });
    $('#delete-venue-dialog').close();
    if (Number($('#venue-id').value) === venueToDelete.id) newVenue();
    venueToDelete = null;
    await loadVenues();
    show('Đã xóa địa điểm.');
  } catch (error) {
    $('#delete-venue-dialog').close();
    show(error.message, true);
    await loadVenues().catch(() => {});
  } finally {
    button.disabled = false;
  }
});

$('#venue-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  const id = $('#venue-id').value;
  const body = {
    name: $('#venue-name').value.trim(),
    address: $('#venue-address').value.trim(),
    capacity: Number($('#venue-capacity').value),
  };
  if (!body.name || !body.address) return show('Vui lòng nhập tên và địa chỉ địa điểm.', true);
  if (button) button.disabled = true;
  try {
    const saved = await api(id ? `/api/v1/venues/${id}` : '/api/v1/venues', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(body),
    });
    await loadVenues();
    editVenue(saved.id);
    show(id ? 'Đã cập nhật địa điểm.' : 'Đã thêm địa điểm. Bạn có thể chọn nó khi tạo sự kiện.');
  } catch (error) {
    show(error.message, true);
  } finally {
    if (button) button.disabled = false;
  }
});
