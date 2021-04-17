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

package org.openqa.selenium.grid.graphql;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.grid.data.DistributorStatus;
import org.openqa.selenium.grid.data.NodeStatus;
import org.openqa.selenium.grid.data.Slot;
import org.openqa.selenium.grid.distributor.Distributor;
import org.openqa.selenium.grid.sessionqueue.NewSessionQueuer;
import org.openqa.selenium.internal.Require;
import org.openqa.selenium.json.Json;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Grid {

  private static final Json JSON = new Json();
  private final URI uri;
  private final Supplier<DistributorStatus> distributorStatus;
  private final List<Set<Capabilities>> queueInfoList;
  private final String version;

  public Grid(Distributor distributor, NewSessionQueuer newSessionQueuer, URI uri,
              String version) {
    Require.nonNull("Distributor", distributor);
    this.uri = Require.nonNull("Grid's public URI", uri);
    NewSessionQueuer sessionQueuer = Require.nonNull("NewSessionQueuer", newSessionQueuer);
    this.queueInfoList = sessionQueuer.getQueueContents();
    this.distributorStatus = Suppliers.memoize(distributor::getStatus);
    this.version = Require.nonNull("Grid's version", version);
  }

  public URI getUri() {
    return uri;
  }

  public String getVersion() {
    return version;
  }

  public List<Node> getNodes() {
    ImmutableList.Builder<Node> toReturn = ImmutableList.builder();

    for (NodeStatus status : distributorStatus.get().getNodes()) {
      Map<Capabilities, Integer> stereotypes = new HashMap<>();
      Map<org.openqa.selenium.grid.data.Session, Slot> sessions = new HashMap<>();

      for (Slot slot : status.getSlots()) {
        slot.getSession().ifPresent(session -> sessions.put(session, slot));

        int count = stereotypes.getOrDefault(slot.getStereotype(), 0);
        count++;
        stereotypes.put(slot.getStereotype(), count);
      }

      OsInfo osInfo = new OsInfo(
        status.getOsInfo().get("arch"),
        status.getOsInfo().get("name"),
        status.getOsInfo().get("version"));

      toReturn.add(new Node(
        status.getId(),
        status.getUri(),
        status.getAvailability(),
        status.getMaxSessionCount(),
        status.getSlots().size(),
        stereotypes,
        sessions,
        status.getVersion(),
        osInfo));
    }

    return toReturn.build();
  }

  public int getNodeCount() {
    return distributorStatus.get().getNodes().size();
  }

  public int getSessionCount() {
    return distributorStatus.get().getNodes().stream()
      .map(NodeStatus::getSlots)
      .flatMap(Collection::stream)
      .filter(slot -> slot.getSession().isPresent())
      .mapToInt(slot -> 1)
      .sum();
  }

  public int getTotalSlots() {
    return distributorStatus.get().getNodes().stream()
      .mapToInt(status -> status.getSlots().size())
      .sum();
  }

  public int getMaxSession() {
    return distributorStatus.get().getNodes().stream()
      .mapToInt(NodeStatus::getMaxSessionCount)
      .sum();
  }

  public int getSessionQueueSize() {
    return queueInfoList.size();
  }

  public List<String> getSessionQueueRequests() {
    // TODO: The Grid UI expects there to be a single capability per new session request, which is not correct
    return queueInfoList.stream()
      .map(set -> set.isEmpty() ? new ImmutableCapabilities() : set.iterator().next())
      .map(JSON::toJson)
      .collect(Collectors.toList());
  }

  public List<Session> getSessions() {
    List<Session> sessions = new ArrayList<>();
    for (NodeStatus status : distributorStatus.get().getNodes()) {
      for (Slot slot : status.getSlots()) {
        if (slot.getSession().isPresent()) {
          org.openqa.selenium.grid.data.Session session = slot.getSession().get();
          sessions.add(
            new org.openqa.selenium.grid.graphql.Session(
              session.getId().toString(),
              session.getCapabilities(),
              session.getStartTime(),
              session.getUri(),
              status.getId().toString(),
              status.getUri(),
              slot)
          );
        }
      }
    }
    return sessions;
  }

}
