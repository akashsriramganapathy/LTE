package com.github.libretube.test.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.libretube.test.R
import com.github.libretube.test.ui.theme.LibreTubeTheme

data class SettingItem(
    val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int? = null,
    @DrawableRes val icon: Int
)

val MainSettingsItems = listOf(
    SettingItem("general", R.string.general, R.string.general_summary, R.drawable.ic_settings),
    SettingItem("downloads", R.string.downloads, R.string.download_path_summary, R.drawable.ic_download),
    SettingItem("appearance", R.string.appearance, R.string.appearance_summary, R.drawable.ic_color),
    SettingItem("player", R.string.player, R.string.player_summary, R.drawable.ic_play_filled),
    SettingItem("content", R.string.content_settings, R.string.content_settings_summary, R.drawable.ic_awesome),
    SettingItem("data_backup", R.string.data_backup, R.string.data_backup_summary, R.drawable.ic_backup),
    SettingItem("notifications", R.string.notifications, R.string.notification_summary, R.drawable.ic_notification)
)

val AboutSettingsItems = listOf(
    SettingItem("update", R.string.version, R.string.update_summary, R.drawable.ic_info),
    SettingItem("view_logs", R.string.view_logs, null, R.drawable.ic_info)
)

@Composable
fun SettingsScreen(
    onItemClick: (String) -> Unit,
    versionName: String = ""
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(MainSettingsItems) { item ->
            SettingItemRow(item = item, onClick = { onItemClick(item.key) })
        }

        item {
           Text(
               text = stringResource(R.string.about),
               style = MaterialTheme.typography.titleSmall,
               color = MaterialTheme.colorScheme.primary,
               modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
           )
        }

        items(AboutSettingsItems) { item ->
            // Special handling for Version summary to include version name
            val summary = if (item.key == "update" && versionName.isNotEmpty()) {
                stringResource(R.string.version_format, versionName)
            } else {
                 item.summary?.let { stringResource(it) }
            }
            
            SettingItemRow(
                item = item,
                customSummary = summary,
                onClick = { onItemClick(item.key) }
            )
        }
    }
}

@Composable
fun SettingItemRow(
    item: SettingItem,
    customSummary: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = item.icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(
                text = stringResource(id = item.title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            val summaryResource = item.summary
            val summaryText = customSummary ?: if (summaryResource != null) stringResource(summaryResource) else null
            if (summaryText != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    LibreTubeTheme {
        SettingsScreen(onItemClick = {}, versionName = "0.1.0")
    }
}
