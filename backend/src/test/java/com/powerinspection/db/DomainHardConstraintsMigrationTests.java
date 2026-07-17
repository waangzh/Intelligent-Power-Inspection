package com.powerinspection.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

class DomainHardConstraintsMigrationTests {
  private static final String NOW = "2026-07-17T00:00:00Z";

  @Test
  void upgradesPopulatedV17DatabaseAndEnforcesRelations() throws Exception {
    String url = databaseUrl();
    migrateToV17(url);

    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      insertDomainGraph(connection);
    }

    Flyway flyway = flyway(url);
    flyway.migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
         Statement statement = connection.createStatement()) {
      try (ResultSet result = statement.executeQuery(
          "SELECT active_robot_key FROM inspection_tasks WHERE id = 'task-1'")) {
        result.next();
        assertEquals("robot-1", result.getString(1));
      }
      assertThrows(SQLException.class, () -> statement.executeUpdate(
        "INSERT INTO routes(id, site_id, name, created_at, updated_at) "
          + "VALUES ('orphan-route', 'missing-site', 'Orphan', '" + NOW + "', '" + NOW + "')"
      ));
    }
  }

  @Test
  void rejectsConflictingLegacyTasksBeforeAlterStatementsAndCanRetry() throws Exception {
    String url = databaseUrl();
    migrateToV17(url);

    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      insertBaseEntities(connection);
      execute(connection,
        "INSERT INTO inspection_tasks(id, name, route_id, robot_id, status, created_at, updated_at) VALUES "
          + "('task-a', 'Task A', 'route-1', 'robot-1', 'RUNNING', '" + NOW + "', '" + NOW + "'),"
          + "('task-b', 'Task B', 'route-1', 'robot-1', 'PAUSED', '" + NOW + "', '" + NOW + "')"
      );
    }

    Flyway flyway = flyway(url);
    assertThrows(FlywayException.class, flyway::migrate);
    assertEquals(0, columnCount(url, "inspection_tasks", "active_robot_key"));

    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      execute(connection, "UPDATE inspection_tasks SET status = 'COMPLETED' WHERE id = 'task-b'");
    }
    flyway.repair();
    flyway.migrate();

    assertEquals(1, columnCount(url, "inspection_tasks", "active_robot_key"));
  }

  private void migrateToV17(String url) {
    Flyway.configure()
      .dataSource(url, "sa", "")
      .locations("classpath:db/migration")
      .target("17")
      .load()
      .migrate();
  }

  private Flyway flyway(String url) {
    return Flyway.configure()
      .dataSource(url, "sa", "")
      .locations("classpath:db/migration")
      .load();
  }

  private void insertDomainGraph(Connection connection) throws SQLException {
    insertBaseEntities(connection);
    execute(connection,
      "INSERT INTO robot_telemetry(robot_id, updated_at) VALUES ('robot-1', '" + NOW + "')"
    );
    execute(connection,
      "INSERT INTO inspection_tasks(id, name, site_id, route_id, robot_id, status, created_at, updated_at) "
        + "VALUES ('task-1', 'Task', 'site-1', 'route-1', 'robot-1', 'DISPATCHED', '" + NOW + "', '" + NOW + "')"
    );
    execute(connection,
      "INSERT INTO task_events(id, task_id, type, created_at, updated_at) "
        + "VALUES ('event-1', 'task-1', 'DISPATCH', '" + NOW + "', '" + NOW + "')"
    );
    execute(connection,
      "INSERT INTO alarms(id, task_id, severity, message, created_at, updated_at) "
        + "VALUES ('alarm-1', 'task-1', 'HIGH', 'Alarm', '" + NOW + "', '" + NOW + "')"
    );
    execute(connection,
      "INSERT INTO work_orders(id, title, alarm_id, task_id, site_id, source, status, priority, "
        + "created_by_id, created_by_name, created_at, updated_at) VALUES "
        + "('order-1', 'Order', 'alarm-1', 'task-1', 'site-1', 'MANUAL', 'PENDING', 'HIGH', "
        + "'user-1', 'User', '" + NOW + "', '" + NOW + "')"
    );
    execute(connection,
      "INSERT INTO inspection_records(id, task_id, site_id, created_at, updated_at) "
        + "VALUES ('record-1', 'task-1', 'site-1', '" + NOW + "', '" + NOW + "')"
    );
  }

  private void insertBaseEntities(Connection connection) throws SQLException {
    execute(connection,
      "INSERT INTO sites(id, name, created_at, updated_at) "
        + "VALUES ('site-1', 'Site', '" + NOW + "', '" + NOW + "')"
    );
    execute(connection,
      "INSERT INTO routes(id, site_id, name, created_at, updated_at) "
        + "VALUES ('route-1', 'site-1', 'Route', '" + NOW + "', '" + NOW + "')"
    );
    execute(connection,
      "INSERT INTO robots(id, name, site_id, status, created_at, updated_at) "
        + "VALUES ('robot-1', 'Robot', 'site-1', 'ONLINE', '" + NOW + "', '" + NOW + "')"
    );
  }

  private void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  private long columnCount(String url, String table, String column) throws SQLException {
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
         var statement = connection.prepareStatement(
           "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
      statement.setString(1, table);
      statement.setString(2, column);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private String databaseUrl() {
    return "jdbc:h2:mem:migration-" + UUID.randomUUID()
      + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  }
}
