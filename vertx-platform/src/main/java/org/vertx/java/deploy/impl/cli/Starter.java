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

package org.vertx.java.deploy.impl.cli;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.impl.CommandLineArgs;
import org.vertx.java.deploy.impl.ModuleConfig;
import org.vertx.java.deploy.impl.VerticleManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 * Command line starter
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class Starter {

  private static final Logger log = LoggerFactory.getLogger(Starter.class);

  private static final String CP_SEPARATOR = System.getProperty("path.separator");

  // TODO shouldn't that come from gradle? E.g. some file content??
  private static final String VERSION = "vert.x-1.3.0.final";

  public static void main(String[] args) {
  	try {
	    if (new Starter().run(args) == false) {
	      displaySyntax();
	    }
  	} catch (Throwable ex) {
  		log.error(ex);
  	}
  }

  /**
   * Constructor
   */
  public Starter() {
  }
  
  /**
   * Extension Point: subclass to handle additional parameter
   * 
   * @param sargs
   * @return
   * @throws Exception 
   */
  protected boolean run(final String[] sargs) throws Exception {
    if (sargs.length < 1) {
      return false;
    } 

    String command = sargs[0].toLowerCase();
    if ("version".equals(command)) {
      System.out.println(VERSION);
      return true;
    } 
    
    if (sargs.length < 2) {
      return false;
    } 
    CommandLineArgs args = new CommandLineArgs(sargs);
    String operand = sargs[1];
    switch (command) {
      case "run":
        runVerticle(false, operand, args);
        return true;
      case "runmod":
        runVerticle(true, operand, args);
        return true;
      case "install":
        installModule(operand, args);
        return true;
      case "uninstall":
        uninstallModule(operand);
        return true;
    }
    return false;
  }

  /**
   * Install a module from the repository
   */
  protected final void installModule(final String modName, final CommandLineArgs args) {
    String repo = args.get("-repo");

    try (ExtendedDefaultVertx vertx = new ExtendedDefaultVertx()) {
			vertx.moduleRepository(repo);
	    vertx.moduleManager(null).install(modName);
    }
  }

  private void uninstallModule(String modName) {
    try (ExtendedDefaultVertx vertx = new ExtendedDefaultVertx()) {
	    vertx.moduleManager(null).uninstall(modName);
    }
  }

  protected final void runVerticle(final boolean module, final String main, 
  		final CommandLineArgs args) throws Exception {

  	// TODO get cluster and repo defaults from VertxConfig
    int clusterPort = 0;
    String clusterHost = null;
    boolean clustered = args.present("-cluster");
    if (clustered) {
      log.info("Starting clustering...");
      clusterPort = args.getInt("-cluster-port", 25500, 25500);
      clusterHost = args.get("-cluster-host");
      if (clusterHost == null) {
        clusterHost = getDefaultAddress();
        if (clusterHost == null) {
          log.error("Unable to find a default network interface for clustering. Please specify one using -cluster-host");
          return;
        } else {
          log.info("No cluster-host specified so using address " + clusterHost);
        }
      }
    }
	  
	  try (ExtendedDefaultVertx vertx = new ExtendedDefaultVertx(clusterPort, clusterHost)) {
	    
	    String repo = args.get("-repo");
			vertx.moduleRepository(repo);
	
	    boolean worker = args.present("-worker");
	    
	    String cp = args.get("-cp", ".");
	    URI[] urls = classpath(cp);
	
	    int instances = args.getInt("-instances", 1, -1);
	    if (instances < 1) {
        log.error("Invalid number of instances");
        displaySyntax();
        return;
	    }
	
	    String configFile = args.get("-conf");
	    JsonObject conf = null;
	    if (configFile != null) {
	    	conf = new ModuleConfig(new File(configFile)).json();
	    }
	
	    final VerticleManager verticleManager = vertx.verticleManager();
	    Handler<String> doneHandler = new Handler<String>() {
	      public void handle(String id) {
	        if (id == null) {
	          // Failed to deploy
	          verticleManager.unblock();
	        }
	      }
	    };
	    
	    if (module) {
	      verticleManager.deployMod(main, conf, instances, null, doneHandler);
	    } else {
	      String includes = args.get("-includes");
	      List<URI> uriList = Arrays.asList(urls);
	      verticleManager.deployVerticle(worker, main, conf, uriList, instances, null, includes, doneHandler);
	    }
	
	    verticleManager.block();
  	}
  }

  private URI[] classpath(String cp) {
    String[] parts;
    if (cp.contains(CP_SEPARATOR)) {
      parts = cp.split(CP_SEPARATOR);
    } else {
      parts = new String[] { cp };
    }
    int index = 0;
    final URI[] urls = new URI[parts.length];
    for (String part: parts) {
      URI url = new File(part).toURI();
      urls[index++] = url;
    }
    return urls;
  }
  
  /**
   * Get default interface to use since the user hasn't specified one
   */
  private String getDefaultAddress() {
    Enumeration<NetworkInterface> nets;
    try {
      nets = NetworkInterface.getNetworkInterfaces();
    } catch (SocketException e) {
      return null;
    }
    
    while (nets.hasMoreElements()) {
    	NetworkInterface netinf = nets.nextElement();
      Enumeration<InetAddress> addresses = netinf.getInetAddresses();

      while (addresses.hasMoreElements()) {
        InetAddress address = addresses.nextElement();
        if (!address.isAnyLocalAddress() && !address.isMulticastAddress()
            && !(address instanceof Inet6Address)) {
          return address.getHostAddress();
        }
      }
    }
    return null;
  }

  /**
   * Prints the help text
   */
  private static void displaySyntax() {
  	try (InputStream in = Starter.class.getResourceAsStream("help.txt");
  			InputStreamReader rin = new InputStreamReader(in);
  			BufferedReader bin = new BufferedReader(rin)) {
  		String line;
  		while (null != (line = bin.readLine())) {
  			System.out.println(line);
  		}
  	} catch (IOException ex) {
  		log.error("Help text not found !?!");
  	}
  }
}
