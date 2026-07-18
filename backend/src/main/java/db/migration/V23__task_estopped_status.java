package db.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Allow terminal ESTOPPED on inspection_tasks (distinct from CANCELLED).
 * H2 uses DROP CONSTRAINT; MySQL 8 uses DROP CHECK.
 */
public class V23__task_estopped_status extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    String product = connection.getMetaData().getDatabaseProductName();
    boolean h2 = product != null && product.toLowerCase().contains("h2");
    try (Statement statement = connection.createStatement()) {
      if (h2) {
        statement.execute("ALTER TABLE inspection_tasks DROP CONSTRAINT chk_inspection_tasks_status");
      } else {
        statement.execute("ALTER TABLE inspection_tasks DROP CHECK chk_inspection_tasks_status");
      }
      statement.execute(
        "ALTER TABLE inspection_tasks ADD CONSTRAINT chk_inspection_tasks_status "
          + "CHECK (status IN ("
          + "'CREATED','DISPATCHED','RUNNING','PAUSED','MANUAL_TAKEOVER',"
          + "'COMPLETED','CANCELLED','ESTOPPED'"
          + "))"
      );
    }
  }
}
