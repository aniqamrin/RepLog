package com.replog.app.feature.social

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replog.app.ui.components.EmptyState
import com.replog.app.ui.components.KeyValueRow
import com.replog.app.ui.components.RpCard
import com.replog.app.ui.components.SectionHeader
import com.replog.app.util.Format

@Composable
fun SocialScreen(viewModel: SocialViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Social", style = MaterialTheme.typography.displaySmall)
        }
        if (!state.signedIn) {
            item {
                RpCard(Modifier.fillMaxWidth()) {
                    Column {
                        SectionHeader("FRIENDS & LEADERBOARDS")
                        Text(
                            "Sign in from Settings (ACCOUNT & SYNC) to add friends, see who trained today, " +
                                "and compete on lifting volume.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            return@LazyColumn
        }
        if (state.loading && state.friends.isEmpty() && state.leaderboard.isEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            }
        }
        state.message?.let { msg ->
            item {
                RpCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(msg, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
                    }
                }
            }
        }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("ADD FRIEND")
                    OutlinedTextField(
                        value = state.addEmail,
                        onValueChange = viewModel::updateAddEmail,
                        label = { Text("Friend's email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::sendRequest,
                        enabled = !state.busy && state.addEmail.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Send friend request") }
                    Text(
                        "Friends must have a REPLOG account and accept your request.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        if (state.incoming.isNotEmpty()) {
            item { RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("REQUESTS")
                    state.incoming.forEach { req ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(req.name, style = MaterialTheme.typography.bodyLarge)
                                Text(req.email, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AssistChip(onClick = { viewModel.respond(req.id, true) }, label = { Text("Accept") })
                            Spacer(Modifier.padding(start = 6.dp))
                            TextButton(onClick = { viewModel.respond(req.id, false) }) { Text("Decline") }
                        }
                    }
                }
            } }
        }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("LEADERBOARD — WEEKLY VOLUME (kg)")
                    if (state.leaderboard.isEmpty()) {
                        Text(
                            "No entries yet. Log strength workouts and sync to appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.leaderboard.forEachIndexed { index, entry ->
                            val medal = when (index) {
                                0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "#${index + 1}"
                            }
                            KeyValueRow(
                                "$medal ${entry.name}${if (entry.is_me) " (you)" else ""}" +
                                    if (entry.trained_today) "  ✓ today" else "",
                                Format.thousands(entry.week_volume.toInt()) + " kg"
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Volume = weight × reps across all logged sets. Ranked this week.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("MY FRIENDS (${state.friends.size})")
                    if (state.friends.isEmpty()) {
                        EmptyState(
                            "No friends yet",
                            "Send a request above — then compete on who trained today and weekly lifting volume."
                        )
                    } else {
                        state.friends.forEach { friend ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        friend.name + if (friend.trained_today) " ✓" else "",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "Today: ${Format.thousands(friend.volume_today.toInt())} kg · Week: " +
                                            Format.thousands(friend.week_volume.toInt()) + " kg",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.removeFriend(friend.id) }) {
                                    Text("Remove")
                                }
                            }
                        }
                        Text(
                            "✓ = trained today",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (state.outgoing.isNotEmpty()) {
            item {
                RpCard(Modifier.fillMaxWidth()) {
                    Column {
                        SectionHeader("PENDING SENT")
                        state.outgoing.forEach {
                            Text(it.email, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(64.dp)) }
    }
}
