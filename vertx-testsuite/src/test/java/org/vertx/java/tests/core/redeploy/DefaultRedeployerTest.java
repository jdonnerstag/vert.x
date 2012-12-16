package org.vertx.java.tests.core.redeploy;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.rules.Timeout;
import org.junit.runner.Description;

import static org.junit.Assert.*;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.impl.ConcurrentHashSet;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.impl.DefaultRedeployer;
import org.vertx.java.deploy.impl.Deployment;
import org.vertx.java.deploy.impl.ModuleReloader;
import org.vertx.java.deploy.impl.Redeployer;
import org.vertx.java.framework.TestUtils;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.DirectoryNotEmptyException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultRedeployerTest {

  private static final Logger log = LoggerFactory.getLogger(DefaultRedeployerTest.class);

  protected static long SLEEP = 500;
  
  // Useful for tests NOT executed via gradle. Gradle hooks into the junit test lifecycle and nicely indents the output.
  // See http://www.gradle.org/docs/current/dsl/org.gradle.api.tasks.testing.Test.html
  @Rule public TestWatcher watchman = new TestWatcher() {
    public void starting(Description descr) {
      log.info(">> " + descr.getMethodName());
    }
  };  

  // Default timeout for all test cases
  @Rule public Timeout timeout = new Timeout(10_000);
  
  protected static VertxInternal vertx;
  protected TestReloader reloader;
  protected File modRoot;
  protected Redeployer red;

  @BeforeClass
  public static void oneTimeSetUp() throws Exception {
  	vertx = new DefaultVertx();
  }

  @AfterClass
  public static void oneTimeTearDown() throws Exception {
  	vertx.stop();
  }

  @Before
  public void setUp() throws Exception {
    reloader = new TestReloader();
    modRoot = new File("reloader-test-mods");
    if (!modRoot.exists()) {
      modRoot.mkdir();
    }
    
    red = newRedeployer(vertx, modRoot, reloader);
  }

  protected Redeployer newRedeployer(final VertxInternal vertx, final File modRoot, final ModuleReloader reloader) {
  	return new DefaultRedeployer(vertx, modRoot, reloader);
  }
  
  @After
  public void tearDown() throws Exception {
    red.close();
    red = null;

    // Windows locks files / directories while in use ...
    int count = 0;
    while(true) {
	    Thread.sleep(200);
	  	try {
	  		if (modRoot.exists()) {
	  			vertx.fileSystem().deleteSync(modRoot.getAbsolutePath(), true);
	  		}
	  		break;
	  	} catch (DirectoryNotEmptyException ex) {
	  		if (++count > 20) {
	  			throw new RuntimeException("Unable to delete directory");
	  		}
	  		// try again
	  	}
    }
  }

  @Test
  public void testCreateFile() throws Exception {
    String modName = "my-mod";
    File modDir = createModDir(modName);
    createFile(modDir, "foo.js", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", "my-mod", null);
    red.moduleDeployed(dep);
    Thread.sleep(SLEEP);
    createFile(modDir, "blah.txt", TestUtils.randomAlphaString(1000));
    waitReload(dep);
  }

  @Test
  public void testModifyFile() throws Exception {
    String modName = "my-mod";
    File modDir = createModDir(modName);
    createFile(modDir, "foo.js", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", "my-mod", null);
    red.moduleDeployed(dep);
    Thread.sleep(SLEEP);
    modifyFile(modDir, "foo.js");
    waitReload(dep);
  }

  @Test
  public void testDeleteFile() throws Exception {
    String modName = "my-mod";
    File modDir = createModDir(modName);
    createFile(modDir, "foo.js", TestUtils.randomAlphaString(1000));
    createFile(modDir, "blah.txt", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", "my-mod", null);
    red.moduleDeployed(dep);
    Thread.sleep(SLEEP);
    deleteFile(modDir, "blah.txt");
    waitReload(dep);
  }

  @Test
  public void testCreateDirectory() throws Exception {
    String modName = "my-mod";
    File modDir = createModDir(modName);
    createFile(modDir, "foo.js", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", "my-mod", null);
    red.moduleDeployed(dep);
    Thread.sleep(SLEEP);
    createDirectory(modDir, "some-dir");
    waitReload(dep);
  }

  @Test
  public void testCreateFileInSubDirectory() throws Exception {
    String modName = "my-mod";
    File modDir = createModDir(modName);
    createFile(modDir, "foo.js", TestUtils.randomAlphaString(1000));
    createDirectory(modDir, "some-dir");
    Deployment dep = createDeployment("dep1", "my-mod", null);
    red.moduleDeployed(dep);
    Thread.sleep(SLEEP);
    File subDir = new File(modDir, "some-dir");
    createFile(subDir, "bar.txt", TestUtils.randomAlphaString(1000));
    waitReload(dep);
  }

  @Test
  public void testDeleteFileInSubDirectory() throws Exception {
    String modName = "my-mod";
    File modDir = createModDir(modName);
    createFile(modDir, "foo.js", TestUtils.randomAlphaString(1000));
    File subDir = createDirectory(modDir, "some-dir");
    createFile(subDir, "bar.txt", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", "my-mod", null);
    red.moduleDeployed(dep);
    Thread.sleep(SLEEP);
    deleteFile(subDir, "bar.txt");
    waitReload(dep);
  }

  @Test
  public void testModifyFileInSubDirectory() throws Exception {
    String modName = "my-mod";
    File modDir = createModDir(modName);
    createFile(modDir, "foo.js", TestUtils.randomAlphaString(1000));
    createDirectory(modDir, "some-dir");
    File subDir = new File(modDir, "some-dir");
    createFile(subDir, "bar.txt", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", "my-mod", null);
    red.moduleDeployed(dep);
    Thread.sleep(SLEEP);
    modifyFile(subDir, "bar.txt");
    waitReload(dep);
  }

  @Test
  public void testDeleteSubDir() throws Exception {
    String modName = "my-mod";
    File modDir = createModDir(modName);
    createFile(modDir, "foo.js", TestUtils.randomAlphaString(1000));
    createDirectory(modDir, "some-dir");
    File subDir = new File(modDir, "some-dir");
    createFile(subDir, "bar.txt", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", "my-mod", null);
    red.moduleDeployed(dep);
    Thread.sleep(SLEEP);
    vertx.fileSystem().deleteSync(subDir.getAbsolutePath(), true);
    waitReload(dep);
  }

  @Test
  public void testReloadMultipleDeps() throws Exception {
    String modName = "my-mod";
    File modDir = createModDir(modName);
    createModDir("other-mod");
    createFile(modDir, "foo.js", TestUtils.randomAlphaString(1000));
    Deployment dep1 = createDeployment("dep1", "my-mod", null);
    red.moduleDeployed(dep1);
    Deployment dep2 = createDeployment("dep2", "my-mod", null);
    red.moduleDeployed(dep2);
    Deployment dep3 = createDeployment("dep3", "other-mod", null);
    red.moduleDeployed(dep3);
    Thread.sleep(SLEEP);
    createFile(modDir, "blah.txt", TestUtils.randomAlphaString(1000));
    waitReload(dep1, dep2);
  }

  private File createModDir(String modName) {
    File modDir = new File(modRoot, modName);
    modDir.mkdir();
    log.info("Create Directory: " + modDir.getAbsolutePath() + "  " + modDir.lastModified());
    return modDir;
  }

  private void createFile(File dir, String fileName, String content) throws Exception {
    File f = new File(dir, fileName);
    vertx.fileSystem().writeFileSync(f.getAbsolutePath(), new Buffer(content));
    log.info("Create File: " + f.getAbsolutePath() + "  " + f.lastModified());
  }

  private void modifyFile(File dir, String fileName) throws Exception {
    File f = new File(dir, fileName);
    FileWriter fw = new FileWriter(f, true);
    fw.write(TestUtils.randomAlphaString(500));
    fw.close();
    log.info("Modify File: " + f.getAbsolutePath() + "  " + f.lastModified());
  }

  private void deleteFile(File dir, String fileName) throws Exception {
    File f = new File(dir, fileName);
    log.info("Delete File: " + f.getAbsolutePath());
    f.delete();
  }

  private File createDirectory(File dir, String dirName) throws Exception {
    File f = new File(dir, dirName);
    vertx.fileSystem().mkdirSync(f.getAbsolutePath());
    log.info("Create Directory: " + f.getAbsolutePath() + "  " + f.lastModified());
    return f;
  }

  private void waitReload(Deployment... deps) throws Exception {
    Set<Deployment> set = new HashSet<>();
    for (Deployment dep: deps) {
      set.add(dep);
    }
    reloader.waitReload(set);
  }

  class TestReloader implements ModuleReloader {

    Set<Deployment> reloaded = new ConcurrentHashSet<>();
    CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void reloadModules(Set<Deployment> deps) {
      reloaded.addAll(deps);
      latch.countDown();
    }

    void waitReload(Set<Deployment> deps) throws Exception {
      if (!reloaded.isEmpty()) {
        checkDeps(deps);
      } else {
        if (!latch.await(30000, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Time out");
        }
        checkDeps(deps);
      }
    }

    private void checkDeps(Set<Deployment> deps) {
      assertEquals("Number of expected Deployments does not match: ", deps.size(), reloaded.size());
      for (Deployment dep: deps) {
        assertTrue("Expected the Deployment was reloaded: " + dep, reloaded.contains(dep));
      }
    }
  }

  private Deployment createDeployment(String name, String modName, String parentName) {
     return new Deployment(name, modName, 1, null, null, null, parentName, true);
  }
}
