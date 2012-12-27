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

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.ActionFuture;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.impl.VertxThreadFactory;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.lang.Args;
import org.vertx.java.deploy.ModuleRepository;

/**
 * The Module manager attempts to downloads missing Modules from registered 
 * Repositories. Each Module gets installed in its own subdirectory of modRoot.
 * and must contain a file called 'mod.json', which is the module config file.
 * Besides a few other attributes, it also defines dependencies on other modules
 * ('includes'). The Module manager make sure that all dependencies are resolved.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class ModuleManager {

	private static final Logger log = LoggerFactory.getLogger(ModuleManager.class);

	private static final String MODULE_ROOT_DIR_PROPERTY_NAME = "vertx.mods";
	private static final String DEFAULT_MODULE_ROOT_DIR = "mods";
	private static final String LIB_DIR = "lib";

	private final VertxInternal vertx;
	private final VerticleManager verticleManager;
	private List<ModuleRepository> moduleRepositories = new ArrayList<>();

	// The root directory where we expect to find the modules, resp. where
	// downloaded modules will be deployed
	private final File modRoot;

	/**
	 * Constructor
	 * 
	 * @param vertx
	 *          Must be != null
	 * @param repository
	 *          Defaults to DEFAULT_REPO_HOST
	 * @param modRoot
	 *          The directory path where all the modules are deployed already or
	 *          will be installed after download from a repository.
	 */
	public ModuleManager(final VertxInternal vertx, final VerticleManager verticleManager, final String repository,
			final String modRoot, final ModuleRepository... moduleRepositories) {

		this.vertx = Args.notNull(vertx, "vertx");
		this.verticleManager = Args.notNull(verticleManager, "verticleManager");
		this.modRoot = initModRoot(modRoot);

		initRepositories(repository, moduleRepositories);
	}

	/**
	 * Initialize modRoot
	 * 
	 * @param modRoot
	 * @return
	 */
	private File initModRoot(final String modRoot) {
		// TODO use VertxConfig once applied
		String modDir = modRoot != null ? modRoot : System.getProperty(MODULE_ROOT_DIR_PROPERTY_NAME);
		if (modDir == null || modDir.trim().isEmpty()) {
			modDir = DEFAULT_MODULE_ROOT_DIR;
		}
		
		File f = new File(modDir);
		if (f.exists() == false) {
			log.info("Module root directory does not exist => create it: " + f.getAbsolutePath());
			if (f.mkdir() == false) {
				throw new IllegalArgumentException("Unable to create directory: " + f.getAbsolutePath());
			}
		} else if (f.isDirectory() == false) {
			throw new IllegalArgumentException("Module root directory exists, but is not a directory: " + f.getAbsolutePath());
		}
		
		return f;
	}

	/**
	 * Initialize the list of repositories
	 * 
	 * @param repository
	 * @param moduleRepositories
	 */
	private void initRepositories(final String repository, final ModuleRepository... moduleRepositories) {
		for (ModuleRepository repo: moduleRepositories) {
			if (repo != null) {
				this.moduleRepositories.add(repo);
			}
		}
		
		if (this.moduleRepositories.size() == 0) {
			this.moduleRepositories.add(new DefaultModuleRepository(vertx, repository, this.modRoot));
		}
	}
	
	/**
	 * This methods provides full unsynchronized access the list of repositories. You can remove the default
	 * entry, add new repositories, etc.. It's a little bit dangerous because it's unsynchronized. At the same 
	 * time we don't expect the list to be modified very often. Adding repos right after ModuleManager was 
	 * created, in the same thread, is absolutely safe and likely the standard use case.
	 * 
	 * @return
	 */
	public final List<ModuleRepository> moduleRepositories() {
		return this.moduleRepositories;
	}
	
	/**
	 * @return The modules root directory
	 */
	public final File modRoot() {
		return modRoot;
	}

	/**
	 * Install a module (sync)
	 * 
	 * @param moduleName
	 */
	public AsyncResult<Void> installMod(final String moduleName) {
		return installMod(moduleName, 30, TimeUnit.SECONDS);
	}

	/**
	 * Install a module (sync)
	 * 
	 * @param moduleName
	 */
	public AsyncResult<Void> installMod(final String moduleName, int timeout, TimeUnit unit) {
    AsyncResult<Void> res = null;
  	for (ModuleRepository repo: this.moduleRepositories) {
  		ActionFuture<Void> f = repo.installMod(moduleName, null);
	    res = f.get(timeout, unit);
	    if (res == null) {
	      log.error("Timeout while waiting to download module '" + moduleName + "' from repository: " + repo.toString());
	    } else if (res.failed()) {
	    	log.error("Failed to install module '" + moduleName + "' from repository: " + repo.toString());
	    } else {
	    	log.info("Successfully installed module '" + moduleName + "' from repository: " + repo.toString());
	    	break;
	    }
  	}
  	return res;
  }

	/**
	 * Uninstall a module (sync)
	 * TODO shouldn't it be async? At least optionally? It is blocking (delete directory)
	 * 
	 * @param moduleName
	 */
  public void uninstallMod(final String moduleName) {
    log.info("Uninstalling module " + moduleName + " from directory " + modRoot.getAbsolutePath());
    File modDir = new File(modRoot, moduleName);
    if (!modDir.exists()) {
      log.error("Cannot find module directory to delete: " + modDir.getAbsolutePath());
    } else {
      try {
        vertx.fileSystem().deleteSync(modDir.getAbsolutePath(), true);
        log.info("Module " + moduleName + " successfully uninstalled (directory deleted)");
      } catch (Exception e) {
        log.error("Failed to delete directory: " + modDir.getAbsoluteFile(), e);
      }
    }
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

		BlockingAction<Void> deployModuleAction = new BlockingAction<Void>(vertx) {
      @Override
      public Void action() throws Exception {
      	// TODO not sure it is correct to wrap the doneHandler here. 
        doDeployMod(false, null, modName, config, instances, currentModDir, wrapDoneHandler(doneHandler));
        return null;
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

    File modDir = new File(modRoot, modName);
    ModuleConfig conf = new ModuleConfig(modDir, modName);
    if (conf.json() != null) {
      String main = conf.main();
      if (main == null) {
        log.error("Runnable module " + modName + " mod.json must contain a \"main\" field");
        callDoneHandler(doneHandler, null);
        return;
      }
      
      boolean worker = conf.worker();
      boolean preserveCwd = conf.preserveCwd();
      
      // preserveCwd deploys dependencies in a subdirectory to the module,
      // thus avoiding issues with incompatible module versions
      File modDirToUse = preserveCwd ? currentModDir : modDir;

      List<URI> urls = processIncludes(modName, new ArrayList<URI>(), modName, modDir, conf, 
      		new HashMap<String, String>(), new HashSet<String>());
      if (urls == null) {
        callDoneHandler(doneHandler, null);
        return;
      }

      boolean autoRedeploy = conf.autoRedeploy();
      verticleManager.doDeploy(depName, autoRedeploy, worker, main, modName, config, 
      		urls.toArray(new URI[urls.size()]), instances, modDirToUse, doneHandler);
    } else {
    	// Install the module first and then try again
      if (installMod(modName).succeeded()) {
        doDeployMod(redeploy, depName, modName, config, instances, currentModDir, doneHandler);
      } else {
      	// Failed to install the module
        callDoneHandler(doneHandler, null);
      }
    }
  }

  /**
   * We walk through the graph of includes making sure we only add each one once
   * We keep track of what jars have been included so we can flag errors if paths
   * are included more than once
   * We make sure we only include each module once in the case of loops in the
   * graph
   */
  public List<URI> processIncludes(final String runModule, List<URI> urls, final String modName, 
  		final File modDir, final ModuleConfig conf, final Map<String, String> includedJars, 
  		final Set<String> includedModules) {
  	
    checkWorkerContext();
    // Add the urls for this module
    urls.add(modDir.toURI());
    File libDir = new File(modDir, LIB_DIR);
    if (libDir.exists()) {
      File[] jars = libDir.listFiles();
      for (File jar: jars) {
        URI jarURL = jar.toURI();
        String sjarURL = jarURL.toString();
        String jarName = sjarURL.substring(sjarURL.lastIndexOf("/") + 1);
        String prevMod = includedJars.get(jarName);
        if (prevMod != null) {
          log.warn("Warning! jar file " + jarName + " is contained in module " +
                   prevMod + " and also in module " + modName +
                   " which are both included (perhaps indirectly) by module " +
                   runModule);
        }
        includedJars.put(jarName, modName);
        urls.add(jarURL);
      }
    }

    includedModules.add(modName);

    List<String> sarr = conf.includes();
    for (String include: sarr) {
      if (includedModules.contains(include)) {
        // Ignore - already included this one
      } else {
        File newmodDir = new File(modRoot, include);
        inner: while (true) {
          ModuleConfig newconf = new ModuleConfig(newmodDir, include);
          if (newconf.json() != null) {
            urls = processIncludes(runModule, urls, include, newmodDir, newconf, includedJars, includedModules);
            if (urls == null) {
              return null;
            }
            break inner;
          } else {
            // Module not installed - let's try to install it
            if (installMod(include).failed()) {
              return null;
            }
          }
        }
      }
    }

    return urls;
  }

  /**
   * 
   */
  private void checkWorkerContext() {
    if (VertxThreadFactory.isWorker(Thread.currentThread()) == false) {
      throw new IllegalStateException("Not a worker thread");
    }
  }

  /**
   * 
   * @param doneHandler
   * @param deploymentID
   */
  private void callDoneHandler(Handler<String> doneHandler, String deploymentID) {
    if (doneHandler != null) {
      doneHandler.handle(deploymentID);
    }
  }

	/**
	 * 
	 * @param doneHandler
	 * @return
	 */
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
}
