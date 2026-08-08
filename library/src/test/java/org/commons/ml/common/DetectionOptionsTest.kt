package org.commons.ml.common

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DetectionOptionsTest {
    @Test
    fun rejectsInvalidThreshold() {
        assertFailsWith<IllegalArgumentException> { DetectionOptions(confidenceThreshold = 2f) }
    }

    @Test
    fun rejectsInvalidMaximum() {
        assertFailsWith<IllegalArgumentException> { DetectionOptions(maximumResults = 0) }
    }
}
