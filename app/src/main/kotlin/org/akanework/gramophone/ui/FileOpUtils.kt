package org.akanework.gramophone.ui

import org.akanework.gramophone.ui.adapters.AlbumAdapter
import org.akanework.gramophone.ui.adapters.ArtistAdapter
import org.akanework.gramophone.ui.adapters.BaseAdapter
import org.akanework.gramophone.ui.adapters.PlaylistAdapter
import org.akanework.gramophone.ui.adapters.SongAdapter

fun getAdapterType(adapter: BaseAdapter<*>) =
when (adapter) {
    is AlbumAdapter -> {
        0
    }

    is ArtistAdapter -> {
        1
    }


    is PlaylistAdapter -> {
        2
    }

    is SongAdapter -> {
        3
    }

    else -> {
        throw IllegalArgumentException()
    }
}