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

import com.google.common.collect.ImmutableMap;

import org.assertj.core.api.Condition;
import org.junit.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriverInfo;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.grid.config.Config;
import org.openqa.selenium.grid.config.ConfigException;
import org.openqa.selenium.grid.config.MapConfig;
import org.openqa.selenium.grid.config.TomlConfig;
import org.openqa.selenium.grid.data.CreateSessionRequest;
import org.openqa.selenium.grid.node.ActiveSession;
import org.openqa.selenium.grid.node.SessionFactory;
import org.openqa.selenium.internal.Either;
import org.openqa.selenium.json.Json;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

@SuppressWarnings("DuplicatedCode")
public class NodeOptionsTest {

  @Test
  public void canConfigureNodeWithDriverDetection() {
    assumeFalse("We don't have driver servers in PATH when we run unit tests",
                Boolean.parseBoolean(System.getenv("GITHUB_ACTIONS")));
    assumeTrue("ChromeDriver needs to be available", new ChromeDriverInfo().isAvailable());

    Config config = new MapConfig(singletonMap("node", singletonMap("detect-drivers", "true")));

    List<Capabilities> reported = new ArrayList<>();
    new NodeOptions(config).getSessionFactories(caps -> {
      reported.add(caps);
      return Collections.singleton(HelperFactory.create(config, caps));
    });

    ChromeDriverInfo chromeDriverInfo = new ChromeDriverInfo();
    String expected = chromeDriverInfo.getDisplayName();

    reported.stream()
      .filter(chromeDriverInfo::isSupporting)
      .filter(caps -> expected.equalsIgnoreCase(caps.getBrowserName()))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Unable to find Chrome info"));
  }

  @Test
  public void shouldDetectCorrectDriversOnWindows() {
    assumeTrue(Platform.getCurrent().is(Platform.WINDOWS));
    assumeFalse("We don't have driver servers in PATH when we run unit tests",
                Boolean.parseBoolean(System.getenv("GITHUB_ACTIONS")));

    Config config = new MapConfig(singletonMap("node", singletonMap("detect-drivers", "true")));

    List<Capabilities> reported = new ArrayList<>();
    new NodeOptions(config).getSessionFactories(caps -> {
      reported.add(caps);
      return Collections.singleton(HelperFactory.create(config, caps));
    });

    assertThat(reported).is(supporting("chrome"));
    assertThat(reported).is(supporting("firefox"));
    assertThat(reported).is(supporting("internet explorer"));
    assertThat(reported).is(supporting("MicrosoftEdge"));
    assertThat(reported).isNot(supporting("safari"));
  }


  @Test
  public void shouldDetectCorrectDriversOnMac() {
    assumeTrue(Platform.getCurrent().is(Platform.MAC));
    assumeFalse("We don't have driver servers in PATH when we run unit tests",
                Boolean.parseBoolean(System.getenv("GITHUB_ACTIONS")));

    Config config = new MapConfig(singletonMap("node", singletonMap("detect-drivers", "true")));

    List<Capabilities> reported = new ArrayList<>();
    new NodeOptions(config).getSessionFactories(caps -> {
      reported.add(caps);
      return Collections.singleton(HelperFactory.create(config, caps));
    });

    // There may be more drivers available, but we know that these are meant to be here.
    assertThat(reported).is(supporting("safari"));
    assertThat(reported).isNot(supporting("internet explorer"));
  }

  @Test
  public void canConfigureNodeWithoutDriverDetection() {
    Config config = new MapConfig(singletonMap("node", singletonMap("detect-drivers", "false")));
    List<Capabilities> reported = new ArrayList<>();
    new NodeOptions(config).getSessionFactories(caps -> {
      reported.add(caps);
      return Collections.singleton(HelperFactory.create(config, caps));
    });

    assertThat(reported).isEmpty();
  }

  @Test
  public void shouldThrowConfigExceptionIfDetectDriversIsFalseAndSpecificDriverIsAdded() {
    Config config = new MapConfig(
      singletonMap("node",
                   ImmutableMap.of(
                     "detect-drivers", "false",
                     "driver-implementation", "[chrome]"
                   )));
    List<Capabilities> reported = new ArrayList<>();
    try {
      new NodeOptions(config).getSessionFactories(caps -> {
        reported.add(caps);
        return Collections.singleton(HelperFactory.create(config, caps));
      });
      fail("Should have not executed 'getSessionFactories' successfully");
    } catch (ConfigException e) {
      // Fall through
    }

    assertThat(reported).isEmpty();
  }

