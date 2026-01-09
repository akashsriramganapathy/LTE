package com.github.libretube.test.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.motion.widget.TransitionAdapter
import android.util.Log
import com.github.libretube.test.R

class SingleViewTouchableMotionLayout(context: Context, attributeSet: AttributeSet? = null) :
    MotionLayout(context, attributeSet) {

    init {
        Log.e("TouchableMotionLayout", "SingleViewTouchableMotionLayout instantiated")
    }

    private val viewToDetectTouch: View? by lazy {
        Log.e("TouchableMotionLayout", "Lazy init viewToDetectTouch")
        findViewById<View>(R.id.main_container) ?: findViewById(R.id.audio_player_container)
    }

    private var miniPlayerControls: View? = null

    private val isAudioPlayer by lazy {
        viewToDetectTouch?.id == R.id.audio_player_container
    }

    fun setViewToDetectTouch(view: View) {
        Log.e("TouchableMotionLayout", "setViewToDetectTouch: $view")
    }

    fun setMiniPlayerControls(view: View) {
        Log.e("TouchableMotionLayout", "setMiniPlayerControls: $view")
        miniPlayerControls = view
    }

    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val transitionListenerList = mutableListOf<TransitionListener?>()
    private val swipeDownListener = mutableListOf<() -> Unit>()
    private val gestureDetector = GestureDetector(context, Listener())

    private var startedMinimized = false
    private var isStrictlyDownSwipe = true
    private var touchInitialY = 0f
    private var isTouchDownInsideHitArea = false
    private var shouldInterceptTouchEvent = false

    init {
        super.setTransitionListener(object : TransitionAdapter() {
            override fun onTransitionChange(p0: MotionLayout?, p1: Int, p2: Int, p3: Float) {
                transitionListenerList.filterNotNull()
                    .forEach { it.onTransitionChange(p0, p1, p2, p3) }
            }

            override fun onTransitionCompleted(p0: MotionLayout?, p1: Int) {
                transitionListenerList.filterNotNull()
                    .forEach { it.onTransitionCompleted(p0, p1) }
            }
        })
    }

    override fun setTransitionListener(listener: TransitionListener?) {
        addTransitionListener(listener)
    }

    override fun addTransitionListener(listener: TransitionListener?) {
        transitionListenerList += listener
    }

    private inner class Listener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            Log.e("TouchableMotionLayout", "onSingleTapUp: progress=$progress")
            if (progress > 0.95f && currentState == R.id.end) {
                Log.e("TouchableMotionLayout", "onSingleTapUp: Calling transitionToStart()")
                setTransitionDuration(250)
                transitionToStart()
                return true
            }
            return false
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (isStrictlyDownSwipe && distanceY > 0) {
                isStrictlyDownSwipe = false
            }

            if (isStrictlyDownSwipe && distanceY < -15F) {
                Log.e("TouchableMotionLayout", "onScroll: Triggering swipe down listener")
                swipeDownListener.forEach { it.invoke() }
                return true
            }

            return false
        }
    }

    /**
     * Add a listener when the view is swiped down while the current transition's state is in
     * end state (minimized state)
     */
    fun addSwipeDownListener(listener: () -> Unit) = apply {
        swipeDownListener.add(listener)
    }

    private fun isTouchOnButton(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()
        val rect = Rect()
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val globalX = x + loc[0]
        val globalY = y + loc[1]
        
        // State-aware button list:
        // If minimized, we only want to bypass CORE controls (Pause, Close).
        // Tapping the Title, Thumbnail, or Background should trigger expansion (onSingleTapUp).
        // If expanded, we want to bypass ALL interactive controls to ensure they work.
        val isMinimized = progress > 0.95f && currentState == R.id.end
        
        val buttons = if (isMinimized) {
            listOf(
                R.id.play_imageView, R.id.close_imageView,
                R.id.miniPlayerPause, R.id.miniPlayerClose,
                R.id.queue_recycler
            )
        } else {
            listOf(
                R.id.play_imageView, R.id.close_imageView,
                R.id.miniPlayerPause, R.id.miniPlayerClose,
                R.id.prev, R.id.rewindBTN, R.id.play_pause, 
                R.id.forwardBTN, R.id.next, R.id.open_queue, 
                R.id.playback_options, R.id.toggle_inline_video, 
                R.id.sleep_timer, R.id.show_more,
                R.id.open_video, R.id.video_quality,
                R.id.minimize_player, R.id.title, R.id.uploader,
                R.id.artwork_container, R.id.timeBar,
                R.id.subtitle,
                R.id.queue_container, R.id.queue_recycler,
                R.id.volume_imageView, R.id.relPlayer_background,
                R.id.queue_container,
                R.id.queue_auto_play_switch, R.id.queue_controls, R.id.queue_repeat,
                R.id.queue_add_to_playlist, R.id.queue_sort, R.id.queue_watch_positions,
                R.id.queue_clear, R.id.queue_recycler
            )
        }
        
        for (id in buttons) {
            val v = findViewById<View>(id)
            if (v != null && v.visibility == View.VISIBLE && v.alpha > 0.1f) {
                v.getGlobalVisibleRect(rect)
                if (rect.contains(globalX, globalY)) {
                    val viewName = try { resources.getResourceEntryName(v.id) } catch (e: Exception) { v.id.toString() }
                    Log.e("TouchableMotionLayout", "isTouchOnButton: Hit button $viewName (id=$id) at ($x, $y), minimized=$isMinimized")
                    return true
                }
            }
        }
        return false
    }

    private fun isTouchInsideHitArea(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()
        val rect = Rect()
        
        // 1. Check main container (Video surface / Audio layout)
        viewToDetectTouch?.let {
            if (it.visibility == View.VISIBLE && it.alpha > 0.1f) {
                it.getHitRect(rect)
                if (rect.contains(x, y)) {
                    Log.e("TouchableMotionLayout", "isTouchInsideHitArea: Hit main container at ($x, $y)")
                    return true
                }
            }
        }
        
        // 2. Check mini player bar elements (for both audio and video players)
        val barElements = listOf(
            R.id.miniPlayerControls, R.id.miniPlayerTitle, R.id.miniPlayerThumbnail,
            R.id.title_textView, R.id.play_imageView, R.id.close_imageView
        )
        for (id in barElements) {
            val v = findViewById<View>(id)
            if (v != null && v.visibility == View.VISIBLE && v.alpha > 0.1f) {
                v.getGlobalVisibleRect(rect) // Use global rect for consistency
                if (rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    Log.e("TouchableMotionLayout", "isTouchInsideHitArea: Hit bar element ${v.id} at (${event.rawX}, ${event.rawY})")
                    return true
                }
            }
        }
        
        Log.e("TouchableMotionLayout", "isTouchInsideHitArea: No hit at ($x, $y)")
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // Detailed log for EVERY event entering the view
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            Log.e("TouchableMotionLayout", "dispatchTouchEvent: DOWN at (${ev.x}, ${ev.y})")
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isTouchOnPlayer(event: MotionEvent): Boolean {
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        val rect = Rect()
        val playerView = findViewById<View>(R.id.player)

        if (playerView != null && playerView.visibility == View.VISIBLE && playerView.alpha > 0.1f) {
            playerView.getGlobalVisibleRect(rect)
            if (rect.contains(x, y)) {
                return true
            }
        }

        val thumbnail = findViewById<View>(R.id.thumbnail)
        if (thumbnail != null && thumbnail.visibility == View.VISIBLE && thumbnail.alpha > 0.1f) {
            thumbnail.getGlobalVisibleRect(rect)
            if (rect.contains(x, y)) {
                return true
            }
        }

        val videoPlayerView = findViewById<View>(R.id.video_player_view)
        if (videoPlayerView != null && videoPlayerView.visibility == View.VISIBLE && videoPlayerView.alpha > 0.1f) {
            videoPlayerView.getGlobalVisibleRect(rect)
            if (rect.contains(x, y)) {
                return true
            }
        }

        val queueHeader = findViewById<View>(R.id.queue_header)
        if (queueHeader != null && queueHeader.visibility == View.VISIBLE && queueHeader.alpha > 0.1f) {
            queueHeader.getGlobalVisibleRect(rect)
            if (rect.contains(x, y)) {
                return true
            }
        }
        
        val queueDragHandle = findViewById<View>(R.id.queue_drag_handle)
        if (queueDragHandle != null && queueDragHandle.visibility == View.VISIBLE && queueDragHandle.alpha > 0.1f) {
            queueDragHandle.getGlobalVisibleRect(rect)
            if (rect.contains(x, y)) {
                return true
            }
        }

        return false
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val hit = isTouchInsideHitArea(event)
                val onButton = isTouchOnButton(event)
                
                isTouchDownInsideHitArea = hit && !onButton
                touchInitialY = event.y
                startedMinimized = progress > 0.95f && currentState == R.id.end
                isStrictlyDownSwipe = true

                Log.e("TouchableMotionLayout", "onInterceptTouchEvent: DOWN, isTouchDownInsideHitArea=$isTouchDownInsideHitArea (hit=$hit, onButton=$onButton), startedMinimized=$startedMinimized")

                if (!isTouchDownInsideHitArea) {
                    Log.e("TouchableMotionLayout", "onInterceptTouchEvent: Returning false for ACTION_DOWN pass-through")
                    return false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isTouchDownInsideHitArea) {
                    return false
                }
                
                if (!startedMinimized && !shouldInterceptTouchEvent) {
                    val deltaY = event.y - touchInitialY

                    // swipe down detected
                    if (deltaY > scaledTouchSlop) {
                        Log.e("TouchableMotionLayout", "onInterceptTouchEvent: MOVE, starting swallow for swipe down")
                        shouldInterceptTouchEvent = true

                        MotionEvent.obtain(event).apply {
                            action = MotionEvent.ACTION_DOWN
                            setLocation(event.x, event.y)
                        }.also { downEvent ->
                            onTouchEvent(downEvent)
                            downEvent.recycle()
                        }
                    }
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                Log.e("TouchableMotionLayout", "onInterceptTouchEvent: UP/CANCEL, shouldInterceptTouchEvent=false")
                shouldInterceptTouchEvent = false
            }
        }

        val result = if (shouldInterceptTouchEvent) true else super.onInterceptTouchEvent(event)
        
        // CRITICAL: If it's a hit on DOWN, we usually intercept.
        // EXCEPTION: If it is the PlayerView (R.id.player), we MUST let it pass through (return false)
        // so it can handle clicks for controls, BUT we still track it (isTouchDownInsideHitArea=true)
        // so we can steal it later for a swipe.
        val onPlayer = if (event.action == MotionEvent.ACTION_DOWN) isTouchOnPlayer(event) else false
        
        val finalResult = if (event.action == MotionEvent.ACTION_DOWN && isTouchDownInsideHitArea && !onPlayer) {
            Log.e("TouchableMotionLayout", "onInterceptTouchEvent: Forcing true for ACTION_DOWN hit")
            true
        } else {
            result
        }

        if (finalResult) {
             Log.e("TouchableMotionLayout", "onInterceptTouchEvent: intercepted=true for action=${event.action}")
        }
        return finalResult
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            Log.e("TouchableMotionLayout", "onTouchEvent: DOWN, isTouchDownInsideHitArea=$isTouchDownInsideHitArea")
        }

        // If the gesture didn't start in our hit area, don't consume ANY events
        if (!isTouchDownInsideHitArea) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                Log.e("TouchableMotionLayout", "onTouchEvent: Passing through ACTION_DOWN (hit=false)")
            }
            return false
        }

        if (progress > 0.95f && currentState == R.id.end) {
            // detect gesture only when the player is in minimized state
            gestureDetector.onTouchEvent(event)
        }

        val result = super.onTouchEvent(event)
        
        // CRITICAL: If it's a hit, we MUST return true on DOWN to receive subsequent events (MOVE/UP)
        // even if super (MotionLayout) returns false (e.g. no OnClick in scene).
        return if (isTouchDownInsideHitArea && event.action == MotionEvent.ACTION_DOWN) {
            Log.e("TouchableMotionLayout", "onTouchEvent: Forcing true for hit on DOWN")
            true
        } else {
            result
        }
    }
}
