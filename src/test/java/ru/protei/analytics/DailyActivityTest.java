package ru.protei.analytics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DailyActivityTest {

    @Test
    void getTimeActivityPerBranch() {
        var dailyActivity = new DailyActivity(null);
        dailyActivity.addActivity("master", 10);
        dailyActivity.addActivity("master2", 1);
        dailyActivity.addActivity("develop", 20);

        var timeActivityPerBranch = dailyActivity.getTimeActivityPerBranch();
        assertEquals(3, timeActivityPerBranch.size());
        assertEquals("3h", timeActivityPerBranch.get("master"));
        assertEquals("1h", timeActivityPerBranch.get("master2"));
        assertEquals("4h", timeActivityPerBranch.get("develop"));
    }

}