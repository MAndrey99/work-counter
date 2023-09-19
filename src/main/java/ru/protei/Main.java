package ru.protei;

import ru.protei.config.Config;
import ru.protei.git.RepoChangesReader;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class Main {
    public static void main(String[] args) throws Exception {
        var config = new Config("/config.properties");
        var reader = new RepoChangesReader(config.getRepoName(), config.getRepositoriesRoot());
        reader.setPullRequired(config.isPullRequired());
        var from = ZonedDateTime.of(2023, 6, 6, 6, 6, 6, 6, ZoneId.systemDefault());
        reader.readChanges(config.getAuthors(), from, ZonedDateTime.now());
        // TODO
    }
}
