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

package org.openqa.selenium.grid.distributor.local;

import com.google.common.collect.ImmutableSet;
import org.openqa.selenium.Beta;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.RetrySessionRequestException;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.concurrent.Regularly;
import org.openqa.selenium.events.EventBus;
import org.openqa.selenium.grid.config.Config;
import org.openqa.selenium.grid.data.CreateSessionRequest;
import org.openqa.selenium.grid.data.CreateSessionResponse;
import org.openqa.selenium.grid.data.DistributorStatus;
import org.openqa.selenium.grid.data.NewSessionErrorResponse;
import org.openqa.selenium.grid.data.NewSessionRejectedEvent;
import org.openqa.selenium.grid.data.NewSessionRequestEvent;
import org.openqa.selenium.grid.data.NewSessionResponse;
import org.openqa.selenium.grid.data.NewSessionResponseEvent;
import org.openqa.selenium.grid.data.NodeAddedEvent;
import org.openqa.selenium.grid.data.NodeDrainComplete;
import org.openqa.selenium.grid.data.NodeHeartBeatEvent;
import org.openqa.selenium.grid.data.NodeId;
import org.openqa.selenium.grid.data.NodeStatus;
import org.openqa.selenium.grid.data.NodeStatusEvent;
import org.openqa.selenium.grid.data.RequestId;
import org.openqa.selenium.grid.data.Slot;
import org.openqa.selenium.grid.data.SlotId;
import org.openqa.selenium.grid.distributor.Distributor;
import org.openqa.selenium.grid.distributor.config.DistributorOptions;
import org.openqa.selenium.grid.distributor.selector.DefaultSlotSelector;
import org.openqa.selenium.grid.log.LoggingOptions;
import org.openqa.selenium.grid.node.HealthCheck;
import org.openqa.selenium.grid.node.Node;
import org.openqa.selenium.grid.node.remote.RemoteNode;
import org.openqa.selenium.grid.security.Secret;
import org.openqa.selenium.grid.security.SecretOptions;
import org.openqa.selenium.grid.server.EventBusOptions;
import org.openqa.selenium.grid.server.NetworkOptions;
import org.openqa.selenium.grid.sessionmap.SessionMap;
import org.openqa.selenium.grid.sessionmap.config.SessionMapOptions;
import org.openqa.selenium.grid.sessionqueue.NewSessionQueuer;
import org.openqa.selenium.grid.sessionqueue.SessionRequest;
import org.openqa.selenium.grid.sessionqueue.config.NewSessionQueuerOptions;
import org.openqa.selenium.internal.Either;
import org.openqa.selenium.internal.Require;
import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.tracing.AttributeKey;
import org.openqa.selenium.remote.tracing.EventAttribute;
import org.openqa.selenium.remote.tracing.EventAttributeValue;
import org.openqa.selenium.remote.tracing.Span;
import org.openqa.selenium.remote.tracing.Tracer;
import org.openqa.selenium.status.HasReadyState;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static org.openqa.selenium.grid.data.Availability.DOWN;
import static org.openqa.selenium.grid.data.Availability.DRAINING;

public class LocalDistributor extends Distributor {

  private static final Logger LOG = Logger.getLogger(LocalDistributor.class.getName());

  private final Tracer tracer;
  private final EventBus bus;
  private final HttpClient.Factory clientFactory;
  private final SessionMap sessions;
  private final Secret registrationSecret;
  private final Regularly hostChecker = new Regularly("distributor host checker");
  private final Map<NodeId, Runnable> allChecks = new HashMap<>();
  private final Queue<RequestId> requestIds = new ConcurrentLinkedQueue<>();
  private final ScheduledExecutorService executorService;
  private final Duration healthcheckInterval;

  private final ReadWriteLock lock = new ReentrantReadWriteLock(/* fair */ true);
  private final GridModel model;
  private final Map<NodeId, Node> nodes;

  private final NewSessionQueuer sessionRequests;

