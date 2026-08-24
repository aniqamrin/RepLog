package com.replog.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replog.app.data.repository.DemoSeeder
import com.replog.app.data.prefs.AppSettings
import com.replog.app.data.prefs.SettingsRepository
import com.replog.app.ui.components.QuickAction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val demoSeeder: DemoSeeder
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _booting = MutableStateFlow(true)
    val booting: StateFlow<Boolean> = _booting

    val quickSheetOpen = MutableStateFlow(false)
    val activeDialog = MutableStateFlow<QuickAction?>(null)

    private val _navigation = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val navigation: SharedFlow<String> = _navigation

    init {
        viewModelScope.launch {
            runCatching { demoSeeder.seedIfEmpty() }
            _booting.value = false
        }
    }

    fun openQuickActions() {
        quickSheetOpen.value = true
    }

    fun dismissQuickSheet() {
        quickSheetOpen.value = false
    }

    fun onQuickAction(action: QuickAction) {
        quickSheetOpen.value = false
        when (action) {
            QuickAction.SCAN_FOOD -> _navigation.tryEmit(Routes.SCANNER)
            QuickAction.LOG_WORKOUT -> _navigation.tryEmit(Routes.CREATE_WORKOUT)
            else -> activeDialog.value = action
        }
    }

    fun openDialog(action: QuickAction) {
        activeDialog.value = action
    }

    fun dismissDialog() {
        activeDialog.value = null
    }

    object Routes {
        const val HOME = "home"
        const val NUTRITION = "nutrition"
        const val WORKOUT = "workout"
        const val PROGRESS = "progress"
        const val AI = "ai"
        const val SOCIAL = "social"
        const val SETTINGS = "settings"
        const val SCANNER = "scanner"
        const val CREATE_WORKOUT = "create_workout"
        fun workoutDetail(id: Long) = "workout/$id"
    }
}
