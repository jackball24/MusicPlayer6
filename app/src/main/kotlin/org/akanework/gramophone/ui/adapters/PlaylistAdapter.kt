package org.akanework.gramophone.ui.adapters

import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.fragments.GeneralSubFragment

/**
 * [PlaylistAdapter] is an adapter for displaying artists.
 */
class PlaylistAdapter(
    fragment: Fragment,
    playlistList: MutableLiveData<List<MediaStoreUtils.Playlist>>,
) : BaseAdapter<MediaStoreUtils.Playlist>
    (
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
        return context.getString(
            if (item is MediaStoreUtils.RecentlyAdded)
                R.string.recently_added else R.string.unknown_playlist
        )
    }

    override fun onClick(item: MediaStoreUtils.Playlist) {
        mainActivity.startFragment(GeneralSubFragment()) {
            putInt("Position", toRawPos(item))
            putInt("Item", R.id.playlist)
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

                R.id.delete -> {
                    deletePlaylist(item)
                    true
                }
                else -> false
            }
        }
    }
    private fun deletePlaylist(context: Context, playlistId: Long): Boolean {
        val resolver = context.contentResolver
        val uri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI
        val where = "${MediaStore.Audio.Playlists._ID} = ?"
        val args = arrayOf(playlistId.toString())

        val deletedRows = resolver.delete(uri, where, args)
        return deletedRows > 0
    }

    private fun deletePlaylist(item: MediaStoreUtils.Playlist) {
        val playlistId = item.id
        if (playlistId != null) {
            val deleted = deletePlaylist(context, playlistId)
            if (deleted) {

                notifyDataSetChanged()
                Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to delete playlist", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Invalid playlist id", Toast.LENGTH_SHORT).show()
        }
    }


}
