package org.vertx.java.tests.core.redeploy;

import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.impl.ModuleReloader;
import org.vertx.java.deploy.impl.PollingRedeployer;
import org.vertx.java.deploy.impl.Redeployer;

import java.io.File;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PollingRedeployerTest extends DefaultRedeployerTest {

  @SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(PollingRedeployerTest.class);

  @Override
  protected Redeployer newRedeployer(final VertxInternal vertx, final File modRoot, final ModuleReloader reloader) {
  	return new PollingRedeployer(vertx, modRoot, reloader);
  }
}
