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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.impl.ActionFuture;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.lang.Args;
import org.vertx.java.deploy.ModuleRepository;

/**
 * The Module manager attempts to downloads missing Modules from registered 
 * Repositories. Each Module gets installed in its own subdirectory
 * and must contain a file called 'mod.json', which is the module's config file.
 * Besides a few other attributes, it also defines dependencies on other modules
 * ('includes'). The Module manager make sure that all dependencies are resolved.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class ModuleManager {

	private static final Logger log = LoggerFactory.getLogger(ModuleManager.class);

	public static final String MODULE_ROOT_DIR_PROPERTY_NAME = "vertx.mods";
	private static final String DEFAULT_MODULE_ROOT_DIR = "mods";
	private static final String LIB_DIR = "lib";

	private final VerticleManager verticleManager;
	private List<ModuleRepository> moduleRepositories = new ArrayList<>();

	// The directory where modules will be downloaded to
	private final File modRoot;

	/**
	 * Constructor
	 * 
	 * @param modRoot
	 *          The directory path where all the modules are deployed already or
	 *          will be installed after download from a repository.
	 * @param repository
	 *          Defaults to DEFAULT_REPO_HOST
	 */
	public ModuleManager(final VerticleManager verticleManager, final File modRoot, ModuleRepository... repos) {
		this.verticleManager = Args.notNull(verticleManager, "verticleManager");
		this.modRoot = initModRoot(modRoot);

		initRepositories(repos);
	}

	public final VertxInternal vertx() {
		return this.verticleManager.vertx();
	}
	
	/**
	 * Initialize modRoot
	 * 
	 * @param modRoot
	 * @return
	 */
	private File initModRoot(File modRoot) {
		// TODO use VertxConfig once applied
		if (modRoot == null) {
			String modDir = System.getProperty(MODULE_ROOT_DIR_PROPERTY_NAME);
			if (modDir == null || modDir.trim().isEmpty()) {
				modDir = DEFAULT_MODULE_ROOT_DIR;
			}
			modRoot = new File(modDir);
		}
		
		if (modRoot.exists() == false) {
			log.info("Module root directory does not exist => create it: " + modRoot.getAbsolutePath());
			if (modRoot.mkdir() == false) {
				throw new IllegalArgumentException("Unable to create directory: " + modRoot.getAbsolutePath());
			}
		} else if (modRoot.isDirectory() == false) {
			throw new IllegalArgumentException("Module root directory exists, but is not a directory: " + 
					modRoot.getAbsolutePath());
		}
		
		return modRoot;
	}

	/**
	 * Initialize the list of repositories. 
	 * 
	 * @param repository
	 * @param moduleRepositories
	 */
	private void initRepositories(final ModuleRepository... repos) {
		for (ModuleRepository repo: repos) {
			moduleRepositories().add(repo);
		}
		
		if (moduleRepositories().size() == 0) {
			moduleRepositories().add(new DefaultModuleRepository(vertx(), null));
		}
	}
	
	/**
	 * This methods provides full unsynchronized access to the list of repositories. You can remove the default
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
	 * (Sync) Gets the config for the module
	 * 
	 * @param modName
	 * @param throwException
	 * @return
	 */
	public final ModuleConfig modConfig(final String modName, boolean throwException) {
		Args.notNull(modName, "modName");
    try {
	    return new ModuleConfig(modRoot, modName);
    } catch (Exception ex) {
    	if (throwException) {
    		throw new RuntimeException(ex);
    	} else {
    		log.error("Failed to load module config: " + modConfigFile(modName));
    	}
    }
    return null;
	}

	public final String modConfigFile(final String modName) {
		Args.notNull(modName, "modName");
		return ModuleConfig.getFile(modRoot, modName).getAbsolutePath();
	}

	/**
	 * (Sync) Install a single module without its dependencies: download from 
	 * a repository and unzip into modRoot. Existing files will be replaced, extraneous 
	 * files will not be removed. Uninstall the module, if you want to delete all associated files.
	 * 
	 * @param modName
	 */
	public AsyncResult<Void> installOne(final String modName) {
		return installOne(modName, 30, TimeUnit.SECONDS);
	}

	/**
	 * (Async) Install a single module without its dependencies: download from 
	 * a repository and unzip into modRoot. Existing files will be replaced, extraneous 
	 * files will not be removed. Uninstall the module, if you want to delete all associated files.
	 * 
	 * @param modName
	 */
	public AsyncResult<Void> installOne(final String modName, int timeout, TimeUnit unit) {
		Args.notNull(modName, "modName");
  	log.info("Install module: " + modName);
    AsyncResult<Void> res = null;
  	for (ModuleRepository repo: this.moduleRepositories) {
  		ActionFuture<Void> f = repo.installMod(modName, this.modRoot, null);
	    res = f.get(timeout, unit);
	    if (res == null) {
	      log.error(message("Timeout while waiting to download", modName, repo));
	    } else if (res.failed()) {
	    	log.error(message("Failed to install", modName, repo));
	    } else {
	    	log.info(message("Successfully installed", modName, repo));
	    	break;
	    }
  	}
  	return res;
  }

	private String message(String prefix, String modName, ModuleRepository repo) {
  	return prefix + " module '" + modName + "' from repository: " + repo.toString();
	}

  /**
   * (Sync) Install a module and all its declared dependencies.
   * 
   * @param modName
   * @return
   */
  public ModuleDependencies install(final String modName) {
		Args.notNull(modName, "modName");
    ModuleDependencies pdata = new ModuleDependencies(modName);
    
    if (ModuleConfig.exists(modRoot, modName) == false) {
      if (installOne(modName).failed()) {
        return pdata.failed("Failed to install module: " + modName);
      }
    } else {
    	log.info("Module '" + modName + "' is already installed");
    }
    
    ModuleConfig conf = modConfig(modName, false);
    if (conf == null) {
    	// Something went wrong
      return pdata.failed("Failed to load module config: " + modConfigFile(modName));
    }
    
    String main = conf.main();
    if (main == null) {
      return pdata.failed("Runnable module " + modName + " mod.json must contain a \"main\" field");
    }

    return processIncludes(pdata, modName, conf);
  }
  
  /**
   * We walk through the graph of includes making sure we only add each one once.
   * We keep track of what jars have been included so we can flag errors if paths
   * are included more than once.
   * We make sure we only include each module once in the case of loops in the
   * graph.
   */
  public ModuleDependencies processIncludes(final ModuleDependencies pdata, final String modName, 
  		final ModuleConfig conf) {

    // Add the urls for this module
    pdata.urls.add(conf.modDir().toURI());
    File libDir = new File(conf.modDir(), LIB_DIR);
    if (libDir.exists()) {
      File[] jars = libDir.listFiles();
      for (File jar: jars) {
        String prevMod = pdata.includedJars.get(jar.getName());
        if (prevMod != null) {
          log.warn("Warning! jar file " + jar.getName() + " is contained in module(s) [" +
                   prevMod + "] and also in module " + modName +
                   " which are both included (perhaps indirectly) by module " +
                   pdata.runModule);
          
          pdata.includedJars.put(jar.getName(), prevMod + ", " + modName);
        } else {
          pdata.includedJars.put(jar.getName(), modName);
        }
        pdata.urls.add(jar.toURI());
      }
    }

    pdata.includedModules.add(modName);

    List<String> sarr = conf.includes();
    for (String include: sarr) {
      if (pdata.includedModules.contains(include)) {
        // Ignore - already included this one
      } else {
     		ModuleConfig newconf = modConfig(include, false);
     		if (newconf == null) {
          // Module not installed - let's try to install it
          if (installOne(include).failed()) {
            return pdata.failed("Failed to install all dependencies");
          }
       		newconf = modConfig(include, false);
       		if (newconf == null) {
            return pdata.failed("?? The module config should really be now available");
       		}
     		}
     		
        if (processIncludes(pdata, include, newconf).failed()) {
          return pdata;
        }
      }
    }

    return pdata;
  }
	
	/**
	 * (Sync) Uninstall a module (but not its dependencies): delete the directory
	 * 
	 * @param modName
	 */
  public void uninstall(final String modName) {
    log.info("Uninstalling module " + modName + " from directory " + modRoot.getAbsolutePath());
    File modDir = new File(modRoot, modName);
    if (!modDir.exists()) {
      log.error("Cannot find module directory to delete: " + modDir.getAbsolutePath());
    } else {
      try {
        vertx().fileSystem().deleteSync(modDir.getAbsolutePath(), true);
        log.info("Module " + modName + " successfully uninstalled (directory deleted)");
      } catch (Exception e) {
        log.error("Failed to delete directory: " + modDir.getAbsoluteFile(), e);
      }
    }
  }
  
  /**
   * Data only 
   */
  public static class ModuleDependencies {
  	
  	// The root module where the analysis started
  	final String runModule;
  	
  	// The module's classpath: all required module directories and all jars
  	final List<URI> urls;
  	
  	// List of all Jars contributed by all modules from their respective 'lib' directory
  	// jar -> module(s)
  	final Map<String, String> includedJars;

  	// List of all required modules (includes)
  	final List<String> includedModules;

  	// true, if analysis completed successfully
  	boolean success = true;

  	final List<String> warnings = new ArrayList<>();
  	
  	public ModuleDependencies(final String runModule) {
  		this.runModule = runModule;
  		this.urls = new ArrayList<URI>();
  		this.includedJars = new HashMap<>();
  		this.includedModules = new ArrayList<>();
  	}

  	public ModuleDependencies(final String runModule, final URI[] urls) {
  		this(runModule);
  		
  		if (urls != null) {
  			for(URI uri: urls) {
  				this.urls.add(uri);
  			}
  		}
  	}
  	
  	public final boolean failed() {
  		return !success;
  	}
  	
  	public final boolean success() {
  		return success;
  	}
  	
  	public final ModuleDependencies failed(final String message) {
    	log.error(message);
  		this.warnings.add(message);
  		this.success = false;
  		return this;
  	}

  	public final URI[] urisToArray() {
  		return urls.toArray(new URI[urls.size()]);
  	}
  }
}
