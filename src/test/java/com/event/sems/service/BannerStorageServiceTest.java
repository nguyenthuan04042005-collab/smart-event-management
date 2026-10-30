package com.event.sems.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.event.sems.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class BannerStorageServiceTest {
  @TempDir Path directory;

  @Test
  void storesValidPngWithGeneratedNameAndDeletesIt() throws Exception {
    BannerStorageService storage = new BannerStorageService();
    ReflectionTestUtils.setField(storage, "directory", directory.toString());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
    var stored = storage.store(new MockMultipartFile(
        "file", "banner.png", "image/png", bytes.toByteArray()));
    assertThat(stored.url()).startsWith("/api/v1/public/banners/").endsWith(".png");
    assertThat(Files.exists(stored.path())).isTrue();
    storage.delete(stored.url());
    assertThat(Files.exists(stored.path())).isFalse();
  }

  @Test
  void rejectsFakeImageAndInvalidPath() {
    BannerStorageService storage = new BannerStorageService();
    ReflectionTestUtils.setField(storage, "directory", directory.toString());
    assertThatThrownBy(() -> storage.store(new MockMultipartFile(
        "file", "fake.png", "image/png", "not an image".getBytes())))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> storage.resolve("../secret.txt"))
        .isInstanceOf(ApiException.class);
  }
}
