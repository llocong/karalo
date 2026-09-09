package com.karalo.core.common.session

import com.karalo.core.common.model.PlayableItemRef
import javax.inject.Inject

class InMemorySearchSessionHolder
    @Inject
    constructor() : SearchSessionHolder {
        @Volatile
        private var lastResults: List<PlayableItemRef> = emptyList()

        override fun setLastResults(items: List<PlayableItemRef>) {
            lastResults = items
        }

        override fun getLastResults(): List<PlayableItemRef> = lastResults

        override fun clear() {
            lastResults = emptyList()
        }
    }
