package ca.pkay.rcloneexplorer.Activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.FilePicker
import ca.pkay.rcloneexplorer.Fragments.FolderSelectorCallback
import ca.pkay.rcloneexplorer.Fragments.RemoteFolderPickerFragment
import ca.pkay.rcloneexplorer.Items.Filter
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Items.SyncDirectionObject
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.SpinnerAdapters.FilterSpinnerAdapter
import ca.pkay.rcloneexplorer.util.ActivityHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import es.dmoral.toasty.Toasty
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

class TaskActivity : AppCompatActivity(), FolderSelectorCallback{


    private lateinit var rcloneInstance: Rclone
    private lateinit var dbHandler: DatabaseHandler

    private lateinit var syncDescription: TextView
    private lateinit var remotePath: EditText
    private lateinit var localPath: EditText
    private lateinit var remoteDropdown: Spinner
    private lateinit var syncDirection: Spinner
    private lateinit var transfersDropdown: Spinner
    private lateinit var fab: FloatingActionButton

    // Destination remote for cloud-to-cloud directions (7/8).
    private lateinit var remotePath2: EditText
    private lateinit var remoteDropdown2: Spinner
    private lateinit var taskLocalCard: View
    private lateinit var taskRemote2Card: View

    private lateinit var switchWifi: Switch
    private lateinit var switchMD5sum: Switch


    private lateinit var filterDropdown: Spinner
    private lateinit var createNewFilterButton: Button
    private lateinit var switchDeleteExcluded: Switch


    private lateinit var onFailDropdown: Spinner
    private lateinit var onSuccessDropdown: Spinner


    private lateinit var filterOptionsButton: ImageButton


