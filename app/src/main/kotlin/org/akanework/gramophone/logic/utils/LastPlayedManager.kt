package org.akanework.gramophone.logic.utils

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.use
import org.akanework.gramophone.logic.utils.exoplayer.EndedWorkaroundPlayer
import java.nio.charset.StandardCharsets

@OptIn(UnstableApi::class)
class LastPlayedManager(context: Context,
                        private val controller: EndedWorkaroundPlayer) {

    companion object {
        private const val TAG = "LastPlayedManager"
    }

    var allowSavingState = true
    private val prefs by lazy { context.getSharedPreferences("LastPlayedManager", 0) }


    //该方法将当前播放列表及其相关信息保存到 MediaItemsWithStartPosition 对象中。
    private fun dumpPlaylist(): MediaItemsWithStartPosition {
        val items = mutableListOf<MediaItem>()
        for (i in 0 until controller.mediaItemCount) {
            items.add(controller.getMediaItemAt(i))
        }
        return MediaItemsWithStartPosition(
            items, controller.currentMediaItemIndex, controller.currentPosition
        )
    }

    //清除保存的随机播放列表
    fun eraseShuffleOrder() {
        prefs.use(relax = true) {
            edit(commit = true) {
                putString("shuffle_persist", null)
            }
        }
    }

    /*
    该方法先检查是否允许保存状态，如果不允许则跳过。
    获取当前播放列表数据、重复模式、随机播放模式、播放参数等信息。
    使用协程在后台线程中将这些数据序列化并保存到 SharedPreferences。
     */
    fun save() {
        if (!allowSavingState) {
            Log.i(TAG, "skipped save")
            return
        }

        val data = dumpPlaylist()
        val repeatMode = controller.repeatMode
        val shuffleModeEnabled = controller.shuffleModeEnabled
        val playbackParameters = controller.playbackParameters
        val persistent = controller.shufflePersistent
        val ended = controller.playbackState == Player.STATE_ENDED
        CoroutineScope(Dispatchers.Default).launch {

            val lastPlayed = PrefsListUtils.dump(
                data.mediaItems.map {
                    val b = SafeDelimitedStringConcat(":")
                    // add new entries at the bottom and remember they are null for upgrade path
                    b.writeStringUnsafe(it.mediaId)
                    b.writeUri(it.localConfiguration?.uri)
                    b.writeStringSafe(it.localConfiguration?.mimeType)
                    b.writeStringSafe(it.mediaMetadata.title)
                    b.writeStringSafe(it.mediaMetadata.artist)
                    b.writeStringSafe(it.mediaMetadata.albumTitle)
                    b.writeStringSafe(it.mediaMetadata.albumArtist)
                    b.writeUri(it.mediaMetadata.artworkUri)
                    b.writeInt(it.mediaMetadata.trackNumber)
                    b.writeInt(it.mediaMetadata.discNumber)
                    b.writeInt(it.mediaMetadata.recordingYear)
                    b.writeInt(it.mediaMetadata.releaseYear)
                    b.writeBool(it.mediaMetadata.isBrowsable)
                    b.writeBool(it.mediaMetadata.isPlayable)
                    b.writeLong(it.mediaMetadata.extras?.getLong("AddDate"))
                    b.writeStringSafe(it.mediaMetadata.writer)
                    b.writeStringSafe(it.mediaMetadata.compilation)
                    b.writeStringSafe(it.mediaMetadata.composer)
                    b.writeStringSafe(it.mediaMetadata.genre)
                    b.writeInt(it.mediaMetadata.recordingDay)
                    b.writeInt(it.mediaMetadata.recordingMonth)
                    b.writeLong(it.mediaMetadata.extras?.getLong("ArtistId"))
                    b.writeLong(it.mediaMetadata.extras?.getLong("AlbumId"))
                    b.writeLong(it.mediaMetadata.extras?.getLong("GenreId"))
                    b.writeStringSafe(it.mediaMetadata.extras?.getString("Author"))
                    b.writeInt(it.mediaMetadata.extras?.getInt("CdTrackNumber"))
                    b.writeLong(it.mediaMetadata.extras?.getLong("Duration"))
                    b.writeStringUnsafe(it.mediaMetadata.extras?.getString("Path"))
                    b.writeLong(it.mediaMetadata.extras?.getLong("ModifiedDate"))
                    b.toString()
                })
            prefs.edit {
                putStringSet("last_played_lst", lastPlayed.first)
                putString("last_played_grp", lastPlayed.second)
                putInt("last_played_idx", data.startIndex)
                putLong("last_played_pos", data.startPositionMs)
                putInt("repeat_mode", repeatMode)
                putBoolean("shuffle", shuffleModeEnabled)
                putString("shuffle_persist", persistent?.toString())
                putBoolean("ended", ended)
                putFloat("speed", playbackParameters.speed)
                putFloat("pitch", playbackParameters.pitch)
                apply()
            }
        }
    }
    /*
    使用协程在后台线程中恢复播放状态。
    反序列化保存的随机播放顺序。
    如果 lastPlayedGrp 或 lastPlayedLst 为空，则调用回调函数并传入 null。
    反序列化保存的播放列表和媒体元数据，更新播放器状态。
     */
    fun restore(callback: (MediaItemsWithStartPosition?, CircularShuffleOrder.Persistent) -> Unit) {

        CoroutineScope(Dispatchers.Default).launch {
            val seed = try {
                CircularShuffleOrder.Persistent.deserialize(prefs.getString("shuffle_persist", null))
            } catch (e: Exception) {
                eraseShuffleOrder()
                throw e
            }
            try {
                val lastPlayedLst = prefs.getStringSet("last_played_lst", null)
                val lastPlayedGrp = prefs.getString("last_played_grp", null)
                val lastPlayedIdx = prefs.getInt("last_played_idx", 0)
                val lastPlayedPos = prefs.getLong("last_played_pos", 0)
                if (lastPlayedGrp == null || lastPlayedLst == null) {
                    runCallback(callback, seed) { null }
                    return@launch
                }
                val repeatMode = prefs.getInt("repeat_mode", Player.REPEAT_MODE_OFF)
                val shuffleModeEnabled = prefs.getBoolean("shuffle", false)
                val ended = prefs.getBoolean("ended", false)
                val playbackParameters = PlaybackParameters(
                    prefs.getFloat("speed", 1f),
                    prefs.getFloat("pitch", 1f)
                )
                val data = MediaItemsWithStartPosition(
                    PrefsListUtils.parse(lastPlayedLst, lastPlayedGrp)
                    .map {
                        val b = SafeDelimitedStringDecat(":", it)
                        val mediaId = b.readStringUnsafe()
                        val uri = b.readUri()
                        val mimeType = b.readStringSafe()
                        val title = b.readStringSafe()
                        val artist = b.readStringSafe()
                        val album = b.readStringSafe()
                        val albumArtist = b.readStringSafe()
                        val imgUri = b.readUri()
                        val trackNumber = b.readInt()
                        val discNumber = b.readInt()
                        val recordingYear = b.readInt()
                        val releaseYear = b.readInt()
                        val isBrowsable = b.readBool()
                        val isPlayable = b.readBool()
                        val addDate = b.readLong()
                        val writer = b.readStringSafe()
                        val compilation = b.readStringSafe()
                        val composer = b.readStringSafe()
                        val genre = b.readStringSafe()
                        val recordingDay = b.readInt()
                        val recordingMonth = b.readInt()
                        val artistId = b.readLong()
                        val albumId = b.readLong()
                        val genreId = b.readLong()
                        val author = b.readStringSafe()
                        val cdTrackNumber = b.readInt()
                        val duration = b.readLong()
                        val path = b.readStringUnsafe()
                        val modifiedDate = b.readLong()
                        MediaItem.Builder()
                            .setUri(uri)
                            .setMediaId(mediaId!!)
                            .setMimeType(mimeType)
                            .setMediaMetadata(
                                MediaMetadata
                                    .Builder()
                                    .setTitle(title)
                                    .setArtist(artist)
                                    .setWriter(writer)
                                    .setComposer(composer)
                                    .setGenre(genre)
                                    .setCompilation(compilation)
                                    .setRecordingDay(recordingDay)
                                    .setRecordingMonth(recordingMonth)
                                    .setAlbumTitle(album)
                                    .setAlbumArtist(albumArtist)
                                    .setArtworkUri(imgUri)
                                    .setTrackNumber(trackNumber)
                                    .setDiscNumber(discNumber)
                                    .setRecordingYear(recordingYear)
                                    .setReleaseYear(releaseYear)
                                    .setIsBrowsable(isBrowsable)
                                    .setIsPlayable(isPlayable)
                                    .setExtras(Bundle().apply {
                                        if (addDate != null) {
                                            putLong("AddDate", addDate)
                                        }
                                        if (artistId != null) {
                                            putLong("ArtistId", artistId)
                                        }
                                        if (albumId != null) {
                                            putLong("AlbumId", albumId)
                                        }
                                        if (genreId != null) {
                                            putLong("GenreId", genreId)
                                        }
                                        if (cdTrackNumber != null) {
                                            putInt("CdTrackNumber", cdTrackNumber)
                                        }
                                        putString("Author", author)
                                        if (duration != null) {
                                            putLong("Duration", duration)
                                        }
                                        putString("Path", path)
                                        if (modifiedDate != null) {
                                            putLong("ModifiedDate", modifiedDate)
                                        }
                                    })
                                    .build()
                            )
                            .build()
                    },
                    lastPlayedIdx,
                    lastPlayedPos
                )
                runCallback(callback, seed) {

                    controller.isEnded = ended
                    controller.repeatMode = repeatMode
                    controller.shuffleModeEnabled = shuffleModeEnabled
                    controller.playbackParameters = playbackParameters
                    data
                }
                return@launch
            } catch (e: Exception) {
                Log.e(TAG, Log.getStackTraceString(e))
                runCallback(callback, seed) { null }
                return@launch
            }
        }
    }
}

