package com.replog.app.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replog.app.domain.model.Goals
import com.replog.app.domain.model.GoalType
import com.replog.app.ui.components.KeyValueRow
import com.replog.app.ui.components.RpCard
import com.replog.app.ui.components.SectionHeader

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val auth by viewModel.authState.collectAsStateWithLifecycle()

    var goals by remember { mutableStateOf(Goals()) }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        goals = viewModel.loadGoals()
        name = viewModel.currentName()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.displaySmall)
        }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("PROFILE")
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.saveName(name) }, enabled = name.isNotBlank()) {
                        Text("Save name")
                    }
                }
            }
        }
        item { GoalsCard(goals) { updated -> goals = updated; viewModel.saveGoals(updated) } }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("APPEARANCE")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dark mode", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = settings.themeDark, onCheckedChange = viewModel::setThemeDark)
                    }
                }
            }
        }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("NOTIFICATIONS")
                    ToggleRow("Food logging reminders", settings.remindFood) {
                        viewModel.setReminder("remind_food", it)
                    }
                    ToggleRow("Workout reminders", settings.remindWorkout) {
                        viewModel.setReminder("remind_workout", it)
                    }
                    ToggleRow("Water reminders", settings.remindWater) {
                        viewModel.setReminder("remind_water", it)
                    }
                    ToggleRow("Daily check-in reminder", settings.remindCheckIn) {
                        viewModel.setReminder("remind_checkin", it)
                    }
                }
            }
        }
        item { AuthCard(auth, viewModel) }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("DATA")
                    OutlinedButton(onClick = viewModel::regenerateDemoData, modifier = Modifier.fillMaxWidth()) {
                        Text("Regenerate demo data (6 months)")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = viewModel::clearAiChat, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear AI conversation history")
                    }
                }
            }
        }
        item {
            Text(
                "REPLOG — Show up. Log it. Get better.\nLocal-first fitness tracking. Data stays on your device unless you sign in to sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { Spacer(Modifier.height(64.dp)) }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun GoalsCard(goals: Goals, onSave: (Goals) -> Unit) {
    var draft by remember(goals) { mutableStateOf(goals) }

    RpCard(Modifier.fillMaxWidth()) {
        Column {
            SectionHeader("GOALS")
            Text("Focus", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                GoalType.entries.forEach { type ->
                    FilterChip(
                        selected = draft.goalType == type,
                        onClick = { draft = draft.copy(goalType = type) },
                        label = { Text(type.label) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            NumberGoalRow("Calorie target (kcal)", draft.calorieTarget.toString()) {
                v -> draft = draft.copy(calorieTarget = v.toIntOrNull() ?: draft.calorieTarget)
            }
            NumberGoalRow("Protein target (g)", draft.proteinTargetG.toString()) {
                v -> draft = draft.copy(proteinTargetG = v.toIntOrNull() ?: draft.proteinTargetG)
            }
            NumberGoalRow("Carbs target (g)", draft.carbsTargetG.toString()) {
                v -> draft = draft.copy(carbsTargetG = v.toIntOrNull() ?: draft.carbsTargetG)
            }
            NumberGoalRow("Fat target (g)", draft.fatTargetG.toString()) {
                v -> draft = draft.copy(fatTargetG = v.toIntOrNull() ?: draft.fatTargetG)
            }
            NumberGoalRow("Water target (ml)", draft.waterTargetMl.toString()) {
                v -> draft = draft.copy(waterTargetMl = v.toIntOrNull() ?: draft.waterTargetMl)
            }
            NumberGoalRow("Step target", draft.stepTarget.toString()) {
                v -> draft = draft.copy(stepTarget = v.toIntOrNull() ?: draft.stepTarget)
            }
            NumberGoalRow("Workout days / week", draft.workoutDaysPerWeek.toString()) {
                v -> draft = draft.copy(workoutDaysPerWeek = v.toIntOrNull() ?: draft.workoutDaysPerWeek)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save goals")
            }
        }
    }
}

@Composable
private fun NumberGoalRow(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.padding(start = 12.dp).width(110.dp),
            singleLine = true
        )
    }
}

@Composable
private fun AuthCard(auth: AuthUiState, viewModel: SettingsViewModel) {
    RpCard(Modifier.fillMaxWidth()) {
        Column {
            SectionHeader("ACCOUNT & SYNC")
            if (auth.loggedInEmail != null) {
                KeyValueRow("Signed in as", auth.loggedInEmail!!)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
                    Text("Log out")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = auth.mode == "login", onClick = { viewModel.updateAuth { it.copy(mode = "login") } }, label = { Text("Sign in") })
                    FilterChip(selected = auth.mode == "register", onClick = { viewModel.updateAuth { it.copy(mode = "register") } }, label = { Text("Register") })
                }
                Spacer(Modifier.height(8.dp))
                if (auth.mode == "register") {
                    OutlinedTextField(
                        value = auth.name,
                        onValueChange = { v -> viewModel.updateAuth { it.copy(name = v) } },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = auth.email,
                    onValueChange = { v -> viewModel.updateAuth { it.copy(email = v) } },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = auth.password,
                    onValueChange = { v -> viewModel.updateAuth { it.copy(password = v) } },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                auth.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = viewModel::submitAuth,
                    enabled = !auth.busy && auth.email.isNotBlank() && auth.password.length >= 6,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (auth.busy) "Please wait…" else if (auth.mode == "register") "Create account" else "Sign in")
                }
                Text(
                    "Optional. The app works fully offline; signing in enables sync and cloud AI.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
