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
package io.rockscript.service.http;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.rockscript.Engine;
import io.rockscript.service.AbstractServiceFunction;
import io.rockscript.service.ServiceFunctionInput;
import io.rockscript.service.ServiceFunctionOutput;
import io.rockscript.http.client.ClientRequest;
import io.rockscript.util.Lists;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class HttpServiceFunction extends AbstractServiceFunction {

  HttpService httpService;
  String method;

  public HttpServiceFunction(HttpService httpService, String method) {
    super(method.toLowerCase());
    this.httpService = httpService;
    this.method = method;
  }

  /** one json object is expected as argument */
  @Override
  public List<String> getArgNames() {
    return null;
  }

  @Override
  public ServiceFunctionOutput invoke(ServiceFunctionInput input) {
    // Parse the ClientRequest object
    Object requestObject = input.getArg(0);

    wrapSingleHeadersInList(requestObject);

    Gson gson = input.getGson();
    JsonElement requestElement = gson.toJsonTree(requestObject);

    ClientRequest clientRequest = gson.fromJson(requestElement, ClientRequest.class);
    clientRequest.setHttp(input.getHttp());
    clientRequest.setMethod(this.method);

    // Create the HttpRequestRunnable command
    Engine engine = input.getEngine();

    // Schedule the HttpRequestRunnable command for execution asynchronously
    input
      .getExecutor()
      .execute(new HttpRequestRunnable(engine, httpService, input.getContinuationReference(), clientRequest));

    return ServiceFunctionOutput.waitForFunctionEndCallback();
  }

  @SuppressWarnings("unchecked")
  private void wrapSingleHeadersInList(Object requestObject) {
    if (requestObject instanceof Map) {
      Object headersObject = ((Map<String,Object>)requestObject).get("headers");
      if (headersObject instanceof Map) {
        Map headers = (Map) headersObject;
        for (Object key: headers.keySet()) {
          Object value = headers.get(key);
          if (value!=null &&
               ( !Collection.class.isAssignableFrom(value.getClass())
                 && !value.getClass().isArray())) {
            headers.put(key, Lists.of(value));
          }
        }
      }
    }
  }
}
