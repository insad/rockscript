/*
 * Copyright ©2017, RockScript.io. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rockscript.engine.impl;

import io.rockscript.Engine;
import io.rockscript.api.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventDispatcher {

  static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

  Engine engine;

  public EventDispatcher(Engine engine) {
    this.engine = engine;
  }

  public void dispatch(Event event) {
    // Could eventually be dispatched to 3 distinct queues
    if (event instanceof ExecutionEvent) {
      engine.getScriptExecutionStore().handle(event);
    } else if (event instanceof JobEvent){
      engine.getJobStore().handle((JobEvent)event);
      engine.getJobExecutor().handle((JobEvent)event);
    } else if (event instanceof ScriptEvent){
      engine.getScriptStore().handle((ScriptEvent)event);
    }
  }
}
