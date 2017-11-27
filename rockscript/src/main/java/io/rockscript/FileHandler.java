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
package io.rockscript;

import io.rockscript.http.servlet.RequestHandler;
import io.rockscript.http.servlet.ServerRequest;
import io.rockscript.http.servlet.ServerResponse;
import io.rockscript.util.Io;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLConnection;
import java.util.Map;

import static io.rockscript.util.Maps.entry;
import static io.rockscript.util.Maps.hashMap;

public class FileHandler implements RequestHandler {

  static Logger log = LoggerFactory.getLogger(FileHandler.class);

  @SuppressWarnings("unchecked")
  private final static Map<String,String> contentTypesByExtension = hashMap(
    entry(".html",  "text/html"),
    entry(".js",    "text/javascript"),
    entry(".css",   "text/css"),
    entry(".map",   "application/json"),
    entry(".woff2", "font/woff2"),
    entry(".ico",   "image/x-icon"),
    entry(".png",   "image/png")
  );

  public FileHandler(Engine engine) {
  }

  @Override
  public String getLogBodyText(ServerRequest request) {
    return "...content of resource "+getResource(request)+"...";
  }

  @Override
  public boolean matches(ServerRequest request) {
    return Io.hasResource(getResource(request));
  }

  @Override
  public void handle(ServerRequest request, ServerResponse response) {
    String resource = getResource(request);
    String contentType = getContentType(resource);
    response.headerContentType(contentType);

    if (contentType.startsWith("text")
        || contentType.startsWith("application/json")) {
      String fileContent = Io.getResourceAsString(resource);
      response.bodyString(fileContent);
    } else if (contentType.startsWith("image")
               || contentType.startsWith("font")) {
      byte[] bytes = Io.getBytesFromResource(resource);
      response.bodyBytes(bytes);
    }
    response.statusOk();
  }

  private String getResource(ServerRequest request) {
    String path = request.getPathInfo();
    if ("/".equals(path)) {
      path = "/index.html";
    }
    return "webfiles"+path;
  }

  private String getContentType(String path) {
    int lastDotIndex = path.lastIndexOf('.');
    if (lastDotIndex!=-1) {
      String extension = path.substring(lastDotIndex);
      String contentType = contentTypesByExtension.get(extension);
      if (contentType!=null) {
        return contentType;
      }
    }
    String contentType = URLConnection.guessContentTypeFromName(path);
    if (contentType!=null) {
      return contentType;
    }
    throw new RuntimeException("Could not determine content type for "+path+". Please, report this stacktrace so the team can fix it.");
  }
}
