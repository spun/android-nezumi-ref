package com.spundev.nezumi

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
import com.spundev.nezumi.databinding.ActivityMainBinding
import com.spundev.nezumi.extensions.findNavControllerWithFragmentContainerView


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // We need to retrieve the NavController using this instead of the already existing
        // findNavController method if we want to use "FragmentContainerView" as a replacement
        // for the "fragment" tag.
        // https://issuetracker.google.com/issues/142847973
        navController = findNavControllerWithFragmentContainerView(R.id.nav_host_fragment)

        // Toolbar
        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        // Get the NavController for your NavHostFragment
        // Set up the Toolbar to stay in sync with the NavController
        setupActionBarWithNavController(navController)

        if (savedInstanceState == null) {
            if (intent?.action == Intent.ACTION_SEND) {
                if ("text/plain" == intent.type) {
                    handleText(intent) // Handle text being sent
                }
            }
        }
    }

    override fun onSupportNavigateUp() = findNavController(R.id.nav_host_fragment).navigateUp()

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Intent.ACTION_SEND) {
            if ("text/plain" == intent.type) {
                handleText(intent) // Handle text being sent
            }
        }
    }

    private fun handleText(intent: Intent) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            val args = bundleOf(
                "videoUrl" to it
            )
            navController.setGraph(R.navigation.nav_graph, args)
        }
    }


    // CREDITS
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.credits -> {
                showCreditsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showCreditsDialog() {
        // Content text view
        val textView = TextView(this)
        textView.setText(R.string.credits_dialog_message)
        textView.movementMethod =
            LinkMovementMethod.getInstance() // this is important to make the links clickable

        // Add margin to the TextView (https://stackoverflow.com/a/27776276)
        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = resources.getDimension(R.dimen.dialog_margin).toInt()
        params.leftMargin = resources.getDimension(R.dimen.dialog_margin).toInt()
        params.rightMargin = resources.getDimension(R.dimen.dialog_margin).toInt()
        textView.layoutParams = params
        container.addView(textView)

        // Show dialog
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.credits_dialog_title))
        builder.setView(container)

        val positiveText = getString(android.R.string.ok)
        builder.setPositiveButton(positiveText) { _, _ ->
            // positive button logic
        }

        val dialog = builder.create()
        // display dialog
        dialog.show()
    }
}
