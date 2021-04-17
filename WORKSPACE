workspace(
    name = "selenium",
    managed_directories = {
        # Share the node_modules directory between Bazel and other tooling
        "@npm": ["node_modules"],
    },
)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "apple_rules_lint",
    sha256 = "ece669d52998c7a0df2c2380f37edbf4ed8ebb1a03587ed1781dfbececef9b3d",
    urls = [
        "https://github.com/apple/apple_rules_lint/releases/download/0.1.0/apple_rules_lint-0.1.0.tar.gz",
    ],
)

load("@apple_rules_lint//lint:repositories.bzl", "lint_deps")

lint_deps()

load("@apple_rules_lint//lint:setup.bzl", "lint_setup")

# Add your linters here.
lint_setup({
    "java-spotbugs": "//java:spotbugs-config",
})

http_archive(
    name = "platforms",
    sha256 = "460caee0fa583b908c622913334ec3c1b842572b9c23cf0d3da0c2543a1a157d",
    urls = [
        "https://github.com/bazelbuild/platforms/releases/download/0.0.3/platforms-0.0.3.tar.gz",
        "https://mirror.bazel.build/github.com/bazelbuild/platforms/releases/download/0.0.3/platforms-0.0.3.tar.gz",
    ],
)

http_archive(
    name = "bazel_skylib",
    sha256 = "1c531376ac7e5a180e0237938a2536de0c54d93f5c278634818e0efc952dd56c",
    urls = [
        "https://github.com/bazelbuild/bazel-skylib/releases/download/1.0.3/bazel-skylib-1.0.3.tar.gz",
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/1.0.3/bazel-skylib-1.0.3.tar.gz",
    ],
)

load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")

bazel_skylib_workspace()

http_archive(
    name = "bazel_toolchains",
    sha256 = "1adf5db506a7e3c465a26988514cfc3971af6d5b3c2218925cd6e71ee443fc3f",
    strip_prefix = "bazel-toolchains-4.0.0",
    urls = [
        "https://github.com/bazelbuild/bazel-toolchains/releases/download/4.0.0/bazel-toolchains-4.0.0.tar.gz",
    ],
)

load("@bazel_toolchains//rules:rbe_repo.bzl", "rbe_autoconfig")

rbe_autoconfig(name = "rbe_default")

http_archive(
    name = "rules_python",
    url = "https://github.com/bazelbuild/rules_python/releases/download/0.2.0/rules_python-0.2.0.tar.gz",
    sha256 = "778197e26c5fbeb07ac2a2c5ae405b30f6cb7ad1f5510ea6fdac03bded96cc6f",
)

# This one is only needed if you're using the packaging rules.
load("@rules_python//python:pip.bzl", "pip_install", "pip_repositories")

pip_install(
    name = "dev_requirements",
    requirements = "//py:requirements.txt",
)

http_archive(
    name = "rules_proto",
    sha256 = "8e7d59a5b12b233be5652e3d29f42fba01c7cbab09f6b3a8d0a57ed6d1e9a0da",
    strip_prefix = "rules_proto-7e4afce6fe62dbff0a4a03450143146f9f2d7488",
    urls = [
        "https://github.com/bazelbuild/rules_proto/archive/7e4afce6fe62dbff0a4a03450143146f9f2d7488.tar.gz",
        "https://mirror.bazel.build/github.com/bazelbuild/rules_proto/archive/7e4afce6fe62dbff0a4a03450143146f9f2d7488.tar.gz",
    ],
)

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

rules_proto_dependencies()

rules_proto_toolchains()

http_archive(
    name = "rules_jvm_external",
    patch_args = [
        "-p1",
    ],
    patches = [
        "//java:rules_jvm_external_javadoc.patch",
        "//java:rules_jvm_external_visibility.patch",
    ],
    sha256 = "3631082609854e0e7c28c6cdc06708eceda15816f5ff3153f84da7932e3e428c",
    strip_prefix = "rules_jvm_external-c0a8a3ea3fed24a66eade21015249ed5ed99bf65",
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/c0a8a3ea3fed24a66eade21015249ed5ed99bf65.zip",
)

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

load("//java:maven_deps.bzl", "selenium_java_deps")

selenium_java_deps()

load("@maven//:defs.bzl", "pinned_maven_install")

pinned_maven_install()

