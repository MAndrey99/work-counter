package ru.protei.config;

import lombok.Getter;

import javax.naming.ConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@Getter
public class Config {
        private final String repoName;
        private final Path repositoriesRoot;
        private final boolean pullRequired;
        private final List<String> authors;

        public Config(String configFilePath) throws IOException, ConfigurationException {
            Properties properties = new Properties();

            try (InputStream input = getClass().getResourceAsStream(configFilePath)) {
                if (input == null) {
                    throw new IOException(String.format("File %s not found", configFilePath));
                }
                properties.load(input);

                this.repoName = getRequiredProperty(properties, "repo_name");
                this.repositoriesRoot = Path.of(getRequiredProperty(properties, "repositories_root"));
                String authorsStr = getRequiredProperty(properties, "authors");
                this.authors = new ArrayList<>(Arrays.asList(authorsStr.split(",")));
                this.pullRequired = Boolean.parseBoolean(getRequiredProperty(properties, "pull_required"));
            }
        }

        private static String getRequiredProperty(Properties properties, String propertyName) throws ConfigurationException {
            String property = properties.getProperty(propertyName);
            if (property == null) {
                throw new ConfigurationException(String.format("Property %s is not specified", propertyName));
            }
            return property;
        }
    }
