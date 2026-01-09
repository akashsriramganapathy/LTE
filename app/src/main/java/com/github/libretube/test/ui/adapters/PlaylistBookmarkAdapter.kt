package com.github.libretube.test.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import com.github.libretube.test.R
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.databinding.LibraryShelfItemBinding
import com.github.libretube.test.databinding.LibraryListItemBinding
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.db.obj.PlaylistBookmark
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.helpers.ImageHelper
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.adapters.callbacks.DiffUtilItemCallback
import com.github.libretube.test.ui.base.BaseActivity
import com.github.libretube.test.ui.sheets.PlaylistOptionsBottomSheet
import com.github.libretube.test.ui.viewholders.PlaylistBookmarkViewHolder
import com.github.libretube.test.ui.viewholders.PlaylistBookmarkListViewHolder
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlaylistBookmarkAdapter(
    private val isGrid: Boolean = true
) : ListAdapter<PlaylistBookmark, RecyclerView.ViewHolder>(
    DiffUtilItemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.playlistId == newItem.playlistId }
    )
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
            PlaylistBookmarkViewHolder(binding)
        } else {
            val binding = LibraryListItemBinding.inflate(layoutInflater, parent, false)
            PlaylistBookmarkListViewHolder(binding)
        }
    }

    private fun showPlaylistOptions(context: Context, bookmark: PlaylistBookmark) {
        val sheet = PlaylistOptionsBottomSheet()
        sheet.arguments = bundleOf(
            IntentData.playlistId to bookmark.playlistId,
            IntentData.playlistName to bookmark.playlistName,
            IntentData.playlistType to PlaylistType.PUBLIC
        )
        sheet.show(
            (context as BaseActivity).supportFragmentManager
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val bookmark = getItem(position)

        if (holder is PlaylistBookmarkViewHolder) {
            with(holder.binding) {
                var isBookmarked = true

                ImageHelper.loadImage(bookmark.thumbnailUrl, playlistThumbnail)
                playlistTitle.text = bookmark.playlistName
                playlistDescription.text = bookmark.uploader
                videoCount.text = bookmark.videos.toString()

                setupBookmarkClick(bookmarkPlaylist, bookmark)
                bookmarkPlaylist.isVisible = true

                root.setOnClickListener {
                    NavigationHelper.navigatePlaylist(
                        root.context,
                        bookmark.playlistId,
                        PlaylistType.PUBLIC
                    )
                }

                if (!isReorderMode) {
                    root.setOnLongClickListener {
                        showPlaylistOptions(root.context, bookmark)
                        true
                    }
                } else {
                    root.setOnLongClickListener(null)
                }
            }
        } else if (holder is PlaylistBookmarkListViewHolder) {
            with(holder.binding) {
                var isBookmarked = true

                ImageHelper.loadImage(bookmark.thumbnailUrl, thumbnail)
                itemTitle.text = bookmark.playlistName
                itemUploader.text = root.context.getString(R.string.videoCount, bookmark.videos)
                videoCount.text = bookmark.videos.toString()
                
                // Note: ListItem layout has item_options but shelf has bookmark icon.
                // For bookmarks list, users might want to remove bookmark.
                // We'll map item_options to the long-press menu which handles deletion/options,
                // or we could add a specific bookmark toggle if needed. 
                // Given the layout, we'll use item_options for the menu.

                root.setOnClickListener {
                    NavigationHelper.navigatePlaylist(
                        root.context,
                        bookmark.playlistId,
                        PlaylistType.PUBLIC
                    )
                }

                itemOptions.setOnClickListener {
                    if (!isReorderMode) showPlaylistOptions(root.context, bookmark)
                }

                if (!isReorderMode) {
                    root.setOnLongClickListener {
                        showPlaylistOptions(root.context, bookmark)
                        true
                    }
                } else {
                    root.setOnLongClickListener(null)
                }
            }
        }
    }

    private fun setupBookmarkClick(bookmarkView: android.widget.ImageView, bookmark: PlaylistBookmark) {
        var isBookmarked = true
        bookmarkView.setOnClickListener {
            isBookmarked = !isBookmarked
            bookmarkView.setImageResource(
                if (isBookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outlined
            )
            CoroutineScope(Dispatchers.IO).launch {
                if (!isBookmarked) {
                    DatabaseHolder.Database.playlistBookmarkDao()
                        .deleteById(bookmark.playlistId)
                } else {
                    DatabaseHolder.Database.playlistBookmarkDao().insert(bookmark)
                }
            }
        }
    }
    private var reorderList: MutableList<PlaylistBookmark>? = null
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

    override fun getItem(position: Int): PlaylistBookmark {
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
    
    fun getItems(): List<PlaylistBookmark> {
        return reorderList?.toList() ?: currentList
    }
}

