package ru.protei.git;

import java.time.ZonedDateTime;
import java.util.Set;

public interface ChangesStat {

    ZonedDateTime getFrom();

    ZonedDateTime getTo();

    int getLinesChanged();

    Set<String> getChangedFiles();

    Set<String> getAuthors();
}
