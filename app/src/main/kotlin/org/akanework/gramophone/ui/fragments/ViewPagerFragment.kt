package org.akanework.gramophone.ui.fragments

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.clone
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.needsManualSnackBarInset
import org.akanework.gramophone.logic.updateMargin
import org.akanework.gramophone.ui.LibraryViewModel
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter
import org.akanework.gramophone.ui.fragments.settings.MainSettingsFragment

/**
 * ViewPagerFragment:
 *   A fragment that's in charge of displaying tabs
 * and is connected to the drawer.
 *
 * @author AkaneTan
 */
@androidx.annotation.OptIn(UnstableApi::class)
class ViewPagerFragment : BaseFragment(true) {
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    lateinit var appBarLayout: AppBarLayout
        private set

    @SuppressLint("StringFormatInvalid", "StringFormatMatches")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_viewpager, container, false)
        val tabLayout = rootView.findViewById<TabLayout>(R.id.tab_layout)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val viewPager2 = rootView.findViewById<ViewPager2>(R.id.fragment_viewpager)

        appBarLayout = rootView.findViewById(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()
        topAppBar.overflowIcon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_more_vert_alt)

        topAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.search -> {
                    (requireActivity() as MainActivity).startFragment(SearchFragment())
                }
                R.id.refresh -> {
                    val activity = requireActivity() as MainActivity
                    val playerLayout = activity.playerBottomSheet
                    activity.updateLibrary {
                        val snackBar =
                            Snackbar.make(
                                requireView(),
                                getString(
                                    R.string.refreshed_songs,
                                    libraryViewModel.mediaItemList.value!!.size,
                                ),
                                Snackbar.LENGTH_LONG,
                            )
                        snackBar.setAction(R.string.dismiss) {
                            snackBar.dismiss()
                        }

                        /*
                         * Let's override snack bar's color here so it would
                         * adapt dark mode.
                         */
                        snackBar.setBackgroundTint(
                            MaterialColors.getColor(
                                snackBar.view,
                                com.google.android.material.R.attr.colorSurface,
                            ),
                        )
                        snackBar.setActionTextColor(
                            MaterialColors.getColor(
                                snackBar.view,
                                com.google.android.material.R.attr.colorPrimary,
                            ),
                        )
                        snackBar.setTextColor(
                            MaterialColors.getColor(
                                snackBar.view,
                                com.google.android.material.R.attr.colorOnSurface,
                            ),
                        )

                        // Set an anchor for snack bar.
                        if (playerLayout.visible && playerLayout.actuallyVisible)
                            snackBar.anchorView = playerLayout
                        else if (needsManualSnackBarInset()) {
                            // snack bar only implements proper insets handling for Q+
                            snackBar.view.updateMargin {
                                val i = ViewCompat.getRootWindowInsets(activity.window.decorView)
                                if (i != null) {
                                    bottom += i.clone()
                                        .getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                                }
                            }
                        }
                        snackBar.show()
                    }
                }
                R.id.settings -> {
                    (requireActivity() as MainActivity).startFragment(MainSettingsFragment())
                }

                else -> throw IllegalStateException()
            }
            true
        }

        // Connect ViewPager2.

        // Set this to 9999 so it won't lag anymore.
        viewPager2.offscreenPageLimit = 9999
        val adapter = ViewPager2Adapter(childFragmentManager, viewLifecycleOwner.lifecycle)
        viewPager2.adapter = adapter
        TabLayoutMediator(tabLayout, viewPager2) { tab, position ->
            tab.text = getString(adapter.getLabelResId(position))
        }.attach()

        /*
         * Add margin to last and first tab.
         * There's no attribute to let you set margin
         * to the last tab.
         */
        val lastTab = tabLayout.getTabAt(tabLayout.tabCount - 1)!!.view
        val firstTab = tabLayout.getTabAt(0)!!.view
        val lastParam = lastTab.layoutParams as ViewGroup.MarginLayoutParams
        val firstParam = firstTab.layoutParams as ViewGroup.MarginLayoutParams
        lastParam.marginEnd = resources.getDimension(R.dimen.tab_layout_content_padding).toInt()
        firstParam.marginStart = resources.getDimension(R.dimen.tab_layout_content_padding).toInt()
        lastTab.layoutParams = lastParam
        firstTab.layoutParams = firstParam

        return rootView
    }
}
