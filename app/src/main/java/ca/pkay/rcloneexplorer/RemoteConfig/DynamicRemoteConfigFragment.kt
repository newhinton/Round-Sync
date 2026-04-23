package ca.pkay.rcloneexplorer.RemoteConfig

import android.app.ActionBar.LayoutParams
import android.content.Context
import android.graphics.Typeface
import android.os.AsyncTask
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.view.setPadding
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.Rclone.RCLONE_CONFIG_NAME_KEY
import ca.pkay.rcloneexplorer.rclone.Provider
import ca.pkay.rcloneexplorer.rclone.ProviderOption
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale


class DynamicRemoteConfigFragment(private val mProviderTitle: String, private val optionMap: HashMap<String, String>?) : Fragment() {

    private val TAG = "DynamicRemoteConfigFragment"
    private lateinit var mContext: Context
    private var rclone: Rclone? = null

    private var mFormView: ViewGroup? = null
    private var mTitleLabel: TextView? = null
    private var mAuthView: View? = null
    private var mCancelAuthButton: Button? = null
    private var mFinishButton: FloatingActionButton? = null
    private var mRemoteName: EditText? = null
    private var mProvider: Provider? = null
    private var mShowAdvanced = false
    private var mIsEditTask = false
    private var mOptionMap = hashMapOf<String, String>()
    private var mAuthTask: AsyncTask<Void?, Void?, Boolean>? = null
    private var mUseOauth = false
    private var mOptionFilter = ""

    constructor(providerTitle: String) : this(providerTitle, null)

