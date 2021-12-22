package com.google.android.exoplayer2.demo

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random


class MultiPlayerActivity : AppCompatActivity() {

    private val playersCounter = AtomicInteger(0)

    private fun createMediaSourceFactory(): MediaSourceFactory {
        val evictor = LeastRecentlyUsedCacheEvictor(500L * 1024 * 1024)
        val cache = SimpleCache(cacheDir, evictor, ExoDatabaseProvider(this))

        val httpDataSourceFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true);
        val defaultDataSourceFactory = DefaultDataSourceFactory(this, httpDataSourceFactory);
        val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(defaultDataSourceFactory)

        return ProgressiveMediaSource.Factory(cacheDataSourceFactory)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rows = 3
        val columns = 3

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        repeat(rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            repeat(columns) {
                rowLayout.addView(
                        PlayerView(this).apply {
                            useController = false
                            keepScreenOn = true
                        }, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1f,
                )
                )
            }
            root.addView(
                    rowLayout, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f,
            )
            )
        }
        root.layoutParams = ViewGroup.MarginLayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
        )

        setContentView(root)

        val mediaSourceFactory = createMediaSourceFactory()

        lifecycleScope.launchWhenStarted {
            val urls = listOf<String>(
                    // TODO place URLS here
            )

            flow {
                while (currentCoroutineContext().isActive) {
                    emit(urls.shuffled())
                    val delay = TimeUnit.SECONDS.toMillis(Random.nextInt(5, 45).toLong())
                    delay(delay)
                }
            }
                    .conflate()
                    .collectLatest { urlsToPlay ->
                        coroutineScope {
                            val playerCount = Random.nextInt(1, 9)
                            root.playerViews().take(playerCount).forEachIndexed { index, playerView ->
                                val url = urlsToPlay.getOrNull(index)

                                if (url != null) {
                                    launch {
                                        playerView.playUrl(mediaSourceFactory, url)
                                    }
                                }
                            }
                        }
                    }
        }
    }

    private suspend fun PlayerView.playUrl(factory: MediaSourceFactory, url: String) {
        val mediaItem = MediaItem.Builder().setUri(Uri.parse(url)).build()
        val player = SimpleExoPlayer.Builder(this@MultiPlayerActivity)
                .setReleaseTimeoutMs(5000)
                .setDetachSurfaceTimeoutMs(5000)
                .build()
        changePlayersCountAndLog(1)
        try {
            player.play()
            player.setMediaSource(factory.createMediaSource(mediaItem))
            player.prepare()
            player.volume = 0.05f
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.e("MultiPlayer", "onPlaybackStateChanged: $playbackState")
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("MultiPlayer", "error during playback", error)
                }
            })
            this.player = player

            awaitCancellation()
        } finally {
            this.player = null
            changePlayersCountAndLog(-1)
            player.release()
        }
    }

    private fun ViewGroup.playerViews(): Sequence<PlayerView> {
        return children.flatMap { view ->
            (view as? ViewGroup)?.children ?: emptySequence()
        }.filterIsInstance<PlayerView>()
    }

    private fun changePlayersCountAndLog(delta: Int) {
        val newValue = playersCounter.addAndGet(delta)
        Log.i("MultiPlayer", "Number of players changed: $newValue")
    }
}