package org.akanework.gramophone.ui.adapters

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.text.InputType
import android.widget.EditText
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

                else -> false
            }
        }
    }


    companion object {
        val playlistList = MutableLiveData<List<MediaStoreUtils.Playlist>>(listOf())
        fun createNewPlaylist(context: Context) {
            // 创建一个输入对话框，获取用户输入的播放列表名称
            val builder = AlertDialog.Builder(context)
            builder.setTitle("创建新播放列表")

            // 设置输入框
            val input = EditText(context)
            input.inputType = InputType.TYPE_CLASS_TEXT
            builder.setView(input)

            // 设置对话框按钮
            builder.setPositiveButton("确定") { dialog, which ->
                val playlistName = input.text.toString()
                if (playlistName.isNotEmpty()) {
                    val playlistId = addPlaylist(context, playlistName)
                    if (playlistId != -1L) {
                        Toast.makeText(context, "播放列表已创建", Toast.LENGTH_SHORT).show()
                        val currentPlaylists = playlistList.value?.toMutableList() ?: mutableListOf()
                        val newPlaylist = MediaStoreUtils.Playlist(playlistId, playlistName, mutableListOf())
                        currentPlaylists.add(newPlaylist)
                        playlistList.value = currentPlaylists.toList()
                    } else {
                        Toast.makeText(context, "播放列表创建失败", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "播放列表名称不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            builder.setNegativeButton("取消") { dialog, which ->
                dialog.cancel()
            }

            builder.show()
        }

        // 添加播放列表方法
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

    }
}
