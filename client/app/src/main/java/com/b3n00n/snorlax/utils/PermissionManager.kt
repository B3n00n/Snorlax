package com.b3n00n.snorlax.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.b3n00n.snorlax.receivers.DeviceOwnerReceiver

class PermissionManager(private val context: Context) {
    companion object {
        private const val TAG = "PermissionManager"

        private val BLUETOOTH_PERMISSIONS = listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT",
            "com.oculus.permission.HAND_TRACKING",
            )
    }

    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)

    fun grantBluetoothPermissions(packageName: String): GrantResult {
        return grantPermissionSet(packageName, BLUETOOTH_PERMISSIONS, "Bluetooth")
    }

    fun grantCommonPermissions(packageName: String): GrantResult {
        val allPermissions = BLUETOOTH_PERMISSIONS;
        return grantPermissionSet(packageName, allPermissions, "Common")
    }

    fun grantPermissionSet(
        packageName: String,
        permissions: List<String>,
        setName: String = "Custom"
    ): GrantResult {
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "App is not device owner, cannot grant permissions")
            return GrantResult.Error("App is not device owner")
        }

        val results = mutableMapOf<String, Boolean>()
        var grantedCount = 0
        var skippedCount = 0
        var failedCount = 0

        for (permission in permissions) {
            if (!isPermissionCompatible(permission)) {
                Log.d(TAG, "Permission $permission not compatible with SDK ${Build.VERSION.SDK_INT}")
                results[permission] = false
                skippedCount++
                continue
            }

            if (!isPermissionDeclaredByPackage(packageName, permission)) {
                Log.d(TAG, "Permission $permission not declared by $packageName")
                results[permission] = false
                skippedCount++
                continue
            }

            if (isPermissionGranted(packageName, permission)) {
                Log.d(TAG, "Permission $permission already granted to $packageName")
                results[permission] = true
                grantedCount++
                continue
            }

            val granted = grantPermission(packageName, permission)
            results[permission] = granted

            if (granted) {
                grantedCount++
                Log.d(TAG, "Successfully granted $permission to $packageName")
            } else {
                failedCount++
                Log.e(TAG, "Failed to grant $permission to $packageName")
            }
        }

        val summary = "$setName permissions: $grantedCount granted, $skippedCount skipped, $failedCount failed"
        Log.i(TAG, "Grant results for $packageName - $summary")

        return if (failedCount > 0) {
            GrantResult.PartialSuccess(summary, results)
        } else if (grantedCount > 0 || skippedCount > 0) {
            GrantResult.Success(summary, results)
        } else {
            GrantResult.Error("No permissions were granted", results)
        }
    }

    private fun grantPermission(packageName: String, permission: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                devicePolicyManager.setPermissionGrantState(
                    adminComponent,
                    packageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )

                // Verify the permission was granted
                val newState = devicePolicyManager.getPermissionGrantState(
                    adminComponent,
                    packageName,
                    permission
                )
                newState == DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            } else {
                Log.w(TAG, "Permission granting requires Android M or higher")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error granting permission $permission to $packageName", e)
            false
        }
    }

    private fun isPermissionGranted(packageName: String, permission: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val state = context.packageManager.checkPermission(permission, packageName)
                state == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission $permission for $packageName", e)
            false
        }
    }

    private fun isPermissionDeclaredByPackage(packageName: String, permission: String): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS
            )
            packageInfo.requestedPermissions?.contains(permission) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if $packageName declares $permission", e)
            false
        }
    }

    private fun isPermissionCompatible(permission: String): Boolean {
        val sdkVersion = Build.VERSION.SDK_INT

        return when (permission) {
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT" -> sdkVersion >= Build.VERSION_CODES.S

            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN" -> sdkVersion < Build.VERSION_CODES.S

            "android.permission.ACCESS_FINE_LOCATION" -> sdkVersion <= Build.VERSION_CODES.R

            else -> true
        }
    }

    fun revokePermission(packageName: String, permission: String): Boolean {
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "App is not device owner, cannot revoke permissions")
            return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                devicePolicyManager.setPermissionGrantState(
                    adminComponent,
                    packageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error revoking permission $permission from $packageName", e)
            false
        }
    }

    fun getPackagePermissions(packageName: String): List<PermissionInfo> {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS
            )

            packageInfo.requestedPermissions?.mapIndexed { index, permission ->
                val flags = packageInfo.requestedPermissionsFlags?.getOrNull(index) ?: 0
                val granted = (flags and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0

                PermissionInfo(
                    permission = permission,
                    granted = granted,
                    isDangerous = isDangerousPermission(permission)
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting permissions for $packageName", e)
            emptyList()
        }
    }

    private fun isDangerousPermission(permission: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val permissionInfo = context.packageManager.getPermissionInfo(permission, 0)
                permissionInfo.protection == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    data class PermissionInfo(
        val permission: String,
        val granted: Boolean,
        val isDangerous: Boolean
    )

    sealed class GrantResult {
        data class Success(
            val message: String,
            val details: Map<String, Boolean> = emptyMap()
        ) : GrantResult()

        data class PartialSuccess(
            val message: String,
            val details: Map<String, Boolean> = emptyMap()
        ) : GrantResult()

        data class Error(
            val message: String,
            val details: Map<String, Boolean> = emptyMap()
        ) : GrantResult()
    }
}