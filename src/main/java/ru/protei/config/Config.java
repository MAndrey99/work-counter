package ru.protei.config;

import lombok.Getter;

import javax.naming.ConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@Getter
public class Config {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .optionalStart()
            .appendPattern(" HH:mm:ss")
            .optionalEnd()
            .toFormatter();

    private final List<String> repoNames;
    private final Path repositoriesRoot;
    private final boolean pullRequired;
    private final List<String> authors;
    private final ZonedDateTime from;
    private final ZonedDateTime to;

    public Config(String configFilePath) throws IOException, ConfigurationException {
        Properties properties = new Properties();

        try (InputStream input = getClass().getResourceAsStream(configFilePath)) {
            if (input == null) {
                throw new IOException(String.format("File %s not found", configFilePath));
            }
            properties.load(input);

            this.repoNames = getRequiredList(properties, "repo_names");
            this.repositoriesRoot = Path.of(getRequiredProperty(properties, "repositories_root"));
            this.authors = getRequiredList(properties, "analyze.authors");
            this.pullRequired = Boolean.parseBoolean(getRequiredProperty(properties, "pull_required"));

            this.from = getRequiredDateTime(properties, "analyze.from");
            this.to = getRequiredDateTime(properties, "analyze.to");
        }
    }

    private static String getRequiredProperty(Properties properties, String propertyName) throws ConfigurationException {
        String property = properties.getProperty(propertyName);
        if (property == null) {
            throw new ConfigurationException(STR."Property \{propertyName} is not specified");
        }
        return property;
    }

    private static List<String> getRequiredList(Properties properties, String propertyName) throws ConfigurationException {
        String property = getRequiredProperty(properties, propertyName);
        return new ArrayList<>(Arrays.stream(property.split(",")).map(String::trim).toList());
    }

    private static ZonedDateTime getRequiredDateTime(Properties properties, String propertyName) throws ConfigurationException {
        String property = getRequiredProperty(properties, propertyName);
        try {
            // ок если есть и дата и время
            return LocalDateTime.parse(property, DATE_TIME_FORMATTER).atZone(ZoneId.systemDefault());
        } catch (DateTimeException e) {
            // ок если есть только дата
            return LocalDate.parse(property, DATE_TIME_FORMATTER).atStartOfDay(ZoneId.systemDefault());
        }
    }
}
