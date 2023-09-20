package ru.protei.analytics;

import lombok.Getter;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DailyActivity {
    private static final int MIN_WORKING_HOURS_PER_BRANCH = 1;
    private static final int TARGET_WORKING_HOURS_PER_DAY = 8;

    private final Map<String, Integer> activityPerBranch;
    private @Getter int totalScore;
    private final @Getter LocalDate date;

    public DailyActivity(LocalDate date) {
        this.activityPerBranch = new HashMap<>();
        this.totalScore = 0;
        this.date = date;
    }

    public void addActivity(String branch, int score) {
        // TODO: должно быть не более 8 веток в день
        activityPerBranch.put(branch, activityPerBranch.getOrDefault(branch, 0) + score);
        totalScore += score;
    }

    public int getActivity(String branch) {
        return activityPerBranch.getOrDefault(branch, 0);
    }

    public float getRelativeActivity(String branch) {
        return Math.round((float) getActivity(branch) / totalScore * 100) / 100f;
    }

    public Map<String, String> getTimeActivityPerBranch() {
        Map<String, Float> relativeActivity = getBranches().stream()
                .collect(Collectors.toMap(Function.identity(), this::getRelativeActivity));
        if (relativeActivity.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> hoursActivity = relativeActivity.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> Math.max(
                                Math.round(e.getValue() * TARGET_WORKING_HOURS_PER_DAY),
                                MIN_WORKING_HOURS_PER_BRANCH)));

        int totalWorkingHours = hoursActivity.values().stream().mapToInt(val -> val).sum();
        while (totalWorkingHours != TARGET_WORKING_HOURS_PER_DAY) {
            int diff = totalWorkingHours - TARGET_WORKING_HOURS_PER_DAY;
            if (diff > 0) {
                String branchToSteal = relativeActivity.entrySet().stream()
                        .filter(entry -> hoursActivity.get(entry.getKey()) > MIN_WORKING_HOURS_PER_BRANCH)
                        .max(Comparator.comparingDouble(Map.Entry::getValue))
                        .orElseThrow().getKey();
                hoursActivity.put(branchToSteal, hoursActivity.get(branchToSteal) - MIN_WORKING_HOURS_PER_BRANCH);
                totalWorkingHours--;
            } else {
                String branchToSteal = relativeActivity.entrySet().stream()
                        .min(Comparator.comparingDouble(Map.Entry::getValue))
                        .orElseThrow().getKey();
                hoursActivity.put(branchToSteal, hoursActivity.get(branchToSteal) + MIN_WORKING_HOURS_PER_BRANCH);
                totalWorkingHours++;
            }
        }

        return hoursActivity.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.format("%dh", entry.getValue())));
    }

    public static int findClosestTimeSlot(List<Integer> timeSlots, float activityFraction, int remainingMinutes) {
        int targetTime = Math.round(remainingMinutes * activityFraction);
        return Collections.min(timeSlots, Comparator.comparingInt(slot -> Math.abs(slot - targetTime)));
    }

    public Collection<String> getBranches() {
        return activityPerBranch.keySet();
    }

    public void stealActivity(DailyActivity other, int totalToSteal) {
        if (totalToSteal == 0) {
            return;
        }

        // TODO: надо бы красть из веток в которых коммиты были раньше в приоритете
        Set<String> commonBranches = new HashSet<>(getBranches());
        commonBranches.retainAll(other.getBranches());
        Function<String, Float> multiplier = branch -> commonBranches.contains(branch) ? 1f : 0.5f;

        String otherTopActivityBranch = other.getBranches().stream()
                .max(Comparator.comparingInt((String b) -> (int) (other.getActivity(b) * multiplier.apply(b))))
                .orElseThrow();
        int otherTopActivity = other.getActivity(otherTopActivityBranch);
        int stolenActivity = Math.min(otherTopActivity, totalToSteal);
        other.addActivity(otherTopActivityBranch, -stolenActivity);
        addActivity(otherTopActivityBranch, stolenActivity);

        if (totalToSteal == stolenActivity) {
            if (otherTopActivity == stolenActivity) {
                other.activityPerBranch.remove(otherTopActivityBranch);
            }
        } else {
            stealActivity(other, totalToSteal - stolenActivity);
        }
    }

    @Override
    public String toString() {
        return String.format("%s: %d total score, %s", date, totalScore, getTimeActivityPerBranch());
    }
}
