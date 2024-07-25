package ca.pkay.rcloneexplorer.Activities


import androidx.appcompat.app.AppCompatActivity
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import androidx.cardview.widget.CardView
import android.widget.EditText
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TimePicker
import android.os.Bundle
import ca.pkay.rcloneexplorer.util.ActivityHelper
import ca.pkay.rcloneexplorer.R
import es.dmoral.toasty.Toasty
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.text.TextWatcher
import android.text.Editable
import android.text.format.DateFormat
import android.view.View
import android.widget.CompoundButton
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.widget.Toolbar
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Items.Trigger
import ca.pkay.rcloneexplorer.Services.TriggerService

class TriggerActivity : AppCompatActivity() {

    companion object {
        const val ID_EXTRA = "TRIGGER_EDIT_ID"
        const val ID_ALL_TASKS = -1000L
        const val UNICODE_CHAR_RANGE = -10_000_000L
    }

    private lateinit var mTrigger: Trigger
    private lateinit var dbHandler: DatabaseHandler
    private var mTaskList: List<Task> = ArrayList()
    private var firstCharOfTasksUnique: List<Char> = ArrayList<Char>()

    private lateinit var mCardInterval: CardView
    private lateinit var mCardWeekday: CardView
    private lateinit var mCardTime: CardView

    private lateinit var mTitle: EditText
    private lateinit var mEnabled: CheckBox
    private lateinit var mType: Spinner
    private lateinit var mInterval: Spinner

    private lateinit var mWeekdayMon: CheckBox
    private lateinit var mWeekdayTue: CheckBox
    private lateinit var mWeekdayWed: CheckBox
    private lateinit var mWeekdayThu: CheckBox
    private lateinit var mWeekdayFri: CheckBox
    private lateinit var mWeekdaySat: CheckBox
    private lateinit var mWeekdaySun: CheckBox