  public LocalDistributor(
    Tracer tracer,
    EventBus bus,
    HttpClient.Factory clientFactory,
    SessionMap sessions,
    NewSessionQueuer sessionRequests,
    Secret registrationSecret,
    Duration healthcheckInterval) {
    super(tracer, clientFactory, new DefaultSlotSelector(), sessions, registrationSecret);
    this.tracer = Require.nonNull("Tracer", tracer);
    this.bus = Require.nonNull("Event bus", bus);
    this.clientFactory = Require.nonNull("HTTP client factory", clientFactory);
    this.sessions = Require.nonNull("Session map", sessions);
    this.model = new GridModel(bus);
    this.nodes = new ConcurrentHashMap<>();
    this.sessionRequests = Require.nonNull("New Session Request Queue", sessionRequests);
    this.registrationSecret = Require.nonNull("Registration secret", registrationSecret);
    this.healthcheckInterval = Require.nonNull("Health check interval", healthcheckInterval);

    bus.addListener(NodeStatusEvent.listener(this::register));
    bus.addListener(NodeStatusEvent.listener(model::refresh));
    bus.addListener(NodeHeartBeatEvent.listener(nodeStatus -> {
      if (nodes.containsKey(nodeStatus.getId())) {
        model.touch(nodeStatus.getId());
      } else {
        register(nodeStatus);
      }
    }));
    bus.addListener(NodeDrainComplete.listener(this::remove));
    bus.addListener(NewSessionRequestEvent.listener(requestIds::offer));

    Regularly regularly = new Regularly("Local Distributor");
    regularly.submit(model::purgeDeadNodes, Duration.ofSeconds(30), Duration.ofSeconds(30));

    Thread shutdownHook = new Thread(this::callExecutorShutdown);
    Runtime.getRuntime().addShutdownHook(shutdownHook);
    NewSessionRunnable runnable = new NewSessionRunnable();
    ThreadFactory threadFactory = r -> {
      Thread thread = new Thread(r);
      thread.setName("New Session Creation");
      thread.setDaemon(true);
      return thread;
    };
    executorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    executorService.scheduleAtFixedRate(runnable, 0, 1000, TimeUnit.MILLISECONDS);
  }

  public static Distributor create(Config config) {
    Tracer tracer = new LoggingOptions(config).getTracer();
    EventBus bus = new EventBusOptions(config).getEventBus();
    DistributorOptions distributorOptions = new DistributorOptions(config);
    HttpClient.Factory clientFactory = new NetworkOptions(config).getHttpClientFactory(tracer);
    SessionMap sessions = new SessionMapOptions(config).getSessionMap();
    SecretOptions secretOptions = new SecretOptions(config);
    NewSessionQueuer sessionRequests =
      new NewSessionQueuerOptions(config).getSessionQueuer(
        "org.openqa.selenium.grid.sessionqueue.remote.RemoteNewSessionQueuer");
    return new LocalDistributor(
      tracer,
      bus,
      clientFactory,
      sessions,
      sessionRequests,
      secretOptions.getRegistrationSecret(),
      distributorOptions.getHealthCheckInterval());
  }

  @Override
  public boolean isReady() {
    try {
      return ImmutableSet.of(bus, sessions).parallelStream()
        .map(HasReadyState::isReady)
        .reduce(true, Boolean::logicalAnd);
    } catch (RuntimeException e) {
      return false;
    }
  }

  private void register(NodeStatus status) {
    Require.nonNull("Node", status);

    Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      if (nodes.containsKey(status.getId())) {
        return;
      }

      Set<Capabilities> capabilities = status.getSlots().stream()
        .map(Slot::getStereotype)
        .map(ImmutableCapabilities::copyOf)
        .collect(toImmutableSet());

      // A new node! Add this as a remote node, since we've not called add
      RemoteNode remoteNode = new RemoteNode(
        tracer,
        clientFactory,
        status.getId(),
        status.getUri(),
        registrationSecret,
        capabilities);

      add(remoteNode);
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public LocalDistributor add(Node node) {
    Require.nonNull("Node", node);

    LOG.info(String.format("Added node %s at %s.", node.getId(), node.getUri()));

    nodes.put(node.getId(), node);
    model.add(node.getStatus());

    // Extract the health check
    Runnable runnableHealthCheck = asRunnableHealthCheck(node);
    allChecks.put(node.getId(), runnableHealthCheck);
    hostChecker.submit(runnableHealthCheck, healthcheckInterval, Duration.ofSeconds(30));

    bus.fire(new NodeAddedEvent(node.getId()));

    return this;
  }

  private Runnable asRunnableHealthCheck(Node node) {
    HealthCheck healthCheck = node.getHealthCheck();
    NodeId id = node.getId();
    return () -> {
      HealthCheck.Result result;
      try {
        result = healthCheck.check();
      } catch (Exception e) {
        LOG.log(Level.WARNING, "Unable to process node " + id, e);
        result = new HealthCheck.Result(DOWN, "Unable to run healthcheck. Assuming down");
      }

      Lock writeLock = lock.writeLock();
      writeLock.lock();
      try {
        model.setAvailability(id, result.getAvailability());
      } finally {
        writeLock.unlock();
      }
    };
  }

  @Override
  public boolean drain(NodeId nodeId) {
    Node node = nodes.get(nodeId);
    if (node == null) {
      LOG.info("Asked to drain unregistered node " + nodeId);
      return false;
    }

    Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      node.drain();
      model.setAvailability(nodeId, DRAINING);
    } finally {
      writeLock.unlock();
    }

    return node.isDraining();
  }

