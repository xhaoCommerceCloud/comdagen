/*
 *  Copyright (c) 2018, salesforce.com, inc.
 *  All rights reserved.
 *  SPDX-License-Identifier: BSD-3-Clause
 *  For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.comdagen.generator

import com.salesforce.comdagen.attributeDefinitions
import com.salesforce.comdagen.config.SourceCodeConfiguration
import com.salesforce.comdagen.model.AttributeDefinition
import com.salesforce.comdagen.model.SourceCodeGroup

data class SourceCodeGenerator(
    override val configuration: SourceCodeConfiguration,
    val pricebookIds: List<String>? = null
) : Generator<SourceCodeConfiguration, SourceCodeGroup> {

    private val effectiveGroupCount: Int
        get() {
            return if (pricebookIds != null && pricebookIds.isNotEmpty()) {
                // Calculate number of groups based on pricebooks
                (pricebookIds.size + configuration.pricebooksPerGroup - 1) / configuration.pricebooksPerGroup
            } else {
                // Fallback to configured elementCount if no pricebooks
                if (configuration.elementCount > 0) configuration.elementCount else 10
            }
        }

    override val objects: Sequence<SourceCodeGroup>
        get() {
            val rng = java.util.Random(configuration.initialSeed)
            val seeds = (1..effectiveGroupCount).map { rng.nextLong() }
            
            // Distribute pricebooks across source code groups
            val pricebookAssignments = distributePricebooks(pricebookIds)
            
            return seeds.mapIndexed { index, seed ->
                SourceCodeGroup(seed, configuration, pricebookAssignments[index])
            }.asSequence()
        }

    private fun distributePricebooks(availablePricebooks: List<String>?): List<List<String>?> {
        if (availablePricebooks == null || availablePricebooks.isEmpty()) {
            return List(effectiveGroupCount) { null }
        }
        
        val assignments = mutableListOf<List<String>?>()
        var currentIndex = 0
        
        for (i in 0 until effectiveGroupCount) {
            val remainingPricebooks = availablePricebooks.size - currentIndex
            if (remainingPricebooks <= 0) {
                assignments.add(null)
                continue
            }
            
            val assignCount = minOf(configuration.pricebooksPerGroup, remainingPricebooks)
            val assigned = availablePricebooks.subList(currentIndex, currentIndex + assignCount)
            assignments.add(assigned)
            currentIndex += assignCount
        }
        
        return assignments
    }

    override val metadata: Map<String, Set<AttributeDefinition>>
        get() = mapOf("SourceCodeGroup" to configuration.attributeDefinitions())
}
