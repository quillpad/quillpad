package org.qosp.notes.data.sync.davx5

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat

data class Davx5CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int? = null,
    val isVisible: Boolean = true
)

object Davx5Helper {
    private const val TAG = "Davx5Helper"

    fun hasReadCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWriteCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCalendarPermissions(context: Context): Boolean {
        return hasReadCalendarPermission(context) && hasWriteCalendarPermission(context)
    }

    fun isDavx5Installed(context: Context): Boolean {
        val pm = context.packageManager
        val packages = listOf(
            Davx5Contract.DAVX5_PACKAGE,
            Davx5Contract.DAVX5_PACKAGE_OSE,
            Davx5Contract.DAVX5_PACKAGE_ORG
        )
        for (pkg in packages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        // Also check if any system authenticator matches DAVx5
        try {
            val accountManager = AccountManager.get(context)
            val authTypes = accountManager.authenticatorTypes
            if (authTypes.any {
                it.type.equals(Davx5Contract.DAVX5_ACCOUNT_TYPE, ignoreCase = true) ||
                it.type.equals(Davx5Contract.DAVX5_ACCOUNT_TYPE_ALT, ignoreCase = true) ||
                it.type.contains("davdroid", ignoreCase = true) ||
                it.type.contains("davx5", ignoreCase = true)
            }) {
                return true
            }
        } catch (_: Exception) {
        }
        return false
    }

    fun getDavx5Package(context: Context): String {
        val pm = context.packageManager
        val packages = listOf(
            Davx5Contract.DAVX5_PACKAGE,
            Davx5Contract.DAVX5_PACKAGE_OSE,
            Davx5Contract.DAVX5_PACKAGE_ORG
        )
        for (pkg in packages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        return Davx5Contract.DAVX5_PACKAGE
    }

    fun getDavx5Accounts(context: Context): List<Account> {
        val results = mutableListOf<Account>()
        try {
            val accountManager = AccountManager.get(context)
            val candidateTypes = listOf(
                Davx5Contract.DAVX5_ACCOUNT_TYPE,
                Davx5Contract.DAVX5_ACCOUNT_TYPE_ALT,
                "at.bitfire.davdroid.ose",
                "org.davx5"
            )
            for (type in candidateTypes) {
                try {
                    val accounts = accountManager.getAccountsByType(type)
                    if (!accounts.isNullOrEmpty()) {
                        results.addAll(accounts)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "getAccountsByType($type) failed", e)
                }
            }

            // Also inspect any accounts accessible to the app
            try {
                for (acc in accountManager.accounts) {
                    if (acc.type.contains("davdroid", ignoreCase = true) ||
                        acc.type.contains("davx5", ignoreCase = true) ||
                        acc.type.contains("bitfire", ignoreCase = true)
                    ) {
                        if (!results.any { it.name == acc.name && it.type == acc.type }) {
                            results.add(acc)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking accountManager.accounts", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get DAVx5 accounts from AccountManager", e)
        }

        // Also discover DAVx5 accounts directly from CalendarContract.Calendars when READ_CALENDAR is granted
        if (hasReadCalendarPermission(context)) {
            try {
                val projection = arrayOf(
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE
                )
                val selection = "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? OR ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
                val selectionArgs = arrayOf(
                    Davx5Contract.DAVX5_ACCOUNT_TYPE,
                    Davx5Contract.DAVX5_ACCOUNT_TYPE_ALT
                )
                val cursor = context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )
                cursor?.use {
                    val nameCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                    val typeCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                    while (it.moveToNext()) {
                        val accName = it.getString(nameCol).orEmpty()
                        val accType = it.getString(typeCol).orEmpty()
                        if (accName.isNotEmpty() && accType.isNotEmpty()) {
                            if (!results.any { acc -> acc.name == accName }) {
                                results.add(Account(accName, accType))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to discover DAVx5 accounts from CalendarContract", e)
            }
        }

        // Also discover accounts recorded in Davx5Contract.Collection
        try {
            val cursor = context.contentResolver.query(
                Davx5Contract.CONTENT_URI_COLLECTION,
                arrayOf(
                    Davx5Contract.Collection.ACCOUNT_NAME,
                    Davx5Contract.Collection.ACCOUNT_TYPE
                ),
                "${Davx5Contract.Collection.SUPPORTS_VJOURNAL} = 1",
                null,
                null
            )
            cursor?.use {
                val nameCol = it.getColumnIndexOrThrow(Davx5Contract.Collection.ACCOUNT_NAME)
                val typeCol = it.getColumnIndexOrThrow(Davx5Contract.Collection.ACCOUNT_TYPE)
                while (it.moveToNext()) {
                    val accName = it.getString(nameCol).orEmpty()
                    val accType = it.getString(typeCol).orEmpty().ifEmpty { Davx5Contract.DAVX5_ACCOUNT_TYPE }
                    if (accName.isNotEmpty() && accName != "Local" && accName != "DAVx5") {
                        if (!results.any { acc -> acc.name == accName }) {
                            results.add(Account(accName, accType))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to discover DAVx5 accounts from Davx5Contract.Collection", e)
        }

        return results.distinctBy { it.name }
    }

    fun getDavx5Calendars(context: Context, targetAccountName: String? = null): List<Davx5CalendarInfo> {
        val results = mutableListOf<Davx5CalendarInfo>()

        // Query Android CalendarProvider (CalendarContract.Calendars)
        if (hasReadCalendarPermission(context)) {
            try {
                val projection = arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                    CalendarContract.Calendars.VISIBLE
                )

                val uri = CalendarContract.Calendars.CONTENT_URI

                fun queryCalendars(accountFilter: String?): List<Davx5CalendarInfo> {
                    val list = mutableListOf<Davx5CalendarInfo>()
                    val selection: String
                    val selectionArgs: Array<String>

                    if (!accountFilter.isNullOrEmpty()) {
                        selection = "(${CalendarContract.Calendars.ACCOUNT_TYPE} = ? OR ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?) AND ${CalendarContract.Calendars.ACCOUNT_NAME} = ?"
                        selectionArgs = arrayOf(Davx5Contract.DAVX5_ACCOUNT_TYPE, Davx5Contract.DAVX5_ACCOUNT_TYPE_ALT, accountFilter)
                    } else {
                        selection = "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? OR ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
                        selectionArgs = arrayOf(Davx5Contract.DAVX5_ACCOUNT_TYPE, Davx5Contract.DAVX5_ACCOUNT_TYPE_ALT)
                    }

                    val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                    cursor?.use {
                        val idCol = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                        val nameCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                        val accNameCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                        val accTypeCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                        val colorCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                        val visCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)

                        while (it.moveToNext()) {
                            val id = it.getLong(idCol)
                            val name = it.getString(nameCol) ?: "Calendar #$id"
                            val accName = it.getString(accNameCol) ?: ""
                            val accType = it.getString(accTypeCol) ?: ""
                            val color = if (!it.isNull(colorCol)) it.getInt(colorCol) else null
                            val vis = it.getInt(visCol) != 0

                            list.add(
                                Davx5CalendarInfo(
                                    id = id,
                                    displayName = name,
                                    accountName = accName,
                                    accountType = accType,
                                    color = color,
                                    isVisible = vis
                                )
                            )
                        }
                    }
                    return list
                }

                val filteredList = queryCalendars(targetAccountName)
                if (filteredList.isNotEmpty() || targetAccountName.isNullOrEmpty()) {
                    results.addAll(filteredList)
                } else {
                    results.addAll(queryCalendars(null))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query CalendarContract.Calendars", e)
            }
        }

        // Fallback to local Davx5Contract.Collection table when CalendarContract has no entries
        if (results.isEmpty()) {
            try {
                val allLocal = mutableListOf<Davx5CalendarInfo>()
                val cursor = context.contentResolver.query(
                    Davx5Contract.CONTENT_URI_COLLECTION,
                    arrayOf(
                        Davx5Contract.Collection._ID,
                        Davx5Contract.Collection.DISPLAY_NAME,
                        Davx5Contract.Collection.ACCOUNT_NAME,
                        Davx5Contract.Collection.ACCOUNT_TYPE,
                        Davx5Contract.Collection.COLOR
                    ),
                    "${Davx5Contract.Collection.SUPPORTS_VJOURNAL} = 1",
                    null,
                    null
                )
                cursor?.use {
                    val idCol = it.getColumnIndexOrThrow(Davx5Contract.Collection._ID)
                    val nameCol = it.getColumnIndexOrThrow(Davx5Contract.Collection.DISPLAY_NAME)
                    val accNameCol = it.getColumnIndexOrThrow(Davx5Contract.Collection.ACCOUNT_NAME)
                    val accTypeCol = it.getColumnIndexOrThrow(Davx5Contract.Collection.ACCOUNT_TYPE)
                    val colorCol = it.getColumnIndexOrThrow(Davx5Contract.Collection.COLOR)

                    while (it.moveToNext()) {
                        val id = it.getLong(idCol)
                        val name = it.getString(nameCol) ?: "Local"
                        val accName = it.getString(accNameCol) ?: "Local"
                        val accType = it.getString(accTypeCol) ?: ""
                        val color = if (!it.isNull(colorCol)) it.getInt(colorCol) else null
                        allLocal.add(
                            Davx5CalendarInfo(
                                id = id,
                                displayName = name,
                                accountName = accName,
                                accountType = accType,
                                color = color
                            )
                        )
                    }
                }
                if (!targetAccountName.isNullOrEmpty()) {
                    val filtered = allLocal.filter { it.accountName == targetAccountName }
                    results.addAll(if (filtered.isNotEmpty()) filtered else allLocal)
                } else {
                    results.addAll(allLocal)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query Davx5Contract.Collection", e)
            }
        }

        return results
    }

    fun createChooseAccountIntent(selectedAccountName: String? = null): Intent {
        val selectedAccount = if (!selectedAccountName.isNullOrEmpty()) {
            Account(selectedAccountName, Davx5Contract.DAVX5_ACCOUNT_TYPE)
        } else {
            null
        }
        val allowableTypes = arrayOf(
            Davx5Contract.DAVX5_ACCOUNT_TYPE,
            Davx5Contract.DAVX5_ACCOUNT_TYPE_ALT,
            "at.bitfire.davdroid.ose",
            "org.davx5"
        )
        return AccountManager.newChooseAccountIntent(
            selectedAccount,
            null,
            allowableTypes,
            null,
            null,
            null,
            null
        )
    }

    fun createGenericChooseAccountIntent(selectedAccountName: String? = null): Intent {
        val selectedAccount = if (!selectedAccountName.isNullOrEmpty()) {
            Account(selectedAccountName, Davx5Contract.DAVX5_ACCOUNT_TYPE)
        } else {
            null
        }
        return AccountManager.newChooseAccountIntent(
            selectedAccount,
            null,
            null,
            null,
            null,
            null,
            null
        )
    }

    fun openDavx5(context: Context) {
        val pm = context.packageManager
        val pkg = getDavx5Package(context)
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            try {
                val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(storeIntent)
            } catch (_: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.davx5.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }

    fun openDavx5Account(context: Context, account: Account) {
        if (!isDavx5Installed(context)) {
            openDavx5(context)
            return
        }
        val pkg = getDavx5Package(context)
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(pkg, "$pkg.ui.account.AccountActivity")
            putExtra("account", account)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open DAVx5 AccountActivity, opening main app", e)
            openDavx5(context)
        }
    }

    fun openDavx5Login(context: Context) {
        if (!isDavx5Installed(context)) {
            openDavx5(context)
            return
        }
        val pkg = getDavx5Package(context)
        val intent = Intent().apply {
            setClassName(pkg, "$pkg.ui.setup.LoginActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open DAVx5 LoginActivity, opening main app", e)
            openDavx5(context)
        }
    }

    fun requestSync(context: Context, targetAccountName: String? = null, collectionId: Long? = null) {
        val accountsToSync = mutableListOf<Account>()

        // If a specific collectionId is selected, look up its exact account in CalendarContract / Davx5Contract
        if (collectionId != null && collectionId > 0L) {
            val matchedCal = getDavx5Calendars(context).firstOrNull {
                it.id == collectionId && it.accountName.isNotEmpty() && it.accountName != "Local" && it.accountName != "DAVx5"
            }
            if (matchedCal != null) {
                val accType = matchedCal.accountType.ifEmpty { Davx5Contract.DAVX5_ACCOUNT_TYPE }
                accountsToSync.add(Account(matchedCal.accountName, accType))
            }
        }

        // Discover accounts via AccountManager, CalendarContract, and Davx5Contract
        val discovered = getDavx5Accounts(context)
        if (!targetAccountName.isNullOrEmpty()) {
            val matching = discovered.filter { it.name == targetAccountName }
            if (matching.isNotEmpty()) {
                accountsToSync.addAll(matching)
            } else {
                accountsToSync.add(Account(targetAccountName, Davx5Contract.DAVX5_ACCOUNT_TYPE))
            }
        } else {
            accountsToSync.addAll(discovered)
        }

        val uniqueAccounts = accountsToSync.distinctBy { "${it.name}:${it.type}" }

        val bundle = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }

        for (acc in uniqueAccounts) {
            try {
                ContentResolver.requestSync(acc, CalendarContract.AUTHORITY, bundle)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request sync on CalendarContract for ${acc.name}", e)
            }
            try {
                ContentResolver.requestSync(acc, Davx5Contract.AUTHORITY, bundle)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request sync on Davx5Contract for ${acc.name}", e)
            }
        }
    }
}
