package com.karalo.core.common.result

import com.karalo.core.common.error.AppError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppResultTest {
    @Test
    fun `map transforms success data`() {
        val result: AppResult<Int> = AppResult.Success(2)

        val mapped = result.map { it * 21 }

        assertEquals(AppResult.Success(42), mapped)
    }

    @Test
    fun `map leaves failure untouched`() {
        val result: AppResult<Int> = AppResult.Failure(AppError.NotFound)

        val mapped = result.map { it * 21 }

        assertEquals(AppResult.Failure(AppError.NotFound), mapped)
    }

    @Test
    fun `onSuccess runs action only for success`() {
        var invoked = false

        (AppResult.Success(1) as AppResult<Int>).onSuccess { invoked = true }

        assertTrue(invoked)
    }

    @Test
    fun `onSuccess does not run for failure`() {
        var invoked = false

        (AppResult.Failure(AppError.NotFound) as AppResult<Int>).onSuccess { invoked = true }

        assertFalse(invoked)
    }

    @Test
    fun `getOrNull returns data for success and null for failure`() {
        assertEquals(1, AppResult.Success(1).getOrNull())
        assertNull(AppResult.Failure(AppError.NotFound).getOrNull())
    }
}
