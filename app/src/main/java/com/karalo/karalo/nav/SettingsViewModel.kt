package com.karalo.karalo.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karalo.core.common.model.SeasonalTheme
import com.karalo.core.common.result.AppResult
import com.karalo.core.karaoke.domain.KaraokeRepository
import com.karalo.feature.update.domain.InstallResult
import com.karalo.feature.update.domain.UpdateRepository
import com.karalo.feature.update.domain.UpdateState
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
        private val updateRepository: UpdateRepository,
    ) : ViewModel() {
        val seasonalTheme: StateFlow<SeasonalTheme> = karaokeRepository.seasonalTheme
        val updateState: StateFlow<UpdateState> = updateRepository.state
        val installedVersion: String get() = updateRepository.installedVersion

        // One-off failures shown as a Toast, like History's confirmations.
        private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val messages: SharedFlow<String> = _messages.asSharedFlow()

        /** Downloads in the background: the user can leave Settings and keep singing. */
        fun onUpdateNow() = updateRepository.startDownload()

        fun onRestartNow() {
            when (updateRepository.install()) {
                InstallResult.STARTED -> Unit
                InstallResult.NEEDS_PERMISSION ->
                    _messages.tryEmit("Allow Karalo to install apps, then choose Restart now again.")
                InstallResult.FAILED -> _messages.tryEmit("Couldn't open the installer. Try again.")
            }
        }

        fun onThemeSelected(theme: SeasonalTheme) {
            if (theme == seasonalTheme.value) return
            viewModelScope.launch {
                if (karaokeRepository.setSeasonalTheme(theme) is AppResult.Failure) {
                    _messages.tryEmit("Couldn't change the theme. Check your connection and try again.")
                }
            }
        }
    }
