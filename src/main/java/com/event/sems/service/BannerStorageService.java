package com.event.sems.service;

import com.event.sems.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BannerStorageService {
  @Value("${app.banner-upload-dir}")
  private String directory;

  public record StoredBanner(String url, Path path) {}

  public StoredBanner store(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Chọn ảnh PNG hoặc JPEG tối đa 5 MB.");
    try {
      byte[] bytes = file.getBytes();
      boolean png = bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
          && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
      boolean jpeg = bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
          && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
      if (!png && !jpeg)
        throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ ảnh PNG hoặc JPEG.");
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null || image.getWidth() < 1 || image.getHeight() < 1
          || image.getWidth() > 4000 || image.getHeight() > 4000)
        throw new ApiException(HttpStatus.BAD_REQUEST, "Ảnh banner không hợp lệ hoặc quá lớn.");
      String extension = png ? "png" : "jpg";
      Path root = Path.of(directory).toAbsolutePath().normalize();
      Files.createDirectories(root);
      String name = UUID.randomUUID() + "." + extension;
      Path target = root.resolve(name);
      Files.write(target, bytes);
      return new StoredBanner("/api/v1/public/banners/" + name, target);
    } catch (IOException ex) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không lưu được ảnh banner.");
    }
  }

  public Path resolve(String name) {
    if (name == null || !name.matches("[a-f0-9-]{36}\\.(png|jpg)"))
      throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh banner.");
    return Path.of(directory).toAbsolutePath().normalize().resolve(name);
  }

  public void delete(String url) {
    if (url == null || !url.startsWith("/api/v1/public/banners/")) return;
    try {
      Files.deleteIfExists(resolve(url.substring(url.lastIndexOf('/') + 1)));
    } catch (IOException ignored) {
      // Ảnh cũ không còn liên kết với sự kiện; có thể dọn thủ công nếu hệ thống tệp lỗi.
    }
  }
}
