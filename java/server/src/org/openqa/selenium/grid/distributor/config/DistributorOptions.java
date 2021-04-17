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

package org.openqa.selenium.grid.distributor.config;

import org.openqa.selenium.grid.config.Config;
import org.openqa.selenium.grid.config.ConfigException;
import org.openqa.selenium.grid.distributor.Distributor;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;

public class DistributorOptions {

  static final String DISTRIBUTOR_SECTION = "distributor";
  static final String DEFAULT_DISTRIBUTOR_IMPLEMENTATION =
    "org.openqa.selenium.grid.distributor.local.LocalDistributor";

  public static final int DEFAULT_HEALTHCHECK_INTERVAL = 300;

  private final Config config;

  public DistributorOptions(Config config) {
    this.config = config;
  }

  public URI getDistributorUri() {
    Optional<URI> host = config.get(DISTRIBUTOR_SECTION, "host").map(str -> {
      try {
        return new URI(str);
      } catch (URISyntaxException e) {
        throw new ConfigException("Distributor URI is not a valid URI: " + str);
      }
    });

    if (host.isPresent()) {
      return host.get();
    }

    Optional<Integer> port = config.getInt(DISTRIBUTOR_SECTION, "port");
    Optional<String> hostname = config.get(DISTRIBUTOR_SECTION, "hostname");

    if (!(port.isPresent() && hostname.isPresent())) {
      throw new ConfigException("Unable to determine host and port for the distributor");
    }

    try {
      return new URI(
          "http",
          null,
          hostname.get(),
          port.get(),
          null,
          null,
          null);
    } catch (URISyntaxException e) {
      throw new ConfigException(
          "Distributor uri configured through host (%s) and port (%d) is not a valid URI",
          hostname.get(),
          port.get());
    }
  }

  public Duration getHealthCheckInterval() {
    // If the user sets 0s or less, we default to 10s.
    int seconds = Math.max(
      config.getInt(DISTRIBUTOR_SECTION, "healthcheck-interval").orElse(DEFAULT_HEALTHCHECK_INTERVAL),
      10);
    return Duration.ofSeconds(seconds);
  }

  public Distributor getDistributor() {
    return config.getClass(
      DISTRIBUTOR_SECTION,
      "implementation",
      Distributor.class,
      DEFAULT_DISTRIBUTOR_IMPLEMENTATION);
  }
}
