package com.kylecorry.trail_sense.tools.packs.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.core.math.DecimalFormatter
import com.kylecorry.andromeda.core.toDoubleCompat
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.sol.units.WeightUnits
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentCreateItemBinding
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.extensions.promptIfUnsavedChanges
import com.kylecorry.trail_sense.tools.packs.domain.ItemCategory
import com.kylecorry.trail_sense.tools.packs.domain.PackItem
import com.kylecorry.trail_sense.tools.packs.infrastructure.PackRepo
import com.kylecorry.trail_sense.tools.packs.ui.mappers.ItemCategoryStringMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CreateItemFragment : BoundFragment<FragmentCreateItemBinding>() {

    private val itemRepo by lazy { PackRepo.getInstance(requireContext()) }
    private val formatService by lazy { FormatService.getInstance(requireContext()) }

    private var editingItem: PackItem? = null

    private var packId: Long = 0
    private var itemId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        itemId = arguments?.getLong("edit_item_id") ?: 0L
        packId = arguments?.getLong("pack_id") ?: 0L

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.itemWeightInput.units = formatService.sortWeightUnits(WeightUnits.entries)

        binding.createBtn.setOnClickListener {
            val name = binding.nameEdit.text?.toString()
            val amount = binding.countEdit.text?.toString()?.toDoubleCompat() ?: 0.0
            val desiredAmount = binding.desiredAmountEdit.text?.toString()?.toDoubleCompat() ?: 0.0
            val category = ItemCategory.values()[binding.categorySpinner.selectedItemPosition]
            val weight = binding.itemWeightInput.value

            if (name != null) {
                inBackground {
                    withContext(Dispatchers.IO) {
                        itemRepo.addItem(
                            PackItem(
                                editingItem?.id ?: 0,
                                packId,
                                name,
                                category,
                                amount,
                                desiredAmount,
                                weight
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        findNavController().popBackStack()
                    }
                }
            }
        }

        val nameMapper = ItemCategoryStringMapper(requireContext())

        binding.categorySpinner.setItems(ItemCategory.values().map { nameMapper.getString(it) })
        binding.categorySpinner.setHint(getString(R.string.category))

        if (itemId == 0L) {
            binding.categorySpinner.setSelection(0)
        }

        promptIfUnsavedChanges(this::hasChanges)

        if (itemId != 0L) {
            loadEditingItem(itemId)
        }
    }

    private fun loadEditingItem(id: Long) {
        inBackground {
            withContext(Dispatchers.IO) {
                editingItem = itemRepo.getItem(id)
            }

            withContext(Dispatchers.Main) {
                editingItem?.let {
                    if (!isBound){
                        return@let
                    }
                    binding.createItemTitle.title.text = getString(R.string.edit_item_title)
                    binding.nameEdit.setText(it.name)
                    binding.countEdit.setText(DecimalFormatter.format(it.amount, 4, false))
                    binding.desiredAmountEdit.setText(
                        DecimalFormatter.format(
                            it.desiredAmount,
                            4,
                            false
                        )
                    )
                    binding.categorySpinner.setSelection(it.category.ordinal)
                    binding.itemWeightInput.value = it.weight
                    if (it.weight == null) {
                        binding.itemWeightInput.unit = binding.itemWeightInput.units.firstOrNull()
                    }
                }
            }

        }
    }

    private fun hasChanges(): Boolean {
        val name = binding.nameEdit.text?.toString()
        val amount = binding.countEdit.text?.toString()?.toDoubleCompat() ?: 0.0
        val desiredAmount = binding.desiredAmountEdit.text?.toString()?.toDoubleCompat() ?: 0.0
        val category = ItemCategory.values()[binding.categorySpinner.selectedItemPosition]
        val weight = binding.itemWeightInput.value

        return !nothingEntered() && (name != editingItem?.name || amount != editingItem?.amount || desiredAmount != editingItem?.desiredAmount || category != editingItem?.category || weight != editingItem?.weight)
    }

    private fun nothingEntered(): Boolean {
        if (editingItem != null) {
            return false
        }

        val name = binding.nameEdit.text?.toString()
        val amount = binding.countEdit.text?.toString()
        val desiredAmount = binding.desiredAmountEdit.text?.toString()
        val category = ItemCategory.values()[binding.categorySpinner.selectedItemPosition]
        val weight = binding.itemWeightInput.value

        return name.isNullOrBlank() && amount.isNullOrBlank() && desiredAmount.isNullOrBlank() && category == ItemCategory.Other && weight == null
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentCreateItemBinding {
        return FragmentCreateItemBinding.inflate(layoutInflater, container, false)
    }

}