  public void remove(NodeId nodeId) {
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      model.remove(nodeId);
      Runnable runnable = allChecks.remove(nodeId);
      if (runnable != null) {
        hostChecker.remove(runnable);
      }
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public DistributorStatus getStatus() {
    Lock readLock = this.lock.readLock();
    readLock.lock();
    try {
      return new DistributorStatus(model.getSnapshot());
    } finally {
      readLock.unlock();
    }
  }

  @Beta
  public void refresh() {
    List<Runnable> allHealthChecks = new ArrayList<>();

    Lock readLock = this.lock.readLock();
    readLock.lock();
    try {
      allHealthChecks.addAll(allChecks.values());
    } finally {
      readLock.unlock();
    }

    allHealthChecks.parallelStream().forEach(Runnable::run);
  }

  @Override
  protected Set<NodeStatus> getAvailableNodes() {
    Lock readLock = this.lock.readLock();
    readLock.lock();
    try {
      return model.getSnapshot().stream()
        .filter(node -> !DOWN.equals(node.getAvailability()))
        .collect(toImmutableSet());
    } finally {
      readLock.unlock();
    }
  }

  @Override
  protected Either<SessionNotCreatedException, CreateSessionResponse> reserve(SlotId slotId, CreateSessionRequest request) {
    Require.nonNull("Slot ID", slotId);
    Require.nonNull("New Session request", request);

    Lock writeLock = this.lock.writeLock();
    writeLock.lock();
    try {
      Node node = nodes.get(slotId.getOwningNodeId());
      if (node == null) {
        return Either.left(new RetrySessionRequestException(
          "Unable to find node. Try a different node"));
      }

      model.reserve(slotId);

      Either<WebDriverException, CreateSessionResponse> response = node.newSession(request);

      if (response.isRight()) {
        model.setSession(slotId, response.right().getSession());
        return Either.right(response.right());
      } else {
        model.setSession(slotId, null);

        WebDriverException exception = response.left();
        if (exception instanceof RetrySessionRequestException) {
          return Either.left(new RetrySessionRequestException(exception.getMessage()));
        } else {
          return Either.left(new SessionNotCreatedException(exception.getMessage()));
        }
      }

    } finally {
      writeLock.unlock();
    }
  }

  public void callExecutorShutdown() {
    LOG.info("Shutting down Distributor executor service");
    executorService.shutdownNow();
  }

  public class NewSessionRunnable implements Runnable {

    @Override
    public void run() {
      Lock writeLock = lock.writeLock();
      writeLock.lock();
      try {
        if (!requestIds.isEmpty()) {
          Set<NodeStatus> availableNodes = ImmutableSet.copyOf(getAvailableNodes());
          boolean hasCapacity = availableNodes.stream()
            .anyMatch(NodeStatus::hasCapacity);
          if (hasCapacity) {
            RequestId reqId = requestIds.poll();
            if (reqId != null) {
              Optional<SessionRequest> maybeRequest = sessionRequests.remove(reqId);
              // Check if polling the queue did not return null
              if (maybeRequest.isPresent()) {
                handleNewSessionRequest(maybeRequest.get(), reqId);
              } else {
                fireSessionRejectedEvent(
                  "Unable to poll request from the new session request queue.",
                  reqId);
              }
            }
          }
        }
      } finally {
        writeLock.unlock();
      }
    }

    private void handleNewSessionRequest(SessionRequest sessionRequest, RequestId reqId) {
      try (Span span = tracer.getCurrentContext().createSpan("distributor.poll_queue")) {
        Map<String, EventAttributeValue> attributeMap = new HashMap<>();
        attributeMap.put(
          AttributeKey.LOGGER_CLASS.getKey(),
          EventAttribute.setValue(getClass().getName()));
        span.setAttribute(AttributeKey.REQUEST_ID.getKey(), reqId.toString());
        attributeMap.put(
          AttributeKey.REQUEST_ID.getKey(),
          EventAttribute.setValue(reqId.toString()));

        attributeMap.put("request", EventAttribute.setValue(sessionRequest.toString()));
        Either<SessionNotCreatedException, CreateSessionResponse> response =
          newSession(sessionRequest);
        if (response.isRight()) {
          CreateSessionResponse sessionResponse = response.right();
          NewSessionResponse newSessionResponse =
            new NewSessionResponse(
              reqId,
              sessionResponse.getSession(),
              sessionResponse.getDownstreamEncodedResponse());

          bus.fire(new NewSessionResponseEvent(newSessionResponse));
        } else {
          SessionNotCreatedException exception = response.left();

          if (exception instanceof RetrySessionRequestException) {
            boolean retried = sessionRequests.retryAddToQueue(sessionRequest);

            attributeMap.put("request.retry_add", EventAttribute.setValue(retried));
            span.addEvent("Retry adding to front of queue. No slot available.", attributeMap);

            if (!retried) {
              span.addEvent("Retry adding to front of queue failed.", attributeMap);
              fireSessionRejectedEvent(exception.getMessage(), reqId);
            }
          } else {
            fireSessionRejectedEvent(exception.getMessage(), reqId);
          }
        }
      }
    }

    private void fireSessionRejectedEvent(String message, RequestId reqId) {
      bus.fire(
        new NewSessionRejectedEvent(new NewSessionErrorResponse(reqId, message)));
    }
  }
}
