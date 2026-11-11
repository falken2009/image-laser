package com.king365.fire

import android.content.Context

/// Version perso (installée depuis GitHub) : pas de boutique, tout est débloqué
object BillingProvider {
    fun create(): BillingBackend = object : BillingBackend {
        override fun start(context: Context, onPremium: (Boolean) -> Unit, onPrices: (Map<String, String>) -> Unit) {}
        override fun purchase(sku: String) {}
        override fun restore() {}
    }
}
