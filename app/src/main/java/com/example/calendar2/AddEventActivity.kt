package com.example.calendar2

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.calendar2.data.AppDatabase
import com.example.calendar2.data.Event
import com.example.calendar2.databinding.ActivityAddEventBinding
import com.example.calendar2.reminder.ReminderManager
import com.example.calendar2.viewmodel.EventViewModel
import com.example.calendar2.viewmodel.EventViewModelFactory
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AddEventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEventBinding
    private lateinit var viewModel: EventViewModel
    private var eventId: Long = -1
    
    private var startDateTime: LocalDateTime = LocalDateTime.now()
    private var endDateTime: LocalDateTime = LocalDateTime.now().plusHours(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dao = AppDatabase.getDatabase(this).eventDao()
        val factory = EventViewModelFactory(dao)
        viewModel = ViewModelProvider(this, factory)[EventViewModel::class.java]

        setupSpinner()
        setupPickers()
        
        if (intent.hasExtra("EVENT_ID")) {
            eventId = intent.getLongExtra("EVENT_ID", -1)
            loadEvent(eventId)
        } else {
            val selectedDateEpoch = intent.getLongExtra("SELECTED_DATE", -1)
            if (selectedDateEpoch != -1L) {
                val date = LocalDate.ofEpochDay(selectedDateEpoch)
                startDateTime = date.atTime(LocalTime.now())
                endDateTime = startDateTime.plusHours(1)
            }
            updateDateTimeViews()
        }

        binding.btnSave.setOnClickListener { saveEvent() }
        binding.btnDelete.setOnClickListener { deleteEvent() }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.reminder_options,
            android.R.layout.simple_spinner_dropdown_item
        )
        binding.spinnerReminder.adapter = adapter
    }

    private fun setupPickers() {
        binding.btnStartDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                startDateTime = startDateTime.withYear(y).withMonth(m + 1).withDayOfMonth(d)
                updateDateTimeViews()
            }, startDateTime.year, startDateTime.monthValue - 1, startDateTime.dayOfMonth).show()
        }

        binding.btnStartTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                startDateTime = startDateTime.withHour(h).withMinute(m)
                updateDateTimeViews()
            }, startDateTime.hour, startDateTime.minute, true).show()
        }

        binding.btnEndDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                endDateTime = endDateTime.withYear(y).withMonth(m + 1).withDayOfMonth(d)
                updateDateTimeViews()
            }, endDateTime.year, endDateTime.monthValue - 1, endDateTime.dayOfMonth).show()
        }

        binding.btnEndTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                endDateTime = endDateTime.withHour(h).withMinute(m)
                updateDateTimeViews()
            }, endDateTime.hour, endDateTime.minute, true).show()
        }
    }

    private fun updateDateTimeViews() {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        binding.btnStartDate.text = startDateTime.format(dateFormatter)
        binding.btnStartTime.text = startDateTime.format(timeFormatter)
        binding.btnEndDate.text = endDateTime.format(dateFormatter)
        binding.btnEndTime.text = endDateTime.format(timeFormatter)
    }

    private fun loadEvent(id: Long) {
        lifecycleScope.launch {
            // Better to add getEventById in ViewModel/Dao. I added it in Dao.
             val e = AppDatabase.getDatabase(this@AddEventActivity).eventDao().getEventById(id)
             if (e != null) {
                 binding.etTitle.setText(e.title)
                 binding.etDescription.setText(e.description)
                 binding.etLocation.setText(e.location)
                 binding.cbAllDay.isChecked = e.isAllDay
                 
                 startDateTime = Instant.ofEpochMilli(e.startTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
                 endDateTime = Instant.ofEpochMilli(e.endTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
                 updateDateTimeViews()

                 binding.btnDelete.visibility = android.view.View.VISIBLE
                 
                 // Set spinner
                 val minutes = e.reminderMinutesBefore
                 val position = when (minutes) {
                     10 -> 1
                     30 -> 2
                     60 -> 3
                     1440 -> 4
                     else -> 0
                 }
                 binding.spinnerReminder.setSelection(position)
             }
        }
    }

    private fun saveEvent() {
        val title = binding.etTitle.text.toString()
        if (title.isBlank()) {
            Toast.makeText(this, getString(R.string.toast_title_required), Toast.LENGTH_SHORT).show()
            return
        }

        val start = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (end < start) {
            Toast.makeText(this, getString(R.string.toast_end_time_error), Toast.LENGTH_SHORT).show()
            return
        }
        
        val reminderSelection = binding.spinnerReminder.selectedItemPosition
        val reminderMinutes = when (reminderSelection) {
            1 -> 10
            2 -> 30
            3 -> 60
            4 -> 1440
            else -> null
        }

        val event = Event(
            id = if (eventId == -1L) 0 else eventId,
            title = title,
            description = binding.etDescription.text.toString(),
            location = binding.etLocation.text.toString(),
            startTime = start,
            endTime = end,
            isAllDay = binding.cbAllDay.isChecked,
            reminderMinutesBefore = reminderMinutes
        )

        lifecycleScope.launch {
            val savedEvent = if (eventId == -1L) {
                val newId = viewModel.insertSync(event)
                event.copy(id = newId)
            } else {
                viewModel.updateSync(event)
                event
            }

            // Schedule Reminder
            if (reminderMinutes != null) {
                ReminderManager.scheduleReminder(this@AddEventActivity, savedEvent)
            } else {
                ReminderManager.cancelReminder(this@AddEventActivity, savedEvent)
            }

            finish()
        }
    }

    private fun deleteEvent() {
        if (eventId != -1L) {
             lifecycleScope.launch {
                 val event = AppDatabase.getDatabase(this@AddEventActivity).eventDao().getEventById(eventId)
                 if (event != null) {
                     viewModel.delete(event)
                     ReminderManager.cancelReminder(this@AddEventActivity, event)
                 }
                 finish()
             }
        }
    }
}