  @Test
  public void detectDriversByDefault() {
    Config config = new MapConfig(emptyMap());

    List<Capabilities> reported = new ArrayList<>();
    new NodeOptions(config).getSessionFactories(caps -> {
      reported.add(caps);
      return Collections.singleton(HelperFactory.create(config, caps));
    });

    assertThat(reported).isNotEmpty();
  }

  @Test
  public void canBeConfiguredToUseHelperClassesToCreateSessionFactories() {
    Capabilities caps = new ImmutableCapabilities("browserName", "cheese");
    StringBuilder capsString = new StringBuilder();
    new Json().newOutput(capsString).setPrettyPrint(false).write(caps);

    Config config = new TomlConfig(new StringReader(String.format(
      "[node]\n" +
        "detect-drivers = false\n" +
        "driver-factories = [" +
        "  \"%s\",\n" +
        "  \"%s\"\n" +
        "]",
      HelperFactory.class.getName(),
      capsString.toString().replace("\"", "\\\""))));

    NodeOptions options = new NodeOptions(config);
    Map<Capabilities, Collection<SessionFactory>> factories = options.getSessionFactories(info -> emptySet());

    Collection<SessionFactory> sessionFactories = factories.get(caps);
    assertThat(sessionFactories).size().isEqualTo(1);
    assertThat(sessionFactories.iterator().next()).isInstanceOf(SessionFactory.class);
  }

  @Test
  public void driversCanBeConfigured() {
    String chromeLocation = "/Applications/Google Chrome Beta.app/Contents/MacOS/Google Chrome Beta";
    String firefoxLocation = "/Applications/Firefox Nightly.app/Contents/MacOS/firefox-bin";
    ChromeOptions chromeOptions = new ChromeOptions();
    chromeOptions.setBinary(chromeLocation);
    FirefoxOptions firefoxOptions = new FirefoxOptions();
    firefoxOptions.setBinary(firefoxLocation);
    StringBuilder chromeCaps = new StringBuilder();
    StringBuilder firefoxCaps = new StringBuilder();
    new Json().newOutput(chromeCaps).setPrettyPrint(false).write(chromeOptions);
    new Json().newOutput(firefoxCaps).setPrettyPrint(false).write(firefoxOptions);

    String[] rawConfig = new String[]{
      "[node]",
      "detect-drivers = false",
      "[[node.driver-configuration]]",
      "name = \"Chrome Beta\"",
      "max-sessions = 1",
      String.format("stereotype = \"%s\"", chromeCaps.toString().replace("\"", "\\\"")),
      "[[node.driver-configuration]]",
      "name = \"Firefox Nightly\"",
      "max-sessions = 2",
      String.format("stereotype = \"%s\"", firefoxCaps.toString().replace("\"", "\\\""))
    };
    Config config = new TomlConfig(new StringReader(String.join("\n", rawConfig)));

    List<Capabilities> reported = new ArrayList<>();
    new NodeOptions(config).getSessionFactories(capabilities -> {
      reported.add(capabilities);
      return Collections.singleton(HelperFactory.create(config, capabilities));
    });

    assertThat(reported).is(supporting("chrome"));
    assertThat(reported).is(supporting("firefox"));
    //noinspection unchecked
    assertThat(reported)
      .filteredOn(capabilities -> capabilities.asMap().containsKey(ChromeOptions.CAPABILITY))
      .anyMatch(
        capabilities ->
          ((Map<String, String>) capabilities.getCapability(ChromeOptions.CAPABILITY))
            .get("binary").equalsIgnoreCase(chromeLocation));
    assertThat(reported)
      .filteredOn(capabilities -> capabilities.asMap().containsKey(ChromeOptions.CAPABILITY))
      .hasSize(1);

    //noinspection unchecked
    assertThat(reported)
      .filteredOn(capabilities -> capabilities.asMap().containsKey(FirefoxOptions.FIREFOX_OPTIONS))
      .anyMatch(
        capabilities ->
          ((Map<String, String>) capabilities.getCapability(FirefoxOptions.FIREFOX_OPTIONS))
            .get("binary").equalsIgnoreCase(firefoxLocation));
    assertThat(reported)
      .filteredOn(capabilities -> capabilities.asMap().containsKey(FirefoxOptions.FIREFOX_OPTIONS))
      .hasSize(2);
  }

