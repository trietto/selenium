// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.grid.sessionqueue;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.grid.data.RequestId;
import org.openqa.selenium.grid.security.RequiresSecretFilter;
import org.openqa.selenium.grid.security.Secret;
import org.openqa.selenium.internal.Require;
import org.openqa.selenium.remote.NewSessionPayload;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.Routable;
import org.openqa.selenium.remote.http.Route;
import org.openqa.selenium.remote.tracing.Tracer;
import org.openqa.selenium.status.HasReadyState;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.openqa.selenium.remote.http.Route.combine;
import static org.openqa.selenium.remote.http.Route.delete;
import static org.openqa.selenium.remote.http.Route.get;
import static org.openqa.selenium.remote.http.Route.post;

public abstract class NewSessionQueuer implements HasReadyState, Routable {

  protected final Tracer tracer;
  private final Route routes;

  protected NewSessionQueuer(Tracer tracer, Secret registrationSecret) {
    this.tracer = Require.nonNull("Tracer", tracer);

    Require.nonNull("Registration secret", registrationSecret);
    RequiresSecretFilter requiresSecret = new RequiresSecretFilter(registrationSecret);

    routes = combine(
      post("/session")
        .to(() -> req -> {
          try (Reader reader = Contents.reader(req);
               NewSessionPayload payload = NewSessionPayload.create(reader)) {
            SessionRequest sessionRequest = new SessionRequest(
              new RequestId(UUID.randomUUID()),
              Instant.now(),
              payload.getDownstreamDialects(),
              payload.stream().collect(Collectors.toSet()));
            return addToQueue(sessionRequest);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }),
      post("/se/grid/newsessionqueuer/session")
        .to(() -> new AddToSessionQueue(tracer, this)),
      post("/se/grid/newsessionqueuer/session/retry/{requestId}")
        .to(params -> new AddBackToSessionQueue(tracer, this, requestIdFrom(params)))
        .with(requiresSecret),
      get("/se/grid/newsessionqueuer/session/{requestId}")
        .to(params -> new RemoveFromSessionQueue(tracer, this, requestIdFrom(params)))
        .with(requiresSecret),
      get("/se/grid/newsessionqueuer/queue")
        .to(() -> new GetSessionQueue(tracer, this)),
      delete("/se/grid/newsessionqueuer/queue")
        .to(() -> new ClearSessionQueue(tracer, this))
        .with(requiresSecret));
  }

  private RequestId requestIdFrom(Map<String, String> params) {
    return new RequestId(UUID.fromString(params.get("requestId")));
  }

  public abstract HttpResponse addToQueue(SessionRequest request);

  public abstract boolean retryAddToQueue(SessionRequest request);

  public abstract Optional<SessionRequest> remove(RequestId reqId);

  public abstract int clearQueue();

  public abstract List<Set<Capabilities>> getQueueContents();

  @Override
  public boolean matches(HttpRequest req) {
    return routes.matches(req);
  }

  @Override
  public HttpResponse execute(HttpRequest req) {
    return routes.execute(req);
  }

}

