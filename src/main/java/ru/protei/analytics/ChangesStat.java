package ru.protei.analytics;

import java.time.ZonedDateTime;
import java.util.Set;

public interface ChangesStat {

    ZonedDateTime getFrom();

    ZonedDateTime getTo();

    int getLinesChanged();

    Set<String> getBranches();

    Set<String> getChangedFiles();

    Set<String> getAuthors();

    default boolean isAtomic() {
        return getFrom() == getTo();
    }

    default boolean isBranchSpecific() {
        return getBranches().size() == 1;
    }
}
