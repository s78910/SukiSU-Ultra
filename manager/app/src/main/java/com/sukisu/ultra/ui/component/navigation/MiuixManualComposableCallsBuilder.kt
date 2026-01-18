package com.sukisu.ultra.ui.component.navigation

import com.ramcosta.composedestinations.manualcomposablecalls.ManualComposableCallsBuilder

data class MiuixManualComposableCallsBuilder(
    val destinationsNavigator: ManualComposableCallsBuilder,
    val routePopupState: RoutePopupStack
)