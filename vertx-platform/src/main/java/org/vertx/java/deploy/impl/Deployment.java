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

import org.vertx.java.core.json.JsonObject;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Deployment {
	// Every deployment has a unique name
  public final String name;
  
  // One module can be associated with a deployment
  // (A module may have dependencies on other modules)
  // TODO replace with VertxModule
  public final String modName;

  // Number of instances of the same Verticle that are started 
  // on (hopefully) different threads
  public final int instances;
  
  // Module config
  // TODO replace with VertxModule
  public final JsonObject config;

  // Module classpath
  // TODO replace with VertxModule
  public final URI[] urls;
  
  // Mpdule directory
  // TODO replace with VertxModule
  public final File modDir;

  // One holder for each instance
  public final List<VerticleHolder> verticles = new ArrayList<>();

  // Deployment tree
  public final List<String> childDeployments = new ArrayList<>();
  public final String parentDeploymentName;
  
  // ??
  public final boolean autoRedeploy;

  /**
   * Constructor
   */
  public Deployment(final String name, final String modName, final int instances, 
  		final JsonObject config, final URI[] urls, final File modDir, 
  		final String parentDeploymentName, final boolean autoRedeploy) {
    this.name = (name != null ? name : createName());
    this.modName = modName;
    this.instances = instances;
    this.config = (config == null ? new JsonObject() : config.copy());
    this.urls = urls;
    this.modDir = modDir;
    this.parentDeploymentName = parentDeploymentName;
    this.autoRedeploy = autoRedeploy;
  }

  /**
   * Extension point: 
   * 
   * @return
   */
  protected String createName() {
    return "deployment-" + UUID.randomUUID().toString();
  }
  
  public final boolean hasParent() {
  	return parentDeploymentName != null;
  }
}