@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package blbl.cat3399.feature.player

import android.content.Context
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

/**
 * 播放页内媒体会话：把现有 ExoPlayer 发布给系统媒体框架
 * （小爱同学/系统媒体中心/蓝牙耳机媒体键）。
 *
 * ExoPlayer 单窗口播放、默认无相邻条目，SEEK_TO_NEXT/PREVIOUS 命令不可用，
 * 因此用 [ForwardingPlayer] 开放这两个命令并拦截：
 * - next 交回播放页执行连播（[onSeekToNext]）；
 * - previous 交回播放页决定（[onSeekToPrevious]），不落到 ExoPlayer 默认行为。
 *
 * 生命周期随播放页：页面销毁时必须 [release]。不提供后台播放能力。
 */
internal class PlayerMediaSession(
    context: Context,
    player: ExoPlayer,
    private val onSeekToNext: () -> Unit,
    private val onSeekToPrevious: () -> Unit,
) {
    private val mediaSession: MediaSession

    private val forwardingPlayer: Player =
        object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return Player.Commands.Builder()
                    .addAll(super.getAvailableCommands())
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()
            }

            override fun seekToNext() {
                onSeekToNext()
            }

            override fun seekToPrevious() {
                onSeekToPrevious()
            }
        }

    init {
        mediaSession = MediaSession.Builder(context, forwardingPlayer).build()
    }

    fun release() {
        mediaSession.release()
    }
}
