/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.modules.ehcache.writebehind;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

import org.terracotta.api.ClusteringToolkit;
import org.terracotta.coordination.Barrier;
import org.terracotta.ehcache.tests.AbstractCacheTestBase;
import org.terracotta.ehcache.tests.ClientBase;
import org.terracotta.util.ClusteredAtomicLong;

import com.tc.test.config.model.TestConfig;

import junit.framework.Assert;

public class SerializationWriteBehindTest extends AbstractCacheTestBase {

  private static final int NODE_COUNT = 2;

  public SerializationWriteBehindTest(TestConfig testConfig) {
    super("basic-writebehind-test.xml", testConfig, App.class, App.class);
  }

  public static class App extends ClientBase {

    private final Barrier     barrier;
    final ClusteredAtomicLong totalWriteCount;
    final ClusteredAtomicLong totalDeleteCount;

    public App(String[] args) {
      super(args);
      this.barrier = getClusteringToolkit().getBarrier("barrier", NODE_COUNT);
      this.totalWriteCount = getClusteringToolkit().getAtomicLong("long1");
      this.totalDeleteCount = getClusteringToolkit().getAtomicLong("long2");
    }

    public static void main(String[] args) {
      new App(args).run();
    }

    @Override
    protected void runTest(Cache cache, ClusteringToolkit clusteringToolkit) throws Throwable {
      final int index = barrier.await();

      WriteBehindCacheWriter writer;

      if (0 == index) {
        writer = new WriteBehindCacheWriter("WriteBehindCacheWriter", index, 20L);
        cache.registerCacheWriter(writer);

        for (int i = 0; i < 1000; i++) {
          cache.putWithWriter(new Element(new SerializationWriteBehindType("key" + i % 200),
                                          new SerializationWriteBehindType("value" + i)));
          if (0 == i % 10) {
            cache.removeWithWriter(new SerializationWriteBehindType("key" + i % 200 / 10));
          }
        }
      } else {
        writer = new WriteBehindCacheWriter("WriteBehindCacheWriter", index, 10L);
        cache.registerCacheWriter(writer);
      }

      Thread.sleep(60000);
      barrier.await();

      System.out.println("[Client " + index + " processed " + writer.getWriteCount() + " writes for writer 1]");
      System.out.println("[Client " + index + " processed " + writer.getDeleteCount() + " deletes for writer 1]");

      totalWriteCount.addAndGet(writer.getWriteCount());
      totalDeleteCount.addAndGet(writer.getDeleteCount());

      barrier.await();

      if (0 == index) {
        System.out.println("[Clients processed a total of " + totalWriteCount.get() + " writes]");
        System.out.println("[Clients processed a total of " + totalDeleteCount.get() + " deletes]");

        Assert.assertEquals(1000, totalWriteCount.get());
        Assert.assertEquals(100, totalDeleteCount.get());
      }

      barrier.await();
    }

  }
}