    init {
        if(optionMap != null) {
            // we assume that if the option-map is passed, we are editing.
            this.mIsEditTask = true
            mOptionMap = optionMap
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (context == null) {
            return
        }
        setHasOptionsMenu(true)
        mContext = context as Context

        rclone = Rclone(this.context)
        mProvider = rclone!!.getProvider(mProviderTitle)
        if(mProvider == null) {
            Log.e(this::class.java.simpleName, "Unknown Provider: $mProviderTitle")
            Toast.makeText(this.mContext, R.string.dynamic_config_unknown_error, Toast.LENGTH_LONG).show()
            requireActivity().finish()
        }

        this.mUseOauth = when (mProvider?.name) {
            "box",
            "dropbox",
            "pcloud",
            "yandex",
            "drive",
            "google photos",
            "onedrive",
            "internxt"  // Internxt uses interactive multi-step auth with optional 2FA
            -> true
            else -> false
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.remote_config_form, container, false)
        mFormView = view.findViewById(R.id.form_content)
        mTitleLabel = view.findViewById(R.id.labelTitle)
        mAuthView = view.findViewById(R.id.auth_screen)

        mRemoteName = view.findViewById(R.id.remote_name)
        mCancelAuthButton = view.findViewById(R.id.cancel_auth)

        mFinishButton = view.findViewById(R.id.finish)

        if(mUseOauth) {
            (mFinishButton as FloatingActionButton).contentDescription = getString(R.string.next)
        }

        setUpForm()
        mFinishButton?.setOnClickListener { setUpRemote() }
        mCancelAuthButton?.setOnClickListener {
            mAuthTask?.cancel(true)
            requireActivity().finish()
        }

        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_config_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_advanced -> {
                mShowAdvanced = mShowAdvanced.not()
                setUpForm()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mAuthTask?.cancel(true)
    }

    fun isEditConfig(): Boolean {
        return mIsEditTask
    }

    fun cancelCurrentStep() {
        if(isEditConfig()){
            activity?.finish()
        } else {
            activity?.onBackPressed()
        }
    }

    fun setSearchterm (term: String) {
        mOptionFilter = term
        Log.e(TAG, "Filter:: $term")
        setUpForm()
    }

    // Todo: required attribute is not honored (also apply to title!)
    // Todo: hidden attribute is not applied
    private fun setUpForm() {

        mFormView?.let { it.removeViews(1, it.size-1) }


        (mFormView?.findViewById(R.id.titleCardView) as CardView).visibility = View.VISIBLE
        if(mOptionFilter.isNotBlank()) {
            if(!getText(R.string.remote_properties_remote_name).contains(mOptionFilter, true)) {
                (mFormView?.findViewById(R.id.titleCardView) as CardView).visibility = View.GONE
            }
        }

        if(mOptionMap.containsKey(RCLONE_CONFIG_NAME_KEY)) {
            mRemoteName?.setText(mOptionMap.getValue(RCLONE_CONFIG_NAME_KEY))
            mRemoteName?.isFocusable = false
            mRemoteName?.isEnabled = false
        }

        mProvider!!.options.forEach {

            if(it.advanced && !mShowAdvanced ) {
                return@forEach
            }

            if(mOptionFilter.isNotBlank()) {
                if(!it.name.contains(mOptionFilter, true) and !it.help.contains(mOptionFilter, true)) {
                    return@forEach
                }
            }

            val layout = LinearLayout(mContext)
            layout.orientation = LinearLayout.VERTICAL

            val textViewTitle = TextView(mContext)
            textViewTitle.contentDescription = it.name
            textViewTitle.text = it.getNameCapitalized()
            textViewTitle.typeface = Typeface.DEFAULT_BOLD
            layout.addView(textViewTitle)


            val textViewDescription = TextView(mContext)
            textViewDescription.contentDescription = it.help
            textViewDescription.text = it.help
            layout.addView(textViewDescription)


            when (it.type) {
                "string" -> {
                    if(it.isPassword) {
                        val input = getAttachedEditText(it.name, layout)
                        input.inputType =
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        //input.setText(mOptionMap[it.name])
                        updateValue(input, it)
                        setTextInputListener(input, it.name)

                    } else if(it.examples.size > 0 && it.type != "CommaSepList") {
                        createSpinnerFromExample(it, it.name ,layout)
                    } else {
                        val input = getAttachedEditText(it.name, layout)
                        //input.setText(mOptionMap[it.name])
                        updateValue(input, it)
                        setTextInputListener(input, it.name)
                    }
                }
                "bool" -> {
                    val input = CheckBox(mContext)
                    input.text = it.name
                    updateValue(input, it)

                    // only now add listener, so to not store default values if they have not been changed.
                    setCheckboxListener(input, it.name)

                    layout.addView(input)
                }
                "int" -> {
                    val input = getAttachedEditText(it.name, layout)
                    input.inputType = InputType.TYPE_CLASS_NUMBER
                    updateValue(input, it)
                    setTextInputListener(input, it.name)
                }
                "SizeSuffix" -> {
                    layout.addView(createSuffixSelector(it))
                }
                // This is a bit lazy. Optimally we would try to build proper ui's for this,
                // but that is tedious. I am going to weasle myself out of this by stating that
                // those are usually advanced options and users are likely to know whats valid.
                "CommaSepList", "Duration", "MultiEncoder"  -> {
                    val input = getAttachedEditText(it.name, layout)
                    //input.setText(mOptionMap[it.name])
                    updateValue(input, it)
                    setTextInputListener(input, it.name)
                }
                else -> {
                    Log.e(this::class.java.simpleName, "Unknown Provideroption: ${it.type}")
                    val unknownType = getAttachedEditText(it.name, layout)
                    //unknownType.hint = it.type
                    updateValue(unknownType, it)
                    setTextInputListener(unknownType, it.name)
                }
            }

            val cardContainer = getCard()
            cardContainer.addView(layout)
            mFormView?.addView(cardContainer)
        }
    }
    
    private fun updateValue(view: View, option: ProviderOption) {
        // check if this value was never set
        if(!mOptionMap.containsKey(option.name)) {
            // then set it to true if it is enabled by default
            when (view::class.java) {
                AutoCompleteTextView::class.java -> {
                    (view as AutoCompleteTextView).setText(option.default)
                }
                TextInputEditText::class.java -> {
                    (view as TextInputEditText).setText(option.default)
                }
                CheckBox::class.java -> {
                    if(option.default.lowercase(Locale.ROOT).toBoolean()){
                        (view as CheckBox).isChecked = true
                    }
                }
                else -> {
                    Log.e(TAG, "Input Class not supported! ${view::class.java}")
                }
            }
        } else {
            //otherwise, set it to what it was.
            when (view::class.java) {
                AutoCompleteTextView::class.java -> {
                    (view as AutoCompleteTextView).setText(mOptionMap[option.name], false)
                }
                TextInputEditText::class.java -> {
                    (view as TextInputEditText).setText(mOptionMap[option.name])
                }
                CheckBox::class.java -> {
                    (view as CheckBox).isChecked = mOptionMap[option.name].toBoolean()
                }
                else -> {
                    Log.e(TAG, "Input Class not supported! ${view::class.java}")
                }
            }
        }
    }

    private fun createSpinnerFromExample(option: ProviderOption, hint: String, layout: LinearLayout): View {

        val padding = resources.getDimensionPixelOffset(R.dimen.cardPadding)
        val textinput = TextInputLayout(ContextThemeWrapper(activity, R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox_ExposedDropdownMenu))
        textinput.hint = hint
        textinput.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        textinput.setPadding(0, padding, 0, 0)

        var input = AutoCompleteTextView(textinput.context)
        input.setPadding(padding)
        val items = ArrayList<String>()

        option.examples.forEach { example ->
            items.add(example.Value)
        }
        val adapter = ArrayAdapter(
            this.mContext,
            android.R.layout.simple_spinner_item,
            items
        )

        input.setAdapter(adapter)
        input.isEnabled = false

        textinput.addView(input)

        updateValue(input, option)

        setTextInputListener(input, option.name)
        layout.addView(textinput)
        return input
    }



    private fun createSuffixSelector(option: ProviderOption): View {
        val padding = resources.getDimensionPixelOffset(R.dimen.cardPadding)

        val regex = Regex("\\d*(p|t|g|m|k|b)")
        val optionvalue = mOptionMap[option.name]?: ""
        var suffix = "";
        var number = "";
        if(regex.matches(optionvalue.lowercase())) {
            number = optionvalue.substring(0,optionvalue.length-1)
            suffix = optionvalue.substring(optionvalue.length-1, optionvalue.length)
        }


        // Outer Container
        val leftright = LinearLayout(mContext)
        leftright.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        leftright.orientation = LinearLayout.HORIZONTAL

        // Value Container
        val valueContainer = TextInputLayout(mContext)
        valueContainer.hint = getString(R.string.dynamic_config_suffixselector_value_hint)
        valueContainer.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        valueContainer.setPadding(0, padding, 0, 0)
        valueContainer.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f)

        val valueInput = TextInputEditText(valueContainer.context)
        valueInput.setPadding(padding)
        valueInput.setText(number)
        //@ManualTheming
        valueInput.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        valueContainer.addView(valueInput)


        // Suffix Container
        val suffixContainer = TextInputLayout(ContextThemeWrapper(activity, R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox_ExposedDropdownMenu))
        suffixContainer.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        suffixContainer.setPadding(padding, padding, 0, 0)
        suffixContainer.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.3f)
        valueInput.setText(suffix)

        val items = listOf("P", "T", "G", "M", "K", "B")
        val adapter = ArrayAdapter(
            this.mContext,
            android.R.layout.simple_spinner_item,
            items
        )

        val suffixSpinner = AutoCompleteTextView(suffixContainer.context)
        suffixSpinner.setPadding(padding)
        suffixSpinner.hint = getString(R.string.dynamic_config_suffixselector_suffix_hint)
        suffixSpinner.setAdapter(adapter)
        //suffixSpinner.isEnabled = false
        suffixContainer.addView(suffixSpinner)


        valueInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                mOptionMap[option.name] = s.toString()+suffixSpinner.text
            }
            override fun afterTextChanged(s: Editable) {}
        })
        suffixSpinner.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                mOptionMap[option.name] = valueInput.text.toString() + s.toString()
            }
            override fun afterTextChanged(s: Editable) {}
        })

        leftright.addView(valueContainer)
        leftright.addView(suffixContainer)
        return leftright
    }

    private fun getAttachedEditText(hint: String, layout: LinearLayout): EditText {
        val padding = resources.getDimensionPixelOffset(R.dimen.cardPadding)

        val textinput = TextInputLayout(mContext)
        textinput.hint = hint
        textinput.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        textinput.setPadding(0, padding, 0, 0)

        val editText = TextInputEditText(textinput.context)
        editText.setPadding(padding)
        //@ManualTheming
        editText.setTextColor(convertAttributeToColor(R.attr.colorOnSecondaryContainer))
        editText.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        textinput.addView(editText)
        layout.addView(textinput)
        return editText
    }

    private fun setTextInputListener(input: TextView, option: String) {

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                mOptionMap[option] = s.toString()
            }
            override fun afterTextChanged(s: Editable) {}
        })
    }

    private fun setCheckboxListener(input: CheckBox, option: String) {

        input.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            if(isChecked) {
                mOptionMap[option] = "true"
            } else {
                mOptionMap[option] = "false"
            }
        }
    }

    private fun getCard(): CardView {
        val card = CardView(mContext)
        val cardLayout = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        cardLayout.topMargin = getDPasPixel(16).toInt()
        cardLayout.marginStart = getDPasPixel(8).toInt()
        cardLayout.marginEnd = getDPasPixel(8).toInt()
        cardLayout.bottomMargin = getDPasPixel(8).toInt()
        card.layoutParams = cardLayout

        //@ManualTheming
        card.setCardBackgroundColor(convertAttributeToColor(R.attr.colorSecondaryContainer))
        card.radius = resources.getDimension(R.dimen.cardCornerRadius)
        card.setContentPadding(
            resources.getDimension(R.dimen.cardPadding).toInt(),
            resources.getDimension(R.dimen.cardPadding).toInt(),
            resources.getDimension(R.dimen.cardPadding).toInt(),
            resources.getDimension(R.dimen.cardPadding).toInt()
        )
        return card
    }

    private fun getDPasPixel(dp: Int): Float {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f)
    }

    private fun convertAttributeToColor(id: Int): Int {
        val typedValue = TypedValue()
        mContext.theme.resolveAttribute(id, typedValue, true)
        return typedValue.data
    }

    private fun setUpRemote() {

        val options = java.util.ArrayList<String>()
        val name: String = mRemoteName?.text.toString()
        options.add(name)
        options.add(mProviderTitle)


        if(mIsEditTask) {
            if(mUseOauth) {
                options.add("config_refresh_token")
                options.add("false")
            }
        }
        for ((key, value) in mOptionMap) {
            //Log.e("TAG", "key: $key value: $value (${value.length})")
            options.add(key)
            options.add(value)
        }

        if(mIsEditTask) {
            RemoteConfigHelper.updateAndWait(context, options)
            requireActivity().finish()
        }

        if(mUseOauth){
            mAuthTask = ConfigCreate(
                options, mFormView!!, mAuthView!!,
                requireContext(), rclone!!, mProvider?.name ?: ""
            ).execute()
        } else {
            RemoteConfigHelper.setupAndWait(context, options)
            requireActivity().finish()
        }
    }
}