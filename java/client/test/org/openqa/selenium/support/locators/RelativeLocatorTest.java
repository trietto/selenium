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

package org.openqa.selenium.support.locators;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.testing.JUnit4TestBase;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openqa.selenium.By.cssSelector;
import static org.openqa.selenium.By.tagName;
import static org.openqa.selenium.By.xpath;
import static org.openqa.selenium.support.locators.RelativeLocator.with;


public class RelativeLocatorTest extends JUnit4TestBase {

  @Test
  public void shouldBeAbleToFindElementsAboveAnotherWithTagName() {
    driver.get(appServer.whereIs("relative_locators.html"));

    WebElement lowest = driver.findElement(By.id("below"));

    List<WebElement> elements = driver.findElements(with(tagName("p")).above(lowest));
    List<String> ids = elements.stream().map(e -> e.getAttribute("id")).collect(Collectors.toList());

    assertThat(ids).containsExactly("mid", "above");

  }

  @Test
  public void shouldBeAbleToFindElementsAboveAnotherWithXpath() {
    driver.get(appServer.whereIs("relative_locators.html"));

    WebElement lowest = driver.findElement(By.id("seventh"));

    List<WebElement> seen = driver.findElements(with(xpath("//td[1]")).above(lowest));

    List<String> ids = seen.stream().map(e -> e.getAttribute("id")).collect(Collectors.toList());

    assertThat(ids).containsExactly("fourth", "first");
  }

  @Test
  public void shouldBeAbleToFindElementsAboveAnotherwithCssSelector() {
    driver.get(appServer.whereIs("relative_locators.html"));

    WebElement lowest = driver.findElement(By.id("below"));

    List<WebElement> elements = driver.findElements(with(cssSelector("p")).above(lowest));
    List<String> ids = elements.stream().map(e -> e.getAttribute("id")).collect(Collectors.toList());

    assertThat(ids).containsExactly("mid", "above");

  }

  @Test
  public void shouldBeAbleToCombineFilters() {
    driver.get(appServer.whereIs("relative_locators.html"));

    List<WebElement> seen = driver.findElements(with(tagName("td")).above(By.id("center")).toRightOf(By.id("second")));

    List<String> ids = seen.stream().map(e -> e.getAttribute("id")).collect(Collectors.toList());

    assertThat(ids).containsExactly("third");
  }

  @Test
  public void shouldBeAbleToCombineFiltersWithXpath() {
    driver.get(appServer.whereIs("relative_locators.html"));

    List<WebElement> seen = driver.findElements(with(xpath("//td[1]")).below(By.id("second")).above(By.id("seventh")));

    List<String> ids = seen.stream().map(e -> e.getAttribute("id")).collect(Collectors.toList());

    assertThat(ids).containsExactly("fourth");

  }

  @Test
  public void shouldBeAbleToCombineFiltersWithCssSelector() {
    driver.get(appServer.whereIs("relative_locators.html"));


    List<WebElement> seen = driver.findElements(with(cssSelector("td")).above(By.id("center")).toRightOf(By.id("second")));

    List<String> ids = seen.stream().map(e -> e.getAttribute("id")).collect(Collectors.toList());

    assertThat(ids).containsExactly("third");
  }

  @Test
  public void exerciseNearLocatorWithTagName() {
    driver.get(appServer.whereIs("relative_locators.html"));

    List<WebElement> seen = driver.findElements(with(tagName("td")).near(By.id("center")));

    // Elements are sorted by proximity and then DOM insertion order.
    // Proximity is determined using distance from center points, so
    // we expect the order to be:
    // 1. Directly above (short vertical distance, first in DOM)
    // 2. Directly below (short vertical distance, later in DOM)
    // 3. Directly left (slight longer distance horizontally, first in DOM)
    // 4. Directly right (slight longer distance horizontally, later in DOM)
    // 5-8. Diagonally close (pythagorus sorting, with top row first
    //    because of DOM insertion order)
    List<String> ids = seen.stream().map(e -> e.getAttribute("id")).collect(Collectors.toList());
    assertThat(ids).containsExactly("second", "eighth", "fourth", "sixth", "first", "third", "seventh", "ninth");
  }

  @Test
  public void exerciseNearLocatorWithXpath() {
    driver.get(appServer.whereIs("relative_locators.html"));

    List<WebElement> seen = driver.findElements(with(xpath("//td")).near(By.id("center")));

    // Elements are sorted by proximity and then DOM insertion order.
    // Proximity is determined using distance from center points, so
    // we expect the order to be:
    // 1. Directly above (short vertical distance, first in DOM)
    // 2. Directly below (short vertical distance, later in DOM)
    // 3. Directly left (slight longer distance horizontally, first in DOM)
    // 4. Directly right (slight longer distance horizontally, later in DOM)
    // 5-8. Diagonally close (pythagorus sorting, with top row first
    //    because of DOM insertion order)
    List<String> ids = seen.stream().map(e -> e.getAttribute("id")).collect(Collectors.toList());

    assertThat(ids).containsExactly("second", "eighth", "fourth", "sixth", "first", "third", "seventh", "ninth");
  }

  @Test
  public void exerciseNearLocatorWithCssSelector() {
    driver.get(appServer.whereIs("relative_locators.html"));

    List<WebElement> seen = driver.findElements(with(cssSelector("td")).near(By.id("center")));

    // Elements are sorted by proximity and then DOM insertion order.
    // Proximity is determined using distance from center points, so
    // we expect the order to be:
    // 1. Directly above (short vertical distance, first in DOM)
    // 2. Directly below (short vertical distance, later in DOM)
    // 3. Directly left (slight longer distance horizontally, first in DOM)
    // 4. Directly right (slight longer distance horizontally, later in DOM)
    // 5-8. Diagonally close (pythagorus sorting, with top row first
    //    because of DOM insertion order)
    List<String> ids = seen.stream().map(e -> e.getAttribute("id")).collect(Collectors.toList());
    assertThat(ids).containsExactly("second", "eighth", "fourth", "sixth", "first", "third", "seventh", "ninth");
  }
}
