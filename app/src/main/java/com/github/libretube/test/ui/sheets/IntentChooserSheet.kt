package com.github.libretube.test.ui.sheets

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.databinding.BottomSheetBinding
import com.github.libretube.test.helpers.IntentHelper
import com.github.libretube.test.ui.adapters.IntentChooserAdapter

class IntentChooserSheet : BaseBottomSheet() {
    private lateinit var url: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        url = arguments?.getString(IntentData.url)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = BottomSheetBinding.bind(view)
        val packages = IntentHelper.getResolveInfo(requireContext(), url)
        binding.optionsRecycler.layoutManager = GridLayoutManager(context, 3)
        binding.optionsRecycler.adapter = IntentChooserAdapter(packages, url)
    }
}

