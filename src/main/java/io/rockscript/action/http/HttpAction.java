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
package io.rockscript.action.http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;

import io.rockscript.action.Action;
import io.rockscript.action.ActionResponse;
import io.rockscript.engine.ArgumentsExpressionExecution;

public class HttpAction implements Action {

  private static Configuration configuration = new Configuration();
  static {
    configuration.connectionTimeoutMilliseconds = 0;
    configuration.readTimeoutMilliseconds = 0;
  }

  @Override
  public ActionResponse invoke(ArgumentsExpressionExecution argumentsExpressionExecution, List<Object> args) {
    Request request;
    try {
      // TODO Construct the HTTP request from the inputs.
      URL url = new URL("https://api.github.com/orgs/RockScript");
      Method method = Method.GET;
      String contentType = null;
      TextRequestBody body = new TextRequestBody(contentType, null);
      Set<RequestHeader> headers = new HashSet<>();
      headers.add(new RequestHeader("Accept", "application/json"));
      // TODO headers.add("X-Correlation-Id", scriptExecutionId);
      request = new Request(url, method, headers, body);
    } catch (MalformedURLException e) {
      return ActionResponse.endFunction(e);
    }

    try {
      HttpURLConnection connection = buildConnection(request);
      // TODO Construct a Response - add headers and body
      Response response = new Response(connection.getResponseCode());
      return ActionResponse.endFunction(response);
    } catch (IOException e) {
      return ActionResponse.endFunction(e);
    }
  }

  private static HttpURLConnection buildConnection(Request request) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) request.url.openConnection();
    connection.setRequestMethod(request.method.name());
    connection.setConnectTimeout(configuration.connectionTimeoutMilliseconds);
    connection.setReadTimeout(configuration.readTimeoutMilliseconds);
    if (request.hasBody()) {
      connection.addRequestProperty("Content-Type", request.body.contentType);
    }
    request.headers.forEach(header -> connection.addRequestProperty(header.name, header.value));

    if (request.method.hasRequestBody()) {
      connection.setDoOutput(true);
      try (OutputStream output = connection.getOutputStream()) {
        output.write(request.body.content.getBytes(Charset.forName("UTF-8")));
      }
    }

    return connection;
  }

  private static class Configuration {
    int connectionTimeoutMilliseconds;
    int readTimeoutMilliseconds;
  }
}
