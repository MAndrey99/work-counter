package ru.protei.analytics;

import lombok.Getter;
import lombok.ToString;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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
                .orElseThrow();
    }

    @Override
    public ZonedDateTime getTo() {
        return changesStats.stream()
                .map(ChangesStat::getTo)
                .max(ZonedDateTime::compareTo)
                .orElseThrow();
    }

    @Override
    public Set<String> getBranches() {
        var result = new HashSet<String>();
        changesStats.forEach(changesStat -> result.addAll(changesStat.getBranches()));
        return result;
    }

    public Stream<ChangesStat> split() {
        return changesStats.stream()
                .flatMap(stat -> {
                    if (stat instanceof CombinatedChangesStat) {
                        return ((CombinatedChangesStat) stat).split();
                    } else {
                        return Stream.of(stat);
                    }
                });
    }

    public CombinatedChangesStat combine(CombinatedChangesStat other) {
        var resultStats = new HashSet<ChangesStat>();
        resultStats.addAll(changesStats);
        resultStats.addAll(other.changesStats);
        return new CombinatedChangesStat(List.copyOf(resultStats));
    }
}
