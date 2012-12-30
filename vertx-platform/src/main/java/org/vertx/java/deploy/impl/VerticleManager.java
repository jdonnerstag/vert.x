/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.deploy.impl;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.impl.VertxThreadFactory;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.lang.Args;
import org.vertx.java.deploy.Container;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.VerticleFactory;

/**
 * This class could benefit from some refactoring
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class VerticleManager implements ModuleReloader {

  private static final Logger log = LoggerFactory.getLogger(VerticleManager.class);

  private final VertxInternal vertx;
  // deployment name --> deployment
  private final Map<String, Deployment> deployments = new ConcurrentHashMap<>();
  private final CountDownLatch stopLatch = new CountDownLatch(1);
  private Map<String, String> factoryNames = new HashMap<>();
  private final Redeployer redeployer;
  private final ModuleManager moduleManager;

  public VerticleManager(VertxInternal vertx) {
    this(vertx, new ModuleManager(vertx));
  }
  
  public VerticleManager(VertxInternal vertx, ModuleManager moduleManager) {
    this.vertx = Args.notNull(vertx, "vertx");
    this.moduleManager = Args.notNull(moduleManager, "moduleManager");
    
    // TODO doesn't fit the explanation given in VertxLocator
    VertxLocator.vertx = vertx;
    VertxLocator.container = new Container(this);
    
    this.redeployer = new Redeployer(vertx, this.moduleManager.modRoot(), this);

    // TODO change to use VertxConfig
    try (InputStream is = getClass().getClassLoader().getResourceAsStream("langs.properties")) {
      if (is == null) {
        log.warn("No language mappings found!");
      } else {
        Properties props = new Properties();
        props.load(new BufferedInputStream(is));
        Enumeration<?> en = props.propertyNames();
        while (en.hasMoreElements()) {
          String propName = (String)en.nextElement();
          factoryNames.put(propName, props.getProperty(propName));
        }
      }
    } catch (IOException e) {
      log.error("Failed to load langs.properties: " + e.getMessage());
    }
  }

  public final Redeployer redeployer() {
  	return redeployer;
  }

  public final ModuleManager moduleManager() {
  	return moduleManager;
  }
  
  public void block() {
    while (true) {
      try {
        stopLatch.await();
        break;
      } catch (InterruptedException e) {
        //Ignore
      }
    }
  }

  public void unblock() {
    stopLatch.countDown();
  }

  public JsonObject getConfig() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.config;
  }

  public String getDeploymentName() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.name;
  }

  public URI[] getDeploymentURLs() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.urls;
  }

  public File getDeploymentModDir() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.modDir;
  }

  public Logger getLogger() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.logger;
  }

  public void deployVerticle(final boolean worker, final String main,
                             final JsonObject config, final URI[] urls,
                             final int instances, final File currentModDir,
                             final String includes,
                             final Handler<String> doneHandler) {

    BlockingAction<Void> deployModuleAction = new BlockingAction<Void>(vertx, null) {
      @Override
      public Void action() throws Exception {
        doDeployVerticle(worker, main, config, urls, instances, currentModDir,
            includes, wrapDoneHandler(doneHandler));
        return null;
      }
    };

    deployModuleAction.run();
  }

  private Handler<String> wrapDoneHandler(final Handler<String> doneHandler) {
    if (doneHandler == null) {
      return null;
    }
    final Context context = vertx.getContext();
    return new Handler<String>() {
      @Override
      public void handle(final String deploymentID) {
        if (context == null) {
          doneHandler.handle(deploymentID);
        } else {
          context.execute(new Runnable() {
            public void run() {
              doneHandler.handle(deploymentID);
            }
          });
        }
      }
    };
  }

  private boolean doDeployVerticle(boolean worker, final String main,
                                   final JsonObject config, final URI[] urls,
                                   int instances, File currentModDir,
                                   String includes, Handler<String> doneHandler)
  {
    checkWorkerContext();
    ModuleDependencies pdata = new ModuleDependencies(main, urls);
    URI[] theURLs;
    // The user has specified a list of modules to include when deploying this verticle
    // so we walk the tree of modules adding tree of includes to classpath
    if (includes != null) {
      List<String> includedMods = ModuleConfig.getParameterList(includes);
      for (String includedMod: includedMods) {
      	moduleManager.install(includedMod, pdata);
      	if (pdata.failed()) {
          callDoneHandler(doneHandler, null);
          return false;
        }
      }
      theURLs = pdata.urls.toArray(new URI[pdata.urls.size()]);
    } else {
      theURLs = urls;
    }
    doDeploy(null, false, worker, main, null, config, theURLs, instances, currentModDir, doneHandler);
    return true;
  }

	/**
	 * Deploy a Module (async)
	 * 
	 * @param modName
	 * @param config
	 * @param instances
	 * @param currentModDir
	 * @param doneHandler
	 * TODO could be improved to return the Future
	 */
	public final void deployMod(final String modName, final JsonObject config, final int instances,
			final File currentModDir, final Handler<String> doneHandler) {

		BlockingAction<Void> deployModuleAction = new BlockingAction<Void>(vertx()) {
      @Override
      public Void action() throws Exception {
      	// TODO not sure it is correct to wrap the doneHandler here. 
        doDeployMod(false, null, modName, config, instances, currentModDir, wrapDoneHandler(doneHandler));
        return null;
      }
      
      @Override
      protected void handle(AsyncResult<Void> result) {
      	if (result.failed()) {
      		log.error("Failed to install module: " + modName + ": " + result.exception.getMessage());
      	}
      }
    };

    deployModuleAction.run();	
  }

	/**
	 * Deploy a Module (sync)
	 * 
	 * @param redeploy
	 * @param depName
	 * @param modName
	 * @param config
	 * @param instances
	 * @param currentModDir
	 * @param doneHandler
	 */
  public void doDeployMod(final boolean redeploy, final String depName, final String modName,
  		final JsonObject config, final int instances, final File currentModDir,
      final Handler<String> doneHandler) {
  	
    checkWorkerContext();

    ModuleDependencies deps = moduleManager.install(modName);
    if (deps.failed()) {
    	callDoneHandler(doneHandler, null);
    	return;
    }

    VertxModule module = moduleManager.module(modName);
    if (!module.exists()) {
    	log.error("Installed the module '" + modName + "'. But still unable to load config");
      callDoneHandler(doneHandler, null);
      return;
    }
    
    ModuleConfig conf = module.config();
    String main = conf.main();
    if (main == null) {
      log.error("Runnable module " + modName + " mod.json must contain a \"main\" field");
      callDoneHandler(doneHandler, null);
      return;
    }
    
    boolean worker = conf.worker();
    boolean autoRedeploy = conf.autoRedeploy();
    boolean preserveCwd = conf.preserveCwd();
    
    File modDirToUse = preserveCwd && currentModDir != null ? 
    		currentModDir : conf.modDir();

    doDeploy(depName, autoRedeploy, worker, main, modName, config, 
    		deps.urisToArray(), instances, modDirToUse, doneHandler);
  }

  public synchronized void undeployAll(final Handler<Void> doneHandler) {
    final CountingCompletionHandler count = new CountingCompletionHandler(
    		vertx.getOrAssignContext(), doneHandler);
    
    if (!deployments.isEmpty()) {
      // We do it this way since undeploy is itself recursive - we don't want
      // to attempt to undeploy the same verticle twice if it's a child of
      // another
      while (!deployments.isEmpty()) {
        String name = deployments.keySet().iterator().next();
        count.incRequired();
        undeploy(name, new SimpleHandler() {
          public void handle() {
            count.incCompleted();
          }
        });
      }
    }
  }

  public synchronized Map<String, Integer> listInstances() {
    Map<String, Integer> map = new HashMap<>();
    for (Map.Entry<String, Deployment> entry: deployments.entrySet()) {
      map.put(entry.getKey(), entry.getValue().verticles.size());
    }
    return map;
  }

  private void checkWorkerContext() {
    if (VertxThreadFactory.isWorker(Thread.currentThread()) == false) {
      throw new IllegalStateException("Not a worker thread");
    }
  }

  // We calculate a path adjustment that can be used by the fileSystem object
  // so that the *effective* working directory can be the module directory
  // this allows modules to read and write the file system as if they were
  // in the module dir, even though the actual working directory will be
  // wherever vertx run or vertx start was called from
  private void setPathAdjustment(File modDir) {
    Path cwd = Paths.get(".").toAbsolutePath().getParent();
    Path pmodDir = Paths.get(modDir.getAbsolutePath());
    Path relative = cwd.relativize(pmodDir);
    vertx.getContext().setPathAdjustment(relative);
  }

  private void callDoneHandler(Handler<String> doneHandler, String deploymentID) {
    if (doneHandler != null) {
      doneHandler.handle(deploymentID);
    }
  }

  private final void doDeploy(final String depName, final boolean autoRedeploy, final boolean worker, 
  		final String main, final String modName, final JsonObject config, final URI[] uris, 
  		final int instances, final File modDir, final Handler<String> doneHandler) {
  	
    checkWorkerContext();

    final String deploymentName =
        depName != null ? depName : "deployment-" + UUID.randomUUID().toString();

    if (log.isDebugEnabled()) {
	    log.debug("Deploying name: " + deploymentName + " main: " + main +
	        " instances: " + instances);
    }

    int dotIndex = main.lastIndexOf('.');
    String extension = dotIndex != -1 ? main.substring(dotIndex + 1) : null;
    String factoryName = null;
    if (extension != null) {
      factoryName = factoryNames.get(extension);
    }
    if (factoryName == null) {
      // Use the default
      factoryName = factoryNames.get("default");
      if (factoryName == null) {
        throw new IllegalArgumentException("No language mapping found and no default specified in langs.properties");
      }
    }

    class AggHandler {
      AtomicInteger count = new AtomicInteger(0);
      boolean failed;

      void done(boolean res) {
        if (!res) {
          failed = true;
        }
        if (count.incrementAndGet() == instances) {
          String deploymentID = failed ? null : deploymentName;
          callDoneHandler(doneHandler, deploymentID);
        }
      }
    }

    final AggHandler aggHandler = new AggHandler();

    String parentDeploymentName = getDeploymentName();
    final Deployment deployment = new Deployment(deploymentName, modName, instances,
        config == null ? new JsonObject() : config.copy(), uris, modDir, parentDeploymentName,
        autoRedeploy);
    deployments.put(deploymentName, deployment);
    if (parentDeploymentName != null) {
      Deployment parent = deployments.get(parentDeploymentName);
      parent.childDeployments.add(deploymentName);
    }

    URL[] urls = uriArrayToUrlArray(uris);
    
    // Workers share a single classloader with all instances in a deployment - this
    // enables them to use libraries that rely on caching or statics to share state
    // (e.g. JDBC connection pools)
    @SuppressWarnings("resource")
		final ClassLoader sharedLoader = worker ? new ParentLastURLClassLoader(urls, getClass().getClassLoader()) : null;

    for (int i = 0; i < instances; i++) {
      // Launch the verticle instance
      final ClassLoader cl = sharedLoader != null ? sharedLoader: new ParentLastURLClassLoader(urls, getClass().getClassLoader());
      Thread.currentThread().setContextClassLoader(cl);

      // We load the VerticleFactory class using the verticle classloader - this allows
      // us to put language implementations in modules

      Class<?> clazz;
      try {
        clazz = cl.loadClass(factoryName);
      } catch (ClassNotFoundException e) {
        log.error("Cannot find class " + factoryName + " to load", e);
        callDoneHandler(doneHandler, null);
        return;
      }

      final VerticleFactory verticleFactory;
      try {
        verticleFactory = (VerticleFactory)clazz.newInstance();
      } catch (Exception e) {
        log.error("Failed to instantiate VerticleFactory: " + clazz.getName(), e);
        callDoneHandler(doneHandler, null);
        return;
      }

      verticleFactory.init(this);

      Runnable runner = new Runnable() {
        public void run() {

          Verticle verticle = null;
          try {
            verticle = verticleFactory.createVerticle(main, cl);
          } catch (ClassNotFoundException e) {
            log.error("Cannot find verticle " + main, e);
          } catch (Throwable t) {
            log.error("Failed to create verticle", t);
          }

          if (verticle == null) {
            doUndeploy(deploymentName, new SimpleHandler() {
              public void handle() {
                aggHandler.done(false);
              }
            });
            return;
          }

          // Inject vertx
          verticle.setVertx(vertx);
          verticle.setContainer(new Container(VerticleManager.this));

          try {
            addVerticle(deployment, verticle, verticleFactory);
            if (modDir != null) {
              setPathAdjustment(modDir);
            }
            verticle.start();
            aggHandler.done(true);
          } catch (Throwable t) {
            vertx.reportException(t);
            doUndeploy(deploymentName, new SimpleHandler() {
              public void handle() {
                aggHandler.done(false);
              }
            });
          }

        }
      };

      if (worker) {
        vertx.startInBackground(runner);
      } else {
        vertx.startOnEventLoop(runner);
      }
    }
  }

	private URL[] uriArrayToUrlArray(final URI[] uris) {
    final URL[] urls = new URL[uris.length];
    for (int i=0; i < urls.length; i++) {
    	try {
				urls[i] = uris[i].toURL();
			} catch (MalformedURLException ex) {
				log.error("URI to URL conversion error", ex);
			}
    }
		return urls;
	}

  private void addVerticle(final Deployment deployment, final Verticle verticle, 
  		final VerticleFactory factory) {
  	
    String loggerName = "org.vertx.deployments." + deployment.name + "-" + deployment.verticles.size();
    Logger logger = LoggerFactory.getLogger(loggerName);
    Context context = vertx.getContext();
    VerticleHolder holder = new VerticleHolder(deployment, context, 
    		verticle, loggerName, logger, deployment.config, factory);
    deployment.verticles.add(holder);
    context.setDeploymentHandle(holder);
  }

  private VerticleHolder getVerticleHolder() {
    Context context = vertx.getContext();
    if (context != null) {
      VerticleHolder holder = (VerticleHolder)context.getDeploymentHandle();
      return holder;
    } else {
      return null;
    }
  }

  /**
   * Undeploy the Deployment
   */
  private void doUndeploy(String name, final Handler<Void> doneHandler) {
     CountingCompletionHandler count = new CountingCompletionHandler(vertx.getOrAssignContext(), doneHandler);
     doUndeploy(name, count);
  }

  /**
   * Undeploy the Deployment
   */
  private void doUndeploy(final String name, final CountingCompletionHandler count) {
  	log.info("Undeploy Deployment: " + name);
  	
    final Deployment deployment = deployments.remove(name);
    if (deployment == null) {
    	log.error("Deployment not found. Already undeployed?? Name: " + name);
    	return;
    }
    
    // Depth first - undeploy children first
    for (String childDeployment: deployment.childDeployments) {
      doUndeploy(childDeployment, count);
    }

    // Stop all instances of the Verticle
    for (final VerticleHolder holder: deployment.verticles) {
      count.incRequired();
      holder.context.execute(new Runnable() {
        public void run() {
          try {
            holder.verticle.stop();
          } catch (Throwable t) {
          	// Vertx -> Context -> VerticleHolder -> VerticleFactory.reportException(t)
            vertx.reportException(t);
          }
          holder.context.runCloseHooks();
          LoggerFactory.removeLogger(holder.loggerName);
          count.incCompleted();
        }
      });
    }

    // Remove deployment from parent child list
    if (deployment.parentDeploymentName != null) {
      Deployment parent = deployments.get(deployment.parentDeploymentName);
      if (parent != null) {
        parent.childDeployments.remove(name);
      }
    }
  }

  /**
   * First undeploy all Deployments provided, than redeploy them
   */
  public void reloadModules(final Set<Deployment> deps) {
  	// TODO change to first undeploy all, than reploy all again 
    for (final Deployment deployment: deps) {
      if (deployments.containsKey(deployment.name)) {
        doUndeploy(deployment.name, new SimpleHandler() {
          public void handle() {
            redeploy(deployment);
          }
        });
      } else {
        // This will be the case if the previous deployment failed, e.g.
        // a code error in a user verticle
        redeploy(deployment);
      }
    }
  }

  /**
   * (Async) Undeploy the Deployment
   */
  public synchronized void undeploy(String name, final Handler<Void> doneHandler) {
    final Deployment dep = deployments.get(name);
    if (dep == null) {
      log.error("Failed to undeploy. There is no deployment with name: " + name);
      if (doneHandler != null) {
        doneHandler.handle(null);
      }
      return;
    }
    
    doUndeploy(name, new SimpleHandler() {
      public void handle() {
        if (dep.modName != null && dep.autoRedeploy) {
          redeployer.moduleUndeployed(dep);
        }
        if (doneHandler != null) {
          doneHandler.handle(null);
        }
      }
    });
  }

  /**
   * (Async) (Re-)deploy the Deployment
   * 
   * @param deployment
   */
  private void redeploy(final Deployment deployment) {
    // Has to occur on a worker thread
    BlockingAction<String> redeployAction = new BlockingAction<String>(vertx) {
      @Override
      public String action() throws Exception {
        doDeployMod(true, deployment.name, deployment.modName, deployment.config, deployment.instances,
            null, null);
        return null;
      }
      @Override
      protected void handle(AsyncResult<String> result) {
        if (!result.succeeded()) {
          log.error(result.exception);
        }
      }
    };
    redeployAction.run();
  }

  public void stop() {
    redeployer.close();
  }
  
  public final VertxInternal vertx() {
  	return vertx;
  }

}
