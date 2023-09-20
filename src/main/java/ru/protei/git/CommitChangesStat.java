package ru.protei.git;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.eclipse.jgit.lib.ObjectId;
import ru.protei.analytics.ChangesStat;

import java.time.ZonedDateTime;
import java.util.Set;

@Getter
@AllArgsConstructor
public class CommitChangesStat implements ChangesStat {
    private final @NonNull ZonedDateTime time;
    private final int linesChanged;
    private final @NonNull Set<String> changedFiles;
    private final @NonNull Set<String> authors;
    private final @NonNull Set<String> branches;
    private final @NonNull ObjectId commitId;

    @Override
    public ZonedDateTime getFrom() {
        return time;
    }

    @Override
    public ZonedDateTime getTo() {
        return time;
    }

    @Override
    public String toString() {
        return String.format("%s: %d lines changed in %d files by %s", time, linesChanged, changedFiles.size(), authors);
    }
}
