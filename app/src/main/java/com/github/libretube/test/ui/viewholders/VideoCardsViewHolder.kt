package com.github.libretube.test.ui.viewholders

import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.test.databinding.AllCaughtUpRowBinding
import com.github.libretube.test.databinding.TrendingRowBinding

class VideoCardsViewHolder : RecyclerView.ViewHolder {
    var trendingRowBinding: TrendingRowBinding? = null
    var allCaughtUpBinding: AllCaughtUpRowBinding? = null

    constructor(binding: TrendingRowBinding) : super(binding.root) {
        trendingRowBinding = binding
    }

    constructor(binding: AllCaughtUpRowBinding) : super(binding.root) {
        allCaughtUpBinding = binding
    }
}

