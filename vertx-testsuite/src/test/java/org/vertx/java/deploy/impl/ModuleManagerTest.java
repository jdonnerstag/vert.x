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

import static org.junit.Assert.*;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.vertx.java.core.impl.DefaultVertx;

/**
 * 
 * @author Juergen Donnerstag
 */
public class ModuleManagerTest {

	public static DefaultVertx vertx;
	public VerticleManager verticleManager;
	public ModuleManager moduleManager;

	@Rule
	public TemporaryFolder modDir = new TemporaryFolder();	
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		vertx = new DefaultVertx();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		vertx.stop();
		vertx = null;
	}

	@Before
	public void setUp() throws Exception {
		verticleManager = new VerticleManager(vertx, modDir.getRoot());
		moduleManager = verticleManager.moduleManager();

		moduleManager.moduleRepositories().clear();
    moduleManager.moduleRepositories().add(
    		new LocalModuleRepository(vertx, new File("src/test/mod-test")));
    
    assertEquals(1, moduleManager.moduleRepositories().size());
	}

	@After
	public void tearDown() throws Exception {
	}

  @Test
  public void testSimple() throws Exception {
    String modName = "testmod1-1";
    moduleManager.installOne(modName, 30, TimeUnit.SECONDS);
    assertTrue(modDir.newFile(modName).isDirectory());
  }

  @Test
  public void testInstallPreserveCwd() throws Exception {
    String modName = "testmod8-1";
    moduleManager.installOne(modName, 30, TimeUnit.SECONDS);
    assertTrue(modDir.newFile(modName).isDirectory());
  }

  @Test
  public void testInstallAllPreserveCwd() throws Exception {
    String modName = "testmod8-1";
    moduleManager.install(modName);
    assertTrue(modDir.newFile(modName).isDirectory());
    assertTrue(modDir.newFile("testmod8-2").isDirectory());
    assertTrue(modDir.newFile("testmod8-3").isDirectory());
  }
}
