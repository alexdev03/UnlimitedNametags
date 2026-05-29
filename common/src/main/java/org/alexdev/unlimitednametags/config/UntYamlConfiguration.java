package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.YamlConfigurationProperties;

import java.nio.charset.StandardCharsets;

public final class UntYamlConfiguration {

  public static final YamlConfigurationProperties PROPERTIES = YamlConfigurationProperties.newBuilder()
      .charset(StandardCharsets.UTF_8)
      .outputNulls(false)
      .inputNulls(false)
      .footer("Authors: AlexDev_")
      .build();

  private UntYamlConfiguration() {
  }
}