    private var existingTask: Task? = null
    private var remotePathHolder = ""
    private var remotePathHolder2 = ""
    // Tracks which remote field the RemoteFolderPickerFragment is choosing a path for.
    private var pickingDestinationRemote = false
    private var selectedFilter: Filter? = null



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_FP_LOCAL -> {
                if (data != null) {
                    localPath.setText(data.getStringExtra(FilePicker.FILE_PICKER_RESULT))
                }
                localPath.clearFocus()
            }
            REQUEST_CODE_FP_REMOTE -> if (data != null) {
                var path = data.data.toString()
                try {
                    path = URLDecoder.decode(path, "UTF-8")
                } catch (e: UnsupportedEncodingException) {
                }

                // Todo: check if this provider is still valid; search other occurences
                Log.e("TaskActivity provider", "recieved path: $path")
                val provider = "content://io.github.x0b.rcx.vcp/tree/rclone/remotes/"
                if (path.startsWith(provider)) {
                    val parts = path.substring(provider.length).split(":").toTypedArray()
                    remotePath.setText(parts[1])
                    var i = 0
                    for (remote in remoteItems) {
                        if (remote == parts[0]) {
                            remoteDropdown.setSelection(i)
                        }
                        i++
                    }
                } else {
                    Toasty.error(this, "This Remote is not a RCX-Remote.").show()
                }
            }
            REQUEST_CODE_FILTER -> if (data != null) {
                val filterId = data.getLongExtra(FilterActivity.SAVED_FILTER_ID_EXTRA, -1)
                if(filterId >= 0) {
                    selectFilter(filterId)
                }
            }
        }
        fab.visibility = View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        ActivityHelper.applyTheme(this)
        setContentView(R.layout.activity_task)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        remotePath = findViewById(R.id.task_remote_path_textfield)
        localPath = findViewById(R.id.task_local_path_textfield)
        remoteDropdown = findViewById(R.id.task_remote_spinner)
        remotePath2 = findViewById(R.id.task_remote_path_textfield2)
        remoteDropdown2 = findViewById(R.id.task_remote_spinner2)
        taskLocalCard = findViewById(R.id.task_local_card)
        taskRemote2Card = findViewById(R.id.task_remote2_card)
        syncDirection = findViewById(R.id.task_direction_spinner)
        syncDescription = findViewById(R.id.descriptionSyncDirection)
        transfersDropdown = findViewById(R.id.task_transfers_spinner)
        filterDropdown = findViewById(R.id.task_filter_spinner)
        switchDeleteExcluded = findViewById(R.id.task_exclude_delete_toggle)
        onFailDropdown = findViewById(R.id.task_onFail_spinner)
        onSuccessDropdown = findViewById(R.id.task_onSuccess_spinner)
        fab = findViewById(R.id.saveButton)
        switchWifi = findViewById(R.id.task_wifionly)
        switchMD5sum = findViewById(R.id.task_md5sum)

        rcloneInstance = Rclone(this)
        dbHandler = DatabaseHandler(this)
        val extras = intent.extras
        val taskId: Long
        if (extras != null) {
            taskId = extras.getLong(ID_EXTRA)
            if (taskId != 0L) {
                existingTask = dbHandler.getTask(taskId)
                if (existingTask == null) {
                    Toasty.error(
                        this,
                        this.resources.getString(R.string.taskactivity_task_not_found)
                    ).show()
                    finish()
                }
            }
        }
        val fab = findViewById<FloatingActionButton>(R.id.saveButton)
        fab.setOnClickListener {
            //Todo fix error when no remotes are available
            if (existingTask == null) {
                saveTask()
            } else {
                persistTaskChanges()
            }
        }

        filterOptionsButton = findViewById(R.id.task_edit_filter_options_button)
        filterOptionsButton.setOnClickListener {
            val filter = if(filterDropdown.selectedItemPosition > 0 && filterDropdown.selectedItemPosition < filterDropdown.count) filterItems[filterDropdown.selectedItemPosition - 1] else null
            showFilterMenu(filterOptionsButton, filter)
        }
        createNewFilterButton = findViewById(R.id.task_edit_filter_add_button)
        createNewFilterButton.setOnClickListener {
            openFilterActivity()
        }

        findViewById<TextView>(R.id.task_title_textfield).text = existingTask?.title
        switchWifi.isChecked = existingTask?.wifionly ?: false
        switchMD5sum.isChecked = existingTask?.md5sum ?: false
        switchDeleteExcluded.isChecked = existingTask?.deleteExcluded ?: false
        prepareSyncDirectionDropdown()
        prepareTransfersDropdown()
        prepareLocal()
        prepareRemote()
        prepareRemote2()
        prepareFilterDropdown()
        prepareOnFailDropdown()
        prepareOnSuccessDropdown()
    }

    private val remoteItems: Array<String?>
        get() {
            val remotes = arrayOfNulls<String>(rcloneInstance.remotes.size)
            for (i in rcloneInstance.remotes.indices) {
                remotes[i] = rcloneInstance.remotes[i].name
            }
            return remotes
        }

    private val taskListWithNone: ArrayList<TaskNameIdPair>
        get() {
            val tasks = dbHandler.allTasks
            val list = ArrayList<TaskNameIdPair>()
            list.add(TaskNameIdPair(-1, "None"))
            tasks.forEach{
                list.add(TaskNameIdPair(it.id, it.title))
            }
            return list
        }



    private val filterItems: List<Filter>
        get() {
            return dbHandler.allFilters
        }


    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun persistTaskChanges() {
        val updatedTask = getTaskValues(existingTask!!.id)
        if (updatedTask != null) {
            dbHandler.updateTask(updatedTask)
            finish()
        }
    }

    private fun saveTask() {
        val newTask = getTaskValues(0)
        if (newTask != null) {
            dbHandler.createTask(newTask)
            finish()
        }
    }

    private fun getTaskValues(id: Long): Task? {
        val taskToPopulate = Task(id)
        taskToPopulate.title = findViewById<EditText>(R.id.task_title_textfield).text.toString()
        val remotename = remoteDropdown.selectedItem.toString()
        taskToPopulate.remoteId = remotename
        val direction = SyncDirectionObject.directionForSpinnerPosition(syncDirection.selectedItemPosition)
        for (ri in rcloneInstance.remotes) {
            if (ri.name == taskToPopulate.remoteId) {
                taskToPopulate.remoteType = ri.type
            }
        }
        taskToPopulate.remotePath = remotePath.text.toString()
        taskToPopulate.localPath = localPath.text.toString()
        taskToPopulate.direction = direction

        // Destination remote is only meaningful for cloud-to-cloud directions (7/8).
        if (direction == SyncDirectionObject.SYNC_REMOTE_TO_REMOTE
            || direction == SyncDirectionObject.COPY_REMOTE_TO_REMOTE) {
            taskToPopulate.remoteId2 = remoteDropdown2.selectedItem.toString()
            for (ri in rcloneInstance.remotes) {
                if (ri.name == taskToPopulate.remoteId2) {
                    taskToPopulate.remoteType2 = ri.type
                }
            }
            taskToPopulate.remotePath2 = remotePath2.text.toString()
        }

        taskToPopulate.wifionly = switchWifi.isChecked
        taskToPopulate.md5sum = switchMD5sum.isChecked
        taskToPopulate.deleteExcluded = switchDeleteExcluded.isChecked
        taskToPopulate.filterId = if(filterDropdown.selectedItemPosition == 0 || filterDropdown.selectedItemPosition == -1) null else filterItems[filterDropdown.selectedItemPosition - 1].id
        taskToPopulate.onFailFollowup = (onFailDropdown.selectedItem as TaskNameIdPair).id
        taskToPopulate.onSuccessFollowup = (onSuccessDropdown.selectedItem as TaskNameIdPair).id
        val transfersValue = resources.getStringArray(R.array.task_transfers_values)
            .getOrNull(transfersDropdown.selectedItemPosition)?.toIntOrNull() ?: -1
        taskToPopulate.transfers = if (transfersValue <= 0) null else transfersValue

        // Verify if data is completed
        val isCloudToCloud = direction == SyncDirectionObject.SYNC_REMOTE_TO_REMOTE
                || direction == SyncDirectionObject.COPY_REMOTE_TO_REMOTE
        if (!isCloudToCloud && localPath.text.toString() == "") {
            Toasty.error(
                this.applicationContext,
                getString(R.string.task_data_validation_error_no_local_path),
                Toast.LENGTH_SHORT,
                true
            ).show()
            return null
        }
        if (remotePath.text.toString() == "") {
            Toasty.error(
                this.applicationContext,
                getString(R.string.task_data_validation_error_no_remote_path),
                Toast.LENGTH_SHORT,
                true
            ).show()
            return null
        }
        if (isCloudToCloud) {
            if (taskToPopulate.remoteId2.isEmpty() || remotePath2.text.toString() == "") {
                Toasty.error(
                    this.applicationContext,
                    getString(R.string.task_data_validation_error_no_remote_path),
                    Toast.LENGTH_SHORT,
                    true
                ).show()
                return null
            }
            if (taskToPopulate.remoteId2 == taskToPopulate.remoteId
                && remotePath2.text.toString() == remotePath.text.toString()) {
                Toasty.error(
                    this.applicationContext,
                    getString(R.string.task_data_validation_error_same_remote),
                    Toast.LENGTH_SHORT,
                    true
                ).show()
                return null
            }
        }
        return taskToPopulate
    }

    private fun startRemotePicker(remote: RemoteItem, initialPath: String, destination: Boolean) {
        pickingDestinationRemote = destination
        val fragment: Fragment = RemoteFolderPickerFragment.newInstance(remote, this, initialPath)
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.create_task_layout, fragment, "FILE_EXPLORER_FRAGMENT_TAG")
        transaction.addToBackStack("FILE_EXPLORER_FRAGMENT_TAG")
        transaction.commit()
        fab.visibility = View.GONE
    }

    override fun selectFolder(path: String) {
        if (pickingDestinationRemote) {
            remotePathHolder2 = path
            remotePath2.setText(remotePathHolder2)
        } else {
            remotePathHolder = path
            remotePath.setText(remotePathHolder)
        }
        fab.visibility = View.VISIBLE
    }

    private fun selectFilter(filterId: Long) {
        prepareFilterDropdown()

        for ((i, filter) in filterItems.withIndex()) {
            if (filter.id == filterId) {
                filterDropdown.setSelection(i + 1)
            }
        }
    }

    private fun prepareLocal() {
        existingTask.let {
            localPath.setText(it?.localPath ?: "")
        }
        localPath.onFocusChangeListener =
            View.OnFocusChangeListener { v: View?, hasFocus: Boolean ->
                if (hasFocus) {
                    val intent = Intent(this.applicationContext, FilePicker::class.java)
                    intent.putExtra(FilePicker.FILE_PICKER_PICK_DESTINATION_TYPE, true)
                    startActivityForResult(intent, REQUEST_CODE_FP_LOCAL)
                }
            }
    }

    private fun prepareRemote() {

        remotePathHolder = existingTask?.remotePath.toString()
        remoteDropdown.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, remoteItems)

        if (existingTask != null) {
            for ((i, remote) in remoteItems.withIndex()) {
                if (remote == existingTask!!.remoteId) {
                    remoteDropdown.setSelection(i)
                }
            }
        }

        remoteDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
                remotePath.setText("")
                val remotename = remoteDropdown.selectedItem.toString()
                if(existingTask?.remoteId.equals(remotename)) {
                    remotePath.setText(remotePathHolder)
                }

            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }

        // Todo: This will break if the remote changed, but the path did not.
        //       Catch this issue by forcing the path to be emtpy
        remotePath.onFocusChangeListener = object : View.OnFocusChangeListener {
            override fun onFocusChange(p0: View?, p1: Boolean) {
                startRemotePicker(
                    rcloneInstance.getRemoteItemFromName(remoteDropdown.selectedItem.toString()), "/", false
                )
                remotePath.clearFocus()
            }
        }
    }

    private fun prepareRemote2() {
        remotePathHolder2 = existingTask?.remotePath2.toString()
        remoteDropdown2.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, remoteItems)

        if (existingTask != null) {
            for ((i, remote) in remoteItems.withIndex()) {
                if (remote == existingTask!!.remoteId2) {
                    remoteDropdown2.setSelection(i)
                }
            }
        }

        remoteDropdown2.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
                remotePath2.setText("")
                val remotename = remoteDropdown2.selectedItem.toString()
                if (existingTask?.remoteId2.equals(remotename)) {
                    remotePath2.setText(remotePathHolder2)
                }
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }

        remotePath2.onFocusChangeListener = object : View.OnFocusChangeListener {
            override fun onFocusChange(p0: View?, p1: Boolean) {
                startRemotePicker(
                    rcloneInstance.getRemoteItemFromName(remoteDropdown2.selectedItem.toString()), "/", true
                )
                remotePath2.clearFocus()
            }
        }
    }

    private fun updateRemoteFieldVisibility(direction: Int) {
        val cloudToCloud = direction == SyncDirectionObject.SYNC_REMOTE_TO_REMOTE
                || direction == SyncDirectionObject.COPY_REMOTE_TO_REMOTE
        taskRemote2Card.visibility = if (cloudToCloud) View.VISIBLE else View.GONE
        taskLocalCard.visibility = if (cloudToCloud) View.GONE else View.VISIBLE
    }

    private fun prepareOnFailDropdown() {
        onFailDropdown.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, taskListWithNone)
        var i = 0
        var found = 0
        taskListWithNone.forEach{
            if(it.id == (existingTask?.onFailFollowup ?: -1)) {
                found = i
            }
            i++
        }
        onFailDropdown.setSelection(found)

        onFailDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
                val pair = parentView?.selectedItem as TaskNameIdPair
                existingTask?.onFailFollowup = pair.id
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
    }

    private fun prepareOnSuccessDropdown() {
        onSuccessDropdown.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, taskListWithNone)
        var i = 0
        var found = 0
        taskListWithNone.forEach{
            if(it.id == (existingTask?.onSuccessFollowup ?: -1)) {
                found = i
            }
            i++
        }
        onSuccessDropdown.setSelection(found)


        onSuccessDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
                val pair = parentView?.selectedItem as TaskNameIdPair
                existingTask?.onSuccessFollowup = pair.id
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
    }



    private fun prepareFilterDropdown() {
        val filterList = filterItems.toMutableList()

        if(filterList.isEmpty()) {
            createNewFilterButton.visibility = View.VISIBLE
            filterDropdown.visibility = View.INVISIBLE
        }
        else {
            val titles = mutableListOf<String?>().apply {
                add(getString(R.string.task_edit_filter_nofilter))
                addAll(filterList.map { it.title })
            }

            val adapter = FilterSpinnerAdapter(this, android.R.layout.simple_spinner_dropdown_item, titles)
            filterDropdown.adapter = adapter
            createNewFilterButton.visibility = View.INVISIBLE
            filterDropdown.visibility = View.VISIBLE

            if (existingTask != null) {
                for ((i, filter) in filterList.withIndex()) {
                    if (filter.id == existingTask!!.filterId) {
                        filterDropdown.setSelection(i + 1)
                    }
                }
            }

            filterDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
                    selectedFilter = if (position > 0 && position < titles.size) filterItems[position - 1] else null
                }
                override fun onNothingSelected(parentView: AdapterView<*>?) {}
            }
        }
    }

    private fun showFilterMenu(view: View, filter: Filter?) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.filter_item_menu, popupMenu.menu)
        val menu = popupMenu.menu
        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            if (menuItem.itemId == R.id.action_edit_filter ||
                    menuItem.itemId == R.id.action_delete_filter) {
                menuItem.isVisible = filter != null
            }
        }
        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_create_new_filter -> {
                    openFilterActivity()
                }
                R.id.action_edit_filter -> {
                    openFilterActivity(filter)
                }
                R.id.action_delete_filter -> {
                    dbHandler.deleteFilter(filter!!.id)
                    filterDropdown.setSelection(0)
                    prepareFilterDropdown()
                }
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popupMenu.show()
    }
    private fun openFilterActivity(filter: Filter? = null) {
        val intent = Intent(this, FilterActivity::class.java)
        if(filter != null) {
            intent.putExtra(FilterActivity.ID_EXTRA, filter.id)
        }
        startActivityForResult(intent, REQUEST_CODE_FILTER)
    }

    private fun prepareSyncDirectionDropdown() {
        val options = SyncDirectionObject.getOptionsArray(this)
        val directionAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        syncDirection.adapter = directionAdapter
        syncDirection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>?,
                selectedItemView: View,
                position: Int,
                id: Long
            ) {
                val direction = SyncDirectionObject.directionForSpinnerPosition(position)
                updateSpinnerDescription(direction)
                updateRemoteFieldVisibility(direction)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
        val initialDirection = existingTask?.direction ?: SyncDirectionObject.SYNC_LOCAL_TO_REMOTE
        syncDirection.setSelection(SyncDirectionObject.spinnerPositionForDirection(initialDirection))
        updateRemoteFieldVisibility(initialDirection)
    }

    private fun prepareTransfersDropdown() {
        val entries = resources.getStringArray(R.array.task_transfers_entries)
        transfersDropdown.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, entries)
        val current = existingTask?.transfers
        var selection = 0
        if (current != null) {
            val values = resources.getStringArray(R.array.task_transfers_values)
            val idx = values.indexOfFirst { it.toIntOrNull() == current }
            if (idx >= 0) selection = idx
        }
        transfersDropdown.setSelection(selection)
    }

    private fun updateSpinnerDescription(value: Int) {
        var text = getString(R.string.description_sync_direction_sync_toremote)
        when (value) {
            SyncDirectionObject.SYNC_LOCAL_TO_REMOTE -> text =
                getString(R.string.description_sync_direction_sync_toremote)
            SyncDirectionObject.SYNC_REMOTE_TO_LOCAL -> text =
                getString(R.string.description_sync_direction_sync_tolocal)
            SyncDirectionObject.COPY_LOCAL_TO_REMOTE -> text =
                getString(R.string.description_sync_direction_copy_toremote)
            SyncDirectionObject.COPY_REMOTE_TO_LOCAL -> text =
                getString(R.string.description_sync_direction_copy_tolocal)
            SyncDirectionObject.SYNC_REMOTE_TO_REMOTE -> text =
                getString(R.string.description_sync_direction_sync_remotetoremote)
            SyncDirectionObject.COPY_REMOTE_TO_REMOTE -> text =
                getString(R.string.description_sync_direction_copy_remotetoremote)
            SyncDirectionObject.SYNC_BIDIRECTIONAL -> text =
                getString(R.string.description_sync_direction_sync_bidirectional)
        }
        syncDescription.text = text
    }

    companion object {
        const val ID_EXTRA = "TASK_EDIT_ID"
        const val REQUEST_CODE_FP_LOCAL = 500
        const val REQUEST_CODE_FP_REMOTE = 444
        const val REQUEST_CODE_FILTER = 333

    }

    inner class TaskNameIdPair(var id: Long, private var name: String) {
        override fun toString(): String {
            return name
        }
    }
}