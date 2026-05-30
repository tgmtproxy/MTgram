package desu.inugram.helpers

import desu.inugram.InuConfig
import desu.inugram.helpers.security.ParanoiaHelper
import desu.inugram.helpers.security.PasscodeHelper
import org.telegram.messenger.R

object NotificationsHelper {
    @JvmStatic
    fun smallIconRes(): Int = when (InuConfig.NOTIFICATION_ICON.value) {
        InuConfig.NotificationIconItem.TELEGRAM -> R.drawable.notification
        else -> R.drawable.icon_notification_inu
    }

    @JvmStatic
    fun shouldSuppressNotifications(account: Int): Boolean =
        PasscodeHelper.isAccountHidden(account) || ParanoiaHelper.shouldSuppressNotifications()
}