@OptIn(UnstableApi::class)
private inline fun runCallback(crossinline callback: (MediaItemsWithStartPosition?,
                                                      CircularShuffleOrder.Persistent) -> Unit,
                               seed: CircularShuffleOrder.Persistent,
                               noinline parameter: () -> MediaItemsWithStartPosition?) {
    CoroutineScope(Dispatchers.Main).launch { callback(parameter(), seed) }
}

private class SafeDelimitedStringConcat(private val delimiter: String) {
    private val b = StringBuilder()
    private var hadFirst = false

    private fun append(s: String?) {
        if (s?.contains(delimiter, false) == true) {
            throw IllegalArgumentException("argument must not contain delimiter")
        }
        if (hadFirst) {
            b.append(delimiter)
        } else {
            hadFirst = true
        }
        s?.let { b.append(it) }
    }

    override fun toString(): String {
        return b.toString()
    }

    fun writeStringUnsafe(s: CharSequence?) = append(s?.toString())
    fun writeBase64(b: ByteArray?) = append(b?.let { Base64.encodeToString(it, Base64.DEFAULT) })
    fun writeStringSafe(s: CharSequence?) =
        writeBase64(s?.toString()?.toByteArray(StandardCharsets.UTF_8))

    fun writeInt(i: Int?) = append(i?.toString())
    fun writeLong(i: Long?) = append(i?.toString())
    fun writeBool(b: Boolean?) = append(b?.toString())
    fun writeUri(u: Uri?) = writeStringSafe(u?.toString())
}

private class SafeDelimitedStringDecat(delimiter: String, str: String) {
    private val items = str.split(delimiter)
    private var pos = 0

    private fun read(): String? {
        if (pos == items.size) return null
        return items[pos++].ifEmpty { null }
    }

    fun readStringUnsafe(): String? = read()
    fun readBase64(): ByteArray? = read()?.let { Base64.decode(it, Base64.DEFAULT) }
    fun readStringSafe(): String? = readBase64()?.toString(StandardCharsets.UTF_8)
    fun readInt(): Int? = read()?.toInt()
    fun readLong(): Long? = read()?.toLong()
    fun readBool(): Boolean? = read()?.toBooleanStrict()
    fun readUri(): Uri? = Uri.parse(readStringSafe())
}

private object PrefsListUtils {
    fun parse(stringSet: Set<String>, groupStr: String): List<String> {
        val groups = groupStr.split(",")
        return groups.map { hashCode ->
            stringSet.first { it.hashCode().toString() == hashCode }
        }
    }

    fun dump(list: List<String>): Pair<Set<String>, String> {
        return Pair(list.toSet(), list.joinToString(",") { it.hashCode().toString() })
    }
}