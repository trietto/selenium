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

package org.openqa.selenium.grid.docker;

import static org.openqa.selenium.grid.config.StandardGridRoles.NODE_ROLE;
import static org.openqa.selenium.grid.docker.DockerOptions.DEFAULT_ASSETS_PATH;
import static org.openqa.selenium.grid.docker.DockerOptions.DEFAULT_DOCKER_URL;
import static org.openqa.selenium.grid.docker.DockerOptions.DEFAULT_VIDEO_IMAGE;
import static org.openqa.selenium.grid.docker.DockerOptions.DOCKER_SECTION;

import com.google.auto.service.AutoService;

import com.beust.jcommander.Parameter;

import org.openqa.selenium.grid.config.ConfigValue;
import org.openqa.selenium.grid.config.HasRoles;
import org.openqa.selenium.grid.config.NonSplittingSplitter;
import org.openqa.selenium.grid.config.Role;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@SuppressWarnings("FieldMayBeFinal")
@AutoService(HasRoles.class)
public class DockerFlags implements HasRoles {

  @Parameter(
    names = {"--docker-url"},
    description = "URL for connecting to the docker daemon"
  )
  @ConfigValue(section = DOCKER_SECTION, name = "url", example = DEFAULT_DOCKER_URL)
  private String dockerUrl;

  @Parameter(
    names = {"--docker-host"},
    description = "Host name where the docker daemon is running"
  )
  @ConfigValue(section = DOCKER_SECTION, name = "host", example = "\"localhost\"")
  private String dockerHost;

  @Parameter(
    names = {"--docker-port"},
    description = "Port where the docker daemon is running"
  )
  @ConfigValue(section = DOCKER_SECTION, name = "port", example = "2375")
  private Integer dockerPort;

  @Parameter(
    names = {"--docker", "-D"},
    description = "Docker configs which map image name to stereotype capabilities (example " +
                  "`-D selenium/standalone-firefox:latest '{\"browserName\": \"firefox\"}')",
    arity = 2,
    variableArity = true,
    splitter = NonSplittingSplitter.class)
  @ConfigValue(
    section = DOCKER_SECTION,
    name = "configs",
    example = "[\"selenium/standalone-firefox:latest\", \"{\\\"browserName\\\": \\\"firefox\\\"}\"]")
  private List<String> images2Capabilities;

  @Parameter(
    names = {"--docker-video-image"},
    description = "Docker image to be used when video recording is enabled"
  )
  @ConfigValue(section = DOCKER_SECTION, name = "video-image", example = DEFAULT_VIDEO_IMAGE)
  private String videoImage = DEFAULT_VIDEO_IMAGE;

  @Parameter(
    names = {"--docker-assets-path"},
    description = "Absolute path where assets will be stored"
  )
  @ConfigValue(section = DOCKER_SECTION, name = "assets-path", example = DEFAULT_ASSETS_PATH)
  private String assetsPath;

  @Override
  public Set<Role> getRoles() {
    return Collections.singleton(NODE_ROLE);
  }
}
