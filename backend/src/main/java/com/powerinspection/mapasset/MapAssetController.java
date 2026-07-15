package com.powerinspection.mapasset;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.robot.RobotBridgeIdMapper;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/map-assets")
public class MapAssetController {
  private static final MediaType YAML_MEDIA_TYPE = MediaType.parseMediaType("application/yaml;charset=UTF-8");
  private static final MediaType PGM_MEDIA_TYPE = MediaType.parseMediaType("image/x-portable-graymap");

  private final MapAssetService mapAssetService;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;
  private final RobotBridgeIdMapper robotBridgeIdMapper;

  public MapAssetController(MapAssetService mapAssetService, PermissionService permissionService, CurrentUser currentUser,
      RobotBridgeIdMapper robotBridgeIdMapper) {
    this.mapAssetService = mapAssetService;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
    this.robotBridgeIdMapper = robotBridgeIdMapper;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
      @RequestParam("siteId") String siteId,
      @RequestPart("yaml") MultipartFile yaml,
      @RequestPart("pgm") MultipartFile pgm) throws IOException {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    Map<String, Object> asset = mapAssetService.create(siteId, yaml, pgm);
    var location = ServletUriComponentsBuilder.fromCurrentRequest()
      .path("/{id}")
      .buildAndExpand(asset.get("id"))
      .toUri();
    return ResponseEntity.created(location).body(ApiResponse.ok(asset));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> metadata(@PathVariable String id,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    requireReadAccess(authorization);
    return ApiResponse.ok(mapAssetService.get(id));
  }

  @GetMapping("/{id}/yaml")
  public ResponseEntity<Resource> yaml(@PathVariable String id,
      @RequestHeader(value = "Authorization", required = false) String authorization) throws IOException {
    requireReadAccess(authorization);
    Map<String, Object> asset = mapAssetService.get(id);
    return fileResponse(mapAssetService.yamlPath(id), String.valueOf(asset.get("yamlName")), String.valueOf(asset.get("yamlSha256")), YAML_MEDIA_TYPE);
  }

  @GetMapping("/{id}/pgm")
  public ResponseEntity<Resource> pgm(@PathVariable String id,
      @RequestHeader(value = "Authorization", required = false) String authorization) throws IOException {
    requireReadAccess(authorization);
    Map<String, Object> asset = mapAssetService.get(id);
    return fileResponse(mapAssetService.pgmPath(id), String.valueOf(asset.get("pgmName")), String.valueOf(asset.get("pgmSha256")), PGM_MEDIA_TYPE);
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable String id) throws IOException {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    mapAssetService.delete(id);
    return ApiResponse.ok();
  }

  private ResponseEntity<Resource> fileResponse(Path path, String filename, String sha256, MediaType mediaType) throws IOException {
    Resource resource = new FileSystemResource(path);
    return ResponseEntity.ok()
      .contentType(mediaType)
      .contentLength(Files.size(path))
      .lastModified(Files.getLastModifiedTime(path).toMillis())
      .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
      .eTag('"' + sha256 + '"')
      .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build().toString())
      .body(resource);
  }

  private void requireReadAccess(String authorization) {
    if (!robotBridgeIdMapper.isBridgePlatformRequest(authorization)) currentUser.get();
  }
}
