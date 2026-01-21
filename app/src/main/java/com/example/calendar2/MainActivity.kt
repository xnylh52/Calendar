package com.example.calendar2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calendar2.data.AppDatabase
import com.example.calendar2.data.Event
import com.example.calendar2.databinding.ActivityMainBinding
import com.example.calendar2.databinding.CalendarDayLayoutBinding
import com.example.calendar2.viewmodel.EventViewModel
import com.example.calendar2.viewmodel.EventViewModelFactory
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.WeekDay
import com.kizitonwose.calendar.core.atStartOfMonth
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.kizitonwose.calendar.view.WeekDayBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: EventViewModel
    private var selectedDate: LocalDate = LocalDate.now()
    private val eventsAdapter = EventsAdapter()
    
    // Cache for events to show dots on calendar
    private val eventsByDate = mutableMapOf<LocalDate, List<Event>>()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        uri?.let { exportEvents(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importEvents(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // Setup ViewModel
        val dao = AppDatabase.getDatabase(this).eventDao()
        val factory = EventViewModelFactory(dao)
        viewModel = ViewModelProvider(this, factory)[EventViewModel::class.java]

        setupRecyclerView()
        setupCalendar()
        setupButtons()
        observeEvents()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import -> {
                importLauncher.launch(arrayOf("text/calendar", "text/plain", "*/*"))
                true
            }
            R.id.action_export -> {
                exportLauncher.launch("calendar_export.ics")
                true
            }
            R.id.action_subscribe -> {
                showSubscribeDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSubscribeDialog() {
        val input = EditText(this)
        input.hint = "https://example.com/calendar.ics"
        
        AlertDialog.Builder(this)
            .setTitle("Subscribe to Calendar")
            .setView(input)
            .setPositiveButton("Subscribe") { _, _ ->
                val url = input.text.toString()
                if (url.isNotBlank()) {
                    subscribeToCalendar(url)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun subscribeToCalendar(urlString: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { inputStream ->
                        val events = IcsUtils.parseIcs(inputStream)
                        withContext(Dispatchers.Main) {
                            events.forEach { viewModel.insert(it) }
                            Toast.makeText(this@MainActivity, "Subscribed: Added ${events.size} events", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Failed to connect: ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Subscription failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importEvents(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val events = IcsUtils.parseIcs(inputStream)
                    withContext(Dispatchers.Main) {
                        events.forEach { viewModel.insert(it) }
                        Toast.makeText(this@MainActivity, "Imported ${events.size} events", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportEvents(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val events = viewModel.allEvents.first()
                val icsContent = IcsUtils.generateIcs(events)
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(icsContent.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Export successful", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.eventsRecyclerView.adapter = eventsAdapter
        
        eventsAdapter.onItemClick = { event ->
             val intent = Intent(this, AddEventActivity::class.java).apply {
                 putExtra("EVENT_ID", event.id)
             }
             startActivity(intent)
        }
    }

    private fun setupButtons() {
        binding.fabAddEvent.setOnClickListener {
            val intent = Intent(this, AddEventActivity::class.java).apply {
                putExtra("SELECTED_DATE", selectedDate.toEpochDay())
            }
            startActivity(intent)
        }

        binding.viewModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnMonth -> {
                        binding.calendarView.isVisible = true
                        binding.weekCalendarView.isVisible = false
                        binding.calendarView.scrollToMonth(YearMonth.from(selectedDate))
                    }
                    R.id.btnWeek -> {
                        binding.calendarView.isVisible = false
                        binding.weekCalendarView.isVisible = true
                        binding.weekCalendarView.scrollToWeek(selectedDate)
                    }
                }
            }
        }
    }

    private fun setupCalendar() {
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(100)
        val endMonth = currentMonth.plusMonths(100)
        val firstDayOfWeek = firstDayOfWeekFromLocale()

        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.day = data
                bindDayView(container, data.date, data.position == DayPosition.MonthDate)
            }
        }
        
        binding.calendarView.monthScrollListener = { month ->
            binding.monthYearText.text = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA).format(month.yearMonth)
        }

        binding.calendarView.setup(startMonth, endMonth, firstDayOfWeek)
        binding.calendarView.scrollToMonth(currentMonth)

        binding.weekCalendarView.dayBinder = object : WeekDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: WeekDay) {
                container.day = CalendarDay(data.date, DayPosition.MonthDate) 
                bindDayView(container, data.date, true)
            }
        }
        
        binding.weekCalendarView.weekScrollListener = { weekDays ->
            val firstDate = weekDays.days.first().date
             binding.monthYearText.text = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA).format(YearMonth.from(firstDate))
        }
        
        binding.weekCalendarView.setup(startMonth.atStartOfMonth(), endMonth.atEndOfMonth(), firstDayOfWeek)
        binding.weekCalendarView.scrollToWeek(selectedDate)
        
        updateSelectedDateText()
    }

    private fun bindDayView(container: DayViewContainer, date: LocalDate, isCurrentMonth: Boolean) {
        container.textView.text = date.dayOfMonth.toString()
        
        container.lunarTextView.text = LunarUtils.getLunarDay(date)

        if (isCurrentMonth) {
            container.textView.setTextColor(getColor(R.color.black))
            container.lunarTextView.isVisible = true
        } else {
            container.textView.setTextColor(getColor(android.R.color.darker_gray))
            container.lunarTextView.isVisible = false
        }

        if (date == selectedDate) {
            container.view.setBackgroundResource(R.drawable.bg_selected_day)
        } else {
            container.view.background = null
        }
        
        val events = eventsByDate[date]
        container.dotView.isVisible = !events.isNullOrEmpty()
    }
    
    inner class DayViewContainer(view: View) : ViewContainer(view) {
        val textView: TextView = CalendarDayLayoutBinding.bind(view).calendarDayText
        val lunarTextView: TextView = CalendarDayLayoutBinding.bind(view).lunarDayText
        val dotView: View = CalendarDayLayoutBinding.bind(view).eventDot
        lateinit var day: CalendarDay

        init {
            view.setOnClickListener {
                if (day.position == DayPosition.MonthDate) {
                    selectDate(day.date)
                }
            }
        }
    }

    private fun selectDate(date: LocalDate) {
        if (selectedDate != date) {
            val oldDate = selectedDate
            selectedDate = date
            binding.calendarView.notifyDateChanged(oldDate)
            binding.calendarView.notifyDateChanged(date)
            binding.weekCalendarView.notifyDateChanged(oldDate)
            binding.weekCalendarView.notifyDateChanged(date)
            updateSelectedDateText()
            updateAdapterForDate(date)
        }
    }
    
    private fun updateSelectedDateText() {
        binding.selectedDateText.text = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA).format(selectedDate)
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.allEvents.collectLatest { events ->
                eventsByDate.clear()
                events.forEach { event ->
                    val date = Instant.ofEpochMilli(event.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
                    val list = eventsByDate.getOrPut(date) { mutableListOf() }
                    (list as MutableList).add(event)
                }
                
                binding.calendarView.notifyCalendarChanged()
                binding.weekCalendarView.notifyCalendarChanged()
                
                updateAdapterForDate(selectedDate)
            }
        }
    }
    
    private fun updateAdapterForDate(date: LocalDate) {
        val events = eventsByDate[date] ?: emptyList()
        eventsAdapter.submitList(events)
    }
}
