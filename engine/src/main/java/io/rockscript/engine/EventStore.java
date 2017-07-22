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

package io.rockscript.engine;

import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.rockscript.ServiceLocator;
import io.rockscript.action.Action;
import io.rockscript.gson.PolymorphicTypeAdapterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventStore implements EventListener {

  static Logger log = LoggerFactory.getLogger(EventStore.class);

  static Gson gson = new GsonBuilder()
    .registerTypeAdapterFactory(new PolymorphicTypeAdapterFactory()
      .typeName(new TypeToken<ActionStartedEventJson>(){},            "actionStarted")
      .typeName(new TypeToken<ActionWaitingEventJson>(){},            "actionWaiting")
      .typeName(new TypeToken<ActionEndedEventJson>(){},              "actionEnd")
      .typeName(new TypeToken<ScriptStartedEventJson>(){},            "scriptStarted")
      .typeName(new TypeToken<ScriptEndedEventJson>(){},              "scriptEnded")
      .typeName(new TypeToken<VariableCreatedEventJson>(){},          "variableCreated")
      .typeName(new TypeToken<ObjectImportedEventJson>(){},           "objectImported")
      .typeName(new TypeToken<IdentifierResolvedEventJson>(){},       "identifierResolved")
      .typeName(new TypeToken<PropertyDereferencedEventJson>(){},     "propertyDereferenced")
    )

    // .setPrettyPrinting()
    .create();

  ServiceLocator serviceLocator;
  List<EventJson> events = new ArrayList<>();


  public EventStore(ServiceLocator serviceLocator) {
    this.serviceLocator = serviceLocator;
  }

  @Override
  public void handle(Event event) {
    EventJson gsonnable = event.toJson();
    events.add(gsonnable);
    String jsonString = eventJsonToJsonString(gsonnable);
    log.debug(jsonString);
  }

  public List<EventJson> findEventsByScriptExecutionId(String scriptExecutionId) {
    return events.stream()
      .filter(event->scriptExecutionId.equals(event.getScriptExecutionId()))
      .collect(Collectors.toList());
  }

  public ScriptExecution loadScriptExecution(String scriptExecutionId) {
    List<EventJson> eventJsons = findEventsByScriptExecutionId(scriptExecutionId);

    return recreateScriptExecution(eventJsons, scriptExecutionId);
  }

  private class LoadingWrapperEventListener implements EventListener {
    ScriptExecution scriptExecution;
    EventListener originalEventListener;
    int previouslyExecutedEvents;
    int replayedEvents;

    public LoadingWrapperEventListener(ScriptExecution scriptExecution, EventListener originalEventListener, List<EventJson> eventJsons) {
      this.scriptExecution = scriptExecution;
      this.originalEventListener = originalEventListener;
      this.previouslyExecutedEvents = eventJsons.size();
      this.replayedEvents = 0;
    }

    @Override
    public void handle(Event event) {
      updateExecutionModeAndCount(event);
      if (scriptExecution.getExecutionMode()==ExecutionMode.EXECUTING) {
        originalEventListener.handle(event);
      } else {
        log.debug("Swallowing ("+scriptExecution.getExecutionMode()+"): "+ EventStore.this.eventToJsonString(event));
      }
    }

    private void updateExecutionModeAndCount(Event event) {
      if (replayedEvents==0) {
        if (previouslyExecutedEvents==1) {
          scriptExecution.setExecutionMode(ExecutionMode.RECOVERING);
        } else {
          scriptExecution.setExecutionMode(ExecutionMode.REBUILDING);
        }
      } else if (scriptExecution.getExecutionMode()==ExecutionMode.REBUILDING) {
        if (replayedEvents==previouslyExecutedEvents-1) {
          scriptExecution.setExecutionMode(ExecutionMode.RECOVERING);
        }
      } else if (scriptExecution.getExecutionMode()==ExecutionMode.RECOVERING) {
        scriptExecution.setExecutionMode(ExecutionMode.EXECUTING);
      }
      replayedEvents++;
    }

    public void eventExecuting(Event event) {
      updateExecutionModeAndCount(event);
    }
  }

  private ScriptExecution recreateScriptExecution(List<EventJson> eventJsons, String scriptExecutionId) {
    String scriptId = findScriptId(eventJsons);
    ScriptException.throwIfNull(scriptId, "Script id not found for scriptExecutionId: %s", scriptExecutionId);
    Script script = serviceLocator
      .getScriptStore()
      .loadScript(scriptId);
    ScriptException.throwIfNull(scriptId, "Script not found for scriptId: %s", scriptId);

    ScriptExecution scriptExecution = new ScriptExecution(scriptExecutionId, serviceLocator, script);

    EventListener originalEventListener = scriptExecution.getEventListener();
    LoadingWrapperEventListener loadingWrapperEventListener = new LoadingWrapperEventListener(scriptExecution, originalEventListener, eventJsons);
    scriptExecution.setEventListener(loadingWrapperEventListener);

    eventJsons = eventJsons.stream()
      .filter(this::isExecutable)
      .collect(Collectors.toList());

    for (EventJson eventJson: eventJsons) {
      Execution execution = scriptExecution.findExecutionRecursive(eventJson.executionId);
      ExecutableEvent event = (ExecutableEvent) eventJson.toEvent(execution);

      // The events that are being executed are not dispatched and hence the
      // LoadingWrapperEventListener doesn't receive them, yet it also must keep
      // track of those to get the counting right
      loadingWrapperEventListener.eventExecuting(event);
      log.debug("Executing ("+scriptExecution.getExecutionMode()+"): "+eventJsonToJsonString(eventJson));
      event.execute();
    }

    scriptExecution.setExecutionMode(ExecutionMode.EXECUTING);

    return scriptExecution;
  }

  private String findScriptId(List<EventJson> scriptExecutionEventJsons) {
    return scriptExecutionEventJsons.stream()
      .map(eventJson->eventJson.getScriptId())
      .filter(Objects::nonNull)
      .findFirst()
      .get();
  }

  public List<ScriptExecution> recoverCrashedScriptExecutions() {
    List<ScriptExecution> scriptExecutions = new ArrayList<>();
    Map<String,List<EventJson>> groupedEvents = findCrashedScriptExecutionEvents();
    for (String scriptExecutionId: groupedEvents.keySet()) {
      List<EventJson> scriptExecutionEvents = groupedEvents.get(scriptExecutionId);
      ScriptExecution scriptExecution = recreateScriptExecution(scriptExecutionEvents, scriptExecutionId);
      scriptExecutions.add(scriptExecution);
    }
    return scriptExecutions;
  }

  private boolean lastEventIsExecutable(List<EventJson> scriptExecutionEvents) {
    EventJson lastEvent = scriptExecutionEvents.get(scriptExecutionEvents.size()-1);
    return RecoverableEventJson.class.isAssignableFrom(lastEvent.getClass());
  }

  /** @return a list of events grouped by script execution. */
  public Map<String,List<EventJson>> findCrashedScriptExecutionEvents() {
    Map<String,List<EventJson>> groupedEvents = new HashMap<>();
    for (EventJson event: events) {
      String scriptExecutionId = event.getScriptExecutionId();
      if (event instanceof ScriptEndedEventJson) {
        groupedEvents.remove(scriptExecutionId);
      } else {
        List<EventJson> scriptExecutionEvents = groupedEvents.get(scriptExecutionId);
        if (scriptExecutionEvents==null) {
          scriptExecutionEvents = new ArrayList<>();
          groupedEvents.put(scriptExecutionId, scriptExecutionEvents);
        }
        scriptExecutionEvents.add(event);
      }
    }
    for (String scriptExecutionId: new ArrayList<>(groupedEvents.keySet())) {
      List<EventJson> scriptExecutionEvents = groupedEvents.get(scriptExecutionId);
      EventJson lastEventJson = scriptExecutionEvents.get(scriptExecutionEvents.size()-1);
      if (isUnlocking(lastEventJson)) {
        groupedEvents.remove(scriptExecutionId);
      }
    }
    return groupedEvents;
  }

  private boolean isExecutable(EventJson eventJson) {
    return eventJson!=null
      && ( eventJson instanceof ScriptStartedEventJson
           || eventJson instanceof ActionEndedEventJson);
  }

  private boolean isUnlocking(EventJson lastEventJson) {
    return lastEventJson!=null
      && ( lastEventJson instanceof ActionWaitingEventJson
           || lastEventJson instanceof ScriptEndedEventJson);
  }

  public String eventToJsonString(Event event) {
    return eventJsonToJsonString(event.toJson());
  }

  public String eventJsonToJsonString(EventJson eventJson) {
    return eventJson!=null ? gson.toJson(eventJson) : "null";
  }

  public Object valueToJson(Object value) {
    if (value==null) {
      return "null";
    }
    if (value instanceof Action) {
      return value.toString();
    }
    if (value instanceof Map) {
      return valueMapToJson((Map)value);
    }
    if (value instanceof JsonObject) {
      return valueMapToJson(((JsonObject)value).properties);
    }
    return value;
  }

  private Map<String,Object> valueMapToJson(Map<String,Object> map) {
    Map convertedMap = new LinkedHashMap();
    for (String key: map.keySet()) {
      Object convertedValue = valueToJson(map.get(key));
      convertedMap.put(key, convertedValue);
    }
    return convertedMap;
  }
}