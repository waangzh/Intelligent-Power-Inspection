package com.powerinspection.auth;

import com.powerinspection.auth.AuthDtos.MeResponse;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.robot.RobotProperties;
import com.powerinspection.user.PermissionService;
import com.powerinspection.user.UserDto;
import com.powerinspection.user.UserEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UserAccessService {
  private final PermissionService permissionService;
  private final DataStoreService dataStore;
  private final RobotProperties robotProperties;

  public UserAccessService(
    PermissionService permissionService,
    DataStoreService dataStore,
    RobotProperties robotProperties
  ) {
    this.permissionService = permissionService;
    this.dataStore = dataStore;
    this.robotProperties = robotProperties;
  }

  public MeResponse me(UserEntity user) {
    return new MeResponse(
      UserDto.from(user),
      permissionService.permissionValuesFor(user.getRole()),
      scopes(),
      features()
    );
  }

  private Map<String, Object> scopes() {
    List<String> siteIds = dataStore.list(DataCategory.SITE).stream()
      .map(item -> String.valueOf(item.get("id")))
      .sorted()
      .toList();
    Map<String, Object> scopes = new LinkedHashMap<>();
    scopes.put("siteIds", siteIds);
    return scopes;
  }

  private Map<String, Object> features() {
    Map<String, Object> features = new LinkedHashMap<>();
    features.put("robotRegistration", robotProperties.isAllowRegistration());
    features.put("agentEnabled", true);
    return features;
  }
}
