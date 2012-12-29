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

import java.util.Collection;
import java.util.Collections;
import java.util.Stack;

import org.vertx.java.core.utils.lang.Args;

/**
 * A little utility to walk a module's dependencies
 * 
 * @author Juergen Donnerstag
 *
 * @param <T> The result type
 */
public class ModuleWalker<T> {
	
	private final ModuleManager moduleManager;

	// Parent are pushed onto the stack which is made available
	private final Stack<ModuleConfig> stack = new Stack<>();
	
	private ModuleVisitor<T> visitor;

	// Temporary store for the result value
	private T result;
		
	/**
	 * Constructor
	 * 
	 * @param moduleManager
	 */
	public ModuleWalker(final ModuleManager moduleManager) {
		this.moduleManager = moduleManager;
	}

	/**
	 * Get the mod.json config for module modName
	 * 
	 * @param modName
	 * @return
	 */
	private ModuleConfig config(String modName) {
		return moduleManager.modConfig(modName, false);
	}

	/**
	 * @return The 'result' after visiting the modules
	 */
	public final T result() {
		return result;
	}
	
	/**
	 * Set the result value
	 * 
	 * @param result
	 */
	public final void result(final T result) {
		this.result = result;
	}

	/**
	 * The (unmodifiable) module stack: parent of the current module
	 * 
	 * @return
	 */
	public final Collection<ModuleConfig> stack() {
		return Collections.unmodifiableCollection(this.stack);
	}

	/**
	 * Same as {@link #visit(String, ModuleVisitor)}, except that all exceptions are 
	 * converted into RuntimeException.
	 * 
	 * @param modName
	 * @param visitor
	 * @return
	 */
	public final T visit2(String modName, ModuleVisitor<T> visitor) {
		try {
			return visit(modName, visitor);
		} catch (Exception ex) {
			throw new RuntimeException(ex.getMessage());
		}
	}

	/**
	 * Walk the module tree
	 * 
	 * @param modName The module to start with
	 * @param visitor User code invoked upon a visit
	 * @return See {@link #result()} and {@link #result(Object)}
	 * @throws Exception
	 */
	public final T visit(String modName, ModuleVisitor<T> visitor) throws Exception {
		Args.notNull(modName, "modName");
		this.visitor = Args.notNull(visitor, "visitor");

		ModuleConfig config = config(modName);
		if (config == null) {
			if (visitor.onMissingModule(modName, this)) {
  			config = config(modName);
			}
		}
		
		this.stack.push(config);
		visitModule(modName, config);
		this.stack.pop();

		return result;
	}

	/**
	 * Visit all includes of a module
	 */
	private ModuleVisitResult visitIncludes(final ModuleConfig cfg) throws Exception {
		this.stack.push(cfg);
		ModuleVisitResult res = ModuleVisitResult.CONTINUE;
		try {
  		for(String modName: cfg.includes()) {
  			ModuleConfig config = config(modName);
  			if (config == null) {
  				if (visitor.onMissingModule(modName, this)) {
  	  			config = config(modName);
  				}
  			}

  			res = visitModule(modName, config);
  			if (res == null) {
  				res = ModuleVisitResult.CONTINUE;
  			}
  			if (res == ModuleVisitResult.TERMINATE) {
  				return res;
  			} else if (res == ModuleVisitResult.SKIP_SIBLINGS) {
  				return ModuleVisitResult.CONTINUE;
  			}
  		}
		} finally {
			this.stack.pop();
		}
		
		return res;
	}

	/**
	 * Invoke the client visitor on a specific module and continue 
	 * depending on the return value
	 */
	private ModuleVisitResult visitModule(final String modName, ModuleConfig config) throws Exception {
		ModuleVisitResult res = ModuleVisitResult.TERMINATE;
		try {
			res = visitor.visit(modName, config, this);
		} catch (Exception ex) {
			res = visitor.onException(modName, config, ex, this);
		}
		if (res == ModuleVisitResult.TERMINATE) {
			return res;
		} else if (res == ModuleVisitResult.SKIP_SIBLINGS) {
			return res;
		} else if (res == ModuleVisitResult.SKIP_SUBTREE) {
			return res;
		} 
		
		res = visitIncludes(config);
		if (res != null && res == ModuleVisitResult.TERMINATE) {
			return res;
		} 
		return ModuleVisitResult.CONTINUE;
	}

	/**
	 * Must be provided (and extended) by the user
	 * 
	 * @param <T>
	 */
  public abstract static class ModuleVisitor<T> {
  
  	/**
  	 * Invoked for each module found.
  	 * 
  	 * @param modName Module name
  	 * @param config null, if the module is not installed
  	 */
  	protected abstract ModuleVisitResult visit(String modName, ModuleConfig config, 
  			ModuleWalker<T> walker);

  	/**
  	 * Upon an exception. By default re-throws the exception.
  	 */
  	protected ModuleVisitResult onException(String modName, ModuleConfig config, 
  			Exception ex, ModuleWalker<T> walker) throws Exception {
			
  		throw ex;
  	}

  	/**
  	 * Upon an exception. By default re-throws the exception.
  	 */
  	protected boolean onMissingModule(String modName, 
  			ModuleWalker<T> walker) throws Exception {
  		return false;
  	}
  }

  /**
   * 
   */
  public enum ModuleVisitResult {
    CONTINUE,
    TERMINATE,
    SKIP_SUBTREE,
    SKIP_SIBLINGS;
  }
}
