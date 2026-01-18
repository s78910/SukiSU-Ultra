package com.sukisu.ultra.ui.component.sharedTransition

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.tween
import com.sukisu.ultra.ui.component.navigation.MiuixNavHostDefaults.NavAnimationEasing
import com.sukisu.ultra.ui.component.navigation.MiuixNavHostDefaults.SHARETRANSITION_DURATION

val defaultBoundsTransform = BoundsTransform { initialBounds, targetBounds ->
    tween(SHARETRANSITION_DURATION, 0, NavAnimationEasing)
}
