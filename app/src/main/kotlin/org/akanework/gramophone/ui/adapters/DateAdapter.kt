package org.akanework.gramophone.ui.adapters

import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.fragments.GeneralSubFragment

/**
 * [DateAdapter] is an adapter for displaying dates.
 */
class DateAdapter(
    fragment: Fragment,
    dateList: MutableLiveData<List<MediaStoreUtils.Date>>,
) : BaseAdapter<MediaStoreUtils.Date>
    (
    fragment,
    liveData = dateList,
    sortHelper = StoreItemHelper(),
    naturalOrderHelper = null,
    initialSortType = Sorter.Type.ByTitleAscending,
    pluralStr = R.plurals.items,
    ownsView = true,
    defaultLayoutType = LayoutType.LIST
) {

    override val defaultCover = R.drawable.ic_default_cover_date

    override fun virtualTitleOf(item: MediaStoreUtils.Date): String {
        return context.getString(R.string.unknown_year)
    }

    override fun onClick(item: MediaStoreUtils.Date) {
        mainActivity.startFragment(GeneralSubFragment()) {
            putInt("Position", toRawPos(item))
            putInt("Item", R.id.dates)
        }
    }

    override fun onMenu(item: MediaStoreUtils.Date, popupMenu: PopupMenu) {
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

                /*
				R.id.share -> {
					val builder = ShareCompat.IntentBuilder(mainActivity)
					val mimeTypes = mutableSetOf<String>()
					builder.addStream(viewModel.fileUriList.value?.get(songList[holder.bindingAdapterPosition].mediaId.toLong())!!)
					mimeTypes.add(viewModel.mimeTypeList.value?.get(songList[holder.bindingAdapterPosition].mediaId.toLong())!!)
					builder.setType(mimeTypes.singleOrNull() ?: "audio/*").startChooser()
				 } */
				 */
                else -> false
            }
        }
    }

}
