/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import org.akanework.gramophone.logic.utils.MediaStoreUtils

/**
 * LibraryViewModel:
 *   A ViewModel that contains library information.
 * Used across the application.
 *
 * @author AkaneTan, nift4
 */
class LibraryViewModel : ViewModel() {
    val mediaItemList: MutableLiveData<List<MediaItem>> = MutableLiveData()
    val albumItemList: MutableLiveData<List<MediaStoreUtils.Album>> = MutableLiveData()
    val albumArtistItemList: MutableLiveData<List<MediaStoreUtils.Artist>> = MutableLiveData()
    val artistItemList: MutableLiveData<List<MediaStoreUtils.Artist>> = MutableLiveData()
    val genreItemList: MutableLiveData<List<MediaStoreUtils.Genre>> = MutableLiveData()
    val dateItemList: MutableLiveData<List<MediaStoreUtils.Date>> = MutableLiveData()
    val playlistList: MutableLiveData<List<MediaStoreUtils.Playlist>> = MutableLiveData()
    val folderStructure: MutableLiveData<MediaStoreUtils.FileNode> = MutableLiveData()
    val shallowFolderStructure: MutableLiveData<MediaStoreUtils.FileNode> = MutableLiveData()
    val allFolderSet: MutableLiveData<Set<String>> = MutableLiveData()
}
