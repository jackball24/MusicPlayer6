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
 * @author 时空L0k1
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
