package com.github.libretube.test.ui.sheets

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.libretube.test.R
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.ui.adapters.ChaptersAdapter
import com.github.libretube.test.ui.models.ChaptersViewModel
import com.github.libretube.test.databinding.SimpleOptionsRecyclerBinding

class ChaptersBottomSheet : ExpandedBottomSheet(R.layout.simple_options_recycler) {
    private val viewModel: ChaptersViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = SimpleOptionsRecyclerBinding.bind(view)

        val duration = arguments?.getLong(IntentData.duration) ?: 0L

        val adapter = ChaptersAdapter(viewModel.chapters, duration) { position ->
            setFragmentResult(
                SEEK_TO_POSITION_REQUEST_KEY,
                bundleOf(IntentData.currentPosition to position)
            )
            dismiss()
        }
        binding.optionsRecycler.layoutManager = LinearLayoutManager(context)
        binding.optionsRecycler.adapter = adapter

        viewModel.currentChapterIndex.value?.let {
            binding.optionsRecycler.scrollToPosition(it)
            adapter.updateSelectedPosition(it)
        }
    }

    companion object {
        const val SEEK_TO_POSITION_REQUEST_KEY = "seek_to_position_request_key"
    }
}
