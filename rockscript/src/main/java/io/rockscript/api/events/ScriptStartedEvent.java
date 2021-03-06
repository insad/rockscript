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
package io.rockscript.api.events;

import io.rockscript.api.model.ScriptVersion;
import io.rockscript.engine.impl.EngineScriptExecution;

public class ScriptStartedEvent extends ExecutableEvent<EngineScriptExecution> {

  String scriptId;
  String scriptVersionId;
  String scriptName;
  Integer scriptVersion;
  Object input;

  /** constructor for gson serialization */
  ScriptStartedEvent() {
  }

  @Override
  public void execute(EngineScriptExecution execution) {
    execution.setInput(input);
    execution.setStart(time);
    execution.startExecute();
  }

  public ScriptStartedEvent(EngineScriptExecution scriptExecution, Object input) {
    super(scriptExecution);

    ScriptVersion scriptVersion = scriptExecution
        .getEngineScript()
        .getScriptVersion();

    this.scriptVersionId = scriptVersion.getId();
    this.scriptId = scriptVersion.getScriptId();
    this.scriptName = scriptVersion.getScriptName();
    this.scriptVersion = scriptVersion.getVersion();
    this.input = input;
  }

  public String getScriptVersionId() {
    return scriptVersionId;
  }

  public String getScriptExecutionId() {
    return scriptExecutionId;
  }

  public Object getInput() {
    return input;
  }

  @Override
  public boolean isReplay() {
    return true;
  }

  @Override
  public boolean isRecoverable() {
    return true;
  }

  @Override
  public String toString() {
    return "[" + scriptExecutionId + "] " +
        "Started script [scriptVersionId=" +
           scriptVersionId + ",scriptName=" +
        scriptName + "]" +
        (input!=null ? " with input "+input.toString() : " without input");
  }
}
