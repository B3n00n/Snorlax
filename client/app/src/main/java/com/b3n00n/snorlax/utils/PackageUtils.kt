package com.b3n00n.snorlax.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object PackageUtils {
    fun getInstalledPackages(context: Context, includeSystemApps: Boolean): List<String> {
        val pm = context.packageManager
        return getLaunchableApps(pm, includeSystemApps)
    }

    private fun getLaunchableApps(pm: PackageManager, includeSystemApps: Boolean = false): List<String> {
        return try {
            val allPackages = mutableSetOf<String>()

            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val launchableApps = pm.queryIntentActivities(mainIntent, 0)
                .map { it.activityInfo.packageName }

            allPackages.addAll(launchableApps)

            if (includeSystemApps) {
                val categories = listOf(
                    Intent.CATEGORY_DEFAULT,
                    Intent.CATEGORY_BROWSABLE
                )

                for (category in categories) {
                    val intent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(category)
                    }
                    pm.queryIntentActivities(intent, 0)
                        .forEach { allPackages.add(it.activityInfo.packageName) }
                }
            }

            allPackages.toList().sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }
}