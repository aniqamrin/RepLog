package com.replog.app.domain.logic

object StreakCalculator {

    fun currentStreak(qualifyingDays: Set<Long>, todayEpochDay: Long): Int {
        if (qualifyingDays.isEmpty()) return 0
        var cursor = todayEpochDay
        if (!qualifyingDays.contains(cursor)) {
            cursor -= 1
            if (!qualifyingDays.contains(cursor)) return 0
        }
        var streak = 0
        while (qualifyingDays.contains(cursor)) {
            streak++
            cursor--
        }
        return streak
    }

    fun longestStreak(qualifyingDays: Set<Long>): Int {
        if (qualifyingDays.isEmpty()) return 0
        val sorted = qualifyingDays.sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1
            if (run > longest) longest = run
        }
        return longest
    }

    fun overallStreak(days: Set<Long>, todayEpochDay: Long): Int = currentStreak(days, todayEpochDay)
}
