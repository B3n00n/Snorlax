package com.b3n00n.snorlax.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log
import com.b3n00n.snorlax.receivers.DeviceOwnerReceiver

class DeviceRestrictionManager(private val context: Context) {
    companion object {
        private const val TAG = "DeviceRestrictionManager"
    }

    private val devicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent by lazy {
        ComponentName(context, DeviceOwnerReceiver::class.java)
    }

    fun setWifiRestriction(restrict: Boolean): Boolean {
        if (!isDeviceOwner()) {
            Log.w(TAG, "App is not device owner, cannot set restrictions")
            return false
        }

        return try {
            if (restrict) {
                // Add restrictions
                devicePolicyManager.addUserRestriction(
                    adminComponent,
                    UserManager.DISALLOW_CONFIG_WIFI
                )
                devicePolicyManager.addUserRestriction(
                    adminComponent,
                    UserManager.DISALLOW_ADD_WIFI_CONFIG
                )
                Log.d(TAG, "WiFi configuration restricted")
            } else {
                // Clear restrictions
                devicePolicyManager.clearUserRestriction(
                    adminComponent,
                    UserManager.DISALLOW_CONFIG_WIFI
                )
                devicePolicyManager.clearUserRestriction(
                    adminComponent,
                    UserManager.DISALLOW_ADD_WIFI_CONFIG
                )
                Log.d(TAG, "WiFi configuration unrestricted")
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception setting WiFi restriction", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set WiFi restriction", e)
            false
        }
    }

    fun isWifiRestricted(): Boolean {
        return try {
            if (!isDeviceOwner()) {
                return false
            }

            val restrictions = devicePolicyManager.getUserRestrictions(adminComponent)
            restrictions.getBoolean(UserManager.DISALLOW_CONFIG_WIFI, false) ||
                    restrictions.getBoolean(UserManager.DISALLOW_ADD_WIFI_CONFIG, false)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception checking WiFi restriction", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check WiFi restriction", e)
            false
        }
    }

    fun isDeviceOwner(): Boolean {
        return try {
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device owner status", e)
            false
        }
    }

    fun clearAllRestrictions(): Boolean {
        if (!isDeviceOwner()) {
            Log.w(TAG, "App is not device owner, cannot clear restrictions")
            return false
        }

        return try {
            // Clear all user restrictions that might have been set
            val restrictionsToClear = listOf(
                UserManager.DISALLOW_CONFIG_WIFI,
                UserManager.DISALLOW_ADD_WIFI_CONFIG,
                UserManager.DISALLOW_CHANGE_WIFI_STATE
            )

            restrictionsToClear.forEach { restriction ->
                try {
                    devicePolicyManager.clearUserRestriction(adminComponent, restriction)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear restriction: $restriction", e)
                }
            }

            Log.d(TAG, "All WiFi restrictions cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all restrictions", e)
            false
        }
    }
}