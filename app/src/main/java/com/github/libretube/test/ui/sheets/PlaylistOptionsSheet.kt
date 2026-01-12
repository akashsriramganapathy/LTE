package com.github.libretube.test.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.libretube.test.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistOptionsSheet(
    onPlayBackground: () -> Unit,
    onDownload: () -> Unit,
    onSort: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onChangeDescription: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            OptionItem(
                text = stringResource(R.string.background_mode),
                icon = painterResource(R.drawable.ic_headphones),
                onClick = {
                    onPlayBackground()
                    onDismissRequest()
                }
            )
            OptionItem(
                text = stringResource(R.string.download),
                icon = painterResource(R.drawable.ic_download),
                onClick = {
                    onDownload()
                    onDismissRequest()
                }
            )
            OptionItem(
                text = "Sort", // TODO: Add string resource
                icon = Icons.Default.List,
                onClick = {
                    onSort()
                    onDismissRequest()
                }
            )
            OptionItem(
                text = "Export", // TODO: Add string resource
                icon = Icons.Default.Share,
                onClick = {
                    onExport()
                    onDismissRequest()
                }
            )
            OptionItem(
                text = stringResource(R.string.renamePlaylist),
                icon = Icons.Default.Edit, // Or generic edit icon
                onClick = {
                    onRename()
                    onDismissRequest()
                }
            )
            OptionItem(
                text = stringResource(R.string.change_playlist_description),
                icon = Icons.Default.Info, // Or generic info/edit icon
                onClick = {
                    onChangeDescription()
                    onDismissRequest()
                }
            )
            OptionItem(
                text = stringResource(R.string.deletePlaylist),
                icon = Icons.Default.Delete,
                color = MaterialTheme.colorScheme.error,
                onClick = {
                    onDelete()
                    onDismissRequest()
                }
            )
        }
    }
}

@Composable
private fun OptionItem(
    text: String,
    icon: Any,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon is ImageVector) {
            Icon(imageVector = icon, contentDescription = null, tint = color)
        } else if (icon is Painter) {
            Icon(painter = icon, contentDescription = null, tint = color)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
