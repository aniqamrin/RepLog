package com.replog.app.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replog.app.data.local.AiMessageEntity
import com.replog.app.domain.logic.InsightsGenerator
import com.replog.app.ui.components.EmptyState
import com.replog.app.ui.components.RpCard
import com.replog.app.ui.components.SectionHeader

private val SUGGESTIONS = listOf(
    "What did I eat today?",
    "How much protein do I have left?",
    "I have 600 calories left, what can I eat?",
    "Give me a high protein dinner.",
    "How consistent have I been?",
    "Compare this month with last month."
)

@Composable
fun AiScreen(
    onScanFood: () -> Unit,
    viewModel: AiViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("REPLOG AI", style = MaterialTheme.typography.displaySmall)
                Text(
                    if (state.backendConfigured) "Connected to backend AI"
                    else "Local coach — uses your logged data",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { DailySummaryCard(state.dailySummary) }
        if (state.insights.isNotEmpty()) {
            item { InsightsCard(state.insights) }
        }
        items(state.messages.size) { i -> ChatBubble(state.messages[i]) }
        if (state.sending) {
            item { TypingIndicator() }
        }
        if (state.messages.isEmpty() && !state.sending) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("TRY ASKING")
                    SUGGESTIONS.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { s ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { viewModel.send(s) }
                                        .padding(12.dp)
                                ) {
                                    Text(s, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(
                        "📷 Scan a meal instead",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onScanFood).padding(top = 4.dp)
                    )
                }
            }
        }
        item { ChatInput(onSend = viewModel::send, enabled = !state.sending) }
        item { Spacer(Modifier.height(64.dp)) }
    }
}

@Composable
private fun DailySummaryCard(summary: String?) {
    RpCard(Modifier.fillMaxWidth()) {
        Column {
            SectionHeader("TODAY'S AI SUMMARY")
            if (summary == null) {
                EmptyState("Crunching your data…", "Open this tab again in a moment.")
            } else {
                Text(summary, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun InsightsCard(insights: List<InsightsGenerator.Insight>) {
    RpCard(Modifier.fillMaxWidth()) {
        Column {
            SectionHeader("INSIGHTS")
            insights.forEach { insight ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.widthIn(min = 8.dp))
                    Text(insight.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: AiMessageEntity) {
    val isUser = message.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(message.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TypingIndicator() {
    Text(
        "…",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
private fun ChatInput(onSend: (String) -> Unit, enabled: Boolean) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {}
    Column(Modifier.fillMaxWidth().imePadding()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Ask your coach anything…") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            trailingIcon = {
                Text(
                    "Send",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled && text.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(enabled = enabled && text.isNotBlank()) {
                            onSend(text); text = ""
                        }
                        .padding(horizontal = 12.dp)
                )
            }
        )
    }
}
