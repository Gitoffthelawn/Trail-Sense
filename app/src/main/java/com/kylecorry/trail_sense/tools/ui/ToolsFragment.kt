package com.kylecorry.trail_sense.tools.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import com.kylecorry.andromeda.core.capitalizeWords
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.tryOrLog
import com.kylecorry.andromeda.core.ui.Colors
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.list.ListView
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentToolsBinding
import com.kylecorry.trail_sense.databinding.ListItemToolBinding
import com.kylecorry.trail_sense.shared.requireMyNavigation


class ToolsFragment : BoundFragment<FragmentToolsBinding>() {

    private lateinit var toolsList: ListView<ToolListItem>
    private val tools by lazy { Tools.getTools(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val primaryColor = Resources.getAndroidColorAttr(requireContext(), androidx.appcompat.R.attr.colorPrimary)
        val textColor = Resources.androidTextColorPrimary(requireContext())
        val attrs = intArrayOf(android.R.attr.selectableItemBackground)
        val typedArray = requireContext().obtainStyledAttributes(attrs)
        val selectableBackground = typedArray.getResourceId(0, 0)
        typedArray.recycle()
        toolsList = ListView(binding.toolRecycler, R.layout.list_item_tool) { itemView, tool ->
            val toolBinding = ListItemToolBinding.bind(itemView)

            if (tool.route != null && tool.icon != null) {
                // Tool
                toolBinding.root.setBackgroundResource(selectableBackground)
                toolBinding.title.text = tool.name.capitalizeWords()
                toolBinding.title.setTextColor(textColor)
                toolBinding.description.text = tool.description
                toolBinding.icon.isVisible = true
                toolBinding.description.isVisible = tool.description != null
                toolBinding.icon.setImageResource(tool.icon)
                Colors.setImageColor(toolBinding.icon, Resources.androidTextColorSecondary(requireContext()))
                toolBinding.root.setOnClickListener {
                    tryOrLog {
                        requireMyNavigation().navigate(tool.route)
                    }
                }
            } else {
                // Tool group
                toolBinding.root.setBackgroundResource(0)
                toolBinding.title.text = tool.name
                toolBinding.title.setTextColor(primaryColor)
                toolBinding.description.text = ""
                toolBinding.icon.isVisible = false
                toolBinding.description.isVisible = false
                toolBinding.root.setOnClickListener(null)
            }

        }

        binding.searchbox.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                updateToolList()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                updateToolList()
                return true
            }

        })

        updateToolList()
    }

    private fun updateToolList() {
        val toolListItems = mutableListOf<ToolListItem>()
        val search = binding.searchbox.query

        if (search.isNullOrBlank()) {
            for (group in tools) {
                toolListItems.add(ToolListItem(group.name, null, null, null))
                for (tool in group.tools) {
                    toolListItems.add(
                        ToolListItem(
                            tool.name,
                            tool.description,
                            tool.icon,
                            tool.route
                        )
                    )
                }
            }
        } else {
            for (group in tools) {
                for (tool in group.tools) {
                    if (tool.name.contains(search, true) || tool.description?.contains(
                            search,
                            true
                        ) == true
                    ) {
                        toolListItems.add(
                            ToolListItem(
                                tool.name,
                                tool.description,
                                tool.icon,
                                tool.route
                            )
                        )
                    }
                }
            }
        }

        toolsList.setData(toolListItems)
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentToolsBinding {
        return FragmentToolsBinding.inflate(layoutInflater, container, false)
    }

    internal data class ToolListItem(
        val name: String,
        val description: String?,
        @DrawableRes val icon: Int?,
        val route: String?
    )

}