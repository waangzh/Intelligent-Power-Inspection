package com.powerinspection.config;

import com.powerinspection.user.Permission;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PermissionOpenApiCustomizer {
  @Bean
  OpenApiCustomizer permissionCodeSchemaCustomizer() {
    List<String> permissionValues = Arrays.stream(Permission.values())
      .map(Permission::value)
      .sorted()
      .toList();

    StringSchema permissionCodeSchema = new StringSchema();
    permissionCodeSchema.setEnum(permissionValues);
    permissionCodeSchema.setDescription("权限码，与 Permission 枚举一致");

    ArraySchema permissionsArraySchema = new ArraySchema();
    permissionsArraySchema.setItems(permissionCodeSchema);
    permissionsArraySchema.setDescription("当前用户拥有的权限集合");

    return openApi -> {
      if (openApi.getComponents() == null) {
        openApi.setComponents(new io.swagger.v3.oas.models.Components());
      }
      openApi.getComponents().addSchemas("PermissionCode", permissionCodeSchema);
      openApi.getComponents().addSchemas("PermissionCodeList", permissionsArraySchema);

      patchSchema(openApi, "LoginResponse", permissionsArraySchema);
      patchSchema(openApi, "MeResponse", permissionsArraySchema);
    };
  }

  private static void patchSchema(
    io.swagger.v3.oas.models.OpenAPI openApi,
    String schemaName,
    ArraySchema permissionsArraySchema
  ) {
    Schema<?> schema = openApi.getComponents().getSchemas().get(schemaName);
    if (schema == null || schema.getProperties() == null) {
      return;
    }
    schema.getProperties().put("permissions", permissionsArraySchema);
  }
}
