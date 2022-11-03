package org.qosp.notes.ui.utils

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.Navigator

/**
 * Calling [NavController].navigate() multiple times quickly can result into crashes.
 * This method checks the current destination first to see if it can navigate safely to the given destination.
 */
fun NavController.navigateSafely(
    navDirections: NavDirections,
    navigatorExtras: Navigator.Extras? = null,
) {

    val action =
        currentDestination?.getAction(navDirections.actionId) ?: graph.getAction(navDirections.actionId) ?: return

    if (currentDestination?.id != action.destinationId) {
        if (navigatorExtras != null) return navigate(navDirections, navigatorExtras)
        navigate(navDirections)
    }
}

fun NavController.navigateSafely(
    @IdRes id: Int,
    args: Bundle? = null,
    navOptions: NavOptions? = null,
    navigatorExtras: Navigator.Extras? = null,
) {
    navigate(id, args, navOptions, navigatorExtras)
}
