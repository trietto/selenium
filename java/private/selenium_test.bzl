load(
    "//common:browsers.bzl",
    "COMMON_TAGS",
    "chrome_data",
    "edge_data",
    "firefox_data",
)
load(
    "//java:browsers.bzl",
    "chrome_jvm_flags",
    "edge_jvm_flags",
    "firefox_jvm_flags",
)
load("//java/private:library.bzl", "add_lint_tests")

DEFAULT_BROWSER = "firefox"

BROWSERS = {
    "chrome": {
        "deps": ["//java/client/src/org/openqa/selenium/chrome"],
        "jvm_flags": ["-Dselenium.browser=chrome"] + chrome_jvm_flags,
        "data": chrome_data,
        "tags": COMMON_TAGS + ["chrome"],
    },
    "edge": {
        "deps": ["//java/client/src/org/openqa/selenium/edge"],
        "jvm_flags": ["-Dselenium.browser=edge"] + edge_jvm_flags,
        "data": edge_data,
        "tags": COMMON_TAGS + ["edge"],
    },
    "firefox": {
        "deps": ["//java/client/src/org/openqa/selenium/firefox"],
        "jvm_flags": ["-Dselenium.browser=ff"] + firefox_jvm_flags,
        "data": firefox_data,
        "tags": COMMON_TAGS + ["firefox"],
    },
    "ie": {
        "deps": ["//java/client/src/org/openqa/selenium/ie"],
        "jvm_flags": ["-Dselenium.browser=ie"] +
                     select({
                         "@selenium//common:windows": ["-Dselenium.skiptest=false"],
                         "@selenium//conditions:default": ["-Dselenium.skiptest=true"],
                     }),
        "data": [],
        "tags": COMMON_TAGS + ["exclusive", "ie"],
    },
    "safari": {
        "deps": ["//java/client/src/org/openqa/selenium/safari"],
        "jvm_flags": ["-Dselenium.browser=safari"] +
                     select({
                         "@selenium//common:macos": ["-Dselenium.skiptest=false"],
                         "@selenium//conditions:default": ["-Dselenium.skiptest=true"],
                     }),
        "data": [],
        "tags": COMMON_TAGS + ["exclusive", "safari"],
    },
}

def selenium_test(name, test_class, size = "medium", browsers = BROWSERS.keys() , **kwargs):
    if len(browsers) == 0:
        fail("At least one browser must be specified.")

    default_browser = DEFAULT_BROWSER if DEFAULT_BROWSER in browsers else browsers[0]

    test_name = test_class.rpartition(".")[2]

    data = kwargs["data"] if "data" in kwargs else []
    jvm_flags = kwargs["jvm_flags"] if "jvm_flags" in kwargs else []
    tags = kwargs["tags"] if "tags" in kwargs else []

    stripped_args = dict(**kwargs)
    stripped_args.pop("data", None)
    stripped_args.pop("jvm_flags", None)
    stripped_args.pop("tags", None)

    for browser in browsers:
        if not browser in BROWSERS:
            fail("Unrecognized browser: " + browser)

        test = name if browser == default_browser else "%s-%s" % (name, browser)

        native.java_test(
            name = test,
            test_class = test_class,
            size = size,
            jvm_flags = BROWSERS[browser]["jvm_flags"] + jvm_flags,
            tags = BROWSERS[browser]["tags"] + tags,
            data = BROWSERS[browser]["data"] + data,
            **stripped_args
        )

        if "selenium-remote" in tags:
            native.java_test(
                name = "%s-remote" % test,
                test_class = test_class,
                size = size,
                jvm_flags = BROWSERS[browser]["jvm_flags"] + jvm_flags + [
                    "-Dselenium.browser.remote=true",
                    "-Dselenium.browser.remote.path=$(location @selenium//java/server/src/org/openqa/selenium/grid:selenium_server_deploy.jar)",
                ],
                tags = BROWSERS[browser]["tags"] + tags + ["remote"],
                data = BROWSERS[browser]["data"] + data + [
                    "@selenium//java/server/src/org/openqa/selenium/grid:selenium_server_deploy.jar",
                ],
                **stripped_args
            )

    # Handy way to run everything
    native.test_suite(name = "%s-all-browsers" % name, tests = [":%s-%s" % (name, default_browser)], tags = tags + ["manual"])
    add_lint_tests(name, **kwargs)
