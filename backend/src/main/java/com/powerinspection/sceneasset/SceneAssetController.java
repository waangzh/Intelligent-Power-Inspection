package com.powerinspection.sceneasset;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scene-assets")
public class SceneAssetController {
  private static final MediaType PLY_MEDIA_TYPE = MediaType.parseMediaType("application/octet-stream");
  private final SceneAssetService sceneAssetService;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public SceneAssetController(SceneAssetService sceneAssetService, PermissionService permissionService,
      CurrentUser currentUser) {
    this.sceneAssetService = sceneAssetService;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String source,
      @RequestParam(required = false) String status, @RequestParam(required = false) String siteId,
      @RequestParam(required = false) String robotId, @RequestParam(required = false) String assetKind) {
    currentUser.get();
    return ApiResponse.ok(sceneAssetService.list(source, status, siteId, robotId, assetKind));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
    currentUser.get();
    return ApiResponse.ok(sceneAssetService.get(id));
  }

  @GetMapping("/{id}/model")
  public ResponseEntity<Resource> model(@PathVariable String id) throws IOException {
    currentUser.get();
    Map<String, Object> asset = sceneAssetService.get(id);
    return file(sceneAssetService.modelPath(id), String.valueOf(asset.get("originalName")),
      String.valueOf(asset.get("modelSha256")), PLY_MEDIA_TYPE, false);
  }

  @GetMapping("/{id}/preview")
  public ResponseEntity<Resource> preview(@PathVariable String id) throws IOException {
    currentUser.get();
    Map<String, Object> asset = sceneAssetService.get(id);
    return file(sceneAssetService.previewPath(id), "preview-" + asset.get("originalName"),
      String.valueOf(asset.get("modelSha256")), PLY_MEDIA_TYPE, true);
  }

  @GetMapping("/{id}/metadata")
  public ResponseEntity<Resource> metadata(@PathVariable String id) throws IOException {
    currentUser.get();
    Map<String, Object> asset = sceneAssetService.get(id);
    return file(sceneAssetService.metadataPath(id), "metadata.json", String.valueOf(asset.get("metadataSha256")),
      MediaType.APPLICATION_JSON, false);
  }

  @PostMapping("/{id}/review")
  public ApiResponse<Map<String, Object>> review(@PathVariable String id,
      @RequestBody SceneAssetReviewRequest request) {
    var user = currentUser.get();
    permissionService.require(user, Permission.ROUTE_EDIT);
    return ApiResponse.ok(sceneAssetService.review(id, request, user.getId()));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    sceneAssetService.delete(id);
    return ApiResponse.ok();
  }

  private ResponseEntity<Resource> file(Path path, String filename, String sha256, MediaType mediaType,
      boolean inline) throws IOException {
    ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
      .filename(filename, StandardCharsets.UTF_8).build();
    return ResponseEntity.ok().contentType(mediaType).contentLength(Files.size(path))
      .lastModified(Files.getLastModifiedTime(path).toMillis())
      .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
      .eTag('"' + sha256 + '"').header(HttpHeaders.ACCEPT_RANGES, "bytes")
      .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(new FileSystemResource(path));
  }
}
