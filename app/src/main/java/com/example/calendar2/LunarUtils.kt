package com.example.calendar2

import android.icu.util.ChineseCalendar
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

object LunarUtils {
    private val chineseCalendar = ChineseCalendar()

    fun getLunarDay(date: LocalDate): String {
        val calendar = Calendar.getInstance()
        calendar.time = java.util.Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
        
        chineseCalendar.timeInMillis = calendar.timeInMillis
        
        val day = chineseCalendar.get(ChineseCalendar.DAY_OF_MONTH)
        // val month = chineseCalendar.get(ChineseCalendar.MONTH) + 1 // 0-indexed
        
        return getChineseDayString(day)
    }

    private fun getChineseDayString(day: Int): String {
        val chineseDays = arrayOf(
            "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
        )
        return if (day in 1..30) chineseDays[day - 1] else ""
    }
}
