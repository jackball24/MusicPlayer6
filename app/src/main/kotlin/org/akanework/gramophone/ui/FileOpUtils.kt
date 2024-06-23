package org.akanework.gramophone.ui

import org.akanework.gramophone.ui.adapters.AlbumAdapter
import org.akanework.gramophone.ui.adapters.ArtistAdapter
import org.akanework.gramophone.ui.adapters.BaseAdapter
import org.akanework.gramophone.ui.adapters.PlaylistAdapter
import org.akanework.gramophone.ui.adapters.SongAdapter

const val ALBUM_ADAPTER_TYPE = 0
const val ARTIST_ADAPTER_TYPE = 1
const val PLAYLIST_ADAPTER_TYPE = 2
const val SONG_ADAPTER_TYPE = 3

fun getAdapterType(adapter: BaseAdapter<*>) =
when (adapter) {
    is AlbumAdapter -> {
        ALBUM_ADAPTER_TYPE
    }

    is ArtistAdapter -> {
        ARTIST_ADAPTER_TYPE
    }


    is PlaylistAdapter -> {
        PLAYLIST_ADAPTER_TYPE
    }

    is SongAdapter -> {
        SONG_ADAPTER_TYPE
    }

    else -> {
        throw IllegalArgumentException()
    }
}