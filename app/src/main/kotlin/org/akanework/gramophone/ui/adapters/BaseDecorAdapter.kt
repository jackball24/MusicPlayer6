package org.akanework.gramophone.ui.adapters

import android.app.AlertDialog
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.media3.common.C
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.getAdapterType
import kotlin.random.Random

open class BaseDecorAdapter<T : BaseAdapter<*>>(
    protected val adapter: T,
    private val pluralStr: Int,
    private val isSubFragment: Boolean = false
) : MyRecyclerView.Adapter<BaseDecorAdapter<T>.ViewHolder>(), ItemHeightHelper {

    protected val context: Context = adapter.context
    private val dpHeight = context.resources.getDimensionPixelSize(R.dimen.decor_height)
    private var recyclerView: MyRecyclerView? = null
    private var prefs = PreferenceManager.getDefaultSharedPreferences(context)
    var jumpUpPos: Int? = null
    var jumpDownPos: Int? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = adapter.layoutInflater.inflate(R.layout.general_decor, parent, false)
        return ViewHolder(view)
    }


    final override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val count = adapter.itemCount
        holder.addtoList.visibility=
            if(adapter is PlaylistAdapter) View.VISIBLE else View.GONE
        holder.playAll.visibility =
            if (adapter is SongAdapter) View.VISIBLE else View.GONE
        holder.shuffleAll.visibility =
            if (adapter is SongAdapter) View.VISIBLE else View.GONE
        holder.counter.text = context.resources.getQuantityString(pluralStr, count, count)
        holder.sortButton.visibility =
            if (adapter.sortType != Sorter.Type.None || adapter.ownsView) View.VISIBLE else View.GONE
        holder.sortButton.setOnClickListener { view ->
            val popupMenu = PopupMenu(context, view)
            popupMenu.inflate(R.menu.sort_menu)
            val buttonMap = mapOf(
                Pair(R.id.natural, Sorter.Type.NaturalOrder),
                Pair(R.id.name, if (adapter.sortTypes.contains(Sorter.Type.NativeOrder))
                    Sorter.Type.NativeOrder else Sorter.Type.ByTitleAscending),
                Pair(R.id.artist, Sorter.Type.ByArtistAscending),
                Pair(R.id.album, Sorter.Type.ByAlbumTitleAscending),
                Pair(R.id.size, Sorter.Type.BySizeDescending),
                Pair(R.id.add_date, Sorter.Type.ByAddDateDescending),
                Pair(R.id.release_date, Sorter.Type.ByReleaseDateDescending),
                Pair(R.id.mod_date, Sorter.Type.ByModifiedDateDescending)
            )
            val layoutMap = mapOf(
                Pair(R.id.list, BaseAdapter.LayoutType.LIST),
                Pair(R.id.compact_list, BaseAdapter.LayoutType.COMPACT_LIST),
                Pair(R.id.grid, BaseAdapter.LayoutType.GRID)
            )
            buttonMap.forEach {
                popupMenu.menu.findItem(it.key).isVisible = adapter.sortTypes.contains(it.value)
            }
            layoutMap.forEach {
                popupMenu.menu.findItem(it.key).isVisible = adapter.ownsView
            }
            popupMenu.menu.findItem(R.id.display).isVisible = adapter.ownsView
            if (adapter.sortType != Sorter.Type.None) {
                when (adapter.sortType) {
                    in buttonMap.values -> {
                        popupMenu.menu.findItem(
                            buttonMap.entries
                                .first { it.value == adapter.sortType }.key
                        ).isChecked = true
                    }

                    else -> throw IllegalStateException("Invalid sortType ${adapter.sortType.name}")
                }
            }
            if (adapter.ownsView) {
                when (adapter.layoutType) {
                    in layoutMap.values -> {
                        popupMenu.menu.findItem(
                            layoutMap.entries
                                .first { it.value == adapter.layoutType }.key
                        ).isChecked = true
                    }

                    else -> throw IllegalStateException("Invalid layoutType ${adapter.layoutType?.name}")
                }
            }
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    in buttonMap.keys -> {
                        if (!menuItem.isChecked) {
                            adapter.sort(buttonMap[menuItem.itemId]!!)
                            menuItem.isChecked = true
                            if (!isSubFragment) {
                                prefs.edit {
                                    putString(
                                        "S" + getAdapterType(adapter).toString(),
                                        buttonMap[menuItem.itemId].toString()
                                    )
                                }
                            }
                        }
                        true
                    }

                    in layoutMap.keys -> {
                        if (!menuItem.isChecked) {
                            adapter.layoutType = layoutMap[menuItem.itemId]!!
                            menuItem.isChecked = true
                            if (!isSubFragment) {
                                prefs.edit {
                                    putString(
                                        "L" + getAdapterType(adapter).toString(),
                                        layoutMap[menuItem.itemId].toString()
                                    )
                                }
                            }
                        }
                        true
                    }

                    else -> onExtraMenuButtonPressed(menuItem)
                }
            }
            onSortButtonPressed(popupMenu)
            popupMenu.show()
        }
        holder.playAll.setOnClickListener {
            if (adapter is SongAdapter) {
                val mediaController = adapter.getActivity().getPlayer()
                val songList = adapter.getSongList()
                mediaController?.apply {
                    shuffleModeEnabled = false
                    repeatMode = REPEAT_MODE_OFF
                    setMediaItems(songList, 0, C.TIME_UNSET)
                    if (songList.size > 0) {
                        prepare()
                        play()
                    }
                }
            }
        }
        holder.shuffleAll.setOnClickListener {
            if (adapter is SongAdapter) {
                val list = adapter.getSongList()
                val controller = adapter.getActivity().getPlayer()
                controller?.shuffleModeEnabled = true
                list.takeIf { it.isNotEmpty() }?.also {
                    controller?.setMediaItems(it)
                    controller?.seekToDefaultPosition(Random.nextInt(0, it.size))
                    controller?.prepare()
                    controller?.play()
                } ?: controller?.setMediaItems(listOf())
            }
        }
        holder.jumpUp.visibility = if (jumpUpPos != null) View.VISIBLE else View.GONE
        holder.jumpUp.setOnClickListener {
            scrollToViewPosition(jumpUpPos!!)
        }
        holder.jumpDown.visibility = if (jumpDownPos != null) View.VISIBLE else View.GONE
        holder.jumpDown.setOnClickListener {
            scrollToViewPosition(jumpDownPos!!)
        }
 }

    override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    private fun scrollToViewPosition(pos: Int) {
        val smoothScroller = object : LinearSmoothScroller(context) {
            override fun calculateDtToFit(
                viewStart: Int,
                viewEnd: Int,
                boxStart: Int,
                boxEnd: Int,
                snapPreference: Int
            ): Int {
                return (super.calculateDtToFit(
                    viewStart,
                    viewEnd,
                    boxStart,
                    boxEnd,
                    snapPreference
                ))// + context.resources.displayMetrics.heightPixels / 3).coerceAtMost(viewEnd)
            }

            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }

            /*override fun calculateTimeForDeceleration(dx: Int): Int {
                return 500
            }*/
        }
        smoothScroller.targetPosition = pos
        recyclerView?.startSmoothScrollCompat(smoothScroller)
    }


    protected open fun onSortButtonPressed(popupMenu: PopupMenu) {}
    protected open fun onExtraMenuButtonPressed(menuItem: MenuItem): Boolean = false

    override fun getItemCount(): Int = 1

    inner class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val sortButton: MaterialButton = view.findViewById(R.id.sort)
        val playAll: MaterialButton = view.findViewById(R.id.play_all)
        val shuffleAll: MaterialButton = view.findViewById(R.id.shuffle_all)
        val jumpUp: MaterialButton = view.findViewById(R.id.jumpUp)
        val jumpDown: MaterialButton = view.findViewById(R.id.jumpDown)
        val counter: TextView = view.findViewById(R.id.song_counter)
        val addtoList: MaterialButton = view.findViewById(R.id.playlist_add)

        init {
            addtoList.setOnClickListener {
                showAddListDialog()

            }

        }
        fun addPlaylist(context: Context, playlistName: String): Long {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Playlists.NAME, playlistName)
                put(MediaStore.Audio.Playlists.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Audio.Playlists.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            }
            val uri = resolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values)
            return uri?.lastPathSegment?.toLong() ?: -1
        }
        private fun showAddListDialog() {
            val editText = EditText(context).apply {
                hint = "Enter playlist name"
            }

            val dialog = AlertDialog.Builder(context)
                .setTitle("Add New Playlist")
                .setView(editText)
                .setPositiveButton("OK") { dialog, _ ->
                    val playlistName = editText.text.toString().trim()
                    if (playlistName.isNotEmpty()) {
                        val playlistId = addPlaylist(context, playlistName)
                        if (playlistId != -1L) {

                            Toast.makeText(context, "Playlist created successfully", Toast.LENGTH_SHORT).show()

                        } else {
                            Toast.makeText(context, "Failed to create playlist", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Playlist name cannot be empty", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.cancel()
                }
                .create()

            dialog.show()
        }

    }

        fun updateSongCounter() {
        notifyItemChanged(0)
    }

    override fun getItemHeightFromZeroTo(to: Int): Int {
        return if (to > 0) dpHeight else 0
    }
}