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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.lang.Args;

/**
 * Vertx modules are essentially a deployment unit. There are installed in their
 * own subdirectory. This little helper provides easy access to typical module data,
 * such as the config (mod.json), lib files, its directory, etc.
 * 
 * @author Juergen Donnerstag
 */
public class VertxModule {

	private static final Logger log = LoggerFactory.getLogger(VertxModule.class);

	public static final ModuleConfig NULL_CONFIG = new ModuleConfig();
	
	public static final boolean exists(final File modDir, final String modName) {
		return getModDir(modDir, modName).canRead();
	}

	public static final File getModDir(final File modDir, final String modName) {
		Args.notNull(modDir, "modDir");
		Args.notNull(modName, "modName");
		
		return new File(modDir, modName);
	}

  private final ModuleManager moduleManager;
	private final String modName;
	private final File modDir;
	private ModuleConfig config;
	private ModuleDependencies dependencies;
	
	/**
	 * Constructor
	 */
	public VertxModule(final ModuleManager moduleManager, final String modName) {
		this.moduleManager = Args.notNull(moduleManager, "moduleManager");
		this.modName = modName;
		if (modName != null) {
			this.modDir = getModDir(moduleManager.modRoot(), modName);
			this.config = NULL_CONFIG;
			this.dependencies = null;
		} else {
			this.modDir = null;
			this.config = new ModuleConfig();
			this.dependencies = new ModuleDependencies(this.modName);
		}
	}

	public final VertxModule config(final ModuleConfig config) {
		this.config = config;
		if (modName == null) {
			this.dependencies = new ModuleDependencies(this.modName);
		} else {
			this.dependencies = null;
		}
		return this;
	}

	/**
	 * (Sync) Gets the config for the module
	 * 
	 * @param modName
	 * @param throwException
	 * @return
	 */
	public final VertxModule loadConfig(final boolean throwException) {
    try {
	    config = new ModuleConfig(modDir);
    } catch (Exception ex) {
    	if (throwException) {
    		throw new RuntimeException(ex);
    	} else {
    		log.error("Failed to load config for module '" + modName + "' from " + modDir.getAbsolutePath());
    	}
    } finally {
    	this.dependencies = null;
    }
    return null;
	}

	public final ModuleConfig config() {
		if (config == NULL_CONFIG) {
			loadConfig(false);
		}
		return config;
	}

	public final String configFile() {
		return config.configFile2();
	}

	public final File modDir() {
		return modDir;
	}

	public final String name() {
		return modName;
	}

	public boolean exists() {
		return modDir.canRead() && config() != NULL_CONFIG;
	}

	/**
	 * Install the module and all its dependencies
	 * @return
	 */
	public final ModuleDependencies install() {
		this.dependencies = moduleManager.install(modName);
		return this.dependencies;
	}

	private void check() {
		if (this.dependencies == null) {
			install();
		}
	}
	
	/**
	 * All modules this module is dependent on
	 * 
	 * @return
	 */
	public final List<String> requiredModules() {
		check();
		return this.dependencies.includedModules;
	}

	/**
	 * The classpath for this module and all its required modules
	 * 
	 * @return
	 */
	public final List<URI> classPath() {
		check();
		return this.dependencies.urls;
	}
	
	public final void classPath(final List<URI> cp, boolean clear) {
		check();
		if (clear) {
			this.dependencies.urls.clear();
		}
		this.dependencies.urls.addAll(cp);
	}
	
	public final List<File> files(final String subdir) {
		File libDir = new File(modDir(), subdir);
		if (libDir.exists()) {
			File[] files = libDir.listFiles();
			return Arrays.asList(files);
			
		}
		return Collections.emptyList();
	}
	
	@Override
	public String toString() {
		return "name: " + modName + "; dir: " + modDir;
	}
}
