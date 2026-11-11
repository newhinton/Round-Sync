package ca.pkay.rcloneexplorer.RemoteConfig

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.RemoteConfig.ProviderListFragment.Companion.newProviderListConfig
import ca.pkay.rcloneexplorer.RemoteConfig.ProviderListFragment.ProviderSelectedListener
import ca.pkay.rcloneexplorer.RuntimeConfiguration
import ca.pkay.rcloneexplorer.rclone.Provider
import ca.pkay.rcloneexplorer.util.ActivityHelper
import es.dmoral.toasty.Toasty
import org.json.JSONException


class RemoteConfig : AppCompatActivity(), ProviderSelectedListener {

    private val OUTSTATE_TITLE = "ca.pkay.rcexplorer.remoteConfig.TITLE"
    private var mFragment: Fragment? = null
    private lateinit var mSearchBar: SearchView
    private var mSearchIcon: MenuItem? = null

    companion object {
        const val CONFIG_EDIT_CODE = 139
        const val CONFIG_EDIT_TARGET = "CONFIG_EDIT_TARGET"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(RuntimeConfiguration.attach(this, newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        applyTheme()
        setContentView(R.layout.activity_remote_config)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowHomeEnabled(true)
        }

        val fragmentTransaction = supportFragmentManager.beginTransaction()
        if (savedInstanceState != null) {
            mFragment = supportFragmentManager.findFragmentByTag("new config")
            fragmentTransaction.replace(R.id.flFragment, getCurrentFragment(), "new config")
            fragmentTransaction.commit()
            val title = savedInstanceState.getString(OUTSTATE_TITLE)
            if (title != null) {
                supportActionBar!!.title = title
            }
            return
        }

        mSearchBar = findViewById(R.id.search)

        mFragment = newProviderListConfig()
        fragmentTransaction.replace(R.id.flFragment, getCurrentFragment(), "config list")
        fragmentTransaction.commit()
        val shouldEdit = intent.getStringExtra(CONFIG_EDIT_TARGET)
        if (shouldEdit != null) {
            if (shouldEdit.isNotEmpty()) {
                val rclone = Rclone(this)
                val config = rclone.getConfig(intent.getStringExtra(CONFIG_EDIT_TARGET))
                if (config != null) {
                    try {
                        val provider = rclone.getProvider(config["type"])
                        mFragment = DynamicRemoteConfigFragment(provider.name, config)
                        startConfig(provider)
                    } catch (e: JSONException) {
                        throw RuntimeException(e)
                    }
                }
            }
        }

        mSearchBar.setOnCloseListener {
            toggleSearch(false)
            return@setOnCloseListener true
        }

        mSearchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                if(isProviderlist()) {
                    (getCurrentFragment() as ProviderListFragment).setSearchterm(newText)
                }
                if(isDynamicRemoteConfigFragment()) {
                    (getCurrentFragment() as DynamicRemoteConfigFragment).setSearchterm(newText)
                }
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                return true
            }
        })

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.remote_config_menu, menu)
        mSearchIcon = menu?.findItem(R.id.app_bar_search);
        return true
    }

    private fun toggleSearch(show: Boolean) {
        if(show) {
            mSearchBar.visibility = View.VISIBLE
            mSearchBar.requestFocus()
            mSearchBar.isFocusable = true
            mSearchBar.isIconified = false
            mSearchBar.requestFocusFromTouch()
            mSearchIcon?.isVisible = false
        } else {
            mSearchBar.visibility = View.GONE
            mSearchIcon?.isVisible = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.app_bar_search -> {
                toggleSearch(true)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (supportActionBar != null) {
            val title = supportActionBar!!.title
            if (title != null) {
                outState.putString(OUTSTATE_TITLE, title.toString())
            }
        }
    }
    override fun onSupportNavigateUp(): Boolean {
        handleBackAction()
        return true
    }

    override fun onBackPressed() {
        super.onBackPressed()
        handleBackAction()
    }

    private fun handleBackAction() {
        if(mSearchBar.visibility == View.VISIBLE) {
            toggleSearch(false)
            return
        }

        if (mFragment is ProviderListFragment) {
            finish()
        } else if (mFragment is DynamicRemoteConfigFragment) {
            if ((mFragment as DynamicRemoteConfigFragment).isEditConfig()) {
                finish()
            } else {
                var title: String? = null
                if (supportActionBar != null) {
                    title = supportActionBar!!.title.toString()
                }
                startProviderlist(title)
            }
        } else {
            startProviderlist()
        }
    }

    override fun onProviderSelected(provider: Provider) {
        if (provider == null) {
            Toasty.error(this, getString(R.string.nothing_selected), Toast.LENGTH_SHORT, true)
                .show()
            return
        }
        toggleSearch(false)
        mFragment = DynamicRemoteConfigFragment(provider.name)
        startConfig(provider)
    }

    private fun applyTheme() {
        ActivityHelper.applyTheme(this)
        val typedValue = TypedValue()
        theme.resolveAttribute(R.attr.colorPrimaryDark, typedValue, true)
        window.statusBarColor = typedValue.data
    }

    private fun startConfig(provider: Provider) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.flFragment, getCurrentFragment(), "new config")
        transaction.commit()
        if (supportActionBar != null) {
            supportActionBar!!.title = provider.getNameCapitalized()
        }
    }

    private fun startProviderlist() {
        startProviderlist(null)
    }
    private fun startProviderlist(preselection: String?) {
        mFragment = newProviderListConfig(preselection)
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.flFragment, getCurrentFragment(), "config list")
        fragmentTransaction.commit()
        if (supportActionBar != null) {
            supportActionBar!!.setTitle(R.string.title_activity_remote_config)
        }
    }

    private fun isProviderlist(): Boolean {
        if (mFragment is ProviderListFragment) {
            return true
        }
        return false
    }
    private fun isDynamicRemoteConfigFragment(): Boolean {
        if (mFragment is DynamicRemoteConfigFragment) {
            return true
        }
        return false
    }


    /**
     * This defauls to the provider list
     */
    private fun getCurrentFragment(): Fragment {
        if(mFragment == null) {
            return newProviderListConfig()
        }
        return mFragment!!
    }
}