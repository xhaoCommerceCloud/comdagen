/*
 *  Copyright (c) 2018, salesforce.com, inc.
 *  All rights reserved.
 *  SPDX-License-Identifier: BSD-3-Clause
 *  For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.comdagen.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonRootName
import com.salesforce.comdagen.ExtendableObjectConfig
import com.salesforce.comdagen.RenderConfig
import com.salesforce.comdagen.SupportedCurrency

/**
 * Root configuration for PricebookGenerator
 */
@JsonRootName("pricebooks")
@JsonIgnoreProperties(ignoreUnknown = true)
data class PricebookConfiguration(
    /**
     * pricebook id (must be unique)
     */
    val id: String,

    /**
     * PriceTables for how many products should get generated?
     * >= 1 -> generate pricetables for all products
     * 0.75 -> generate pricetables for 75% of the products
     */
    val coverage: Float = 1f,

    /**
     * minimum amount value in USD
     */
    val minAmount: Double = 0.01,

    /**
     * maximum amount value in USD
     */
    val maxAmount: Double = 2000.0,

    /**
     * Price uniqueness ratio (0.0 to 1.0) - ratio of unique prices to total price entries.
     * e.g., 0.1 means 10% unique prices, so if 1000 products, only 100 unique price values.
     * If null, prices are generated as continuous random values (effectively 1.0).
     */
    val priceUniquenessRatio: Double? = null,

    /**
     * minimum number of values per product
     */
    val minAmountCount: Int = 1,

    /**
     * maximum number of values per product
     */
    val maxAmountCount: Int = 5,

    /**
     * overwrites site currencies
     */
    val currencies: List<SupportedCurrency>? = null,

    /**
     * is this pricebook a sales pricebook?
     */
    val sales: Boolean = false,

    /**
     * Percentage (0.0 to 1.0) of generated pricebooks that should contain exactly one time-based price.
     */
    val timeBasedPricebookPercentage: Double = 0.0,

    /**
     * custom attributes for pricebooks
     */
    override val customAttributes: Map<String, AttributeConfig>? = null,

    /**
     * randomly generate custom attributes
     */
    override val generatedAttributes: GeneratedAttributeConfig? = null,

    /**
     * child pricebooks get generated for each parent pricebook
     */
    val children: List<PricebookConfiguration>? = null,

    /**
     * Maximum number of pricebooks per XML file.
     * When set, the output will be split into multiple files if the total number of pricebooks exceeds this limit.
     * If null or 0, all pricebooks will be written to a single file.
     */
    val maxPricebooksPerFile: Int? = null,

    override val elementCount: Int = 1,
    override val initialSeed: Long,
    override val outputFilePattern: String = "pricebooks\${i}.xml",
    override val outputDir: String = "pricebooks",
    override val templateName: String = "pricebooks.ftlx"
) : RenderConfig, ExtendableObjectConfig {
    init {
        require(maxAmount >= minAmount, { "maxAmount needs to be greater equal minAmount" })
        require(maxAmountCount >= minAmountCount, { "maxAmountCount needs to be greater equal minAmountCount" })
        require(priceUniquenessRatio == null || (priceUniquenessRatio > 0.0 && priceUniquenessRatio <= 1.0), 
            { "priceUniquenessRatio must be between 0.0 (exclusive) and 1.0 (inclusive) if set" })
        require(timeBasedPricebookPercentage in 0.0..1.0,
            { "timeBasedPricebookPercentage must be between 0.0 and 1.0" })
    }
}
