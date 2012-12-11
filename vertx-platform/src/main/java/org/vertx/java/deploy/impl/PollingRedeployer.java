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

import org.vertx.java.core.Handler;
import org.vertx.java.core.file.impl.FolderWatcher.WatchDirContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is a Redeployer based on old fashioned file tree scanning for OS'es where Java's NIO 
 * implementation is perceived flaky.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PollingRedeployer extends Redeployer {

  private static final Logger log = LoggerFactory.getLogger(PollingRedeployer.class);

  // Periodic timer: process the file system events
  private static final long CHECK_PERIOD = 2_000;

  // Periodic timer: every CHECK_PERIOD
  private final long timerID;

  // The directories we are monitoring
  private Map<Path, Boolean> dirs = new ConcurrentHashMap<>();

  // time when the last check completed
  private long lastCheck = System.currentTimeMillis();
  
  /**
   * Constructor
   * 
   * @param vertx
   * @param modRoot
   * @param reloader
   */
  public PollingRedeployer(final VertxInternal vertx, final File modRoot, final ModuleReloader reloader) {
  	super(vertx, modRoot, reloader);
    
    // Start a new periodic timer to regularly process the watcher events
    timerID = vertx.setPeriodic(CHECK_PERIOD, new Handler<Long>() {
      public void handle(Long id) {
      	// Timer shutdown is asynchronous and might not have completed yet.
      	if (closed()) {
      		vertx.cancelTimer(timerID);
      		return;
      	}
      	
        checkContext();
        
        try {
        	onTimerEvent();
        } catch (Exception e) {
          log.error("Error while checking file system events", e);
        }
      }
    });
  }
  
  /**
   * Process module registration and unregistration, and any file system events.
   * {@link #onGraceEvent(Path)} can be subclassed to change how file system
   * changes get handled.
   */
  protected void onTimerEvent() {
    processUndeployments();
    processDeployments();

    scanDirectories();
  }

  /**
   * Scan the registered directories for modified files or directories
   */
  private void scanDirectories() {
  	// Remember when we last started with a scan
  	long newLastCheck = System.currentTimeMillis();

  	// For all registered directories
  	for(final Map.Entry<Path, Boolean> e: this.dirs.entrySet()) {
      try {
      	// The logic is simple: if a file tree has not been changed for 
      	// one cycle after it has been changed, than trigger an event
				Boolean oldValue = e.getValue();
				
				// Reset to determine changes
		    e.setValue(false);
      	Files.walkFileTree(e.getKey(), new SimpleFileVisitor<Path>() {
      		
      		@Override
      		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
      			log.error("dir: " + dir.toString() + " " + attrs.creationTime() + " " + attrs.lastModifiedTime());
				  	return check(dir, attrs);
      		}
      		
				  @Override
				  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      			log.error("file: " + file.toString() + " " + attrs.creationTime() + " " + attrs.lastModifiedTime());
				  	return check(file, attrs);
				  }
				  
				  private FileVisitResult check(Path file, BasicFileAttributes attrs) {
				    long diff = lastCheck - Math.max(attrs.lastModifiedTime().toMillis(), attrs.creationTime().toMillis());
				    // Has the file changed since our last visit?
				    if (diff < 0) {
					    // The file was modified
					    e.setValue(true);
					    return FileVisitResult.TERMINATE;
				    }
			    	return FileVisitResult.CONTINUE;
				  }
				});

      	// If modified previously, but not modified since then, than ...
      	log.error("old: " + oldValue + "; new: " + e.getValue());
				if ((oldValue == true) && (e.getValue() == false)) {
					onGraceEvent(e.getKey());
				}
			} catch (IOException ex) {
				log.error(ex);
			}
  	}
  	
  	// We have a slide overlap but that is ok. We definitely don't want a gap.
  	this.lastCheck = newLastCheck;
  }

  /**
   * Shutdown the service. Free up all resources.
   */
  @Override
  public void close() {
  	super.close();
    vertx().cancelTimer(timerID);
  }

  /**
   * register a deployment
   */
  @Override
  protected void registerDeployment(final Path modDir) {
  	if (this.dirs.containsKey(modDir) == false) {
  		this.dirs.put(modDir, false);
  	}
  }

  /**
   * Unregister a deployment
   */
  protected void unregisterDeployment(final Path modDir) {
		this.dirs.remove(modDir);
  }
}