    private lateinit var mTargetDropdown: Spinner
    private lateinit var mTimepicker: TimePicker


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)
        setContentView(R.layout.activity_trigger)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)


        mCardInterval = findViewById(R.id.intervalCard)
        mCardWeekday = findViewById(R.id.weekdaysCard)
        mCardTime = findViewById(R.id.timeCard)
        mTitle = findViewById(R.id.trigger_name_edit)
        mEnabled = findViewById(R.id.cb_is_enabled)
        mType = findViewById(R.id.triggerType)
        mInterval = findViewById(R.id.spinnerIntervals)

        mWeekdayMon = findViewById(R.id.trigger_cb_monday)
        mWeekdayTue = findViewById(R.id.trigger_cb_tuesday)
        mWeekdayWed = findViewById(R.id.trigger_cb_wednesday)
        mWeekdayThu = findViewById(R.id.trigger_cb_thursday)
        mWeekdayFri = findViewById(R.id.trigger_cb_friday)
        mWeekdaySat = findViewById(R.id.trigger_cb_saturday)
        mWeekdaySun = findViewById(R.id.trigger_cb_sunday)
        mTimepicker = findViewById(R.id.trigger_time)
        mTargetDropdown = findViewById(R.id.trigger_targets)

        dbHandler = DatabaseHandler(this)
        mTaskList = dbHandler.allTasks

        if (this.mTaskList.isNotEmpty()) {
            val firstCharsOfTaskTitles : HashSet<Char> = HashSet<Char>()
            for (i in this.mTaskList.indices) {
                firstCharsOfTaskTitles.add(this.mTaskList[i].title.trim().uppercase().first())
            }
            firstCharOfTasksUnique = firstCharsOfTaskTitles.toList().sortedBy { it.toString() }
        }

        val extras = intent.extras
        val triggerId: Long
        if (extras != null) {
            triggerId = extras.getLong(ID_EXTRA)
            if (triggerId != 0L) {
                val notNullTrigger = dbHandler.getTrigger(triggerId)
                if (notNullTrigger == null) {
                    Toasty.error(
                        this,
                        this.resources.getString(R.string.triggeractivity_trigger_not_found)
                    ).show()
                    finish()
                }
                mTrigger = notNullTrigger!!
            }
        } else {
            mTrigger = Trigger(Trigger.TRIGGER_ID_DOESNTEXIST)
        }
        
        val saveButton = findViewById<FloatingActionButton>(R.id.saveButton)
        saveButton.setOnClickListener { saveTrigger() }
        setUpTargetsDropdown()

        // Todo: use KTX Extensions. https://stackoverflow.com/a/60409004
        mTitle.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                mTrigger.title = s.toString()
            }
        })

        mEnabled.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            mTrigger.isEnabled = isChecked
        }

        val initialTriggerType = mTrigger.type
        mType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, pos: Int, id: Long) {
                if (pos == Trigger.TRIGGER_TYPE_SCHEDULE) {
                    mTrigger.type = Trigger.TRIGGER_TYPE_SCHEDULE
                } else {
                    mTrigger.type = Trigger.TRIGGER_TYPE_INTERVAL
                }

                //update ui here, because typechanges also changes displayed items.
                updateUiFromTrigger()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        //restore initial triggerstate here
        mType.setSelection(initialTriggerType)
        mWeekdayMon.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            mTrigger.setEnabledAtDay(
                Trigger.TRIGGER_DAY_MON, isChecked
            )
        }
        mWeekdayTue.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            mTrigger.setEnabledAtDay(
                Trigger.TRIGGER_DAY_TUE, isChecked
            )
        }
        mWeekdayWed.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            mTrigger.setEnabledAtDay(
                Trigger.TRIGGER_DAY_WED, isChecked
            )
        }
        mWeekdayThu.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            mTrigger.setEnabledAtDay(
                Trigger.TRIGGER_DAY_THU, isChecked
            )
        }
        mWeekdayFri.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            mTrigger.setEnabledAtDay(
                Trigger.TRIGGER_DAY_FRI, isChecked
            )
        }
        mWeekdaySat.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            mTrigger.setEnabledAtDay(
                Trigger.TRIGGER_DAY_SAT, isChecked
            )
        }
        mWeekdaySun.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            mTrigger.setEnabledAtDay(
                Trigger.TRIGGER_DAY_SUN, isChecked
            )
        }
        mTimepicker.setOnTimeChangedListener { _: TimePicker?, hourOfDay: Int, minute: Int ->
            mTrigger.time = hourOfDay * 60 + minute
        }

        mInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, pos: Int, id: Long) {
                when (pos) {
                    0 -> mTrigger.time = 15
                    1 -> mTrigger.time = 30
                    3 -> mTrigger.time = 120
                    2 -> mTrigger.time = 60
                    else -> mTrigger.time = 60
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        mTargetDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, pos: Int, id: Long) {
                if (pos == (mTaskList.size)) {
                    mTrigger.triggerTarget = ID_ALL_TASKS
                } else if (pos > (mTaskList.size)) {
                    val charForGrouping = firstCharOfTasksUnique[pos - mTaskList.size - 1]
                    mTrigger.triggerTarget = UNICODE_CHAR_RANGE - charForGrouping.code.toLong()
                } else {
                    mTrigger.triggerTarget = mTaskList[pos].id
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        updateUiFromTrigger()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * Set up Task-Target Dropdown
     */
    private fun setUpTargetsDropdown() {
        val items = arrayOfNulls<String>(if (this.mTaskList.size == 0) 0 else (this.mTaskList.size + firstCharOfTasksUnique.size + 1))
        for (i in this.mTaskList.indices) {
            items[i] = this.mTaskList[i].title
        }
        if (this.mTaskList.size > 0) items[this.mTaskList.size] = this.resources.getString(R.string.sync_option_all_tasks_asc)
        for (i in firstCharOfTasksUnique.indices) {
            items[i + this.mTaskList.size + 1] = this.resources.getString(R.string.sync_option_all) + " " + firstCharOfTasksUnique[i] + this.resources.getString(R.string.sync_option_all_tasks_prefixed_asc)
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        mTargetDropdown.adapter = adapter
    }

    private fun updateUiFromTrigger() {
        if (DateFormat.is24HourFormat(this)) {
            mTimepicker.setIs24HourView(true)
        }
        mCardInterval.visibility = View.GONE
        mCardWeekday.visibility = View.GONE
        mCardTime.visibility = View.GONE
        mTitle.setText(mTrigger.title)
        mEnabled.isChecked = mTrigger.isEnabled

        // seconds if time is schedule, otherwise minutes (as in interval).
        val timeValue = mTrigger.time
        if (mTrigger.type == Trigger.TRIGGER_TYPE_SCHEDULE) {
            mCardWeekday.visibility = View.VISIBLE
            mCardTime.visibility = View.VISIBLE
            mWeekdayMon.isChecked = mTrigger.isEnabledAtDay(Trigger.TRIGGER_DAY_MON)
            mWeekdayTue.isChecked = mTrigger.isEnabledAtDay(Trigger.TRIGGER_DAY_TUE)
            mWeekdayWed.isChecked = mTrigger.isEnabledAtDay(Trigger.TRIGGER_DAY_WED)
            mWeekdayThu.isChecked = mTrigger.isEnabledAtDay(Trigger.TRIGGER_DAY_THU)
            mWeekdayFri.isChecked = mTrigger.isEnabledAtDay(Trigger.TRIGGER_DAY_FRI)
            mWeekdaySat.isChecked = mTrigger.isEnabledAtDay(Trigger.TRIGGER_DAY_SAT)
            mWeekdaySun.isChecked = mTrigger.isEnabledAtDay(Trigger.TRIGGER_DAY_SUN)
            mTimepicker.hour = timeValue / 60
            mTimepicker.minute = timeValue % 60
        } else {
            mCardInterval.visibility = View.VISIBLE
            when (timeValue) {
                15 -> mInterval.setSelection(0)
                30 -> mInterval.setSelection(1)
                120 -> mInterval.setSelection(3)
                60 -> mInterval.setSelection(2)
                else -> mInterval.setSelection(2)
            }
        }

        //Todo properly populate the fields
        for (task in mTaskList) {
            if (mTrigger.triggerTarget == ID_ALL_TASKS) {
                mTargetDropdown.setSelection(mTaskList.size)
            } else if (task.id == mTrigger.triggerTarget) {
                mTargetDropdown.setSelection(mTaskList.indexOf(task))
            } else if (UNICODE_CHAR_RANGE - mTrigger.triggerTarget >= 0) {
                val firstChar = (UNICODE_CHAR_RANGE - mTrigger.triggerTarget).toInt().toChar()
                if (firstCharOfTasksUnique.contains(firstChar)) {
                    mTargetDropdown.setSelection(mTaskList.size + firstCharOfTasksUnique.indexOf(firstChar) + 1)
                }
            }
        }
    }

    private fun checkTaskExistence(): Boolean {
        if (mTaskList.isEmpty()) {
            Toasty.error(this, this.resources.getString(R.string.trigger_save_notasks)).show()
            return false
        }
        return true
    }

    private fun saveTrigger() {
        // check if title is set
        if (mTrigger.title.isBlank()) {
            Toasty.error(this, this.resources.getString(R.string.trigger_save_notitle)).show()
            return
        }

        // check if target task is set
        if (!checkTaskExistence()) {
            Toasty.error(this, this.resources.getString(R.string.trigger_save_notasks)).show()
            return
        }
        if (mTrigger.id == Trigger.TRIGGER_ID_DOESNTEXIST) {
            mTrigger = dbHandler.createTrigger(mTrigger)
        } else {
            dbHandler.updateTrigger(mTrigger)
        }
        TriggerService(this).queueSingleTrigger(mTrigger)
        finish()
    }
}