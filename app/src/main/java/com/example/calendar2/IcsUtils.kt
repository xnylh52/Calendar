package com.example.calendar2

import com.example.calendar2.data.Event
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object IcsUtils {

    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val floatingFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun generateIcs(events: List<Event>): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\n")
        sb.append("VERSION:2.0\n")
        sb.append("PRODID:-//My Calendar App//EN\n")

        for (event in events) {
            sb.append("BEGIN:VEVENT\n")
            sb.append("UID:${event.id}@mycalendar\n")
            sb.append("SUMMARY:${escape(event.title)}\n")
            if (!event.description.isNullOrEmpty()) {
                sb.append("DESCRIPTION:${escape(event.description)}\n")
            }
            if (!event.location.isNullOrEmpty()) {
                sb.append("LOCATION:${escape(event.location)}\n")
            }
            
            val start = Instant.ofEpochMilli(event.startTime).atZone(ZoneId.of("UTC"))
            val end = Instant.ofEpochMilli(event.endTime).atZone(ZoneId.of("UTC"))
            
            sb.append("DTSTART:${formatter.format(start)}\n")
            sb.append("DTEND:${formatter.format(end)}\n")
            sb.append("END:VEVENT\n")
        }

        sb.append("END:VCALENDAR\n")
        return sb.toString()
    }

    fun parseIcs(inputStream: InputStream): List<Event> {
        val events = mutableListOf<Event>()
        val lines = unfoldLines(inputStream)
        
        var inEvent = false
        var title = ""
        var description: String? = null
        var location: String? = null
        var startTime = 0L
        var endTime = 0L
        
        for (line in lines) {
            val parts = line.split(":", limit = 2)
            if (parts.size < 2) continue
            
            val keyPart = parts[0]
            val value = parts[1]
            val key = keyPart.split(";")[0]

            if (key == "BEGIN" && value == "VEVENT") {
                inEvent = true
                title = ""
                description = null
                location = null
                startTime = 0L
                endTime = 0L
            } else if (key == "END" && value == "VEVENT") {
                if (inEvent && title.isNotEmpty()) {
                    events.add(Event(
                        title = title,
                        description = description,
                        location = location,
                        startTime = startTime,
                        endTime = endTime,
                        isAllDay = false
                    ))
                }
                inEvent = false
            } else if (inEvent) {
                when (key) {
                    "SUMMARY" -> title = unescape(value)
                    "DESCRIPTION" -> description = unescape(value)
                    "LOCATION" -> location = unescape(value)
                    "DTSTART" -> startTime = parseDate(value)
                    "DTEND" -> endTime = parseDate(value)
                }
            }
        }
        return events
    }
    
    private fun unfoldLines(inputStream: InputStream): List<String> {
        val lines = mutableListOf<String>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line = reader.readLine()
        while (line != null) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (lines.isNotEmpty()) {
                    val last = lines.removeAt(lines.lastIndex)
                    lines.add(last + line.substring(1))
                }
            } else {
                lines.add(line)
            }
            line = reader.readLine()
        }
        return lines
    }

    private fun escape(s: String): String {
        return s.replace("\n", "\\n").replace(",", "\\,")
    }
    
    private fun unescape(s: String): String {
        return s.replace("\\n", "\n").replace("\\,", ",")
    }

    private fun parseDate(s: String): Long {
        return try {
            val clean = s.trim()
            if (clean.length == 8) {
                 val date = LocalDate.parse(clean, dateFormatter)
                 return date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            }
            
            if (clean.endsWith("Z")) {
                 val ta = LocalDateTime.parse(clean, formatter)
                 return ta.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
            }
            
             val ta = LocalDateTime.parse(clean, floatingFormatter)
             return ta.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
             
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
