package com.powerinspection.user;

import com.powerinspection.common.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
  private static final Map<UserRole, Set<Permission>> ROLE_PERMISSIONS = new EnumMap<>(UserRole.class);

  static {
    ROLE_PERMISSIONS.put(UserRole.ADMIN, EnumSet.of(
      Permission.TASK_VIEW,
      Permission.TASK_ESTOP,
      Permission.SITE_EDIT,
      Permission.ROUTE_EDIT,
      Permission.ROBOT_MANAGE,
      Permission.DETECTION_MANAGE,
      Permission.USER_MANAGE,
      Permission.RECORD_EXPORT,
      Permission.WORKORDER_VIEW,
      Permission.WORKORDER_CREATE,
      Permission.WORKORDER_REVIEW,
      Permission.AGENT_VIEW,
      Permission.AGENT_RUN,
      Permission.AGENT_APPROVE,
      Permission.AGENT_ADMIN
    ));
    ROLE_PERMISSIONS.put(UserRole.DISPATCHER, EnumSet.of(
      Permission.TASK_VIEW,
      Permission.TASK_CREATE,
      Permission.TASK_DISPATCH,
      Permission.TASK_CONTROL,
      Permission.SITE_EDIT,
      Permission.ROUTE_EDIT,
      Permission.ALARM_ACK,
      Permission.RECORD_EXPORT,
      Permission.WORKORDER_VIEW,
      Permission.WORKORDER_PROCESS,
      Permission.AGENT_VIEW,
      Permission.AGENT_RUN,
      Permission.AGENT_APPROVE
    ));
    ROLE_PERMISSIONS.put(UserRole.VIEWER, EnumSet.of(Permission.TASK_VIEW));
  }

  public boolean has(UserRole role, Permission permission) {
    return ROLE_PERMISSIONS.getOrDefault(role, Set.of()).contains(permission);
  }

  public void require(UserEntity user, Permission permission) {
    if (user == null) {
      throw ApiException.unauthorized("未登录");
    }
    if (!Boolean.TRUE.equals(user.getEnabled())) {
      throw ApiException.forbidden("用户已被禁用");
    }
    if (!has(user.getRole(), permission)) {
      throw ApiException.forbidden("无权限访问");
    }
  }

  public void requireAny(UserEntity user, Permission... permissions) {
    if (user == null) {
      throw ApiException.unauthorized("未登录");
    }
    if (!Boolean.TRUE.equals(user.getEnabled())) {
      throw ApiException.forbidden("用户已被禁用");
    }
    for (Permission permission : permissions) {
      if (has(user.getRole(), permission)) {
        return;
      }
    }
    throw ApiException.forbidden("无权限访问");
  }

  public void requireRole(UserEntity user, UserRole role) {
    if (user == null) {
      throw ApiException.unauthorized("未登录");
    }
    if (user.getRole() != role) {
      throw ApiException.forbidden("无权限访问");
    }
  }
}
