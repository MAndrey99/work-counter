package ru.protei.git;

import lombok.Getter;
import lombok.ToString;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@ToString
public class CombinatedChangesStat implements ChangesStat {
    private final List<ChangesStat> changesStats;
    
    public CombinatedChangesStat(List<ChangesStat> changesStats) {
        this.changesStats = changesStats;
    }
    
    @Override
    public int getLinesChanged() {
        return changesStats.stream()
                .mapToInt(ChangesStat::getLinesChanged)
                .sum();
    }

    @Override
    public Set<String> getChangedFiles() {
        var result = new HashSet<String>();
        changesStats.forEach(changesStat -> result.addAll(changesStat.getChangedFiles()));
        return result;
    }

    @Override
    public Set<String> getAuthors() {
        var result = new HashSet<String>();
        changesStats.forEach(changesStat -> result.addAll(changesStat.getAuthors()));
        return result;
    }

    @Override
    public ZonedDateTime getFrom() {
        return changesStats.stream()
                .map(ChangesStat::getFrom)
                .min(ZonedDateTime::compareTo)
                .orElse(null);
    }

    @Override
    public ZonedDateTime getTo() {
        return changesStats.stream()
                .map(ChangesStat::getTo)
                .max(ZonedDateTime::compareTo)
                .orElse(null);
    }
}
