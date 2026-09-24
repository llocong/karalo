package com.karalo.karalo.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karalo.core.common.model.SeasonalTheme
import com.karalo.core.common.result.AppResult
import com.karalo.core.karaoke.domain.KaraokeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val karaokeRepository: KaraokeRepository,
    ) : ViewModel() {
        val seasonalTheme: StateFlow<SeasonalTheme> = karaokeRepository.seasonalTheme

        // One-off failures shown as a Toast, like History's confirmations.
        private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val messages: SharedFlow<String> = _messages.asSharedFlow()

        fun onThemeSelected(theme: SeasonalTheme) {
            if (theme == seasonalTheme.value) return
            viewModelScope.launch {
                if (karaokeRepository.setSeasonalTheme(theme) is AppResult.Failure) {
                    _messages.tryEmit("Couldn't change the theme. Check your connection and try again.")
                }
            }
        }
    }
