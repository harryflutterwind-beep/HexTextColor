package com.example.examplemod.dailyweekly;

/**
 * Read-only snapshot used by the native Daily / Weekly GUI.
 *
 * Phase 1 intentionally leaves the CustomNPCs player script authoritative.
 * The script publishes an exact GUI view into PlayerPersisted and this object
 * carries that view to the client.
 */
public final class DailyWeeklySnapshot {

    public boolean bridged;
    public boolean operator;
    public boolean dailyClaimed;
    public boolean weeklyClaimed;
    public boolean dailyReady;
    public boolean weeklyReady;

    public int points;
    public int todayEarned;
    public int todayCap;
    public int capBase;
    public int capDaily;
    public int capWeekly;
    public int streak;

    public int dailyEcoGoal;
    public int dailyEcoProgress;
    public int weeklyEcoGoal;
    public int weeklyEcoProgress;

    public int dailySeriesCount;
    public int weeklySeriesCount;
    public int dailySeriesMask;
    public int weeklySeriesMask;

    public int dailyRerollsUsed;
    public int weeklyRerollsUsed;
    public int dailyRerollsMax;
    public int weeklyRerollsMax;

    public String dailyKey = "";
    public String weeklyKey = "";
    public String lastLogin = "";
    public String status = "";
}
