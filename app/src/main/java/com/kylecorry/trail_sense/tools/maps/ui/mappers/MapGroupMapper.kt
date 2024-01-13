package com.kylecorry.trail_sense.tools.maps.ui.mappers

import android.content.Context
import com.kylecorry.andromeda.views.list.ListItem
import com.kylecorry.andromeda.views.list.ListItemMapper
import com.kylecorry.andromeda.views.list.ListMenuItem
import com.kylecorry.andromeda.views.list.ResourceListIcon
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.colors.AppColor
import com.kylecorry.trail_sense.tools.maps.domain.IMap
import com.kylecorry.trail_sense.tools.maps.domain.MapGroup

class MapGroupMapper(
    private val context: Context,
    private val actionHandler: (MapGroup, MapGroupAction) -> Unit
) :
    ListItemMapper<IMap> {
    override fun map(value: IMap): ListItem {
        val group = value as MapGroup
        return ListItem(
            -value.id,
            value.name,
            context.resources.getQuantityString(
                R.plurals.map_group_summary,
                group.count ?: 0,
                group.count ?: 0
            ),
            icon = ResourceListIcon(R.drawable.ic_map_group, AppColor.Gray.color, size = 48f, foregroundSize = 24f),
            menu = listOf(
                ListMenuItem(context.getString(R.string.rename)) {
                    actionHandler(group, MapGroupAction.Rename)
                },
                ListMenuItem(context.getString(R.string.move_to)) {
                    actionHandler(value, MapGroupAction.Move)
                },
                ListMenuItem(context.getString(R.string.delete)) {
                    actionHandler(group, MapGroupAction.Delete)
                },
            )
        ) {
            actionHandler(group, MapGroupAction.View)
        }
    }
}