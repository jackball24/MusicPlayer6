package org.akanework.gramophone.ui.adapters

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.MediaStore
import android.view.Gravity
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.marginTop
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
                    deletePlaylist(context,item)
                    true
                }
                else -> false
            }
        }
    }
    @SuppressLint("NotifyDataSetChanged")
    private fun deletePlaylist(context: Context, item: MediaStoreUtils.Playlist) {
        val playlistId = item.id
        val resolver = context.contentResolver
        @Suppress("DEPRECATION")
        val uri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION")
        val where = "${MediaStore.Audio.Playlists._ID} = ?"
        val args = arrayOf(playlistId.toString())

        val deletedRows = resolver.delete(uri, where, args)
        val deleted = deletedRows > 0

        if (deleted) {
            notifyDataSetChanged()
            Toast.makeText(context, "删除成功！", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "删除失败！默认歌单不可以删除~", Toast.LENGTH_SHORT).show()
        }
    }
    companion object {
        fun newPlaylist(context: Context) {
            val editText = EditText(context).apply {
                hint = "请输入歌单名"
                setPadding(80, 50, 0, 0)
                background = ColorDrawable(Color.TRANSPARENT)
            }

            MaterialAlertDialogBuilder(context)
                .setTitle("添加新歌单")
                .setView(editText)
                .setPositiveButton("确认") { _, _ ->
                    val playlistName = editText.text.toString().trim()
                    if (playlistName.isNotEmpty()) {
                        val resolver = context.contentResolver
                        val values = ContentValues().apply {
                            @Suppress("DEPRECATION")
                            put(MediaStore.Audio.Playlists.NAME, playlistName)
                            put(MediaStore.Audio.AudioColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
                            put(MediaStore.Audio.AudioColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                        }

                        val uri = resolver.insert(
                            @Suppress("DEPRECATION")
                            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values)
                        val playlistId = uri?.lastPathSegment?.toLong() ?: -1
                        if (playlistId != -1L) {
                            Toast.makeText(context, "歌单创建成功！", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "歌单创建失败！", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "歌单名不能为空！", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .create().show()
        }
    }

}
