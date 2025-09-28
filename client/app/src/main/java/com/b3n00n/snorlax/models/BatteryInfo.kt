package com.b3n00n.snorlax.models

data class BatteryInfo(
    val headsetLevel: Int,
    val isCharging: Boolean,
    val leftControllerLevel: Int? = null,
    val rightControllerLevel: Int? = null
)