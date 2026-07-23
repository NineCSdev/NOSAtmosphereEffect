package com.app.nosatmosphereeffect.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

internal class CropApplyState : ViewModel() {
    var isApplying by mutableStateOf(false)
    var applyCompleted by mutableStateOf(false)
    var applyError by mutableStateOf<String?>(null)
}
