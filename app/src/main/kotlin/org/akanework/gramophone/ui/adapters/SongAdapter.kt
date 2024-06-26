package org.akanework.gramophone.ui.adapters

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.LibraryViewModel
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.NowPlayingDrawable
import org.akanework.gramophone.ui.fragments.ArtistSubFragment
import org.akanework.gramophone.ui.fragments.DetailDialogFragment
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import java.util.GregorianCalendar


/**
 * [SongAdapter] is an adapter for displaying songs.
 */
class SongAdapter(
    fragment: Fragment,
    songList: MutableLiveData<List<MediaItem>>?,
    canSort: Boolean,
    helper: Sorter.NaturalOrderHelper<MediaItem>?,
    ownsView: Boolean,
    isSubFragment: Boolean = false,
    allowDiffUtils: Boolean = false,
    rawOrderExposed: Boolean = !isSubFragment,
    fallbackSpans: Int = 1
) : BaseAdapter<MediaItem>
    (
    fragment,
    liveData = songList,
    sortHelper = MediaItemHelper(),
    naturalOrderHelper = if (canSort) helper else null,
    initialSortType = if (canSort)
        (if (helper != null) Sorter.Type.NaturalOrder else
                (if (rawOrderExposed) Sorter.Type.NativeOrder else Sorter.Type.ByTitleAscending))
    else Sorter.Type.None,
    canSort = canSort,
    pluralStr = R.plurals.songs,
    ownsView = ownsView,
    defaultLayoutType = LayoutType.COMPACT_LIST,
    isSubFragment = isSubFragment,
    rawOrderExposed = rawOrderExposed,
    allowDiffUtils = allowDiffUtils,
    fallbackSpans = fallbackSpans
) {

    constructor(
        fragment: Fragment,
        songList: List<MediaItem>,
        canSort: Boolean,
        helper: Sorter.NaturalOrderHelper<MediaItem>?,
        ownsView: Boolean,
        isSubFragment: Boolean = false,
        allowDiffUtils: Boolean = false,
        rawOrderExposed: Boolean = !isSubFragment,
        fallbackSpans: Int = 1
    ) : this(
        fragment,
        null,
        canSort,
        helper,
        ownsView,
        isSubFragment,
        allowDiffUtils,
        rawOrderExposed,
        fallbackSpans
    ) {
        updateList(songList, now = true, false)
    }

    fun getSongList() = list

    fun getActivity() = mainActivity

    private val viewModel: LibraryViewModel by fragment.activityViewModels()
    private val mediaControllerViewModel: MediaControllerViewModel by fragment.activityViewModels()
    private var idToPosMap: HashMap<String, Int>? = null
    private var currentMediaItem: String? = null
        set(value) {
            if (field != value) {
                val oldValue = field
                field = value
                if (idToPosMap != null) {
                    val oldPos = idToPosMap!![oldValue]
                    val newPos = idToPosMap!![value]
                    if (oldPos != null) {
                        notifyItemChanged(oldPos, true)
                    }
                    if (newPos != null) {
                        notifyItemChanged(newPos, true)
                    }
                }
            }
        }
    private var currentIsPlaying: Boolean? = null
        set(value) {
            if (field != value) {
                field = value
                if (value != null && currentMediaItem != null) {
                    idToPosMap?.get(currentMediaItem)?.let {
                        notifyItemChanged(it, false)
                    }
                }
            }
        }

    init {
        mediaControllerViewModel.addRecreationalPlayerListener(
            fragment.viewLifecycleOwner.lifecycle) {
            currentMediaItem = it.currentMediaItem?.mediaId
            currentIsPlaying = it.playWhenReady && it.playbackState != Player.STATE_ENDED && it.playbackState != Player.STATE_IDLE
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentMediaItem = mediaItem?.mediaId
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    currentIsPlaying = playWhenReady && it.playbackState != Player.STATE_ENDED && it.playbackState != Player.STATE_IDLE
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    currentIsPlaying = it.playWhenReady && playbackState != Player.STATE_ENDED && it.playbackState != Player.STATE_IDLE
                }
            }
        }
    }

    override fun onListUpdated() {
        // TODO run this method on a different thread / in advance
        idToPosMap = hashMapOf()
        list.forEachIndexed { i, item -> idToPosMap!![item.mediaId] = i }
    }

    override fun virtualTitleOf(item: MediaItem): String {
        return "null"
    }

    override fun onClick(item: MediaItem) {
        val mediaController = mainActivity.getPlayer()
        mediaController?.apply {
            val songList = getSongList()
            setMediaItems(songList, songList.indexOf(item), C.TIME_UNSET)
            prepare()
            play()
        }
    }

    override fun onMenu(item: MediaItem, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu)

        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItem(
                        mediaController.currentMediaItemIndex + 1,
                        item,
                    )
                    true
                }

                R.id.album -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        val positionAlbum =
                            viewModel.albumItemList.value?.indexOfFirst {
                                (it.title == item.mediaMetadata.albumTitle) &&
                                        (it.songList.contains(item))
                            }
                        if (positionAlbum != null) {
                            withContext(Dispatchers.Main) {
                                mainActivity.startFragment(GeneralSubFragment()) {
                                    putInt("Position", positionAlbum)
                                    putInt("Item", R.id.album)
                                }
                            }
                        }
                    }
                    true
                }

                R.id.artist -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        val positionArtist =
                            viewModel.artistItemList.value?.indexOfFirst {
                                val isMatching =
                                    (it.title == item.mediaMetadata.artist) &&
                                            (it.songList.contains(item))
                                isMatching
                            }
                        if (positionArtist != null) {
                            withContext(Dispatchers.Main) {
                                mainActivity.startFragment(ArtistSubFragment()) {
                                    putInt("Position", positionArtist)
                                    putInt("Item", R.id.artist)
                                }
                            }
                        }
                    }
                    true
                }

                R.id.details -> {
                    val position = viewModel.mediaItemList.value?.indexOfFirst {
                        it.mediaId == item.mediaId
                    }
                    mainActivity.startFragment(DetailDialogFragment()) {
                        putInt("Position", position!!)
                    }
                    true
                }

                R.id.add -> {
                    addToPlaylist(item)
                    true
                }
                else -> false
            }
        }
    }
    private fun addToPlaylist(item: MediaItem) {
        // 过滤掉ID为-1的播放列表和标题为"收藏"的播放列表
        val filteredPlaylists = viewModel.playlistList.value?.filter {
            it.id != -1L && !it.title.equals("收藏", ignoreCase = true)
        }

        val playlistNames = filteredPlaylists?.map { it.title ?: "Unknown Playlist" }?.toTypedArray()
        val selectedPlaylistIds = ArrayList<Long>()
        MaterialAlertDialogBuilder(context)
            .setTitle("添加至歌单")
            .setMultiChoiceItems(playlistNames, null) { _, which, isChecked ->
                if (isChecked) {
                    val selectedPlaylist = filteredPlaylists?.get(which)
                    selectedPlaylist?.let { targetPlaylist ->
                        selectedPlaylistIds.add(targetPlaylist.id)
                    }
                }
            }
            .setPositiveButton("确认") { _, _ ->
                // 循环遍历选中的歌单ID，并将歌曲添加到每个歌单中
                selectedPlaylistIds.forEach { playlistId ->
                    val targetPlaylist = filteredPlaylists?.find { it.id == playlistId }
                    targetPlaylist?.let { selectedPlaylist ->
                        // 检查歌单中是否已包含该歌曲
                        if (!selectedPlaylist.songList.contains(item)) {
                            val resolver = context.contentResolver
                            val values = ContentValues().apply {
                                @Suppress("DEPRECATION")
                                put(MediaStore.Audio.Playlists.Members.AUDIO_ID, item.mediaId.toLong())
                                @Suppress("DEPRECATION")
                                put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, 0) // 可以根据实际情况设置播放顺序
                            }

                            val uri = resolver.insert(
                                @Suppress("DEPRECATION")
                                MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
                                values
                            )

                            // 检查插入是否成功，并根据需要更新界面或显示消息
                            if (uri != null) {
                                val updatedPlaylist = viewModel.playlistList.value?.map { playlist ->
                                    if (playlist.id == playlistId) {
                                        // 更新目标播放列表中的歌曲列表
                                        val updatedSongList = playlist.songList.toMutableList().apply {
                                            add(item)
                                        }
                                        MediaStoreUtils.Playlist(playlist.id, playlist.title, updatedSongList)
                                    } else {
                                        playlist
                                    }
                                }
                                viewModel.playlistList.postValue(updatedPlaylist)
                                // 显示消息
                                Toast.makeText(context, "歌曲成功添加至歌单 ${selectedPlaylist.title}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "添加至歌单 ${selectedPlaylist.title} 失败", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "歌曲已在歌单 ${selectedPlaylist.title} 中", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            if (payloads.none { it is Boolean && it }) {
                holder.nowPlaying.drawable.level = if (currentIsPlaying == true) 1 else 0
                return
            }
            if (currentMediaItem == null || list[position].mediaId != currentMediaItem) {
                (holder.nowPlaying.drawable as? NowPlayingDrawable?)?.level2Done = Runnable {
                    holder.nowPlaying.visibility = View.GONE
                    holder.nowPlaying.setImageDrawable(null)
                }
                holder.nowPlaying.drawable?.level = 2
                return
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
            if (currentMediaItem == null || list[position].mediaId != currentMediaItem)
                return
        }
        holder.nowPlaying.setImageDrawable(NowPlayingDrawable()
            .also { it.level = if (currentIsPlaying == true) 1 else 0 })
        holder.nowPlaying.visibility = View.VISIBLE
    }

    class MediaItemHelper(
        types: Set<Sorter.Type> = setOf(
            Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
            Sorter.Type.ByArtistDescending, Sorter.Type.ByArtistAscending,
            Sorter.Type.ByAlbumArtistDescending, Sorter.Type.ByAlbumArtistAscending,
            Sorter.Type.ByAddDateDescending, Sorter.Type.ByAddDateAscending,
            Sorter.Type.ByDiscAndTrack
        )
    ) : Sorter.Helper<MediaItem>(types) {
        override fun getId(item: MediaItem): String {
            return item.mediaId
        }

        override fun getTitle(item: MediaItem): String {
            return item.mediaMetadata.title.toString()
        }

        override fun getArtist(item: MediaItem): String? {
            return item.mediaMetadata.artist?.toString()
        }

        override fun getAlbumTitle(item: MediaItem): String {
            return item.mediaMetadata.albumTitle?.toString() ?: ""
        }

        override fun getAlbumArtist(item: MediaItem): String {
            return item.mediaMetadata.albumArtist?.toString() ?: ""
        }

        override fun getCover(item: MediaItem): Uri? {
            return item.mediaMetadata.artworkUri
        }

        override fun getDiscAndTrack(item: MediaItem): Int {
            return (item.mediaMetadata.discNumber ?: 0) * 1000 + (item.mediaMetadata.trackNumber ?: 0)
        }

        override fun getAddDate(item: MediaItem): Long {
            return item.mediaMetadata.extras!!.getLong("AddDate")
        }

        override fun getReleaseDate(item: MediaItem): Long {
            if (item.mediaMetadata.releaseYear == null && item.mediaMetadata.releaseMonth == null
                && item.mediaMetadata.releaseDay == null) {
                return GregorianCalendar((item.mediaMetadata.recordingYear ?: 0) + 1900,
                    (item.mediaMetadata.recordingMonth ?: 1) - 1,
                    item.mediaMetadata.recordingDay ?: 0, 0, 0, 0)
                    .timeInMillis
            }
            return GregorianCalendar((item.mediaMetadata.releaseYear ?: 0) + 1900,
                (item.mediaMetadata.releaseMonth ?: 1) - 1,
                item.mediaMetadata.releaseDay ?: 0, 0, 0, 0)
                .timeInMillis
        }

        override fun getModifiedDate(item: MediaItem): Long {
            return item.mediaMetadata.extras!!.getLong("ModifiedDate")
        }
    }
}