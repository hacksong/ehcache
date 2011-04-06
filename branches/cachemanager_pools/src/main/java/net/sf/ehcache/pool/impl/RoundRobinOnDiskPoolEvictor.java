package net.sf.ehcache.pool.impl;

import net.sf.ehcache.pool.PoolEvictor;
import net.sf.ehcache.pool.PoolableStore;

import java.util.Collection;

/**
 * todo measuring size before & after eviction isn't safe in multi-threaded case
 * @author Ludovic Orban
 */
public class RoundRobinOnDiskPoolEvictor implements PoolEvictor<PoolableStore> {
    public boolean freeSpace(Collection<PoolableStore> from, long bytes) {
        long remaining = bytes;

        while (true) {
            for (PoolableStore poolableStore : from) {
                long beforeEvictionSize = poolableStore.getOnDiskSizeInBytes();
                if (!poolableStore.evictFromOnDisk(1, bytes)) {
                    return false;
                }
                long afterEvictionSize = poolableStore.getOnDiskSizeInBytes();

                remaining -= (beforeEvictionSize - afterEvictionSize);
                if (remaining <= 0L) {
                    return true;
                }
            }
        }
    }
}
