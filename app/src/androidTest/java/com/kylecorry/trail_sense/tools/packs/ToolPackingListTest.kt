package com.kylecorry.trail_sense.tools.packs

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.test_utils.TestUtils
import com.kylecorry.trail_sense.test_utils.TestUtils.clickListItemMenu
import com.kylecorry.trail_sense.test_utils.TestUtils.not
import com.kylecorry.trail_sense.test_utils.TestUtils.waitFor
import com.kylecorry.trail_sense.test_utils.views.Side
import com.kylecorry.trail_sense.test_utils.views.click
import com.kylecorry.trail_sense.test_utils.views.hasText
import com.kylecorry.trail_sense.test_utils.views.input
import com.kylecorry.trail_sense.test_utils.views.isChecked
import com.kylecorry.trail_sense.test_utils.views.toolbarButton
import com.kylecorry.trail_sense.test_utils.views.view
import com.kylecorry.trail_sense.test_utils.views.viewWithText
import com.kylecorry.trail_sense.tools.tools.infrastructure.Tools
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ToolPackingListTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    val grantPermissionRule = TestUtils.mainPermissionsGranted()

    @get:Rule
    val instantExec = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        hiltRule.inject()
        TestUtils.setWaitForIdleTimeout(50)
        TestUtils.setupApplication()
        TestUtils.startWithTool(Tools.PACKING_LISTS)

        waitFor {
            view(R.id.pack_list_title).hasText(R.string.packing_lists)
        }
    }

    @Test
    fun verifyBasicFunctionality() {
        shouldHaveNoPacks()
        canCreateAPack()
        canCreateAnItem()
        canCheckAnItem()
        canEditAnItemOnClick()
        canAddASecondItem()

        // Item menu items
        canIncrementItem()
        canDecrementItem()
        canEditAnItemFromMenu()
        canDeleteAnItem()

        // Pack menu items
        canRenamePack()
        canClearPackAmounts()
        canExportPack()
        // TODO: Sort items

        // Pack list
        canShowPackList()
        canCopyPack()
        canDeletePack()
        canImportPack()
    }

    private fun shouldHaveNoPacks() {
        view(R.id.empty_text).hasText(R.string.no_packing_lists)
    }

    private fun canCreateAPack() {
        createPackingList("Test Pack 1")
    }

    private fun canCreateAnItem() {
        createItem("Test Item 1", 1, 2, TestUtils.getString(R.string.category_food), 1.1f)
        waitFor {
            hasItem(
                "Test Item 1",
                1,
                2,
                TestUtils.getString(R.string.category_food),
                "1 lb",
                false
            )
        }
        view(R.id.total_percent_packed).hasText("50% packed")
        view(R.id.total_packed_weight).hasText("1.1 lb")
    }

    private fun canCheckAnItem() {
        view(com.kylecorry.andromeda.views.R.id.checkbox).click()
        waitFor {
            hasItem(
                "Test Item 1",
                2,
                2,
                TestUtils.getString(R.string.category_food),
                "2 lb",
                true
            )
        }
        view(R.id.total_percent_packed).hasText("100% packed")
        view(R.id.total_packed_weight).hasText("2.2 lb")
    }

    private fun canEditAnItemOnClick() {
        view(com.kylecorry.andromeda.views.R.id.title).click()
        waitFor {
            view(R.id.create_item_title).hasText(R.string.edit_item_title)
        }
        hasItemForm("Test Item 1", 2, 2, TestUtils.getString(R.string.category_food), 1.1f)
        view(R.id.create_btn).click()
        waitFor {
            hasItem(
                "Test Item 1",
                2,
                2,
                TestUtils.getString(R.string.category_food),
                "2 lb",
                true
            )
        }
    }

    private fun canEditAnItemFromMenu() {
        clickListItemMenu(TestUtils.getString(R.string.edit), 1)
        waitFor {
            view(R.id.create_item_title).hasText(R.string.edit_item_title)
        }
        hasItemForm("Test Item 1", 2, 2, TestUtils.getString(R.string.category_food), 1.1f)
        view(R.id.create_btn).click()
        waitFor {
            hasItem(
                "Test Item 1",
                2,
                2,
                TestUtils.getString(R.string.category_food),
                "2 lb",
                true,
                1
            )
        }
    }

    private fun canAddASecondItem() {
        createItem("Test Item 2", 1, 2, TestUtils.getString(R.string.category_clothing), 0.5f)
        waitFor {
            hasItem(
                "Test Item 2",
                1,
                2,
                TestUtils.getString(R.string.category_clothing),
                "1 lb",
                false,
                0
            )
            hasItem(
                "Test Item 1",
                2,
                2,
                TestUtils.getString(R.string.category_food),
                "2 lb",
                true,
                1
            )
        }
        view(R.id.total_percent_packed).hasText("75% packed")
        view(R.id.total_packed_weight).hasText("2.7 lb")
    }

    private fun canIncrementItem() {
        clickListItemMenu(TestUtils.getString(R.string.add), 0)
        waitFor {
            viewWithText(R.string.dialog_item_amount).input("1")
        }
        viewWithText(android.R.string.ok).click()
        waitFor {
            hasItem(
                "Test Item 2",
                2,
                2,
                TestUtils.getString(R.string.category_clothing),
                "1 lb",
                true,
                0
            )
        }
    }

    private fun canDecrementItem() {
        clickListItemMenu(TestUtils.getString(R.string.subtract), 0)
        waitFor {
            viewWithText(R.string.dialog_item_amount).input("2")
        }
        viewWithText(android.R.string.ok).click()
        waitFor {
            hasItem(
                "Test Item 2",
                0,
                2,
                TestUtils.getString(R.string.category_clothing),
                "0 lb",
                false,
                0
            )
        }
    }

    private fun canDeleteAnItem() {
        clickListItemMenu(TestUtils.getString(R.string.delete), 0)
        waitFor {
            not {
                hasItem(
                    "Test Item 2",
                    0,
                    2,
                    TestUtils.getString(R.string.category_clothing),
                    "0 lb",
                    false,
                    0
                )
            }
        }
    }

    private fun canRenamePack() {
        clickToolbarMenuItem(TestUtils.getString(R.string.rename))
        waitFor {
            viewWithText("Test Pack 1").input("Test Pack 2")
            viewWithText(android.R.string.ok).click()
        }
        waitFor {
            view(R.id.inventory_list_title).hasText("Test Pack 2")
        }
    }

    private fun canClearPackAmounts() {
        clickToolbarMenuItem(TestUtils.getString(R.string.clear_amounts))
        waitFor {
            viewWithText(android.R.string.ok).click()
        }
        waitFor {
            hasItem(
                "Test Item 1",
                0,
                2,
                TestUtils.getString(R.string.category_food),
                "0 lb",
                false
            )
        }
    }

    private fun canExportPack() {
        clickToolbarMenuItem(TestUtils.getString(R.string.export))
        waitFor {
            // TODO: Figure out how to click save
//            viewWithText("SAVE").click()
            viewWithText("test-pack-2.csv")
        }
        waitFor {
            waitFor {
                TestUtils.back()
            }
            waitFor {
                view(R.id.inventory_list_title).hasText("Test Pack 2")
            }
        }
    }

    private fun canShowPackList() {
        waitFor {
            TestUtils.back()
        }
        waitFor {
            view(R.id.pack_list_title).hasText(R.string.packing_lists)
            view(com.kylecorry.andromeda.views.R.id.title).hasText("Test Pack 2")
        }
    }

    private fun canCopyPack() {
        clickListItemMenu(TestUtils.getString(android.R.string.copy))
        waitFor {
            viewWithText(R.string.name).input("Copied Pack")
            viewWithText(android.R.string.ok).click()
        }
        waitFor {
            view(R.id.inventory_list_title).hasText("Copied Pack")
        }
        hasItem(
            "Test Item 1",
            0,
            2,
            TestUtils.getString(R.string.category_food),
            "0 lb",
            false
        )
    }

    private fun canDeletePack(){
        clickToolbarMenuItem(TestUtils.getString(R.string.delete))
        waitFor {
            viewWithText(android.R.string.ok).click()
        }
        waitFor {
            view(R.id.pack_list_title).hasText(R.string.packing_lists)
            not {
                view(com.kylecorry.andromeda.views.R.id.title).hasText("Copied Pack")
            }
        }
    }

    private fun canImportPack(){
        view(R.id.add_btn).click()
        viewWithText(R.string.import_packing_list).click()
        // TODO: Actually import a pack and verify it
    }

    // HELPERS
    private fun hasItemForm(
        name: String,
        amount: Int,
        desiredAmount: Int,
        category: String,
        weight: Float
    ) {
        view(R.id.name_edit).hasText { it.startsWith(name) }
        view(R.id.count_edit).hasText { it.startsWith(amount.toString()) }
        view(R.id.desired_amount_edit).hasText { it.startsWith(desiredAmount.toString()) }
        view(R.id.category_spinner).hasText { it.startsWith(category) }
        view(R.id.item_weight_input, R.id.amount).hasText { it.startsWith(weight.toString()) }
    }

    private fun hasItem(
        name: String,
        amount: Int,
        desiredAmount: Int,
        category: String,
        weight: String,
        isChecked: Boolean,
        index: Int = 0
    ) {
        view(com.kylecorry.andromeda.views.R.id.title, index = index).hasText(name)
        view(com.kylecorry.andromeda.views.R.id.tag, index = index).hasText(category)
        view(
            com.kylecorry.andromeda.views.R.id.data_1,
            index = index
        ).hasText("$amount / $desiredAmount")
        view(com.kylecorry.andromeda.views.R.id.data_2, index = index).hasText(weight)
        view(com.kylecorry.andromeda.views.R.id.checkbox, index = index).isChecked(isChecked)
    }

    private fun clickToolbarMenuItem(label: String) {
        toolbarButton(R.id.inventory_list_title, Side.Right).click()
        waitFor {
            viewWithText(label).click()
        }
    }

    private fun createItem(
        name: String,
        amount: Int,
        desiredAmount: Int,
        category: String,
        weight: Float
    ) {
        // Create a new item
        view(R.id.add_btn).click()
        waitFor {
            view(R.id.create_item_title).hasText(R.string.create_item_title)
        }

        view(R.id.name_edit).input(name)
        view(R.id.count_edit).input(amount.toString())
        view(R.id.desired_amount_edit).input(desiredAmount.toString())
        view(R.id.category_spinner).click()
        waitFor {
            viewWithText(category).click()
            viewWithText(android.R.string.ok).click()
        }
        waitFor {
            view(R.id.item_weight_input, R.id.amount).input(weight.toString())
        }
        view(R.id.create_btn).click()
    }

    private fun createPackingList(name: String) {
        // Create a new packing list
        view(R.id.add_btn).click()
        viewWithText(R.string.new_packing_list).click()
        waitFor {
            viewWithText(R.string.name).input(name)
            viewWithText(android.R.string.ok).click()
        }

        // Verify the new pack is created
        waitFor {
            view(R.id.inventory_list_title).hasText(name)
            view(R.id.inventory_empty_text).hasText(R.string.inventory_empty_text)
        }
    }
}