  @Test
  public void driversConfigNeedsStereotypeField() {
    String[] rawConfig = new String[]{
      "[node]",
      "detect-drivers = false",
      "[[node.driver-configuration]]",
      "name = \"Chrome Beta\"",
      "max-sessions = 2",
      "cheese = \"paipa\"",
      "[[node.driver-configuration]]",
      "name = \"Firefox Nightly\"",
      "max-sessions = 2",
      "cheese = \"sabana\"",
    };
    Config config = new TomlConfig(new StringReader(String.join("\n", rawConfig)));

    List<Capabilities> reported = new ArrayList<>();
    try {
      new NodeOptions(config).getSessionFactories(caps -> {
        reported.add(caps);
        return Collections.singleton(HelperFactory.create(config, caps));
      });
      fail("Should have not executed 'getSessionFactories' successfully because driver config " +
           "needs the stereotype field");
    } catch (ConfigException e) {
      // Fall through
    }

    assertThat(reported).isEmpty();
  }

  @Test
  public void shouldNotOverrideMaxSessionsByDefault() {
    assumeTrue("ChromeDriver needs to be available", new ChromeDriverInfo().isAvailable());
    int maxRecommendedSessions = Runtime.getRuntime().availableProcessors();
    int overriddenMaxSessions = maxRecommendedSessions + 10;
    Config config = new MapConfig(
      singletonMap("node",
                   ImmutableMap
                     .of("max-sessions", overriddenMaxSessions)));
    List<Capabilities> reported = new ArrayList<>();
    try {
      new NodeOptions(config).getSessionFactories(caps -> {
        reported.add(caps);
        return Collections.singleton(HelperFactory.create(config, caps));
      });
    } catch (ConfigException e) {
      // Fall through
    }
    long chromeSlots = reported.stream()
      .filter(capabilities -> "chrome".equalsIgnoreCase(capabilities.getBrowserName()))
      .count();
    assertThat(chromeSlots).isEqualTo(maxRecommendedSessions);
  }

  @Test
  public void canOverrideMaxSessionsWithFlag() {
    assumeTrue("ChromeDriver needs to be available", new ChromeDriverInfo().isAvailable());
    int maxRecommendedSessions = Runtime.getRuntime().availableProcessors();
    int overriddenMaxSessions = maxRecommendedSessions + 10;
    Config config = new MapConfig(
      singletonMap("node",
                   ImmutableMap
                     .of("max-sessions", overriddenMaxSessions,
                         "override-max-sessions", true)));
    List<Capabilities> reported = new ArrayList<>();
    try {
      new NodeOptions(config).getSessionFactories(caps -> {
        reported.add(caps);
        return Collections.singleton(HelperFactory.create(config, caps));
      });
    } catch (ConfigException e) {
      // Fall through
    }
    long chromeSlots = reported.stream()
      .filter(capabilities -> "chrome".equalsIgnoreCase(capabilities.getBrowserName()))
      .count();
    assertThat(chromeSlots).isEqualTo(overriddenMaxSessions);
  }

  private Condition<? super List<? extends Capabilities>> supporting(String name) {
    return new Condition<>(
      caps -> caps.stream().anyMatch(cap -> name.equals(cap.getBrowserName())),
      "supporting %s",
      name);
  }

  public static class HelperFactory {

    public static SessionFactory create(Config config, Capabilities caps) {
      return new SessionFactory() {
        @Override
        public Either<WebDriverException, ActiveSession> apply(
          CreateSessionRequest createSessionRequest) {
          return Either.left(new SessionNotCreatedException("HelperFactory for testing"));
        }

        @Override
        public boolean test(Capabilities capabilities) {
          return true;
        }
      };
    }
  }
}
