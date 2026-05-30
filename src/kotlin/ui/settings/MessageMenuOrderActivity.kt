package desu.inugram.ui.settings

import android.view.View
import desu.inugram.InuConfig
import desu.inugram.helpers.menu.MessageMenuConfig
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R

class MessageMenuOrderActivity : MenuOrderActivity<MessageMenuConfig.Item>() {
    override val config get() = InuConfig.MESSAGE_MENU_ITEMS
    override val infoStringRes = R.string.InuMessageMenuOrderInfo
    override val headerStringRes = R.string.InuMessageMenuItems
    override val resetStringRes = R.string.InuMessageMenuReset

    override fun getTitle(): CharSequence = LocaleController.getString(R.string.InuMessageMenuOrder)

    override fun subCellValue(item: MessageMenuConfig.Item): CharSequence? {
        val cfg = LONG_TAP_CONFIGS[item] ?: return null
        val labelRes = cfg.options.firstOrNull { it.first == cfg.getter() }?.second
            ?: cfg.options.first().second
        return LocaleController.getString(labelRes)
    }

    override fun showSubCellPicker(item: MessageMenuConfig.Item, row: MenuOrderRow) {
        val cfg = LONG_TAP_CONFIGS[item] ?: return
        val current = cfg.options.indexOfFirst { it.first == cfg.getter() }.coerceAtLeast(0)
        val anchor = row.getSubAnchor() ?: row
        RadioItemOptions.show(
            this, anchor,
            cfg.options.map { LocaleController.getString(it.second) },
            current,
        ) { which ->
            val (value, labelRes) = cfg.options.getOrNull(which) ?: return@show
            cfg.setter(value)
            row.setSubCell(
                LocaleController.getString(R.string.InuLongTapAction),
                LocaleController.getString(labelRes)
            )
        }
    }

    companion object {
        private class LongTapConfig(
            val options: List<Pair<Int, Int>>,
            val getter: () -> Int,
            val setter: (Int) -> Unit,
        )

        private val LONG_TAP_CONFIGS: Map<MessageMenuConfig.Item, LongTapConfig> = mapOf(
            MessageMenuConfig.Item.FORWARD to LongTapConfig(
                listOf(
                    InuConfig.ForwardLongTapItem.OFF to R.string.InuForwardLongTapOff,
                    InuConfig.ForwardLongTapItem.CHOOSE_MODE to R.string.InuLongTapChooseMode,
                    InuConfig.ForwardLongTapItem.WITHOUT_AUTHOR to R.string.InuForwardWithoutAuthor,
                    InuConfig.ForwardLongTapItem.WITHOUT_CAPTION to R.string.InuForwardWithoutCaption,
                ),
                { InuConfig.FORWARD_LONG_TAP_ACTION.value },
                { InuConfig.FORWARD_LONG_TAP_ACTION.value = it },
            ),
            MessageMenuConfig.Item.REPLY to LongTapConfig(
                listOf(
                    InuConfig.ReplyLongTapItem.OFF to R.string.InuForwardLongTapOff,
                    InuConfig.ReplyLongTapItem.CHOOSE_MODE to R.string.InuLongTapChooseMode,
                    InuConfig.ReplyLongTapItem.REPLY_IN to R.string.InuReplyIn,
                    InuConfig.ReplyLongTapItem.REPLY_IN_DMS to R.string.InuReplyInDms,
                ),
                { InuConfig.REPLY_LONG_TAP_ACTION.value },
                { InuConfig.REPLY_LONG_TAP_ACTION.value = it },
            ),
        )
    }
}
