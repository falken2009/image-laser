package com.king365.fire

import android.content.Context
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.FulfillmentResult
import com.amazon.device.iap.model.ProductDataResponse
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.PurchaseUpdatesResponse
import com.amazon.device.iap.model.UserDataResponse

/// Version boutique : abonnement Premium via les achats intégrés Amazon (Appstore SDK)
object BillingProvider {
    fun create(): BillingBackend = AmazonBilling()
}

private class AmazonBilling : BillingBackend {
    private var onPremium: (Boolean) -> Unit = {}
    private var foundActive = false

    override fun start(context: Context, onPremium: (Boolean) -> Unit, onPrices: (Map<String, String>) -> Unit) {
        this.onPremium = onPremium
        PurchasingService.registerListener(context.applicationContext, object : PurchasingListener {
            override fun onUserDataResponse(response: UserDataResponse) {}

            override fun onProductDataResponse(response: ProductDataResponse) {
                if (response.requestStatus == ProductDataResponse.RequestStatus.SUCCESSFUL) {
                    onPrices(response.productData.mapValues { it.value.price })
                }
            }

            override fun onPurchaseResponse(response: PurchaseResponse) {
                when (response.requestStatus) {
                    PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                        response.receipt?.let { PurchasingService.notifyFulfillment(it.receiptId, FulfillmentResult.FULFILLED) }
                        onPremium(true)
                    }
                    PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> onPremium(true)
                    else -> {}
                }
            }

            override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
                if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) return
                if (response.receipts.any { !it.isCanceled }) foundActive = true
                if (response.hasMore()) {
                    PurchasingService.getPurchaseUpdates(false)
                } else {
                    onPremium(foundActive)
                    foundActive = false
                }
            }
        })
        PurchasingService.getUserData()
        PurchasingService.getPurchaseUpdates(true)
        PurchasingService.getProductData(setOf(Premium.SKU_MONTH, Premium.SKU_YEAR))
    }

    override fun purchase(sku: String) {
        PurchasingService.purchase(sku)
    }

    override fun restore() {
        foundActive = false
        PurchasingService.getPurchaseUpdates(true)
    }
}
