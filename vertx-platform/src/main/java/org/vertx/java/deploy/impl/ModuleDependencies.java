package org.vertx.java.deploy.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A utility class to collect module dependency data while walking the tree
 * 
 * @author Juergen Donnerstag
 */
public class ModuleDependencies {
	
	// The root module where the analysis started
	String runModule;
	
	// The module's classpath: all required module directories and all jars
	final List<URI> urls;
	
	// List of all Jars contributed by all modules from their respective 
	// 'lib' directory. jar name -> module(s)
	final Map<String, String> includedJars;

	// List of all required modules (includes)
	final List<String> includedModules;

	// true, if analysis completed successfully
	private boolean success = true;

	final List<String> warnings = new ArrayList<>();
	
	ModuleDependencies(final String runModule) {
		this.runModule = runModule;
		this.urls = new ArrayList<URI>();
		this.includedJars = new HashMap<>();
		this.includedModules = new ArrayList<>();
	}

	ModuleDependencies(final String runModule, final URI[] urls) {
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
  	ModuleManager.log.error(message);
		this.warnings.add(message);
		this.success = false;
		return this;
	}

	public final URI[] urisToArray() {
		return urls.toArray(new URI[urls.size()]);
	}
}