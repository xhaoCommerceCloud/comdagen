/*
 *  Copyright (c) 2018, salesforce.com, inc.
 *  All rights reserved.
 *  SPDX-License-Identifier: BSD-3-Clause
 *  For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.comdagen.generator

import com.salesforce.comdagen.GeneratorHelper
import com.salesforce.comdagen.GeneratorHelper.getPartialProductSequence
import com.salesforce.comdagen.SupportedCurrency
import com.salesforce.comdagen.attributeDefinitions
import com.salesforce.comdagen.config.CatalogListConfiguration
import com.salesforce.comdagen.config.PricebookConfiguration
import com.salesforce.comdagen.model.AttributeDefinition
import com.salesforce.comdagen.model.ChildPricebook
import com.salesforce.comdagen.model.ParentPriceBook
import com.salesforce.comdagen.model.Pricebook
import java.util.*

/**
 * Generate sequence of Pricebook objects
 *
 * @property configuration configure pricebook generation
 *
 * @property catalogConfiguration configuration of catalogs associated to this pricebooks
 *
 * @property currencies generate pricebooks for different currencies
 *
 * @property objects sequence of generated pricebooks
 */
data class PricebookGenerator(
    override val configuration: PricebookConfiguration,
    private val catalogConfiguration: CatalogListConfiguration,
    private val currencies: List<SupportedCurrency>
) : Generator<PricebookConfiguration, Pricebook> {

    override val objects: Sequence<Pricebook>
        get() {
            val pricebooks: MutableList<Pricebook> = mutableListOf()

            // Use totalProductCount() to avoid consuming the sequence
            val actualProductCount = catalogConfiguration.totalProductCount()
            
            // Calculate total price entries: products with prices * pricebooks * currencies * avg amounts per product
            val productsWithPrices = (actualProductCount * configuration.coverage).toInt()
            val avgAmountsPerProduct = (configuration.minAmountCount + configuration.maxAmountCount) / 2
            val totalPriceEntryCount = productsWithPrices * configuration.elementCount * currencies.size * avgAmountsPerProduct

            // Generate global list of unique prices if priceUniquenessRatio is set
            val uniquePrices = if (configuration.priceUniquenessRatio != null && totalPriceEntryCount > 0) {
                val uniquePriceCount = (totalPriceEntryCount.toDouble() * configuration.priceUniquenessRatio).toInt().coerceAtLeast(1)
                // Generate evenly spaced prices across the range
                val step = (configuration.maxAmount - configuration.minAmount) / (uniquePriceCount - 1).coerceAtLeast(1)
                (0 until uniquePriceCount).map { i ->
                    configuration.minAmount + (step * i)
                }
            } else {
                null
            }

            var globalPriceIndex = 0
            for (currency in currencies) {
                val rng = Random(configuration.initialSeed)
                for (i in (1..configuration.elementCount)) {
                    val seed = rng.nextLong()

                    // generate ParentPriceBook
                    val currentProductIds = GeneratorHelper.getProductIds(catalogConfiguration)

                    val productIds =
                        getPartialProductSequence(seed, actualProductCount, configuration.coverage, currentProductIds)

                    val parent = ParentPriceBook(
                        productIds,
                        seed,
                        metadata["PriceBook"].orEmpty(),
                        configuration,
                        currency.toString(),
                        i,
                        catalogConfiguration.hashCode(),
                        totalPriceEntryCount,
                        uniquePrices,
                        globalPriceIndex
                    )
                    pricebooks.add(parent)
                    
                    // Update global counter for next pricebook
                    if (uniquePrices != null) {
                        globalPriceIndex = (globalPriceIndex + productsWithPrices) % uniquePrices.size
                    }

                    // generate child pricebooks for parent if any
                    configuration.children?.forEach { childConfig ->
                        pricebooks.add(
                            ChildPricebook(
                                parent,
                                getPartialProductSequence(
                                    seed,
                                    actualProductCount,
                                    configuration.coverage,
                                    currentProductIds
                                ),
                                seed,
                                metadata["PriceBook"].orEmpty(),
                                childConfig,
                                currency.toString(),
                                i,
                                catalogConfiguration.hashCode(),
                                totalPriceEntryCount,
                                uniquePrices
                            )
                        )
                    }
                }
            }

            return pricebooks.asSequence()
        }

    override val metadata: Map<String, Set<AttributeDefinition>> = mapOf(
        "PriceBook" to configuration.attributeDefinitions()
    )
}
