package ru.protei.git;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.time.ZonedDateTime;
import java.util.Set;

@Getter
@AllArgsConstructor
@ToString
public class ChangesStatData implements ChangesStat {
    private final @NonNull ZonedDateTime time;
    private final int linesChanged;
    private final @NonNull Set<String> changedFiles;
    private final @NonNull Set<String> authors;

    @Override
    public ZonedDateTime getFrom() {
        return time;
    }

    @Override
    public ZonedDateTime getTo() {
        return time;
    }
}