http_archive(
    name = "d2l_rules_csharp",
    sha256 = "7b2a83621049904b6e898ffdbe7893a5b410aedf599d63f127ef81eac839b6c1",
    strip_prefix = "rules_csharp-bf24e589bbadcc20f15a16e13f577a0abd42a1d1",
    urls = [
        "https://github.com/Brightspace/rules_csharp/archive/bf24e589bbadcc20f15a16e13f577a0abd42a1d1.tar.gz",
    ],
)

load("//dotnet:workspace.bzl", "selenium_register_dotnet")

selenium_register_dotnet()

http_archive(
    name = "build_bazel_rules_nodejs",
    sha256 = "1134ec9b7baee008f1d54f0483049a97e53a57cd3913ec9d6db625549c98395a",
    urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/3.4.0/rules_nodejs-3.4.0.tar.gz"],
)

load("@build_bazel_rules_nodejs//:index.bzl", "npm_install")

npm_install(
    name = "npm",
    package_json = "//:package.json",
    package_lock_json = "//:package-lock.json",
)

http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "d66deed38a0bb20581c15664f0ab62270af5940786855c7adc3087b27168b529",
    strip_prefix = "rules_closure-0.11.0",
    urls = [
        "https://github.com/bazelbuild/rules_closure/archive/0.11.0.tar.gz",
    ],
)

load("@io_bazel_rules_closure//closure:repositories.bzl", "rules_closure_dependencies", "rules_closure_toolchains")

rules_closure_dependencies()

rules_closure_toolchains()

http_archive(
    name = "rules_pkg",
    sha256 = "aeca78988341a2ee1ba097641056d168320ecc51372ef7ff8e64b139516a4937",
    url = "https://github.com/bazelbuild/rules_pkg/releases/download/0.2.6-1/rules_pkg-0.2.6.tar.gz",
)

http_archive(
    name = "io_bazel_rules_docker",
    sha256 = "df3ef4a4b53b0145c9751c1e2a840f900e322e7798612a46257abe285d046dc5",
    strip_prefix = "rules_docker-7da0de3d094aae5601c45ae0855b64fb2771cd72",
    urls = ["https://github.com/bazelbuild/rules_docker/archive/7da0de3d094aae5601c45ae0855b64fb2771cd72.zip"],
)

load(
    "@io_bazel_rules_docker//repositories:repositories.bzl",
    container_repositories = "repositories",
)

container_repositories()

load("@io_bazel_rules_docker//repositories:deps.bzl", container_deps = "deps")

container_deps()

load(
    "@io_bazel_rules_docker//container:container.bzl",
    "container_pull",
)

container_pull(
    name = "java_image_base",
    # This pulls the java 11 version of the jave base image
    digest = "sha256:34c3598d83f0dba27820323044ebe79e63ad4f137b405676da75a3905a408adf",
    registry = "gcr.io",
    repository = "distroless/java",
)

container_pull(
    name = "firefox_standalone",
    # selenium/standalone-firefox-debug:3.141.59
    digest = "sha256:27864b3c5ad5a4c4311bfa3e01cf389ec517980df12d3354b33cfc93b726b372",
    registry = "index.docker.io",
    repository = "selenium/standalone-firefox-debug",
)

container_pull(
    name = "chrome_standalone",
    # selenium/standalone-chrome-debug:3.141.59
    digest = "sha256:4c56bcaba306dfc70b873f4e1f2292facb705984de90004c42c65a4380e0d3e3",
    registry = "index.docker.io",
    repository = "selenium/standalone-chrome-debug",
)

http_archive(
    name = "io_bazel_rules_k8s",
    sha256 = "51f0977294699cd547e139ceff2396c32588575588678d2054da167691a227ef",
    strip_prefix = "rules_k8s-0.6",
    url = "https://github.com/bazelbuild/rules_k8s/archive/v0.6.tar.gz",
)

load("@io_bazel_rules_k8s//k8s:k8s.bzl", "k8s_repositories")

k8s_repositories()

load("@io_bazel_rules_k8s//k8s:k8s_go_deps.bzl", k8s_go_deps = "deps")

k8s_go_deps()

load(
    "@io_bazel_rules_go//go:deps.bzl",
    "go_register_toolchains",
    "go_rules_dependencies",
)

go_rules_dependencies()

go_register_toolchains()

load("@io_bazel_rules_k8s//k8s:k8s.bzl", "k8s_defaults")

k8s_defaults(
    name = "k8s_dev",
    cluster = "docker-desktop",
    image_chroot = "localhost:5000",
    kind = "deployment",
    namespace = "selenium",
)

load("//common:repositories.bzl", "pin_browsers")

pin_browsers()
