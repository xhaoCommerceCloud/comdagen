/*
 *  Copyright (c) 2018, salesforce.com, inc.
 *  All rights reserved.
 *  SPDX-License-Identifier: BSD-3-Clause
 *  For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.comdagen.model

import com.salesforce.comdagen.config.PricebookConfiguration
import com.salesforce.comdagen.generator.CatalogGenerator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Abstract Pricebook
 *
 * @property config pricebook configuration
 * @property seed pseudo randomization seed
 * @property productIds the product ids to generate price entries for
 * @property index this is the N-th price book (out of [PricebookConfiguration.elementCount] in total)
 * @property catalogHashCode hash code of the catalog config the price book is based on (differentiates price books for
 *                        different catalog configurations)
 * @property pricetables each list entry defines price for one product
 * @property productIds the catalog products, mapped to their [Product.id]
 *
 * @property id unique identifier of the pricebook
 * @property currency price book defines prices for one currency
 * @property customAttributes list of custom attributes
 */
abstract class Pricebook(
    protected val config: PricebookConfiguration, val currency: String, protected val seed: Long,
    private val attributeDefinitions: Set<AttributeDefinition>,
    private val productIds: Sequence<String>,
    private val index: Int, private val catalogHashCode: Int,
    private val totalPriceEntryCount: Int = 0,
    private val uniquePrices: List<Double>? = null,
    private val startPriceIndex: Int = 0,
    private val hasTimeBasedPrice: Boolean = false
) {
    val pricetables: Sequence<PriceTable>
        get() {
            val rng = Random(seed)
            var priceIndex = startPriceIndex
            var hasEmittedTimeBasedPrice = false
            return productIds.map { productId ->
                val isTimeBased = hasTimeBasedPrice && !hasEmittedTimeBasedPrice
                val table = PriceTable(productId, rng.nextLong(), config, currency, salePriceBook, uniquePrices, priceIndex, isTimeBased)
                if (isTimeBased) {
                    hasEmittedTimeBasedPrice = true
                }
                if (uniquePrices != null) {
                    priceIndex = (priceIndex + 1) % uniquePrices.size
                }
                table
            }
        }

    open val parentId: String? = null

    open val salePriceBook: Boolean = false

    val id: String
        get() = "$index-${config.id}-$currency"

    val customAttributes: List<CustomAttribute>
        get() {
            val rng = Random(seed + "customAttributes".hashCode())
            return attributeDefinitions.map { CustomAttribute(it, rng.nextLong()) }
        }
}

/**
 * Represents a price for a product and a specific quantity
 *
 * @param seed randomization seed
 *
 * @param config configuration of the pricebook the amount belongs to
 *
 * @param currency currency of the price
 *
 * @param sale is the price a sales price?
 *
 * @property quantity how many products do you have to order to get this price?
 *
 * @property amount price of the product for a given quantity
 *
 * @author ojauch
 */
class Amount(
    private val seed: Long, private val config: PricebookConfiguration, val quantity: Int,
    private val currency: String, private val sale: Boolean, private val uniquePrices: List<Double>? = null,
    private val priceIndex: Int = 0
) {
    val amount: Double
        get() {
            val rng = Random(seed)
            val price = if (uniquePrices != null && uniquePrices.isNotEmpty()) {
                // Use sequential price from the list (cycling through)
                uniquePrices[priceIndex] / quantity
            } else {
                // Continuous random price
                (config.minAmount + (config.maxAmount - config.minAmount) * rng.nextDouble()) / quantity
            }
            val exchangeRate: Double = CatalogGenerator.EXCHANGE_RATES.getProperty(currency).toDouble()

            // 10% discount for sales pricelist
            if (sale) {
                return price * exchangeRate * 0.9
            }

            return price * exchangeRate
        }
}

/**
 * Represents prices for different quantities for a single product
 *
 * @param seed randomization seed
 *
 * @param config configuration of the pricebook the PriceTable belongs to
 *
 * @param currency currency of the generated prices
 *
 * @param sale are the prices sale prices?
 *
 * @property productId id of the product
 *
 * @property amounts list of different prices for one product
 *
 * @author ojauch
 */
class PriceTable(
    val productId: String, private val seed: Long, private val config: PricebookConfiguration,
    private val currency: String, private val sale: Boolean, private val uniquePrices: List<Double>? = null,
    private val priceIndex: Int = 0,
    private val timeBased: Boolean = false
) {
    companion object {
        private val ISO_UTC_MILLIS: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    }

    val onlineFrom: String?
        get() {
            if (!timeBased) {
                return null
            }

            val from = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC)
            return ISO_UTC_MILLIS.format(from)
        }

    val onlineTo: String?
        get() {
            if (!timeBased) {
                return null
            }

            val to = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .plusSeconds(90L * 24L * 60L * 60L)
            return ISO_UTC_MILLIS.format(to)
        }

    val amounts: List<Amount>
        get() {
            val rng = Random(seed)

            var amountCount = 1
            if (!timeBased && config.maxAmountCount > 1) {
                amountCount = if (config.maxAmountCount > config.minAmountCount)
                    rng.nextInt(config.maxAmountCount - config.minAmountCount) + config.minAmountCount
                else config.minAmountCount

            }
            return (1..amountCount).map { quantity -> Amount(seed, config, quantity, currency, sale, uniquePrices, priceIndex) }
        }
}

/**
 * Root of several price books.
 */
class ParentPriceBook(
    productIds: Sequence<String>,
    seed: Long, attributeDefinitions: Set<AttributeDefinition>, config: PricebookConfiguration,
    currency: String, index: Int, catalogHashCode: Int, totalPriceEntryCount: Int = 0,
    uniquePrices: List<Double>? = null, startPriceIndex: Int = 0,
    hasTimeBasedPrice: Boolean = false
) : Pricebook(config, currency, seed, attributeDefinitions, productIds, index, catalogHashCode, totalPriceEntryCount, uniquePrices, startPriceIndex, hasTimeBasedPrice)

/**
 * Represents a single pricebook that has a parent pricebook, for example a sales pricebook that defines
 * cheaper prices for a subset of the products
 *
 * @param parentPriceBook pricebook that should be the parent pricebook of the child
 *
 * @param seed pseudo random data seed
 *
 * @param config child pricebook configuration
 *
 * @param currency currency of the defined prices
 */
class ChildPricebook(
    private val parentPriceBook: ParentPriceBook,
    productIds: Sequence<String>,
    seed: Long,
    attributeDefinitions: Set<AttributeDefinition>,
    config: PricebookConfiguration,
    currency: String,
    index: Int,
    catalogHashCode: Int,
    totalPriceEntryCount: Int = 0,
    uniquePrices: List<Double>? = null,
    startPriceIndex: Int = 0,
    hasTimeBasedPrice: Boolean = false
) : Pricebook(config, currency, seed, attributeDefinitions, productIds, index, catalogHashCode, totalPriceEntryCount, uniquePrices, startPriceIndex, hasTimeBasedPrice) {

    // by default child price books are for sales (get 10% discount applied)
    override val salePriceBook: Boolean
        get() = true

    override val parentId: String
        get() = parentPriceBook.id
}
