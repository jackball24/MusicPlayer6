package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.closeKeyboard
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.showKeyboard
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.LibraryViewModel
import org.akanework.gramophone.ui.adapters.SongAdapter

/**
 * SearchFragment:
 *   A fragment that contains a search bar which browses
 * the library finding items matching user input.
 *
 * @author grizzly03
 */
class SearchFragment : BaseFragment(false) {

    private val handler = Handler(Looper.getMainLooper())
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private val filteredList: MutableList<MediaItem> = mutableListOf()
    private lateinit var editText: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        val rootView = inflater.inflate(R.layout.fragment_search, container, false)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()
        editText = rootView.findViewById(R.id.edit_text)
        val recyclerView = rootView.findViewById<MyRecyclerView>(R.id.recyclerview)
        val songAdapter =
            SongAdapter(this, listOf(),
                true, null, false, isSubFragment = true,
                allowDiffUtils = true, rawOrderExposed = true)
        val returnButton = rootView.findViewById<Button>(R.id.return_button)

        recyclerView.enableEdgeToEdgePaddingListener(ime = true)
        recyclerView.setAppBar(appBarLayout)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = songAdapter.concatAdapter

        // Build FastScroller.
        recyclerView.fastScroll(songAdapter, songAdapter.itemHeightHelper)

        editText.addTextChangedListener { rawText ->

            if (rawText.isNullOrBlank()) {
                songAdapter.updateList(listOf(), now = true, true)
            } else {
                // make sure the user doesn't edit away our text while we are filtering
                val text = rawText.toString()
                // Launch a coroutine for searching in the library.
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    // Clear the list from the last search.
                    filteredList.clear()
                    // Filter the library.
                    libraryViewModel.mediaItemList.value?.filter {
                        val isMatchingTitle = it.mediaMetadata.title?.contains(text, true) ?: false
                        val isMatchingAlbum =
                            it.mediaMetadata.albumTitle?.contains(text, true) ?: false
                        val isMatchingArtist =
                            it.mediaMetadata.artist?.contains(text, true) ?: false
                        isMatchingTitle || isMatchingAlbum || isMatchingArtist
                    }?.let {
                        filteredList.addAll(
                            it
                        )
                    }
                    handler.post {
                        songAdapter.updateList(filteredList, now = true, true)
                    }
                }
            }
        }

        returnButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        return rootView
    }

    override fun onPause() {
        if (!isHidden) {
            requireActivity().closeKeyboard(editText)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            requireActivity().showKeyboard(editText)
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (hidden) {
            requireActivity().closeKeyboard(editText)
            super.onHiddenChanged(true)
        } else {
            super.onHiddenChanged(false)
            requireActivity().showKeyboard(editText)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewLifecycleOwner.lifecycleScope.cancel()
    }

}
