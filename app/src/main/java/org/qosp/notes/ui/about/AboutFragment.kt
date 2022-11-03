package org.qosp.notes.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import org.qosp.notes.BuildConfig
import org.qosp.notes.R
import org.qosp.notes.databinding.FragmentAboutBinding
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.common.BaseFragment
import org.qosp.notes.ui.utils.liftAppBarOnScroll
import org.qosp.notes.ui.utils.viewBinding
import javax.inject.Inject

@AndroidEntryPoint
class AboutFragment : BaseFragment(resId = R.layout.fragment_about) {
    private val binding by viewBinding(FragmentAboutBinding::bind)

    override val hasMenu = false
    override val toolbar: Toolbar
        get() = binding.layoutAppBar.toolbar
    override val toolbarTitle: String
        get() = getString(R.string.nav_about)

    @Inject
    lateinit var markwon: Markwon

    private fun launchUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        binding.scrollView.liftAppBarOnScroll(
            binding.layoutAppBar.appBar,
            requireContext().resources.getDimension(R.dimen.app_bar_elevation)
        )

        if (!BuildConfig.IS_GOOGLE) {
            binding.actionSupport.isVisible = true
        }
    }

    private fun setupListeners() = with(binding) {
        actionWebsite.setOnClickListener { launchUrl(requireContext().getString(R.string.app_website)) }
        actionContribute.setOnClickListener { launchUrl(requireContext().getString(R.string.app_repo)) }
        actionVisitDeveloper.setOnClickListener { launchUrl(requireContext().getString(R.string.app_developer_repo)) }
        actionViewLibraries.setOnClickListener { showLibrariesDialog() }
        if (!BuildConfig.IS_GOOGLE) {
            actionSupport.setOnClickListener { launchUrl(requireContext().getString(R.string.app_support_page)) }
        }
    }

    private fun showLibrariesDialog() {
        BaseDialog.build(requireContext()) {
            setTitle(R.string.about_libraries)
            setMessage(markwon.toMarkdown(getString(R.string.licenses_markdown_text)))
            setPositiveButton(R.string.ok, null)
        }
            .show()
    }
}
