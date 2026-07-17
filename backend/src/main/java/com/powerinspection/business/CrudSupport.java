package com.powerinspection.business;

import com.powerinspection.common.Ids;
import com.powerinspection.common.ListQuery;
import com.powerinspection.common.PageResult;
import com.powerinspection.data.DataStoreService;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public abstract class CrudSupport {
  protected final DataStoreService dataStore;

  protected CrudSupport(DataStoreService dataStore) {
    this.dataStore = dataStore;
  }

  protected List<Map<String, Object>> list(String category) {
    return dataStore.list(category);
  }

  protected PageResult<Map<String, Object>> page(String category, ListQuery query, String... filters) {
    return dataStore.page(
      category, query.getPage(), query.getSize(), query.getSort(), query.getDirection(),
      query.getUpdatedAfter(), query.getQ(), query.filters(filters)
    );
  }

  protected Map<String, Object> create(String category, String prefix, Map<String, Object> body) {
    body.putIfAbsent("id", Ids.next(prefix));
    body.putIfAbsent("createdAt", Instant.now().toString());
    return dataStore.upsert(category, body);
  }

  protected Map<String, Object> update(String category, String id, Map<String, Object> patch) {
    return dataStore.patch(category, id, patch);
  }

  protected void delete(String category, String id) {
    dataStore.delete(category, id);
  }
}
