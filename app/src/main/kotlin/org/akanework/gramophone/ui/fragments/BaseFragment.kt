package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.MaterialSharedAxis
import org.akanework.gramophone.ui.MainActivity

/**
 * BaseFragment:
 *   It is a base fragment for all main fragments that
 * can appear in MainActivity's FragmentContainer.
 *   It creates material transitions easily and also make
 * overlapping colors more convenient. It can also manage
 * whether to show up bottom's mini player or not.
 *
 * @author 时空L0k1,grizzly03
 * @see MainActivity
 */
abstract class BaseFragment(val wantsPlayer: Boolean? = null) : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable material transitions.
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Overlap colors.
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.colorBackground))
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) return
        // see registerFragmentLifecycleCallbacks in MainActivity
        if (wantsPlayer != null) {
            (requireActivity() as MainActivity).playerBottomSheet.visible = wantsPlayer
        }
    }
}