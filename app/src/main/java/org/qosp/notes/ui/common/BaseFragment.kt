package org.qosp.notes.ui.common

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.transition.MaterialSharedAxis
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.qosp.notes.ui.ActivityViewModel
import org.qosp.notes.ui.MainActivity
import org.qosp.notes.ui.utils.ExportNotesContract

const val FRAGMENT_MESSAGE = "FRAGMENT_MESSAGE"

open class BaseFragment(@LayoutRes resId: Int) : Fragment(resId) {

    protected open val hasMenu: Boolean = true
    protected open val hasDefaultAnimation: Boolean = true

    protected val TAG = this::class.simpleName ?: "Quillpad"

    val activityModel: ActivityViewModel by activityViewModel()
    protected open val toolbar: Toolbar? = null
    protected open val toolbarTitle: String = ""

    protected val exportNotesLauncher = registerForActivityResult(ExportNotesContract) { uri ->
        if (uri == null) return@registerForActivityResult
        (activity as MainActivity).startBackup(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(hasMenu)
        if (hasDefaultAnimation) {
            enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true).apply { duration = 300L }
            reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true).apply { duration = 300L }
            exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false).apply { duration = 300L }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
    }

    protected open fun setupToolbar() {
        (activity as MainActivity).apply {
            setSupportActionBar(toolbar)
            setupActionBarWithNavController(navController, appBarConfiguration)
            supportActionBar?.title = toolbarTitle
        }
    }

    protected fun sendMessage(message: String) {
        setFragmentResult(FRAGMENT_MESSAGE, bundleOf(FRAGMENT_MESSAGE to message))
    }
}
