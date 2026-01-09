package com.github.libretube.test.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.ListAdapter
import com.github.libretube.test.R
import com.github.libretube.test.api.obj.Playlists
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.databinding.LibraryShelfItemBinding
import com.github.libretube.test.databinding.LibraryListItemBinding
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.helpers.ImageHelper
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.adapters.callbacks.DiffUtilItemCallback
import com.github.libretube.test.ui.base.BaseActivity
import com.github.libretube.test.ui.sheets.PlaylistOptionsBottomSheet
import com.github.libretube.test.ui.sheets.PlaylistOptionsBottomSheet.Companion.PLAYLIST_OPTIONS_REQUEST_KEY
import com.github.libretube.test.ui.viewholders.PlaylistsViewHolder
import com.github.libretube.test.ui.viewholders.PlaylistsListViewHolder
import androidx.recyclerview.widget.RecyclerView

class PlaylistsAdapter(
    private val playlistType: PlaylistType,
    private val isGrid: Boolean = true
) : ListAdapter<Playlists, RecyclerView.ViewHolder>(
    DiffUtilItemCallback(areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id })
) {
    companion object {
        const val VIEW_TYPE_GRID = 0
        const val VIEW_TYPE_LIST = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGrid) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_GRID) {
            val binding = LibraryShelfItemBinding.inflate(layoutInflater, parent, false)
            PlaylistsViewHolder(binding)
        } else {
            val binding = LibraryListItemBinding.inflate(layoutInflater, parent, false)
            PlaylistsListViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val playlist = getItem(position)
        
        if (holder is PlaylistsViewHolder) {
            holder.binding.apply {
                if (playlist.thumbnail.orEmpty().split("/").size <= 4) {
                    playlistThumbnail.setImageResource(R.drawable.ic_empty_playlist)
                    playlistThumbnail
                        .setBackgroundColor(com.google.android.material.R.attr.colorSurface)
                } else {
                    ImageHelper.loadImage(playlist.thumbnail, playlistThumbnail)
                }
                playlistTitle.text = playlist.name
                playlistDescription.text = playlist.shortDescription

                videoCount.text = playlist.videos.toString()

                root.setOnClickListener {
                    NavigationHelper.navigatePlaylist(root.context, playlist.id, playlistType)
                }
                
                setupLongClick(root, playlist, position)
            }
        } else if (holder is PlaylistsListViewHolder) {
            holder.binding.apply {
                if (playlist.thumbnail.orEmpty().split("/").size <= 4) {
                    thumbnail.setImageResource(R.drawable.ic_empty_playlist)
                    thumbnail.setBackgroundColor(com.google.android.material.R.attr.colorSurface)
                } else {
                    ImageHelper.loadImage(playlist.thumbnail, thumbnail)
                }
                itemTitle.text = playlist.name
                itemUploader.text = root.context.getString(R.string.videoCount, playlist.videos)
                videoCount.text = playlist.videos.toString()
                
                // Reuse existing navigation logic
                root.setOnClickListener {
                    NavigationHelper.navigatePlaylist(root.context, playlist.id, playlistType)
                }

                itemOptions.setOnClickListener {
                    if (!isReorderMode) showOptions(root.context, playlist, position)
                }
                
                if (!isReorderMode) {
                    root.setOnLongClickListener {
                        showOptions(root.context, playlist, position)
                        true
                    }
                } else {
                    root.setOnLongClickListener(null)
                }
            }
        }
    }

    private fun setupLongClick(root: android.view.View, playlist: Playlists, position: Int) {
        if (!isReorderMode) {
            root.setOnLongClickListener {
                showOptions(root.context, playlist, position)
                true
            }
        } else {
            root.setOnLongClickListener(null)
        }
    }

    private fun showOptions(context: android.content.Context, playlist: Playlists, position: Int) {
        val fragmentManager = (context as BaseActivity).supportFragmentManager
        
        fragmentManager.setFragmentResultListener(
            PLAYLIST_OPTIONS_REQUEST_KEY,
            context
        ) { _, resultBundle ->
            val newPlaylistDescription = resultBundle.getString(IntentData.playlistDescription)
            val newPlaylistName = resultBundle.getString(IntentData.playlistName)
            val isPlaylistToBeDeleted = resultBundle.getBoolean(IntentData.playlistTask)

            if (newPlaylistDescription != null) {
                playlist.shortDescription = newPlaylistDescription
                notifyItemChanged(position)
            }
            if (newPlaylistName != null) {
                playlist.name = newPlaylistName
                notifyItemChanged(position)
            }
            if (isPlaylistToBeDeleted) {
                onDelete(position)
            }
        }

        val playlistOptionsDialog = PlaylistOptionsBottomSheet()
        playlistOptionsDialog.arguments = bundleOf(
            IntentData.playlistId to playlist.id!!,
            IntentData.playlistName to playlist.name!!,
            IntentData.playlistType to playlistType
        )
        playlistOptionsDialog.show(
            fragmentManager,
            PlaylistOptionsBottomSheet::class.java.name
        )
    }

    private var reorderList: MutableList<Playlists>? = null
    var isReorderMode: Boolean = false
        set(value) {
            field = value
            if (value) {
                reorderList = currentList.toMutableList()
            } else {
                reorderList = null
            }
            notifyDataSetChanged()
        }

    override fun getItem(position: Int): Playlists {
        return reorderList?.getOrNull(position) ?: super.getItem(position)
    }

    override fun getItemCount(): Int {
        return reorderList?.size ?: super.getItemCount()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        val list = reorderList ?: return
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                java.util.Collections.swap(list, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                java.util.Collections.swap(list, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }
    
    fun getItems(): List<Playlists> {
        return reorderList?.toList() ?: currentList
    }

    private fun onDelete(position: Int) {
        val newList = currentList.toMutableList().also {
            it.removeAt(position)
        }
        submitList(newList)
    }
}

