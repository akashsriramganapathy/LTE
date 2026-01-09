package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.libretube.test.R
import com.github.libretube.test.api.PlaylistsHelper
import com.github.libretube.test.databinding.FragmentLibraryListingBinding
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.ui.adapters.PlaylistBookmarkAdapter
import com.github.libretube.test.ui.adapters.PlaylistsAdapter
import com.github.libretube.test.ui.base.DynamicLayoutManagerFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.ui.models.LibraryViewModel
import com.github.libretube.test.ui.sheets.BaseBottomSheet

class LibraryListingFragment : DynamicLayoutManagerFragment(R.layout.fragment_library_listing) {
    private var _binding: FragmentLibraryListingBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<LibraryListingFragmentArgs>()
    private val viewModel: LibraryViewModel by activityViewModels()

    private val playlistsAdapter by lazy { 
        PlaylistsAdapter(PlaylistType.LOCAL, isGrid = false) 
    }
    private val bookmarkAdapter by lazy { 
        PlaylistBookmarkAdapter(isGrid = false) 
    }

    private var isReorderMode = false

    private val itemTouchHelper by lazy {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val type = Type.valueOf(args.type)
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition

                if (type == Type.PLAYLISTS) {
                    playlistsAdapter.onItemMove(from, to)
                } else {
                    bookmarkAdapter.onItemMove(from, to)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean {
                return isReorderMode
            }
        })
    }

    private fun toggleReorderMode() {
        isReorderMode = !isReorderMode
        val type = Type.valueOf(args.type)
        
        if (type == Type.PLAYLISTS) {
            playlistsAdapter.isReorderMode = isReorderMode
        } else {
            bookmarkAdapter.isReorderMode = isReorderMode
        }

        binding.toolbar.menu.findItem(R.id.action_reorder)?.apply {
            if (isReorderMode) {
                setIcon(R.drawable.ic_done)
                setTitle(R.string.save)
            } else {
                setIcon(R.drawable.ic_reorder)
                setTitle(R.string.reorder_playlist)
            }
        }
        
        binding.toolbar.menu.findItem(R.id.action_sort)?.isVisible = !isReorderMode
        binding.swipeRefresh.isEnabled = !isReorderMode

        if (!isReorderMode) {
            // Saved
            lifecycleScope.launch {
                if (type == Type.PLAYLISTS) {
                    val ids = playlistsAdapter.getItems().mapNotNull { it.id }
                    PlaylistsHelper.updateLocalPlaylistOrder(ids)
                } else {
                    val ids = bookmarkAdapter.getItems().map { it.playlistId }
                    PlaylistsHelper.updateBookmarkOrder(ids)
                }
                
                // Force custom sort
                PreferenceHelper.putString(PreferenceKeys.PLAYLISTS_ORDER, "custom")
                viewModel.refreshData()
            }
        }
    }

    enum class Type { PLAYLISTS, BOOKMARKS }

    override fun setLayoutManagers(gridItems: Int) {
        _binding?.listingRecView?.layoutManager = LinearLayoutManager(context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentLibraryListingBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        itemTouchHelper.attachToRecyclerView(binding.listingRecView)

        val type = Type.valueOf(args.type)
        binding.toolbar.title = when (type) {
            Type.PLAYLISTS -> getString(R.string.playlists)
            Type.BOOKMARKS -> getString(R.string.bookmarks)
        }

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        when (type) {
            Type.PLAYLISTS -> {
                binding.listingRecView.adapter = playlistsAdapter
                viewModel.playlists.observe(viewLifecycleOwner) {
                    playlistsAdapter.submitList(it)
                    binding.emptyState.isVisible = it.isEmpty()
                    binding.listingRecView.isVisible = it.isNotEmpty()
                }
            }
            Type.BOOKMARKS -> {
                binding.listingRecView.adapter = bookmarkAdapter
                viewModel.bookmarks.observe(viewLifecycleOwner) {
                    bookmarkAdapter.submitList(it)
                    binding.emptyState.isVisible = it.isEmpty()
                    binding.listingRecView.isVisible = it.isNotEmpty()
                }
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshData()
        }

        viewModel.isRefreshing.observe(viewLifecycleOwner) {
            binding.swipeRefresh.isRefreshing = it
        }

        viewModel.refreshData()

        binding.toolbar.inflateMenu(R.menu.menu_library_listing)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_reorder -> {
                    toggleReorderMode()
                    true
                }
                R.id.action_sort -> {
                    val sortOptions = resources.getStringArray(R.array.playlistSortingOptions)
                    val sortOptionValues = resources.getStringArray(R.array.playlistSortingOptionsValues)
                    
                    com.github.libretube.test.ui.sheets.BaseBottomSheet().apply {
                        setSimpleItems(sortOptions.toList()) { index ->
                            val value = sortOptionValues[index]
                            com.github.libretube.test.helpers.PreferenceHelper.putString(com.github.libretube.test.constants.PreferenceKeys.PLAYLISTS_ORDER, value)
                            viewModel.refreshData()
                        }
                    }.show(childFragmentManager)
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
