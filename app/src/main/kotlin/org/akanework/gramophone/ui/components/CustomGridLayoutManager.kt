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

package org.akanework.gramophone.ui.components

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager

/**
 * CustomGridLayoutManager:
 *   A grid layout manager for making the grid view
 * intact.
 *
 * @author AkaneTan
 */
class CustomGridLayoutManager(
    context: Context,
    spanCount: Int,
) : GridLayoutManager(context, spanCount) {
    init {
        spanSizeLookup =
            object : SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (position == 0) {
                        spanCount
                    } else {
                        1
                    }
            }
    }
}
