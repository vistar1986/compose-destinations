package com.ramcosta.composedestinations.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.ramcosta.composedestinations.spec.DestinationSpec
import com.ramcosta.composedestinations.spec.NavGraphSpec
import com.ramcosta.composedestinations.spec.NavHostGraphSpec
import com.ramcosta.composedestinations.spec.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform

/**
 * The top level navigation graph associated with this [NavController].
 * Can only be called after [com.ramcosta.composedestinations.DestinationsNavHost].
 */
val NavController.navGraph: NavHostGraphSpec
    get() {
        return NavGraphRegistry[this]?.topLevelNavGraph(this)
            ?: error("Cannot call rootNavGraph before DestinationsNavHost!")
    }

/**
 * Finds the [DestinationSpec] correspondent to this [NavBackStackEntry].
 * Some [NavBackStackEntry] are not [DestinationSpec], but are [NavGraphSpec] instead.
 * If you want a method that works for both, use [route] extension function instead.
 *
 * Use this ONLY if you're sure your [NavBackStackEntry] corresponds to a [DestinationSpec],
 * for example when converting from "current NavBackStackEntry", since a [NavGraphSpec] is never
 * the "current destination" shown on screen.
 */
fun NavBackStackEntry.destination(): DestinationSpec {
    return when (val route = route()) {
        is DestinationSpec -> route
        is NavGraphSpec -> error(
            "Cannot call `destination()` for a NavBackStackEntry which corresponds to a nav graph, use `route()` instead!"
        )
    }
}

/**
 * Finds the [Route] (so either a [DestinationSpec] or a [NavGraphSpec])
 * correspondent to this [NavBackStackEntry].
 */
fun NavBackStackEntry.route(): Route {
    val registry = NavGraphRegistry[this]
        ?: error("Cannot call NavBackStackEntry.route() before DestinationsNavHost!")

    val navGraph = registry.navGraph(this)
    if (navGraph != null) {
        return navGraph
    }

    // If it's not a nav graph, then it must have a parent
    val parentNavGraph = registry.parentNavGraph(this)!!
    return destination.route?.let { parentNavGraph.findDestination(it) }
        ?: parentNavGraph.startDestination
}

/**
 * Finds the [NavGraphSpec] that this [NavBackStackEntry] belongs to.
 * If [NavBackStackEntry] corresponds to the top level nav graph (i.e, there is no parent),
 * then this returns the top level [NavGraphSpec].
 */
fun NavBackStackEntry.navGraph(): NavGraphSpec {
    val registry = NavGraphRegistry[this]
        ?: error("Cannot call NavBackStackEntry.parentNavGraph() before DestinationsNavHost!")

    return registry.parentNavGraph(this) ?: route() as NavGraphSpec
}

/**
 * Emits the currently active [DestinationSpec] whenever it changes. If
 * there is no active [DestinationSpec], no item will be emitted.
 */
val NavController.currentDestinationFlow: Flow<DestinationSpec>
    get() = currentBackStackEntryFlow.transform { navStackEntry ->
        when (val route = navStackEntry.route()) {
            is DestinationSpec -> emit(route)
            is NavGraphSpec -> Unit
        }
    }

/**
 * Gets the current [DestinationSpec] as a [State].
 */
@Composable
fun NavController.currentDestinationAsState(): State<DestinationSpec?> {
    return currentDestinationFlow.collectAsState(initial = null)
}

/**
 * Checks if a given [Route] (which is either [com.ramcosta.composedestinations.spec.NavGraphSpec]
 * or [com.ramcosta.composedestinations.spec.DestinationSpec]) is currently somewhere in the back stack.
 */
fun NavController.isRouteOnBackStack(route: Route): Boolean {
    return runCatching { getBackStackEntry(route.route) }.isSuccess
}

/**
 * Same as [isRouteOnBackStack] but provides a [State] which you can use to make sure
 * your Composables get recomposed when this changes.
 */
@Composable
fun NavController.isRouteOnBackStackAsState(route: Route): State<Boolean> {
    val mappedFlow = remember(currentBackStackEntryFlow) {
        currentBackStackEntryFlow.map { isRouteOnBackStack(route) }
    }
    return mappedFlow.collectAsState(initial = isRouteOnBackStack(route))
}

/**
 * If this [Route] is a [DestinationSpec], returns it
 *
 * If this [Route] is a [NavGraphSpec], returns its
 * start [DestinationSpec].
 */
val Route.startDestination get(): DestinationSpec {
    return when (this) {
        is DestinationSpec -> this
        is NavGraphSpec -> startRoute.startDestination
    }
}

/**
 * Filters all destinations of this [NavGraphSpec] and its nested nav graphs with given [predicate]
 */
inline fun NavGraphSpec.filterDestinations(predicate: (DestinationSpec) -> Boolean): List<DestinationSpec> {
    return allDestinations.filter { predicate(it) }
}

/**
 * Checks if any destination of this [NavGraphSpec] matches with given [predicate]
 */
inline fun NavGraphSpec.anyDestination(predicate: (DestinationSpec) -> Boolean): Boolean {
    return allDestinations.any { predicate(it) }
}

/**
 * Checks if this [NavGraphSpec] contains given [destination]
 */
fun NavGraphSpec.contains(destination: DestinationSpec): Boolean {
    return allDestinations.contains(destination)
}

/**
 * Returns all [DestinationSpec]s including those of nested graphs
 */
val NavGraphSpec.allDestinations get() = addAllDestinationsTo(mutableListOf())

internal fun NavGraphSpec.addAllDestinationsTo(currentList: MutableList<DestinationSpec>): List<DestinationSpec> {
    currentList.addAll(destinations)

    nestedNavGraphs.forEach {
        it.addAllDestinationsTo(currentList)
    }

    return destinations
}

/**
 * Returns all [Route]s including those of nested graphs recursively
 */
val NavGraphSpec.allRoutes: List<Route> get() = addAllRoutesTo(mutableListOf())

internal fun NavGraphSpec.addAllRoutesTo(currentList: MutableList<Route>): List<Route> {
    currentList.addAll(destinations)
    currentList.addAll(nestedNavGraphs)

    nestedNavGraphs.forEach {
        it.addAllRoutesTo(currentList)
    }

    return currentList
}

/**
 * Finds a destination for a `route` in this navigation graph
 * or its nested graphs.
 * Returns `null` if there is no such destination.
 */
fun NavGraphSpec.findDestination(route: String): DestinationSpec? {
    val destination = destinations.find { it.route == route }

    if (destination != null) {
        return destination
    }

    for (nestedGraph in nestedNavGraphs) {
        val nestedDestination = nestedGraph.findDestination(route)

        if (nestedDestination != null) {
            return nestedDestination
        }
    }

    return null
}
