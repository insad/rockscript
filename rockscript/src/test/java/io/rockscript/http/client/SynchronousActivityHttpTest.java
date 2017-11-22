/*
 * Copyright (c) 2017 RockScript.io.
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.rockscript.http.client;

import io.rockscript.activity.ActivityInput;
import io.rockscript.activity.ActivityOutput;
import io.rockscript.api.model.ScriptVersion;
import io.rockscript.api.model.ScriptExecution;
import io.rockscript.test.HttpTest;
import io.rockscript.test.HttpTestServer;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.rockscript.util.Maps.entry;
import static io.rockscript.util.Maps.hashMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SynchronousActivityHttpTest extends HttpTest {

  protected static Logger log = LoggerFactory.getLogger(SynchronousActivityHttpTest.class);

  List<ActivityInput> activityInputs = new ArrayList<>();

  @Override
  protected void configure(HttpTestServer httpTestServer) {
    httpTestServer
      .post("/approve", (request,response)-> {
        ActivityInput activityInput = gson.fromJson(request.body(), ActivityInput.class);
        activityInputs.add(activityInput);
        ActivityOutput activityOutput = ActivityOutput.endActivity(
          hashMap(
            entry("country", "Belgium"),
            entry("currency", "EUR")
          )
        );
        response
            .status(200)
            .headerContentTypeApplicationJson()
            .body(gson.toJson(activityOutput))
            .send();
      });
  }

  @Test
  public void testHttpActivity() {
    ScriptVersion scriptVersion = deployScript(
        "var approvals = system.import('localhost:"+PORT+"'); \n" +
            "var currency = approvals.approve('oo',7).currency; ");

    ScriptExecution scriptExecution = startScriptExecution(scriptVersion);

    ActivityInput activityInput = activityInputs.get(0);
    assertEquals("EUR", scriptExecution.getVariable("currency"));
    assertTrue(scriptExecution.isEnded());
  }
}