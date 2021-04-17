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

package org.openqa.selenium.support.decorators;

import org.openqa.selenium.Alert;
import org.openqa.selenium.Beta;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.internal.Require;
import org.openqa.selenium.virtualauthenticator.VirtualAuthenticator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class helps to create decorators for instances of {@link WebDriver} and
 * derived objects, such as {@link WebElement}s and {@link Alert}, that can
 * extend or modify their "regular" behavior. It provides a flexible
 * alternative to subclassing WebDriver.
 * <p>
 * Here is a general usage pattern:
 * <ol>
 *   <li>implement a subclass of WebDriverDecorator that adds something to WebDriver behavior:<br>
 *     <code>
 *       public class MyWebDriverDecorator extends WebDriverDecorator { ... }
 *     </code><br>
 *     (see below for details)</li>
 *   <li>use a decorator instance to decorate a WebDriver object:<br>
 *     <code>
 *       WebDriver original = new FirefoxDriver();
 *       WebDriver decorated = new MyWebDriverDecorator().decorate(original);
 *     </code></li>
 *   <li>use the decorated WebDriver instead of the original one:<br>
 *    <code>
 *      decorated.get("http://example.com/");
 *      ...
 *      decorated.quit();
 *    </code>
 *   </li>
 * </ol>
 * By subclassing WebDriverDecorator you can define what code should be executed
 * <ul>
 *   <li>before executing a method of the underlying object,</li>
 *   <li>after executing a method of the underlying object,</li>
 *   <li>instead of executing a method of the underlying object,</li>
 *   <li>when an exception is thrown by a method of the underlying object.</li>
 * </ul>
 * The same decorator is used under the hood to decorate all the objects
 * derived from the underlying WebDriver instance. For example,
 * <code>decorated.findElement(someLocator)</code> automatically decorates
 * the returned WebElement.
 * <p>
 * Instances created by the decorator implement all the same interfaces as
 * the original objects.
 * <p>
 * When you implement a decorator there are two main options (that can be used
 * both separately and together):
 * <ul>
 *   <li>if you want to apply the same behavior modification to all methods of
 *   a WebDriver instance and its derived objects you can subclass
 *   WebDriverDecorator and override some of the following methods:
 *   {@link #beforeCall(Decorated, Method, Object[])},
 *   {@link #afterCall(Decorated, Method, Object[], Object)},
 *   {@link #call(Decorated, Method, Object[])} and
 *   {@link #onError(Decorated, Method, Object[], InvocationTargetException)}</li>
 *   <li>if you want to modify behavior of a specific class instances only
 *   (e.g. behaviour of WebElement instances) you can override one of the
 *   overloaded <code>createDecorated</code> methods to create a non-trivial
 *   decorator for the specific class only.</li>
 * </ul>
 * Let's consider both approaches by examples.
 * <p>
 * One of the most widely used decorator examples is a logging decorator.
 * In this case we want to add the same piece of logging code before and after
 * each invoked method:
 * <code>
 *   public class LoggingDecorator extends WebDriverDecorator {
 *     final Logger logger = LoggerFactory.getLogger(Thread.currentThread().getName());
 *
 *     @Override
 *     public void beforeCall(Decorated<?> target, Method method, Object[] args) {
 *       logger.debug("before {}.{}({})", target, method, args);
 *     }
 *     @Override
 *     public void afterCall(Decorated<?> target, Method method, Object[] args, Object res) {
 *       logger.debug("after {}.{}({}) => {}", target, method, args, res);
 *     }
 *   }
 * </code>
 * For the second example let's implement a decorator that implicitly waits
 * for an element to be visible before any click or sendKeys method call.
 * <code>
 *   public class ImplicitlyWaitingDecorator extends WebDriverDecorator {
 *     private WebDriverWait wait;
 *
 *     @Override
 *     public Decorated<WebDriver> createDecorated(WebDriver driver) {
 *       wait = new WebDriverWait(driver, Duration.ofSeconds(10));
 *       return super.createDecorated(driver);
 *     }
 *     @Override
 *     public Decorated<WebElement> createDecorated(WebElement original) {
 *       return new DefaultDecorated<>(original, this) {
 *         @Override
 *         public void beforeCall(Method method, Object[] args) {
 *           String methodName = method.getName();
 *           if ("click".equals(methodName) || "sendKeys".equals(methodName)) {
 *             wait.until(d -> getOriginal().isDisplayed());
 *           }
 *         }
 *       };
 *     }
 *   }
 * </code>
 * This class is not a pure decorator, it allows to not only add new behavior
 * but also replace "normal" behavior of a WebDriver or derived objects.
 * <p>
 * Let's suppose you want to use JavaScript-powered clicks instead of normal
 * ones (yes, this allows to interact with invisible elements, it's a bad
 * practice in general but sometimes it's inevitable). This behavior change
 * can be achieved with the following "decorator":
 * <code>
 *   public class JavaScriptPoweredDecorator extends WebDriverDecorator {
 *     @Override
 *     public Decorated<WebElement> createDecorated(WebElement original) {
 *       return new DefaultDecorated<>(original, this) {
 *         @Override
 *         public Object call(Method method, Object[] args) throws Throwable {
 *           String methodName = method.getName();
 *           if ("click".equals(methodName)) {
 *             JavascriptExecutor executor = (JavascriptExecutor) getDecoratedDriver().getOriginal();
 *             executor.executeScript("arguments[0].click()", getOriginal());
 *             return null;
 *           } else {
 *             return super.call(method, args);
 *           }
 *         }
 *       };
 *     }
 *   }
 * </code>
 * It is possible to apply multiple decorators to compose behaviors added
 * by each of them. For example, if you want to log method calls and
 * implicitly wait for elements visibility you can use two decorators:
 * <code>
 *   WebDriver original = new FirefoxDriver();
 *   WebDriver decorated =
 *     new ImplicitlyWaitingDecorator().decorate(
 *       new LoggingDecorator().decorate(original));
 * </code>
 */
@Beta
public class WebDriverDecorator {

  private Decorated<WebDriver> decorated;

  public final WebDriver decorate(WebDriver original) {
    Require.nonNull("WebDriver", original);

    decorated = createDecorated(original);
    return createProxy(decorated);
  }

  public Decorated<WebDriver> getDecoratedDriver() {
    return decorated;
  }

  public Decorated<WebDriver> createDecorated(WebDriver driver) {
    return new DefaultDecorated<>(driver, this);
  }

  public Decorated<WebElement> createDecorated(WebElement original) {
    return new DefaultDecorated<>(original, this);
  }

  public Decorated<WebDriver.TargetLocator> createDecorated(WebDriver.TargetLocator original) {
    return new DefaultDecorated<>(original, this);
  }

  public Decorated<WebDriver.Navigation> createDecorated(WebDriver.Navigation original) {
    return new DefaultDecorated<>(original, this);
  }

  public Decorated<WebDriver.Options> createDecorated(WebDriver.Options original) {
    return new DefaultDecorated<>(original, this);
  }

  public Decorated<WebDriver.Timeouts> createDecorated(WebDriver.Timeouts original) {
    return new DefaultDecorated<>(original, this);
  }

  public Decorated<WebDriver.Window> createDecorated(WebDriver.Window original) {
    return new DefaultDecorated<>(original, this);
  }

  public Decorated<Alert> createDecorated(Alert original) {
    return new DefaultDecorated<>(original, this);
  }

  public Decorated<VirtualAuthenticator> createDecorated(VirtualAuthenticator original) {
    return new DefaultDecorated<>(original, this);
  }

  public void beforeCall(Decorated<?> target, Method method, Object[] args) {}

  public Object call(Decorated<?> target, Method method, Object[] args) throws Throwable {
    return decorateResult(method.invoke(target.getOriginal(), args));
  }

  public void afterCall(Decorated<?> target, Method method, Object[] args, Object res) {}

  public Object onError(Decorated<?> target, Method method, Object[] args,
                        InvocationTargetException e) throws Throwable
  {
    throw e.getTargetException();
  }

  private Object decorateResult(Object toDecorate) {
    if (toDecorate instanceof WebDriver) {
      return createProxy(getDecoratedDriver());
    }
    if (toDecorate instanceof WebElement) {
      return createProxy(createDecorated((WebElement) toDecorate));
    }
    if (toDecorate instanceof Alert) {
      return createProxy(createDecorated((Alert) toDecorate));
    }
    if (toDecorate instanceof VirtualAuthenticator) {
      return createProxy(createDecorated((VirtualAuthenticator) toDecorate));
    }
    if (toDecorate instanceof WebDriver.Navigation) {
      return createProxy(createDecorated((WebDriver.Navigation) toDecorate));
    }
    if (toDecorate instanceof WebDriver.Options) {
      return createProxy(createDecorated((WebDriver.Options) toDecorate));
    }
    if (toDecorate instanceof WebDriver.TargetLocator) {
      return createProxy(createDecorated((WebDriver.TargetLocator) toDecorate));
    }
    if (toDecorate instanceof WebDriver.Timeouts) {
      return createProxy(createDecorated((WebDriver.Timeouts) toDecorate));
    }
    if (toDecorate instanceof WebDriver.Window) {
      return createProxy(createDecorated((WebDriver.Window) toDecorate));
    }
    if (toDecorate instanceof List) {
      return ((List<?>) toDecorate).stream()
        .map(this::decorateResult)
        .collect(Collectors.toList());
    }
    return toDecorate;
  }

  protected final <Z> Z createProxy(final Decorated<Z> decorated) {
    Set<Class<?>> decoratedInterfaces = extractInterfaces(decorated);
    Set<Class<?>> originalInterfaces = extractInterfaces(decorated.getOriginal());

    final InvocationHandler handler = (proxy, method, args) -> {
      try {
        if (method.getDeclaringClass().equals(Object.class)
            || decoratedInterfaces.contains(method.getDeclaringClass())) {
          return method.invoke(decorated, args);
        }
        if (originalInterfaces.contains(method.getDeclaringClass())) {
          decorated.beforeCall(method, args);
          Object result = decorated.call(method, args);
          decorated.afterCall(method, result, args);
          return result;
        }
        return method.invoke(decorated.getOriginal(), args);
      } catch (InvocationTargetException e) {
        return decorated.onError(method, e, args);
      }
    };

    Set<Class<?>> allInterfaces = new HashSet<>();
    allInterfaces.addAll(decoratedInterfaces);
    allInterfaces.addAll(originalInterfaces);
    Class<?>[] allInterfacesArray = allInterfaces.toArray(new Class<?>[0]);

    return (Z) Proxy.newProxyInstance(
      this.getClass().getClassLoader(), allInterfacesArray, handler);
  }

  static Set<Class<?>> extractInterfaces(final Object object) {
    return extractInterfaces(object.getClass());
  }

  private static Set<Class<?>> extractInterfaces(final Class<?> clazz) {
    Set<Class<?>> allInterfaces = new HashSet<>();
    extractInterfaces(allInterfaces, clazz);

    return allInterfaces;
  }

  private static void extractInterfaces(final Set<Class<?>> collector, final Class<?> clazz) {
    if (clazz == null || Object.class.equals(clazz)) {
      return;
    }

    final Class<?>[] classes = clazz.getInterfaces();
    for (Class<?> interfaceClass : classes) {
      collector.add(interfaceClass);
      for (Class<?> superInterface : interfaceClass.getInterfaces()) {
        collector.add(superInterface);
        extractInterfaces(collector, superInterface);
      }
    }
    extractInterfaces(collector, clazz.getSuperclass());
  }
}
