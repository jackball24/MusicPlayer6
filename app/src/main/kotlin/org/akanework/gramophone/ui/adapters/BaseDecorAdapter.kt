package org.akanework.gramophone.ui.adapters

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.getAdapterType
import kotlin.random.Random

open class BaseDecorAdapter<T : BaseAdapter<*>, ItemType>(
    protected val adapter: T,
    private val itemTypeClass: Class<ItemType>,
    private val pluralStr: Int,
    private val isSubFragment: Boolean = false
) : MyRecyclerView.Adapter<BaseDecorAdapter<T, ItemType>.ViewHolder>(), ItemHeightHelper {

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
        holder.addToList.visibility =
            if (adapter is PlaylistAdapter) View.VISIBLE else View.GONE
        holder.playAll.visibility =
            if (adapter is SongAdapter) View.VISIBLE else View.GONE
        holder.shuffleAll.visibility =
            if (adapter is SongAdapter) View.VISIBLE else View.GONE
        holder.counter.text = context.resources.getQuantityString(pluralStr, count, count)
        holder.sortButton.visibility =
            if (adapter.sortType != Sorter.Type.None || adapter.ownsView) View.VISIBLE else View.GONE

        holder.playlistAdd.setOnClickListener {
            showCreatePlaylistDialog()
        }

        holder.playAll.setOnClickListener {
            if (adapter is SongAdapter) {
                val mediaController = adapter.getActivity().getPlayer()
                val songList = adapter.getSongList()
                mediaController?.apply {
                    shuffleModeEnabled = false
                    repeatMode = REPEAT_MODE_OFF
                    setMediaItems(songList, 0, C.TIME_UNSET)
                    if (songList.isNotEmpty()) {
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

    private fun showCreatePlaylistDialog() {
        val dialogView = adapter.layoutInflater.inflate(R.layout.dialog_create_playlist, null)
        val playlistNameEditText = dialogView.findViewById<EditText>(R.id.playlist_name)
        val createButton = dialogView.findViewById<Button>(R.id.create_playlist_button)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.create_playlist)
            .setView(dialogView)
            .create()

        createButton.setOnClickListener {
            val playlistName = playlistNameEditText.text.toString().trim()
            if (playlistName.isNotEmpty()) {
                createNewPlaylist(playlistName)
                dialog.dismiss()
            } else {
                Toast.makeText(context, R.string.enter_valid_name, Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    val baseDecorAdapter = BaseDecorAdapter(adapter, MediaStoreUtils.Playlist::class.java, R.plurals.items)

    private fun createNewPlaylist(name: String) {
        val playlistId = System.currentTimeMillis()
        val newPlaylist = MediaStoreUtils.Playlist(playlistId, name, mutableListOf())

        val currentPlaylists = adapter.liveData?.value?.toMutableList() ?: mutableListOf()
        if (itemTypeClass.isInstance(newPlaylist)) {
            currentPlaylists.add(newPlaylist as ItemType)
            adapter.liveData?.value = currentPlaylists as List<ItemType>
        } else {
            throw IllegalArgumentException("Type mismatch: expected ${itemTypeClass}, found ${newPlaylist::class.java}")
        }
    }



    private fun showSelectPlaylistDialog(song: MediaItem) {
        val dialogView = adapter.layoutInflater.inflate(R.layout.dialog_select_playlist, null)
        val playlistListView = dialogView.findViewById<ListView>(R.id.playlist_list)

        val playlists = adapter.liveData?.value?.filterIsInstance<MediaStoreUtils.Playlist>() ?: emptyList()
        val playlistTitles = playlists.map { it.title ?: context.getString(R.string.unknown_playlist) }
        val playlistAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, playlistTitles)
        playlistListView.adapter = playlistAdapter

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.select_playlist)
            .setView(dialogView)
            .create()

        playlistListView.setOnItemClickListener { _, _, position, _ ->
            val selectedPlaylist = playlists[position]
            addToPlaylist(selectedPlaylist, song)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addToPlaylist(playlist: MediaStoreUtils.Playlist, song: MediaItem) {
        playlist.songList.add(song)
        @Suppress("UNCHECKED_CAST")
        adapter.liveData?.value = adapter.liveData?.value as List<MediaStoreUtils.Playlist>?
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
                ))
            }

            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
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
        val playlistAdd: MaterialButton = view.findViewById(R.id.playlist_add)
        val shuffleAll: MaterialButton = view.findViewById(R.id.shuffle_all)
        val jumpUp: MaterialButton = view.findViewById(R.id.jumpUp)
        val jumpDown: MaterialButton = view.findViewById(R.id.jumpDown)
        val counter: TextView = view.findViewById(R.id.song_counter)
        val addToList: MaterialButton = view.findViewById(R.id.playlist_add)
    }

    fun updateSongCounter() {
        notifyItemChanged(0)
    }

    override fun getItemHeightFromZeroTo(to: Int): Int {
        return if (to > 0) dpHeight else 0
    }
}
