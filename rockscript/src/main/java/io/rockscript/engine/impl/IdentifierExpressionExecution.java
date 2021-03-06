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

import io.rockscript.engine.EngineException;
import io.rockscript.util.Maps;

import java.util.Map;

import static io.rockscript.util.Maps.entry;
import static io.rockscript.util.Maps.hashMap;

public class IdentifierExpressionExecution extends Execution<IdentifierExpression> implements Assignable {

  static final Map<String,Object> CONSTANTS = hashMap(
    entry("encodeURI", EncodeUriFunction.INSTANCE),
    entry("undefined", Literal.UNDEFINED));

  Variable variable = null;

  public IdentifierExpressionExecution(IdentifierExpression element, Execution parent) {
    super(parent.createInternalExecutionId(), element, parent);
  }

  @Override
  public void start() {
    Object identifierValue = getIdentifierValue();
    setResult(identifierValue);
    end();
  }

  public Object getIdentifierValue() {
    String identifier = element.getIdentifier();

    this.variable = parent.getVariable(identifier);
    if (variable!=null) {
      return variable.getValue();
    }

    if (CONSTANTS.containsKey(identifier)) {
      return CONSTANTS.get(identifier);
    }

    throw new EngineException("ReferenceError: "+identifier+" is not defined", this);
  }

  @Override
  public void assign(Object value) {
    if (variable!=null) {
      variable.setValue(value);
    }
  }
}
