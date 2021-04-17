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

package org.openqa.selenium.grid.node.config;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverInfo;
import org.openqa.selenium.grid.config.Config;
import org.openqa.selenium.grid.config.ConfigException;
import org.openqa.selenium.grid.node.Node;
import org.openqa.selenium.grid.node.SessionFactory;
import org.openqa.selenium.internal.Require;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.json.JsonOutput;
import org.openqa.selenium.remote.service.DriverService;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class NodeOptions {

  public static final int DEFAULT_MAX_SESSIONS = Runtime.getRuntime().availableProcessors();
  public static final int DEFAULT_HEARTBEAT_PERIOD = 60;
  public static final int DEFAULT_SESSION_TIMEOUT = 300;
  static final String NODE_SECTION = "node";
  static final boolean DEFAULT_DETECT_DRIVERS = true;
  static final boolean OVERRIDE_MAX_SESSIONS = false;
  static final int DEFAULT_REGISTER_CYCLE = 10;
  static final int DEFAULT_REGISTER_PERIOD = 120;

  private static final Logger LOG = Logger.getLogger(NodeOptions.class.getName());
  private static final Json JSON = new Json();
  private static final String DEFAULT_IMPL = "org.openqa.selenium.grid.node.local.LocalNodeFactory";
  private static final ImmutableCapabilities CURRENT_PLATFORM =
    new ImmutableCapabilities("platformName", Platform.getCurrent());

  private final Config config;

  public NodeOptions(Config config) {
    this.config = Require.nonNull("Config", config);
  }

  public Optional<URI> getPublicGridUri() {
    return config.get(NODE_SECTION, "grid-url").map(url -> {
      try {
        return new URI(url);
      } catch (URISyntaxException e) {
        throw new ConfigException("Unable to construct public URL: " + url);
      }
    });
  }

  public Node getNode() {
    return config.getClass(NODE_SECTION, "implementation", Node.class, DEFAULT_IMPL);
  }

  public Duration getRegisterCycle() {
    // If the user sets 0 or less, we default to 1s.
    int seconds = Math.max(
      config.getInt(NODE_SECTION, "register-cycle").orElse(DEFAULT_REGISTER_CYCLE),
      1);

    return Duration.ofSeconds(seconds);
  }

  public Duration getRegisterPeriod() {
    // If the user sets 0 or less, we default to 1s.
    int seconds = Math.max(
      config.getInt(NODE_SECTION, "register-period").orElse(DEFAULT_REGISTER_PERIOD),
      1);

    return Duration.ofSeconds(seconds);
  }

  public Duration getHeartbeatPeriod() {
    // If the user sets 0 or less, we default to 1s.
    int seconds = Math.max(
      config.getInt(NODE_SECTION, "heartbeat-period").orElse(DEFAULT_HEARTBEAT_PERIOD),
      1);
    return Duration.ofSeconds(seconds);
  }

  public Map<Capabilities, Collection<SessionFactory>> getSessionFactories(
    /* Danger! Java stereotype ahead! */
    Function<Capabilities, Collection<SessionFactory>> factoryFactory) {

    LOG.log(Level.INFO, "Detected {0} available processors", DEFAULT_MAX_SESSIONS);
    boolean overrideMaxSessions = config.getBool(NODE_SECTION, "override-max-sessions")
      .orElse(OVERRIDE_MAX_SESSIONS);
    if (overrideMaxSessions) {
      LOG.log(Level.WARNING,
              "Overriding max recommended number of {0} concurrent sessions. "
              + "Session stability and reliability might suffer!",
              DEFAULT_MAX_SESSIONS);
      LOG.warning("One browser session is recommended per available processor. IE and "
                  + "Safari are always limited to 1 session per host.");
      LOG.warning("Double check if enabling 'override-max-sessions' is really needed");
    }
    int maxSessions = getMaxSessions();
    if (maxSessions > DEFAULT_MAX_SESSIONS) {
      LOG.log(Level.WARNING, "Max sessions set to {0} ", maxSessions);
    }

    Map<WebDriverInfo, Collection<SessionFactory>> allDrivers =
      discoverDrivers(maxSessions, factoryFactory);

    ImmutableMultimap.Builder<Capabilities, SessionFactory> sessionFactories =
      ImmutableMultimap.builder();

    addDriverFactoriesFromConfig(sessionFactories);
    addDriverConfigs(factoryFactory, sessionFactories);
    addSpecificDrivers(allDrivers, sessionFactories);
    addDetectedDrivers(allDrivers, sessionFactories);

    return sessionFactories.build().asMap();
  }

  public int getMaxSessions() {
    int maxSessions = config.getInt(NODE_SECTION, "max-sessions")
      .orElse(DEFAULT_MAX_SESSIONS);
    Require.positive("Driver max sessions", maxSessions);
    boolean overrideMaxSessions = config.getBool(NODE_SECTION, "override-max-sessions")
      .orElse(OVERRIDE_MAX_SESSIONS);
    if (maxSessions > DEFAULT_MAX_SESSIONS && overrideMaxSessions) {
      return maxSessions;
    }
    return Math.min(maxSessions, DEFAULT_MAX_SESSIONS);
  }

  public Duration getSessionTimeout() {
    // If the user sets 10s or less, we default to 10s.
    int seconds = Math.max(
      config.getInt(NODE_SECTION, "session-timeout").orElse(DEFAULT_SESSION_TIMEOUT),
      10);
    return Duration.ofSeconds(seconds);
  }

  private void addDriverFactoriesFromConfig(ImmutableMultimap.Builder<Capabilities,
    SessionFactory> sessionFactories) {
    config.getAll(NODE_SECTION, "driver-factories").ifPresent(allConfigs -> {
      if (allConfigs.size() % 2 != 0) {
        throw new ConfigException("Expected each driver class to be mapped to a config");
      }

      Map<String, String> configMap = IntStream.range(0, allConfigs.size()/2).boxed()
        .collect(Collectors.toMap(i -> allConfigs.get(2*i), i -> allConfigs.get(2*i + 1)));

      configMap.forEach((clazz, config) -> {
        Capabilities stereotype = JSON.toType(config, Capabilities.class);
        SessionFactory sessionFactory = createSessionFactory(clazz, stereotype);
        sessionFactories.put(stereotype, sessionFactory);
      });
    });
  }

  private SessionFactory createSessionFactory(String clazz, Capabilities stereotype) {
    LOG.fine(String.format("Creating %s as instance of %s", clazz, SessionFactory.class));

    try {
      // Use the context class loader since this is what the `--ext`
      // flag modifies.
      Class<?> ClassClazz =
        Class.forName(clazz, true, Thread.currentThread().getContextClassLoader());
      Method create = ClassClazz.getMethod("create", Config.class, Capabilities.class);

      if (!Modifier.isStatic(create.getModifiers())) {
        throw new IllegalArgumentException(String.format(
          "Class %s's `create(Config, Capabilities)` method must be static", clazz));
      }

      if (!SessionFactory.class.isAssignableFrom(create.getReturnType())) {
        throw new IllegalArgumentException(String.format(
          "Class %s's `create(Config, Capabilities)` method must be static", clazz));
      }

      return (SessionFactory) create.invoke(null, config, stereotype);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(String.format(
        "Class %s must have a static `create(Config, Capabilities)` method", clazz));
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("Unable to find class: " + clazz, e);
    }
  }

  private void addDriverConfigs(
    Function<Capabilities, Collection<SessionFactory>> factoryFactory,
    ImmutableMultimap.Builder<Capabilities, SessionFactory> sessionFactories) {
    Multimap<WebDriverInfo, SessionFactory> driverConfigs = HashMultimap.create();
    int configElements = 3;
    config.getAll(NODE_SECTION, "driver-configuration").ifPresent(drivers -> {
      if (drivers.size() % configElements != 0) {
        throw new ConfigException("Expected each driver config to have three elements " +
                                  "(name, stereotype and max-sessions)");
      }

      drivers.stream()
        .filter(driver -> !driver.contains("="))
        .peek(driver -> LOG.warning(driver + " does not have the required 'key=value' " +
                                    "structure for the configuration"))
        .findFirst()
        .ifPresent(ignore -> {
          throw new ConfigException("One or more driver configs does not have the " +
                                    "required 'key=value' structure");
        });

      List<Map<String, String>> driversMap = new ArrayList<>();
      IntStream.range(0, drivers.size()/configElements).boxed()
        .forEach(i -> {
          ImmutableMap<String, String> configMap = ImmutableMap.of(
            drivers.get(i*configElements).split("=")[0],
            drivers.get(i*configElements).split("=")[1],
            drivers.get(i*configElements+1).split("=")[0],
            drivers.get(i*configElements+1).split("=")[1],
            drivers.get(i*configElements+2).split("=")[0],
            drivers.get(i*configElements+2).split("=")[1]
          );
          driversMap.add(configMap);
        });

      List<DriverService.Builder<?, ?>> builders = new ArrayList<>();
      ServiceLoader.load(DriverService.Builder.class).forEach(builders::add);

      List<WebDriverInfo> infos = new ArrayList<>();
      ServiceLoader.load(WebDriverInfo.class).forEach(infos::add);

      driversMap.forEach(configMap -> {
        if (!configMap.containsKey("stereotype")) {
          throw new ConfigException("Driver config is missing stereotype value. " + configMap);
        }
        Capabilities stereotype = JSON.toType(configMap.get("stereotype"), Capabilities.class);
        String configName = configMap.getOrDefault("name", "Custom Slot Config");
        int driverMaxSessions = Integer.parseInt(configMap.getOrDefault("max-sessions", "1"));
        Require.positive("Driver max sessions", driverMaxSessions);

        WebDriverInfo info = infos.stream()
          .filter(webDriverInfo -> webDriverInfo.isSupporting(stereotype))
          .findFirst()
          .orElseThrow(() ->
                         new ConfigException("Unable to find matching driver for %s", stereotype));

        WebDriverInfo driverInfoConfig = createConfiguredDriverInfo(info, stereotype, configName);

        builders.stream()
          .filter(builder -> builder.score(stereotype) > 0)
          .forEach(builder -> {
            int maxDriverSessions = getDriverMaxSessions(info, driverMaxSessions);
            for (int i = 0; i < maxDriverSessions; i++) {
              driverConfigs.putAll(driverInfoConfig, factoryFactory.apply(stereotype));
            }
          });
      });
    });
    driverConfigs.asMap().entrySet()
      .stream()
      .peek(this::report)
      .forEach(
        entry ->
          sessionFactories.putAll(entry.getKey().getCanonicalCapabilities(), entry.getValue()));
  }

  private void addDetectedDrivers(
    Map<WebDriverInfo, Collection<SessionFactory>> allDrivers,
    ImmutableMultimap.Builder<Capabilities, SessionFactory> sessionFactories) {
    if (!config.getBool(NODE_SECTION, "detect-drivers").orElse(DEFAULT_DETECT_DRIVERS)) {
      return;
    }

    // Only specified drivers should be added, not all the detected ones
    if (config.getAll(NODE_SECTION, "driver-implementation").isPresent()) {
      return;
    }

    allDrivers.entrySet()
      .stream()
      .peek(this::report)
      .forEach(
        entry -> {
          Capabilities capabilities = entry.getKey()
            .getCanonicalCapabilities().merge(CURRENT_PLATFORM);
          sessionFactories.putAll(capabilities, entry.getValue());
        });

    if (sessionFactories.build().size() == 0) {
      String logMessage = "No drivers have been configured or have been found on PATH";
      LOG.warning(logMessage);
      throw new ConfigException(logMessage);
    }
  }

  private void addSpecificDrivers(
    Map<WebDriverInfo, Collection<SessionFactory>> allDrivers,
    ImmutableMultimap.Builder<Capabilities, SessionFactory> sessionFactories) {
    if (!config.getAll(NODE_SECTION, "driver-implementation").isPresent()) {
      return;
    }

    if (!config.getBool(NODE_SECTION, "detect-drivers").orElse(DEFAULT_DETECT_DRIVERS)) {
      String logMessage = "Specific drivers cannot be added if 'detect-drivers' is set to false";
      LOG.warning(logMessage);
      throw new ConfigException(logMessage);
    }

    List<String> drivers = config.getAll(NODE_SECTION, "driver-implementation")
      .orElse(new ArrayList<>())
      .stream()
      .distinct()
      .map(String::toLowerCase)
      .peek(driver -> {
        boolean noneMatch = allDrivers
          .entrySet()
          .stream()
          .noneMatch(entry -> entry.getKey().getDisplayName().equalsIgnoreCase(driver));
        if (noneMatch) {
          LOG.log(Level.WARNING, "Could not find {0} driver on PATH.", driver);
        }
      })
      .collect(Collectors.toList());

    allDrivers.entrySet().stream()
      .filter(entry -> drivers.contains(entry.getKey().getDisplayName().toLowerCase()))
      .findFirst()
      .orElseThrow(() ->
                     new ConfigException("No drivers were found for %s", drivers.toString()));

    allDrivers.entrySet().stream()
      .filter(entry -> drivers.contains(entry.getKey().getDisplayName().toLowerCase()))
      .sorted(Comparator.comparing(entry -> entry.getKey().getDisplayName().toLowerCase()))
      .peek(this::report)
      .forEach(
        entry -> {
          Capabilities capabilities = entry.getKey()
            .getCanonicalCapabilities().merge(CURRENT_PLATFORM);
          sessionFactories.putAll(capabilities, entry.getValue());
        });
  }

  private Map<WebDriverInfo, Collection<SessionFactory>> discoverDrivers(
    int maxSessions, Function<Capabilities, Collection<SessionFactory>> factoryFactory) {

    if (!config.getBool(NODE_SECTION, "detect-drivers").orElse(DEFAULT_DETECT_DRIVERS)) {
      return ImmutableMap.of();
    }

    // We don't expect duplicates, but they're fine
    List<WebDriverInfo> infos =
      StreamSupport.stream(ServiceLoader.load(WebDriverInfo.class).spliterator(), false)
        .filter(WebDriverInfo::isAvailable)
        .sorted(Comparator.comparing(info -> info.getDisplayName().toLowerCase()))
        .collect(Collectors.toList());

    LOG.log(Level.INFO, "Discovered {0} driver(s)", infos.size());

    // Same
    List<DriverService.Builder<?, ?>> builders = new ArrayList<>();
    ServiceLoader.load(DriverService.Builder.class).forEach(builders::add);

    Multimap<WebDriverInfo, SessionFactory> toReturn = HashMultimap.create();
    infos.forEach(info -> {
      Capabilities caps = info.getCanonicalCapabilities().merge(CURRENT_PLATFORM);
      builders.stream()
        .filter(builder -> builder.score(caps) > 0)
        .forEach(builder -> {
          int maxDriverSessions = getDriverMaxSessions(info, maxSessions);
          for (int i = 0; i < maxDriverSessions; i++) {
            toReturn.putAll(info, factoryFactory.apply(caps));
          }
        });
    });

    return toReturn.asMap();
  }

  private WebDriverInfo createConfiguredDriverInfo(
    WebDriverInfo detectedDriver, Capabilities canonicalCapabilities, String displayName) {
    return new WebDriverInfo() {
      @Override
      public String getDisplayName() {
        return displayName;
      }

      @Override
      public Capabilities getCanonicalCapabilities() {
        return canonicalCapabilities;
      }

      @Override
      public boolean isSupporting(Capabilities capabilities) {
        return detectedDriver.isSupporting(capabilities);
      }

      @Override
      public boolean isSupportingCdp() {
        return detectedDriver.isSupportingCdp();
      }

      @Override
      public boolean isAvailable() {
        return detectedDriver.isAvailable();
      }

      @Override
      public int getMaximumSimultaneousSessions() {
        return detectedDriver.getMaximumSimultaneousSessions();
      }

      @Override
      public Optional<WebDriver> createDriver(Capabilities capabilities)
        throws SessionNotCreatedException {
        return Optional.empty();
      }
    };
  }

  private int getDriverMaxSessions(WebDriverInfo info, int desiredMaxSessions) {
    // IE and Safari
    if (info.getMaximumSimultaneousSessions() == 1) {
      return info.getMaximumSimultaneousSessions();
    }
    boolean overrideMaxSessions = config.getBool(NODE_SECTION, "override-max-sessions")
      .orElse(OVERRIDE_MAX_SESSIONS);
    if (desiredMaxSessions > info.getMaximumSimultaneousSessions() && overrideMaxSessions) {
      String logMessage = String.format(
        "Overriding max recommended number of %s concurrent sessions for %s, setting it to %s",
        info.getMaximumSimultaneousSessions(),
        info.getDisplayName(),
        desiredMaxSessions);
      LOG.log(Level.FINE, logMessage);
      return desiredMaxSessions;
    }
    return Math.min(info.getMaximumSimultaneousSessions(), desiredMaxSessions);
  }

  private void report(Map.Entry<WebDriverInfo, Collection<SessionFactory>> entry) {
    StringBuilder caps = new StringBuilder();
    try (JsonOutput out = JSON.newOutput(caps)) {
      out.setPrettyPrint(false);
      Capabilities capabilities = entry.getKey().getCanonicalCapabilities();
      if (capabilities.getPlatformName() == null) {
        capabilities = capabilities.merge(CURRENT_PLATFORM);
      }
      out.write(capabilities);
    }

    LOG.info(String.format(
      "Adding %s for %s %d times",
      entry.getKey().getDisplayName(),
      caps.toString().replaceAll("\\s+", " "),
      entry.getValue().size()));
  }
}
