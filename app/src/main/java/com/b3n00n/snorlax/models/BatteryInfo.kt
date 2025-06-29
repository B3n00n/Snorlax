package com.b3n00n.snorlax.models

data class BatteryInfo(
    val headsetLevel: Int,
    val leftControllerLevel: Int,
    val rightControllerLevel: Int,
    val isCharging: Boolean
)