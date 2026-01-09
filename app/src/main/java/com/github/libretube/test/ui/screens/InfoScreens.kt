package com.github.libretube.test.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.libretube.test.R
import com.github.libretube.test.ui.theme.LibreTubeTheme

@Composable
fun AboutScreen(
    onDonateClick: () -> Unit,
    onWebsiteClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onGithubClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onDeviceInfoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // We can reuse SettingItemRow-like structure or specialized LinkItem
        LinkItem(
            title = stringResource(R.string.donate),
            icon = R.drawable.ic_donate, 
            onClick = onDonateClick
        )
        LinkItem(
            title = stringResource(R.string.website),
            icon = R.drawable.ic_info, // Generic info icon
            onClick = onWebsiteClick
        )
        LinkItem(
            title = stringResource(R.string.translate),
            icon = R.drawable.ic_translate,
            onClick = onTranslateClick
        )
        LinkItem(
            title = stringResource(R.string.github),
            icon = R.drawable.ic_github,
            onClick = onGithubClick
        )

        HorizontalDivider()

        LinkItem(
            title = stringResource(R.string.license),
            icon = R.drawable.ic_license,
            onClick = onLicenseClick
        )
        
        LinkItem(
            title = stringResource(R.string.device_info),
            icon = R.drawable.ic_device,
            onClick = onDeviceInfoClick
        )
    }
}

@Composable
fun HelpScreen(
    onFaqClick: () -> Unit,
    onMatrixClick: () -> Unit,
    onMastodonClick: () -> Unit,
    onLemmyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        LinkItem(
            title = stringResource(R.string.faq),
            icon = R.drawable.ic_help,
            onClick = onFaqClick
        )
        LinkItem(
            title = stringResource(R.string.matrix),
            icon = R.drawable.ic_matrix,
            onClick = onMatrixClick
        )
        LinkItem(
            title = stringResource(R.string.mastodon),
            icon = R.drawable.ic_mastodon,
            onClick = onMastodonClick
        )
        LinkItem(
            title = stringResource(R.string.lemmy),
            icon = R.drawable.ic_lemmy,
            onClick = onLemmyClick
        )
    }
}

@Composable
fun LinkItem(
    title: String,
    icon: Int,
    onClick: () -> Unit
) {
    // Reusing the style from SettingsItemRow directly here to avoid circular dependency or extract to common
    // For now, duplicate standard Row logic for simplicity in this file
    SettingItemRow(
        item = SettingItem("link", 0, null, icon), // Hacky reuse?
        customSummary = null
    ) {
        onClick()
    }
}

// Better: create a distinct SimpleListItem Composable
// Responding to design: Just use SettingItemRow but we need a valid SettingItem. 
// Refactoring SettingsScreen to expose SettingItemRow publicly (passed preview check).
