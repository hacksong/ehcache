package net.sf.ehcache.osgi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.ops4j.pax.exam.CoreOptions.bootDelegationPackages;
import static org.ops4j.pax.exam.CoreOptions.cleanCaches;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.workingDirectory;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;

import javax.transaction.TransactionManager;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.osgi.util.TestUtil;
import net.sf.ehcache.transaction.manager.DefaultTransactionManagerLookup;
import net.sf.ehcache.transaction.manager.TransactionManagerLookup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.ops4j.pax.exam.util.PathUtils;
import org.osgi.framework.Constants;

/**
 * Simple XA strict Ehcache test
 * 
 * @author hhuynh
 * 
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class EhcacheXATest {

  @Configuration
  public Option[] configBitronix() {
    return options(
        bootDelegationPackages("sun.*,javax.naming,javax.naming.spi,javax.naming.event,javax.management"),
        wrappedBundle(maven("javax.transaction", "jta").versionAsInProject()).exports(
            "javax.transaction;version=1.1"),
        wrappedBundle(maven("org.codehaus.btm", "btm").versionAsInProject()).exports(
            "bitronix.tm.*"),
        mavenBundle("net.sf.ehcache", "ehcache-ee").versionAsInProject(),
        TestUtil.commonOptions(),
        systemProperty("com.tc.productkey.path").value(
            PathUtils.getBaseDir() + "/src/test/resources/enterprise-suite-license.key"));
  }

  @Configuration
  public Option[] configAtomikos() {
    return options(
        bootDelegationPackages("sun.*,javax.naming,javax.naming.spi,javax.naming.event,javax.management"),
        wrappedBundle(maven("javax.transaction", "jta").versionAsInProject()).exports(
            "javax.transaction;version=1.1"),
        wrappedBundle(maven("com.atomikos", "transactions-jta").versionAsInProject()).imports(
            "!javax.servlet.*,!javax.jms,!javax.resource,*;resolution:=optional").exports(
            "com.atomikos.icatch.standalone,com.atomikos.datasource.xa,com.atomikos.*"),
        wrappedBundle(maven("com.atomikos", "transactions").versionAsInProject()).imports(
            "!javax.servlet.*,com.atomikos.datasource.xa,*").exports("*"),
        wrappedBundle(maven("com.atomikos", "transactions-api").versionAsInProject()).imports(
            "!javax.servlet.*,*").exports("*"),
        wrappedBundle(maven("com.atomikos", "atomikos-util").versionAsInProject()).imports(
            "!javax.servlet.*,com.atomikos.icatch.standalone,*").exports("*"),
        mavenBundle("net.sf.ehcache", "ehcache-ee").versionAsInProject(),
        junitBundles(),
        workingDirectory("target/pax-exam"),
        cleanCaches(),
        systemProperty("com.tc.productkey.path").value(
            PathUtils.getBaseDir() + "/src/test/resources/enterprise-suite-license.key"));
  }

  @ProbeBuilder
  public TestProbeBuilder extendProbe(TestProbeBuilder builder) {
    builder.setHeader(Constants.IMPORT_PACKAGE, "javax.transaction;version=1.1");
    return builder;
  }

  @Test
  public void testXACache() throws Exception {
    TransactionManagerLookup lookup = new DefaultTransactionManagerLookup();
    TransactionManager tm = lookup.getTransactionManager();
    CacheManager cacheManager = new CacheManager(
        EhcacheXATest.class.getResourceAsStream("/xa-ehcache.xml"));

    Ehcache cache1 = cacheManager.getEhcache("txCache1");
    Ehcache cache2 = cacheManager.getEhcache("txCache2");
    tm.begin();

    cache1.removeAll();
    cache2.removeAll();

    tm.commit();

    tm.begin();
    cache1.get(1);
    cache1.put(new Element(1, "one"));
    tm.commit();

    tm.begin();
    Element e = cache1.get(1);
    assertEquals("one", (String) e.getObjectValue());
    cache1.remove(1);
    e = cache1.get(1);
    assertNull(e);
    int size = cache1.getSize();
    assertEquals(0, size);
    tm.rollback();

    tm.begin();
    e = cache1.get(1);
    assertEquals("one", (String) e.getObjectValue());

    // tear down
    tm.rollback();

    cacheManager.shutdown();
  }
}