package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.github.libretube.test.api.MediaServiceRepository
import com.github.libretube.test.api.obj.Channel
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.extensions.TAG
import com.github.libretube.test.extensions.toastFromMainDispatcher
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.screens.ChannelScreen
import com.github.libretube.test.ui.sheets.ChannelOptionsBottomSheet
import com.github.libretube.test.ui.sheets.VideoOptionsBottomSheet
import com.github.libretube.test.ui.theme.LibreTubeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelFragment : Fragment() {
    private val args by navArgs<ChannelFragmentArgs>()
    
    private var channelId: String? = null
    private var channelName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channelName = args.channelName
            ?.replace("/c/", "")
            ?.replace("/user/", "")
        channelId = args.channelId
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LibreTubeTheme {
                    var channelData by remember { mutableStateOf<Channel?>(null) }
                    var isLoading by remember { mutableStateOf(true) }
                    var isSubscribed by remember { mutableStateOf(false) }

                    // Fetch channel data
                    LaunchedEffect(Unit) {
                        fetchChannel { channel ->
                            channelData = channel
                            isLoading = false
                        }
                    }

                    ChannelScreen(
                        channelData = channelData,
                        isLoading = isLoading,
                        onRefresh = {
                            isLoading = true
                            lifecycleScope.launch {
                                fetchChannel { channel ->
                                    channelData = channel
                                    isLoading = false
                                }
                            }
                        },
                        onShowOptions = {
                            channelData?.let { channel ->
                                ChannelOptionsBottomSheet().apply {
                                    arguments = bundleOf(
                                        IntentData.channelId to channel.id,
                                        IntentData.channelName to channel.name,
                                        IntentData.isSubscribed to isSubscribed
                                    )
                                }.show(childFragmentManager)
                            }
                        },
                        onVideoClick = { streamItem ->
                            NavigationHelper.navigateVideo(requireContext(), streamItem.url)
                        },
                        onVideoLongClick = { streamItem ->
                            VideoOptionsBottomSheet().apply {
                                arguments = bundleOf(IntentData.streamItem to streamItem)
                            }.show(childFragmentManager, VideoOptionsBottomSheet::class.java.name)
                        }
                    )
                }
            }
        }
    }

    private suspend fun fetchChannel(onSuccess: (Channel) -> Unit) {
        try {
            val response = withContext(Dispatchers.IO) {
                if (channelId != null) {
                    MediaServiceRepository.instance.getChannel(channelId!!)
                } else {
                    MediaServiceRepository.instance.getChannelByName(channelName!!)
                }
            }
            
            // Update channel ID if loaded by name
            channelId = response.id
            channelName = response.name
            
            onSuccess(response)
        } catch (e: Exception) {
            Log.e(TAG(), e.stackTraceToString())
            context?.toastFromMainDispatcher(e.localizedMessage.orEmpty())
        }
    }
}
