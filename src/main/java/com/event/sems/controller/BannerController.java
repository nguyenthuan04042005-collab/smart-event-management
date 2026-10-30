package com.event.sems.controller;

import com.event.sems.common.ApiException;
import com.event.sems.dto.ApiDtos.EventView;
import com.event.sems.service.BannerStorageService;
import com.event.sems.service.EventService;
import java.io.IOException;
import java.nio.file.Files;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class BannerController {
  private final BannerStorageService storage;
  private final EventService events;

  @PostMapping("/api/v1/events/{id}/banner")
  public EventView upload(@PathVariable long id, @RequestParam("file") MultipartFile file) {
    // Kiểm tra quyền trước khi ghi tệp.
    events.checkBannerChange(id);
    var stored = storage.store(file);
    try {
      EventView previous = events.get(id);
      EventView updated = events.changeBanner(id, stored.url());
      storage.delete(previous.bannerUrl());
      return updated;
    } catch (RuntimeException ex) {
      storage.delete(stored.url());
      throw ex;
    }
  }

  @DeleteMapping("/api/v1/events/{id}/banner")
  public EventView remove(@PathVariable long id) {
    EventView previous = events.get(id);
    EventView updated = events.changeBanner(id, null);
    storage.delete(previous.bannerUrl());
    return updated;
  }

  @GetMapping("/api/v1/public/banners/{name}")
  public ResponseEntity<FileSystemResource> image(@PathVariable String name) throws IOException {
    var path = storage.resolve(name);
    if (!Files.isRegularFile(path))
      throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh banner.");
    MediaType type = name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
    return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(type)
        .header("X-Content-Type-Options", "nosniff")
        .body(new FileSystemResource(path));
  }
}
