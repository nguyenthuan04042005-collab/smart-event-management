package com.event.sems.service;

import com.event.sems.common.ApiException;
import com.event.sems.dto.ApiDtos.VenueInput;
import com.event.sems.dto.ApiDtos.VenueView;
import com.event.sems.entity.Venue;
import com.event.sems.entity.UserAccount;
import com.event.sems.repository.EventRepository;
import com.event.sems.repository.UserAccountRepository;
import com.event.sems.repository.VenueRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VenueService {
  private final VenueRepository venues;
  private final EventRepository events;
  private final UserAccountRepository users;

  @Transactional(readOnly = true)
  public List<VenueView> list() {
    List<Venue> visible = hasRole("ADMIN") ? venues.findAll()
        : venues.findByOrganizerIdOrderByNameAsc(currentOrganizer().getId());
    return visible.stream().map(this::view).toList();
  }

  @Transactional
  public VenueView create(VenueInput input) {
    UserAccount organizer = currentOrganizer();
    String name = input.name().trim();
    String address = input.address().trim();
    ensureUnique(organizer.getId(), name, address, 0L);
    Venue venue = new Venue();
    venue.setOrganizer(organizer);
    venue.setName(name);
    venue.setAddress(address);
    venue.setCapacity(input.capacity());
    return view(venues.saveAndFlush(venue));
  }

  @Transactional
  public VenueView update(long id, VenueInput input) {
    Venue venue = requireOwnedForUpdate(id);
    String name = input.name().trim();
    String address = input.address().trim();
    ensureUnique(venue.getOrganizer().getId(), name, address, id);
    if ((!venue.getName().equals(name) || !venue.getAddress().equals(address))
        && events.countByVenueIdAndApprovalStatus(id, "APPROVED") > 0)
      throw new ApiException(HttpStatus.CONFLICT,
          "Địa điểm đang dùng cho sự kiện đã duyệt; không thể đổi tên hoặc địa chỉ tại đây.");
    Integer maxCapacity = events.maxCapacityByVenueId(id);
    if (maxCapacity != null && input.capacity() < maxCapacity)
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Sức chứa không được nhỏ hơn số vé tối đa của sự kiện đang dùng địa điểm này ("
              + maxCapacity
              + ").");
    venue.setName(name);
    venue.setAddress(address);
    venue.setCapacity(input.capacity());
    return view(venues.saveAndFlush(venue));
  }

  @Transactional
  public void delete(long id) {
    Venue venue = requireOwnedForUpdate(id);
    if (events.countByVenueId(id) > 0)
      throw new ApiException(
          HttpStatus.CONFLICT, "Địa điểm đã được dùng cho sự kiện nên không thể xóa.");
    venues.delete(venue);
    venues.flush();
  }

  @Transactional
  public VenueView setActive(long id, boolean active) {
    Venue venue = requireOwnedForUpdate(id);
    venue.setActive(active);
    return view(venue);
  }

  private void ensureUnique(long organizerId, String name, String address, long excludeId) {
    if (venues.existsByOrganizerIdAndNameIgnoreCaseAndAddressIgnoreCaseAndIdNot(
        organizerId, name, address, excludeId))
      throw new ApiException(HttpStatus.CONFLICT, "Địa điểm với tên và địa chỉ này đã tồn tại.");
  }

  private Venue requireOwnedForUpdate(long id) {
    UserAccount organizer = currentOrganizer();
    Venue venue = venues.findForUpdate(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy địa điểm."));
    if (!venue.getOrganizer().getId().equals(organizer.getId()))
      throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy địa điểm.");
    return venue;
  }

  private UserAccount currentOrganizer() {
    if (!hasRole("ORGANIZER"))
      throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ ban tổ chức được quản lý địa điểm.");
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    return users.findByUsernameIgnoreCase(username)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập lại."));
  }

  private boolean hasRole(String role) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getAuthorities().stream()
        .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
  }

  private VenueView view(Venue venue) {
    return new VenueView(
        venue.getId(),
        venue.getName(),
        venue.getAddress(),
        venue.getOrganizer().getDisplayName(),
        venue.getCapacity(),
        events.countByVenueId(venue.getId()),
        events.countByVenueIdAndApprovalStatus(venue.getId(), "APPROVED"),
        venue.isActive());
  }
}
