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
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 实现 Cache 接口，支持事务的 Cache 实现类，主要用于二级缓存中
 * The 2nd level cache transactional buffer.
 *
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  /**
   * 底层封装的二级缓存的Cache对象
   */
  private final Cache delegate;
  /**
   * 该字段为true时, 则表示当前TransactionCache不可查询, 且提交事务时会将底层Cache清空
   */
  private boolean clearOnCommit;
  /**
   * 暂时记录添加到TransactionCache中的数据, 在事务提交时, 会将其中的数据添加到二级缓存中
   */
  private final Map<Object, Object> entriesToAddOnCommit;
  /**
   * 记录缓存未命中的CacheKey对象
   */
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 根据指定的key, 在二级缓存中查询对应的结果对象
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    // issue #116  先从底层的Cache中查找数据
    Object object = delegate.getObject(key);
    
    //数据找不到, 则将key记录到entriesMissedInCache集合中
    if (object == null) {
      entriesMissedInCache.add(key);
    }
    // issue #146
    //如果clearOnCommit为true, 则当前TransactionCache不可用, 直接返回null
    if (clearOnCommit) {
      return null;
    } else {    //否则返回查询到的对象
      return object;
    }
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  /**
   * 向缓存对象中添加数据, 一般情况下, key是CacheKey, value是查询结果
   * 注意, 这里不是将数据直接存放到二级缓存中, 而是先存放到临时缓存entriesToAddOnCommit中, 等执行commit()操作之后才会将数据存放到底层的二级缓存中
   * @param key key
   * @param object value           
   */
  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  /**
   * 清空。注意, 这里也不是清空底层的二级缓存, 而是清空临时缓存entriesToAddOnCommit
   */
  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  /**
   * 提交操作
   */
  public void commit() {
    //在提交前清空二级缓存
    if (clearOnCommit) {
      delegate.clear();
    }
    flushPendingEntries();   //将entriesToAddOnCommit集合的数据保存到二级缓存中
    reset();   //重置
  }

  /**
   * 回滚
   */
  public void rollback() {
    unlockMissedEntries();    //将entriesMissedInCache集合中记录的缓存项从二级缓存中删除
    reset();     //重置
  }

  /**
   * 重置操作
   *  1、将clearOnCommit置为false
   *  2、清空entriesToAddOnCommit
   *  3、清空entriesMissedInCache
   */
  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 将entriesToAddOnCommit数据保存到二级缓存中
   */
  private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  /**
   * 将entriesMissedInCache集合中记录的缓存项从二级缓存中删除
   */
  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifiying a rollback to the cache adapter."
            + "Consider upgrading your cache adapter to the latest version.  Cause: " + e);
      }
    }
  }

}
