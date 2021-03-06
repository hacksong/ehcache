/**
 *  Copyright 2003-2009 Terracotta, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.sf.ehcache.openjpa.datacache;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.apache.openjpa.datacache.AbstractQueryCache;
import org.apache.openjpa.datacache.QueryCache;
import org.apache.openjpa.datacache.QueryKey;
import org.apache.openjpa.datacache.QueryResult;

import java.util.Collection;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Craig Andrews
 * @author Greg Luck
 */
public class EhCacheQueryCache extends AbstractQueryCache implements QueryCache {

    /**
     *
     */
    protected boolean useDefaultForUnnamedCaches = true;

    /**
     *
     */
    protected String cacheName = "openjpa-querycache";

    /**
     *
     */
    protected ReentrantLock writeLock = new ReentrantLock();

    /**
     *
     */
    @Override
    protected void clearInternal() {
        getOrCreateCache(cacheName).removeAll();
    }

    /**
     *
     * @param qk
     * @return
     */
    @Override
    protected QueryResult getInternal(QueryKey qk) {
        Ehcache cache = getOrCreateCache(cacheName);
        Element result = cache.get(qk);
        if (result == null) {
            return null;
        } else {
            return (QueryResult) result.getValue();
        }
    }

    /**
     *
     * @return
     */
    @Override
    protected Collection keySet() {
        Ehcache cache = getOrCreateCache(cacheName);
        return cache.getKeys();
    }

    /**
     *
     * @param qk
     * @return
     */
    @Override
    protected boolean pinInternal(QueryKey qk) {
        return false;
    }

    /**
     *
     * @param qk
     * @param oids
     * @return
     */
    @Override
    protected QueryResult putInternal(QueryKey qk, QueryResult oids) {
        Ehcache cache = getOrCreateCache(cacheName);
        Element element = new Element(qk, oids);
        cache.put(element);
        return oids;
    }

    /**
     *
     * @param qk
     * @return
     */
    @Override
    protected QueryResult removeInternal(QueryKey qk) {
        Ehcache cache = getOrCreateCache(cacheName);
        QueryResult queryResult = getInternal(qk);
        cache.remove(qk);
        return queryResult;
    }

    /**
     *
     * @param qk
     * @return
     */
    @Override
    protected boolean unpinInternal(QueryKey qk) {
        return false;
    }

    /**
     *
     */
    public void writeLock() {
        writeLock.lock();
    }

    /**
     *
     */
    public void writeUnlock() {
        writeLock.unlock();
    }

    /**
     *
     * @param name
     * @return
     */
    protected synchronized Ehcache getOrCreateCache(String name) {
        CacheManager cacheManager = CacheManager.getInstance();
        Ehcache ehCache = cacheManager.getEhcache(name);
        if (ehCache == null) {
            cacheManager.addCache(name);
            ehCache = cacheManager.getEhcache(name);
        }
        return ehCache;
    }
}
