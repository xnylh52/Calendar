package com.example.calendar2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calendar2.data.Event
import com.example.calendar2.data.EventDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class EventViewModel(private val eventDao: EventDao) : ViewModel() {

    val allEvents: Flow<List<Event>> = eventDao.getAllEvents()

    fun getEventsForDate(date: LocalDate): Flow<List<Event>> {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        return eventDao.getEventsInRange(startOfDay, endOfDay)
    }
    
    fun getEventsInRange(start: Long, end: Long): Flow<List<Event>> {
        return eventDao.getEventsInRange(start, end)
    }

    fun insert(event: Event) = viewModelScope.launch {
        eventDao.insertEvent(event)
    }

    suspend fun insertSync(event: Event): Long {
        return eventDao.insertEvent(event)
    }

    fun update(event: Event) = viewModelScope.launch {
        eventDao.updateEvent(event)
    }

    suspend fun updateSync(event: Event) {
        eventDao.updateEvent(event)
    }

    fun delete(event: Event) = viewModelScope.launch {
        eventDao.deleteEvent(event)
    }
}

class EventViewModelFactory(private val eventDao: EventDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EventViewModel(eventDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
