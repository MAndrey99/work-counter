package ru.protei.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ActivityAnalyzer {
    private final CombinatedChangesStat changes;

    /**
     * Анализ с учетом того, что коммит мог быть создан после создания изменений.
     */
    public List<DailyActivity> linearAnalyze() {
        List<DailyActivity> analyzed = linealDistributeFromWeekend(analyze());
        for (int i = analyzed.size() - 1; i > 0; i--) {
            var prev = analyzed.get(i - 1);
            var cur = analyzed.get(i);
            if (prev.getTotalScore() * 2 < cur.getTotalScore()) {
                log.debug("Stealing {} from {} to {}", cur.getTotalScore(), cur.getDate(), prev.getDate());
                prev.stealActivity(cur, (cur.getTotalScore() + prev.getTotalScore()) / 2);
            }
        }
        log.info("Linear Analyze Result: \n{}", formatResult(analyzed));
        return analyzed;
    }

    public List<DailyActivity> analyze() {
        var result = buildEmptyDailyActivities();
        Map<LocalDate, List<ChangesStat>> dailyChanges = changes.split()
                .collect(Collectors.toMap(cs -> cs.getFrom().toLocalDate(),
                        cs -> new ArrayList<>(List.of(cs)), (a, b) -> {
                    a.addAll(b);
                    return a;
                }));
        for (var dailyActivity : result) {
            var changesStats = dailyChanges.get(dailyActivity.getDate());
            if (changesStats != null) {
                for (var changesStat : changesStats) {
                    Set<String> branches = changesStat.getBranches();
                    if (branches.size() == 2) {
                        branches = branches.stream()
                                .filter(branch -> !branch.contains("master"))
                                .collect(Collectors.toSet());
                    }
                    String branch = branches.size() == 1 ? branches.iterator().next() : "unknown";
                    dailyActivity.addActivity(branch, changesStat.getLinesChanged());
                }
            }
        }
        log.info("Analyze Result: \n{}", formatResult(result));
        return result;
    }

    private List<DailyActivity> linealDistributeFromWeekend(List<DailyActivity> analyzed) {
        var result = new LinkedList<DailyActivity>();
        boolean isFirst = true;
        for (int i = analyzed.size() - 1; i > 0; i--) {
            var prev = analyzed.get(i - 1);
            var cur = analyzed.get(i);
            if (isWeekend(cur.getDate())) {
                log.debug("Stealing {} from {} to {}", cur.getTotalScore(), cur.getDate(), prev.getDate());
                prev.stealActivity(cur, cur.getTotalScore());
            } else if (isFirst) {
                result.addFirst(cur);
                isFirst = false;
            }
            if (!isWeekend(prev.getDate())) {
                result.addFirst(prev);
            }
        }
        return result;
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private List<DailyActivity> buildEmptyDailyActivities() {
        var from = changes.getFrom().toLocalDate();
        var to = changes.getTo().toLocalDate();
        var result = new ArrayList<DailyActivity>();
        for (var date = from; date.isBefore(to); date = date.plusDays(1)) {
            result.add(new DailyActivity(date));
        }
        return result;
    }

    private static String formatResult(List<DailyActivity> result) {
        return result.stream()
                .map(DailyActivity::toString)
                .collect(Collectors.joining("\n"));
    }
}
