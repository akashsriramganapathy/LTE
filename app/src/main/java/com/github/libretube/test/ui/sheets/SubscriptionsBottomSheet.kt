package com.github.libretube.test.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.fragment.app.activityViewModels
import com.github.libretube.test.R
import com.github.libretube.test.api.obj.Subscription
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.extensions.toID
import com.github.libretube.test.extensions.formatShort
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.ui.models.EditChannelGroupsModel
import com.github.libretube.test.ui.models.SubscriptionsViewModel
import com.github.libretube.test.ui.theme.LibreTubeTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SubscriptionsBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: SubscriptionsViewModel by activityViewModels()
    private val channelGroupsModel: EditChannelGroupsModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LibreTubeTheme {
                    SubscriptionsSheetContent()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SubscriptionsSheetContent() {
        val subscriptions by viewModel.subscriptions.observeAsState(emptyList())
        val channelGroups by channelGroupsModel.groups.observeAsState(emptyList())
        
        // Ensure subscriptions is not null
        val safestSubscriptions = subscriptions ?: emptyList()

        var selectedLocalGroup by remember { 
            mutableIntStateOf(PreferenceHelper.getInt(PreferenceKeys.SELECTED_CHANNEL_GROUP, 0)) 
        }
        var searchQuery by remember { mutableStateOf("") }
        var isSearchVisible by remember { mutableStateOf(false) }

        // Filter valid groups
        val validGroups = remember(channelGroups) {
            listOf(getString(R.string.all)) + (channelGroups?.map { it.name } ?: emptyList())
        }
        
        // Filter logic
        val filteredSubscriptions = remember(safestSubscriptions, selectedLocalGroup, searchQuery, channelGroups) {
            val groupFiltered = if (selectedLocalGroup == 0) {
                safestSubscriptions
            } else {
                val group = channelGroups?.getOrNull(selectedLocalGroup - 1)
                if (group != null) {
                    safestSubscriptions.filter { sub -> group.channels.contains(sub.url.toID()) }
                } else {
                    safestSubscriptions
                }
            }
            
            if (searchQuery.isBlank()) {
                groupFiltered
            } else {
                groupFiltered.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Header
            if (isSearchVisible) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { 
                            isSearchVisible = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search")
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { /* Could toggle groups logic differently */ }) {
                        Text(
                            text = if (selectedLocalGroup == 0) 
                                "${stringResource(R.string.subscriptions)} (${safestSubscriptions.size})"
                            else 
                                "${validGroups.getOrElse(selectedLocalGroup) { "" }} (${filteredSubscriptions.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Row {
                        IconButton(onClick = { isSearchVisible = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_hint))
                        }
                        if (selectedLocalGroup != 0) {
                            IconButton(onClick = {
                                val group = channelGroups?.getOrNull(selectedLocalGroup - 1)
                                if (group != null) {
                                  channelGroupsModel.groupToEdit = group
                                  EditChannelGroupSheet().show(parentFragmentManager, null)
                                }
                            }) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_edit),
                                    contentDescription = "Edit Group"
                                )
                            }
                        }
                    }
                }
            }
            
            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp) // Limit height
            ) {
                items(filteredSubscriptions, key = { it.url }) { sub ->
                    SubscriptionItemRow(
                        subscription = sub,
                        onClick = {
                            NavigationHelper.navigateChannel(requireContext(), sub.url)
                            dismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionItemRow(
    subscription: Subscription,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        coil3.compose.AsyncImage(
            model = subscription.avatar,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = subscription.name, // name is non-nullable in data class
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            // Subscriber count not available in Subscription object
        }
    }
}
