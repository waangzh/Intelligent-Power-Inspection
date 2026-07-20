package com.powerinspection.workorder;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.config.ModelFileWebConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WorkOrderPhotoService {
  private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
    "image/jpeg", "image/png", "image/webp", "image/bmp"
  );
  static final Path ROOT = ModelFileWebConfig.MODEL_FILE_ROOT.resolve("work-order-photos").normalize();
  static final int MAX_PHOTOS_PER_ORDER = 3;

  private final long maxBytes;

  public WorkOrderPhotoService(@Value("${app.work-order.max-photo-bytes:5242880}") long maxBytes) {
    this.maxBytes = maxBytes;
  }

  public String save(String workOrderId, MultipartFile photo) throws IOException {
    if (!StringUtils.hasText(workOrderId)) {
      throw ApiException.badRequest("工单 ID 不能为空");
    }
    validateImage(photo);
    String photoId = Ids.next("woph");
    Path orderDir = ROOT.resolve(workOrderId).normalize();
    if (!orderDir.startsWith(ROOT)) {
      throw ApiException.badRequest("工单 ID 非法");
    }
    Files.createDirectories(orderDir);
    Path output = orderDir.resolve(photoId + extension(photo)).normalize();
    if (!output.startsWith(orderDir)) {
      throw ApiException.badRequest("上传文件名非法");
    }
    Files.copy(photo.getInputStream(), output, StandardCopyOption.REPLACE_EXISTING);
    return publicUrl(workOrderId, output.getFileName().toString());
  }

  public void deleteFile(String workOrderId, String publicUrl) {
    verifyWorkOrderUrl(workOrderId, publicUrl);
    Path file = resolveStoragePath(publicUrl);
    try {
      Files.deleteIfExists(file);
    } catch (IOException ex) {
      throw ApiException.badRequest("删除现场照片失败");
    }
  }

  public List<String> normalizePhotoUrls(String workOrderId, Object rawPhotos) {
    if (rawPhotos == null) {
      return List.of();
    }
    if (!(rawPhotos instanceof List<?> list)) {
      throw ApiException.badRequest("现场照片格式不正确");
    }
    if (list.size() > MAX_PHOTOS_PER_ORDER) {
      throw ApiException.badRequest("现场照片最多 " + MAX_PHOTOS_PER_ORDER + " 张");
    }
    return list.stream()
      .map(item -> {
        String url = item == null ? "" : String.valueOf(item).trim();
        if (url.isBlank()) {
          throw ApiException.badRequest("现场照片地址不能为空");
        }
        verifyWorkOrderUrl(workOrderId, url);
        Path file = resolveStoragePath(url);
        if (!Files.isRegularFile(file)) {
          throw ApiException.badRequest("现场照片不存在或已失效");
        }
        return url;
      })
      .toList();
  }

  static String publicUrl(String workOrderId, String filename) {
    return "/model-files/work-order-photos/" + workOrderId + "/" + filename;
  }

  static void verifyWorkOrderUrl(String workOrderId, String publicUrl) {
    String prefix = "/model-files/work-order-photos/" + workOrderId + "/";
    if (!StringUtils.hasText(publicUrl) || !publicUrl.startsWith(prefix)) {
      throw ApiException.badRequest("现场照片地址不合法");
    }
  }

  static Path resolveStoragePath(String publicUrl) {
    if (!StringUtils.hasText(publicUrl) || !publicUrl.startsWith("/model-files/work-order-photos/")) {
      throw ApiException.badRequest("现场照片地址不合法");
    }
    String relative = publicUrl.substring("/model-files/".length());
    Path path = ModelFileWebConfig.MODEL_FILE_ROOT.resolve(relative).normalize();
    if (!path.startsWith(ROOT)) {
      throw ApiException.badRequest("现场照片地址不合法");
    }
    return path;
  }

  private void validateImage(MultipartFile photo) {
    if (photo == null || photo.isEmpty()) {
      throw ApiException.badRequest("请上传现场照片");
    }
    if (photo.getSize() > maxBytes) {
      throw ApiException.badRequest("现场照片不能超过 " + (maxBytes / 1024 / 1024) + "MB");
    }
    String contentType = photo.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      throw ApiException.badRequest("仅支持 JPG/PNG/WebP/BMP 图片");
    }
  }

  private String extension(MultipartFile photo) {
    String original = photo.getOriginalFilename();
    if (original != null) {
      int dot = original.lastIndexOf('.');
      if (dot >= 0 && dot < original.length() - 1) {
        String ext = original.substring(dot).toLowerCase(Locale.ROOT);
        if (ext.matches("\\.(jpg|jpeg|png|webp|bmp)")) {
          return ext;
        }
      }
    }
    String contentType = photo.getContentType();
    if ("image/png".equals(contentType)) return ".png";
    if ("image/webp".equals(contentType)) return ".webp";
    if ("image/bmp".equals(contentType)) return ".bmp";
    return ".jpg";
  }
}
