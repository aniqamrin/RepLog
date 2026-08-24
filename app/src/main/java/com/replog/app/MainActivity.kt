package com.replog.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.replog.app.feature.ai.AiScreen
import com.replog.app.feature.home.HomeScreen
import com.replog.app.feature.nutrition.NutritionScreen
import com.replog.app.feature.progress.ProgressScreen
import com.replog.app.feature.scanner.ScannerScreen
import com.replog.app.feature.settings.SettingsScreen
import com.replog.app.feature.workout.CreateWorkoutScreen
import com.replog.app.feature.workout.WorkoutDetailScreen
import com.replog.app.feature.workout.WorkoutScreen
import com.replog.app.ui.components.QuickAction
import com.replog.app.ui.components.QuickActionSheet
import com.replog.app.ui.dialogs.AddFoodDialog
import com.replog.app.ui.dialogs.CheckInDialog
import com.replog.app.ui.dialogs.LogCardioDialog
import com.replog.app.ui.dialogs.LogWeightDialog
import com.replog.app.ui.dialogs.QuickLogViewModel
import com.replog.app.ui.theme.RepLogTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()
            RepLogTheme(darkTheme = settings.themeDark) {
                RepLogApp(mainViewModel)
            }
        }
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepLogApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val booting by viewModel.booting.collectAsStateWithLifecycle()
    val quickSheetOpen by viewModel.quickSheetOpen.collectAsStateWithLifecycle()
    val activeDialog by viewModel.activeDialog.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val topLevel = listOf(
        TabItem(MainViewModel.Routes.HOME, "Home", Icons.Outlined.Home),
        TabItem(MainViewModel.Routes.NUTRITION, "Nutrition", Icons.Outlined.RestaurantMenu),
        TabItem(MainViewModel.Routes.WORKOUT, "Workout", Icons.Outlined.FitnessCenter),
        TabItem(MainViewModel.Routes.PROGRESS, "Progress", Icons.Outlined.BarChart),
        TabItem(MainViewModel.Routes.AI, "AI", Icons.Outlined.Psychology)
    )

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { route -> navController.navigate(route) }
    }

    if (booting) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("REPLOG", style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary)
                Text("Show up. Log it. Get better.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                CircularProgressIndicator(Modifier.padding(top = 24.dp))
            }
        }
        return
    }

    val isTopLevel = topLevel.any { it.route == currentRoute } || currentRoute == MainViewModel.Routes.SETTINGS

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (isTopLevel && currentRoute != null) {
                TopAppBar(
                    title = { Text("REPLOG", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        IconButton(onClick = { navController.navigate(MainViewModel.Routes.SETTINGS) }) {
                            Icon(Icons.Outlined.Person, contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (topLevel.any { it.route == currentRoute }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    topLevel.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(MainViewModel.Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute != MainViewModel.Routes.SCANNER &&
                currentRoute != MainViewModel.Routes.CREATE_WORKOUT &&
                !currentRoute.isNullOrBlank()
            ) {
                FloatingActionButton(
                    onClick = viewModel::openQuickActions,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Quick actions")
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainViewModel.Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(MainViewModel.Routes.HOME) {
                HomeScreen(onOpenCheckIn = {})
            }
            composable(MainViewModel.Routes.NUTRITION) {
                NutritionScreen(
                    onScanFood = { navController.navigate(MainViewModel.Routes.SCANNER) },
                    onAddFood = { _ -> viewModel.openDialog(QuickAction.LOG_FOOD) }
                )
            }
            composable(MainViewModel.Routes.WORKOUT) {
                WorkoutScreen(
                    onOpenWorkout = { id -> navController.navigate(MainViewModel.Routes.workoutDetail(id)) },
                    onCreateWorkout = { navController.navigate(MainViewModel.Routes.CREATE_WORKOUT) }
                )
            }
            composable(MainViewModel.Routes.PROGRESS) {
                ProgressScreen()
            }
            composable(MainViewModel.Routes.AI) {
                AiScreen(onScanFood = { navController.navigate(MainViewModel.Routes.SCANNER) })
            }
            composable(MainViewModel.Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(MainViewModel.Routes.SCANNER) {
                ScannerScreen(onDone = { navController.popBackStack() })
            }
            composable(MainViewModel.Routes.CREATE_WORKOUT) {
                CreateWorkoutScreen(onDone = { navController.popBackStack() })
            }
            composable("workout/{id}") {
                WorkoutDetailScreen()
            }
        }
    }

    if (quickSheetOpen) {
        QuickActionSheet(
            onAction = viewModel::onQuickAction,
            onDismiss = viewModel::dismissQuickSheet
        )
    }

    activeDialog?.let { action ->
        val quickVm: QuickLogViewModel = hiltViewModel()
        when (action) {
            QuickAction.LOG_FOOD -> AddFoodDialog(
                onDismiss = viewModel::dismissDialog,
                onSave = { name, meal, serving, kcal, p, c, f, fib ->
                    quickVm.addFood(name, meal, serving, kcal, p, c, f, fib)
                    viewModel.dismissDialog()
                },
                viewModel = quickVm
            )
            QuickAction.LOG_WEIGHT -> LogWeightDialog(
                onDismiss = viewModel::dismissDialog,
                onSave = { kg, bf -> quickVm.addWeight(kg, bf); viewModel.dismissDialog() }
            )
            QuickAction.LOG_CARDIO -> LogCardioDialog(
                onDismiss = viewModel::dismissDialog,
                onSave = { type, km, min, kcal ->
                    quickVm.addCardio(type, km, min, kcal); viewModel.dismissDialog()
                }
            )
            QuickAction.LOG_WATER -> WaterQuickAdd(
                onDismiss = viewModel::dismissDialog,
                onAdd = { ml -> quickVm.addWater(ml); viewModel.dismissDialog() }
            )
            QuickAction.CHECK_IN -> CheckInDialog(
                onDismiss = viewModel::dismissDialog,
                onSave = { trained, energy, mood, sleep, hitKcal, hitProtein, steps, notes ->
                    quickVm.saveCheckIn(trained, energy, mood, sleep, hitKcal, hitProtein, steps, notes)
                    viewModel.dismissDialog()
                }
            )
            else -> viewModel.dismissDialog()
        }
    }
}

@Composable
fun WaterQuickAdd(onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log water") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("How much did you drink?", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(250, 500, 750).forEach { ml ->
                        androidx.compose.material3.FilledTonalButton(onClick = { onAdd(ml) }) {
                            Text("+$ml")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
