package com.github.libretube.test.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.libretube.test.helpers.RoomPreferenceDataStore
import com.github.libretube.test.ui.models.PreferenceItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGroupScreen(
    title: String,
    items: List<PreferenceItem>,
    onNavigate: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            when (item) {
                is PreferenceItem.Switch -> SwitchPreferenceWidget(item)
                is PreferenceItem.List -> ListPreferenceWidget(item)
                is PreferenceItem.Clickable -> ClickablePreferenceWidget(item, onNavigate)
                is PreferenceItem.Category -> CategoryHeaderWidget(item)
                is PreferenceItem.Custom -> item.content()
            }
        }
    }
}

@Composable
fun PreferenceRow(
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    widget: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (widget != null) {
            Box(modifier = Modifier.padding(start = 16.dp)) {
                widget()
            }
        }
    }
}

@Composable
private fun SwitchPreferenceWidget(item: PreferenceItem.Switch) {
    val checked by RoomPreferenceDataStore.getBooleanFlow(item.key, item.defaultValue)
        .collectAsStateWithLifecycle(initialValue = item.defaultValue)

    PreferenceRow(
        title = item.title,
        summary = item.summary,
        onClick = { RoomPreferenceDataStore.putBoolean(item.key, !checked) },
        widget = {
            Switch(
                checked = checked,
                onCheckedChange = { RoomPreferenceDataStore.putBoolean(item.key, it) }
            )
        }
    )
}

@Composable
private fun ListPreferenceWidget(item: PreferenceItem.List) {
    val value by RoomPreferenceDataStore.getStringFlow(item.key, item.defaultValue)
        .collectAsStateWithLifecycle(initialValue = item.defaultValue)
    
    var showDialog by remember { mutableStateOf(false) }

    val currentLabel = item.entries.entries.find { it.value == value }?.key ?: value

    PreferenceRow(
        title = item.title,
        summary = item.summary ?: currentLabel,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(item.title) },
            text = {
                Column {
                    item.entries.forEach { (label, entryValue) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    RoomPreferenceDataStore.putString(item.key, entryValue)
                                    showDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = value == entryValue, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ClickablePreferenceWidget(item: PreferenceItem.Clickable, onNavigate: (String) -> Unit) {
    PreferenceRow(
        title = item.title,
        summary = item.summary,
        onClick = {
            item.onClick()
            item.key?.let { onNavigate(it) }
        }
    )
}

@Composable
private fun CategoryHeaderWidget(item: PreferenceItem.Category) {
    Text(
        text = item.title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}
