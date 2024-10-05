/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;

/**
 * TransactionalCache 管理器
 * TransactionalCacheManager主要是对TransactionalCache的管理操作，基本的操作都会走到
 *
 * @author Clinton Begin
 */
public class TransactionalCacheManager {

  /**
   * 缓存CachingExecutor和TransactionCache的映射关系
   */
  private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

  /**
   * 清空指定的二级缓存
   * @param cache 二级缓存对象
   */
  public void clear(Cache cache) {
    getTransactionalCache(cache).clear();
  }

  /**
   * 从二级缓存中获取数据
   * @param cache 二级缓存
   * @param key 缓存的Key
   * @return 数据
   */
  public Object getObject(Cache cache, CacheKey key) {
    return getTransactionalCache(cache).getObject(key);
  }

  /**
   * 这里不是将数据直接存放到二级缓存中, 而是存放到TransactionalCache中的临时缓存, 等commit()之后才会将数据同步到我们的二级缓存中
   * @param cache 二级缓存对象
   * @param key Key
   * @param value 数据
   */
  public void putObject(Cache cache, CacheKey key, Object value) {
    getTransactionalCache(cache).putObject(key, value);
  }

  /**
   * 提交操作
   */
  public void commit() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.commit();
    }
  }

  /**
   * 回滚操作
   */
  public void rollback() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.rollback();
    }
  }

  /**
   * 通过Cache获取TransactionCache。如果不存在就创建一个TransactionCache
   * @param cache Cache
   * @return TransactionCache
   */
  private TransactionalCache getTransactionalCache(Cache cache) {
    return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
  }

}
