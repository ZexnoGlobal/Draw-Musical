package com.wcs.drawing.game.draw.with.music.`fun`

object BillingConfig {
    const val PRODUCT_ID="remove_fullscreen_ads"
    const val PURCHASE_OPTION_ID="buy"
    const val SUBSCRIPTION_ID="draw_musical_premium"
    const val WEEKLY_BASE_PLAN="weekly"
    const val MONTHLY_BASE_PLAN="monthly"
    const val WEEKLY_TRIAL_OFFER_ID="weekly-trial"
    // Optional hosted policy links. Blank uses the app's local information panel.
    const val TERMS_URL="https://zexnoglobal.github.io/Draw-Music-Legal/DrawMusical_Terms_of_Service.html"
    const val PRIVACY_URL="https://zexnoglobal.github.io/Draw-Music-Legal/DrawMusical_Privacy_Policy.html"
    // Play Console > Monetize with Play > Monetization setup > Licensing.
    // This is the app's PUBLIC Base64 RSA license key, never a service-account key.
    // Leave blank until configured: purchases fail closed, never grant a fake entitlement.
    const val PLAY_LICENSE_PUBLIC_KEY=""
}
