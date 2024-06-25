package org.akanework.gramophone.ui.adapters

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.fragments.GeneralSubFragment

class PlaylistAdapter(
    fragment: Fragment,
    private val playlistList: MutableLiveData<List<MediaStoreUtils.Playlist>>,
    private val onPlaylistSelected: ((MediaStoreUtils.Playlist) -> Unit)? = null
) : BaseAdapter<MediaStoreUtils.Playlist>(
    fragment,
    liveData = playlistList,
    sortHelper = StoreItemHelper(),
    naturalOrderHelper = null,
    initialSortType = Sorter.Type.ByTitleAscending,
    pluralStr = R.plurals.items,
    ownsView = true,
    defaultLayoutType = LayoutType.LIST
) {

    override val defaultCover = R.drawable.ic_default_cover_playlist

    override fun virtualTitleOf(item: MediaStoreUtils.Playlist): String {
        return if (item.title.isNullOrEmpty()) {
            context.getString(R.string.unknown_playlist)
        } else {
            context.getString(R.string.playlist_template, item.title)
        }
    }

    override fun onClick(item: MediaStoreUtils.Playlist) {
        onPlaylistSelected?.invoke(item) ?: run {
            mainActivity.startFragment(GeneralSubFragment()) {
                putInt("Position", toRawPos(item))
                putInt("Item", R.id.playlist)
            }
        }
    }

    override fun onMenu(item: MediaStoreUtils.Playlist, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu_less)

        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItems(
                        mediaController.currentMediaItemIndex + 1,
                        item.songList,
                    )
                    true
                }
                R.id.details -> {
                    // Show playlist details
                    true
                }
                R.id.addtoList -> {
                    val song = getSelectedSong()
                    if (song != null) {
                        showSelectPlaylistDialog(song)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun getSelectedSong(): MediaItem? {
        // 实现获取选中的歌曲逻辑
        // 这里可以是通过某种方式获取当前选中的歌曲
        // 示例中直接返回一个新的MediaItem
        return MediaItem.Builder()
            .setMediaId("1")
            .setUri(Uri.parse("sample_uri"))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Sample Song")
                    .setArtist("Sample Artist")
                    .build()
            )
            .build()
    }

    private fun showCreatePlaylistDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_playlist, null)
        val playlistNameEditText = dialogView.findViewById<EditText>(R.id.playlist_name)
        val createButton = dialogView.findViewById<MaterialButton>(R.id.create_playlist_button)

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

    private fun createNewPlaylist(name: String) {
        val playlistId = System.currentTimeMillis()
        val newPlaylist = MediaStoreUtils.Playlist(playlistId, name, mutableListOf())

        val currentPlaylists = playlistList.value?.toMutableList() ?: mutableListOf()
        currentPlaylists.add(newPlaylist)
        playlistList.value = currentPlaylists
    }

    private fun showSelectPlaylistDialog(song: MediaItem) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_select_playlist, null)
        val playlistListView = dialogView.findViewById<ListView>(R.id.playlist_list)

        val playlists = playlistList.value ?: emptyList()
        val playlistAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, playlists.map { it.title ?: context.getString(R.string.unknown_playlist) })
        playlistListView.adapter = playlistAdapter

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.select_playlist)
            .setView(dialogView)
            .create()

        playlistListView.setOnItemClickListener { _, _, position, _ ->
            val selectedPlaylist = playlists[position]
            selectedPlaylist.songList.add(song)
            playlistList.value = playlistList.value
            dialog.dismiss()
        }

        dialog.show()
    }
}
