package blbl.cat3399.feature.player

import android.view.KeyEvent
import androidx.lifecycle.lifecycleScope
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.prefs.AppPrefs
import blbl.cat3399.core.prefs.PlayerCustomShortcut
import blbl.cat3399.core.prefs.PlayerCustomShortcutAction
import blbl.cat3399.core.prefs.PlayerCustomShortcutsStore
import blbl.cat3399.core.prefs.PlayerPlaybackModes
import blbl.cat3399.feature.player.engine.ExoEngine
import blbl.cat3399.feature.player.engine.ExoPlayerEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.abs

private class PlayerCustomShortcutToggleMemory {
    val playbackSpeedPrevByKey = HashMap<Int, Float>()
    val targetQnPrevByKey = HashMap<Int, Int>()
    val targetAudioIdPrevByKey = HashMap<Int, Int>()
    val codecPrevByKey = HashMap<Int, String>()
    val playbackModeOverridePrevByKey = HashMap<Int, String?>()
    val subtitleLangOverridePrevByKey = HashMap<Int, String?>()
    val subtitleTextSizePrevByKey = HashMap<Int, Float>()
    val danmakuOpacityPrevByKey = HashMap<Int, Float>()
    val danmakuTextSizePrevByKey = HashMap<Int, Float>()
    val danmakuSpeedLevelPrevByKey = HashMap<Int, Int>()
    val danmakuAreaPrevByKey = HashMap<Int, Float>()
}

private class LongPressDetector {
    var pendingKeyCode: Int = 0
    var pendingJob: kotlinx.coroutines.Job? = null
}

private val shortcutToggleMemoryByPlayer = WeakHashMap<PlayerActivity, PlayerCustomShortcutToggleMemory>()
private val longPressDetectorByPlayer = WeakHashMap<PlayerActivity, LongPressDetector>()

private fun PlayerActivity.shortcutToggleMemory(): PlayerCustomShortcutToggleMemory {
    return shortcutToggleMemoryByPlayer.getOrPut(this) { PlayerCustomShortcutToggleMemory() }
}

private fun PlayerActivity.longPressDetector(): LongPressDetector {
    return longPressDetectorByPlayer.getOrPut(this) { LongPressDetector() }
}

private fun sameFloat(a: Float, b: Float): Boolean {
    if (!a.isFinite() || !b.isFinite()) return false
    return abs(a - b) < 0.0001f
}

private fun audioIdHintText(id: Int): String = if (id > 0) audioLabel(id) else "默认"

private fun qnHintText(qn: Int): String = if (qn > 0) qnLabel(qn) else "默认"

private fun PlayerActivity.showShortcutOsd() {
    setControlsVisible(true)
    focusDownKeyOsdTargetControl()
}

internal fun PlayerActivity.dispatchPlayerCustomShortcutIfNeeded(event: KeyEvent): Boolean {
    val keyCode = event.keyCode
    if (keyCode <= 0 || keyCode == KeyEvent.KEYCODE_UNKNOWN) return false
    if (PlayerCustomShortcutsStore.isForbiddenKeyCode(keyCode)) return false
    if (
        !PlayerCustomShortcutInputPolicy.canDispatchInVod(
            hasInteractiveOsd = osdMode != PlayerActivity.OsdMode.Hidden,
            hasSidePanel = isSidePanelVisible(),
            hasBottomCardPanel = isBottomCardPanelVisible(),
        )
    ) {
        return false
    }

    val binding = BiliClient.prefs.playerCustomShortcuts.firstOrNull { it.keyCode == keyCode } ?: return false

    // If no long press action, handle normally
    if (binding.longClickAction == null) {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount != 0) return false
        noteUserInteraction()
        binding.clickAction?.let { applyPlayerCustomShortcut(keyCode = keyCode, action = it) }
        return true
    }

    // Has long press action - need detection
    val detector = longPressDetector()

    when (event.action) {
        KeyEvent.ACTION_DOWN -> {
            if (event.repeatCount != 0) {
                // Repeat events are ignored - long press already triggered
                return detector.pendingKeyCode == keyCode
            }
            noteUserInteraction()
            // Start long press detection
            detector.pendingKeyCode = keyCode
            detector.pendingJob?.cancel()
            detector.pendingJob = lifecycleScope.launch {
                delay(500) // Standard long press timeout
                // Long press triggered
                binding.longClickAction?.let { applyPlayerCustomShortcut(keyCode = keyCode, action = it) }
                detector.pendingKeyCode = 0
                detector.pendingJob = null
            }
            return true
        }
        KeyEvent.ACTION_UP -> {
            val pending = detector.pendingJob != null && detector.pendingKeyCode == keyCode
            if (pending) {
                // Cancel pending long press and trigger click
                detector.pendingJob?.cancel()
                detector.pendingKeyCode = 0
                detector.pendingJob = null
                binding.clickAction?.let { applyPlayerCustomShortcut(keyCode = keyCode, action = it) }
                return true
            }
            return false
        }
        else -> return false
    }
}

private fun PlayerActivity.applyPlayerCustomShortcut(keyCode: Int, action: PlayerCustomShortcutAction) {
    val memory = shortcutToggleMemory()
    when (action) {
        is PlayerCustomShortcutAction.OpenVideoList -> {
            if (!showListPanelFromShortcut(target = action.target)) {
                showSeekHint("视频列表：暂无", hold = false)
            }
        }

        PlayerCustomShortcutAction.OpenComments -> {
            showCommentsPanel()
        }

        PlayerCustomShortcutAction.OpenSettings -> {
            showSettingsPanel()
        }

        PlayerCustomShortcutAction.ShowOsd -> {
            showShortcutOsd()
        }

        PlayerCustomShortcutAction.TogglePlayPause -> {
            togglePlayPause(showControls = false, showHint = true)
        }

        PlayerCustomShortcutAction.PlayPrevious -> {
            playPrevByPlaybackMode(userInitiated = true)
        }

        PlayerCustomShortcutAction.PlayNext -> {
            playNextByPlaybackMode(userInitiated = true)
        }

        PlayerCustomShortcutAction.OpenVideoDetail -> {
            openCurrentMediaDetail()
        }

        PlayerCustomShortcutAction.OpenUpDetail -> {
            openCurrentUpDetail()
        }

        PlayerCustomShortcutAction.Like -> {
            onLikeButtonClicked(showControls = false)
        }

        PlayerCustomShortcutAction.Coin -> {
            onCoinButtonClicked(showControls = false)
        }

        PlayerCustomShortcutAction.Fav -> {
            onFavButtonClicked(showControls = false)
        }

        PlayerCustomShortcutAction.ToggleSubtitles -> {
            val engine = player ?: return showSeekHint("字幕：未就绪", hold = false)
            val exo = (engine as? ExoPlayerEngine)?.exoPlayer
            if (exo == null) {
                showSeekHint("字幕：当前内核不支持", hold = false)
                return
            }
            if (!subtitleAvailabilityKnown) {
                showSeekHint("字幕：加载中", hold = false)
                return
            }
            if (!subtitleAvailable) {
                showSeekHint("字幕：暂无", hold = false)
                return
            }
            applySubtitleEnabledSetting(!session.subtitleEnabled, exo)
            val state = if (session.subtitleEnabled) "开" else "关"
            showSeekHint("字幕：$state", hold = false)
        }

        PlayerCustomShortcutAction.ToggleDanmaku -> {
            setDanmakuEnabled(!session.danmaku.enabled)
            val state = if (session.danmaku.enabled) "开" else "关"
            showSeekHint("弹幕：$state", hold = false)
        }

        PlayerCustomShortcutAction.ToggleDebugOverlay -> {
            toggleSessionSettingFlag(
                current = session.debugEnabled,
                updateSession = { copy(debugEnabled = it) },
                syncToGlobal = { playerDebugEnabled = it },
                afterApplied = { updateDebugOverlay() },
            )
            val state = if (session.debugEnabled) "开" else "关"
            showSeekHint("调试信息：$state", hold = false)
        }

        PlayerCustomShortcutAction.TogglePersistentBottomProgress -> {
            toggleSessionSettingFlag(
                current = session.persistentBottomProgressEnabled,
                updateSession = { copy(persistentBottomProgressEnabled = it) },
                syncToGlobal = { playerPersistentBottomProgressEnabled = it },
                afterApplied = { updatePersistentBottomProgressBarVisibility() },
            )
            val state = if (session.persistentBottomProgressEnabled) "开" else "关"
            showSeekHint("底部进度条：$state", hold = false)
        }

        is PlayerCustomShortcutAction.SetPlaybackSpeed -> {
            val target = action.speed.takeIf { it.isFinite() }?.coerceIn(0.25f, 4.0f) ?: 1.0f
            val current = session.playbackSpeed
            val next =
                if (sameFloat(current, target)) {
                    memory.playbackSpeedPrevByKey[keyCode] ?: target
                } else {
                    memory.playbackSpeedPrevByKey[keyCode] = current
                    target
                }
            applySessionSettingValue(
                value = next.coerceIn(0.25f, 4.0f),
                updateSession = { copy(playbackSpeed = it) },
                syncToGlobal = { playerSpeed = it },
                afterApplied = { player?.setPlaybackSpeed(it) },
            )
            showSeekHint("播放速度：${String.format(Locale.US, "%.2fx", next)}", hold = false)
        }

        is PlayerCustomShortcutAction.SetResolutionQn -> {
            val target = action.qn.takeIf { it > 0 } ?: return
            val current = selectedResolutionQn()
            val next =
                if (current == target) {
                    memory.targetQnPrevByKey[keyCode] ?: target
                } else {
                    memory.targetQnPrevByKey[keyCode] = current
                    target
                }
            applyResolutionSetting(next)
            showSeekHint("分辨率：${qnHintText(next)}", hold = false)
        }

        is PlayerCustomShortcutAction.SetAudioId -> {
            val target = action.audioId.takeIf { it > 0 } ?: return
            val current = selectedAudioTrackId()
            val next =
                if (current == target) {
                    memory.targetAudioIdPrevByKey[keyCode] ?: target
                } else {
                    memory.targetAudioIdPrevByKey[keyCode] = current
                    target
                }
            applyAudioTrackSetting(next)
            showSeekHint("音轨：${audioIdHintText(next)}", hold = false)
        }

        is PlayerCustomShortcutAction.SetCodec -> {
            val target = action.codec.trim().ifBlank { return }
            val current = session.preferCodec
            val next =
                if (current == target) {
                    memory.codecPrevByKey[keyCode] ?: target
                } else {
                    memory.codecPrevByKey[keyCode] = current
                    target
                }
            applySessionSettingValue(
                value = next,
                updateSession = { copy(preferCodec = it) },
                syncToGlobal = { playerPreferredCodec = it },
                afterApplied = { reloadStream(keepPosition = true) },
            )
            showSeekHint("视频编码：$next", hold = false)
        }

        is PlayerCustomShortcutAction.SetPlaybackMode -> {
            val engine = player ?: return
            val mode = action.mode.trim()
            val normalized = PlayerPlaybackModes.normalize(mode)
            val currentMode = resolvedPlaybackMode()
            val nextMode =
                if (currentMode == normalized) {
                    if (memory.playbackModeOverridePrevByKey.containsKey(keyCode)) {
                        memory.playbackModeOverridePrevByKey[keyCode]
                    } else {
                        normalized
                    }
                } else {
                    memory.playbackModeOverridePrevByKey[keyCode] = currentMode
                    normalized
                }
            applyPlaybackModeSetting(nextMode ?: normalized, engine)
            showSeekHint("播放模式：${playbackModeLabel(resolvedPlaybackMode())}", hold = false)
        }

        is PlayerCustomShortcutAction.SetSubtitleLang -> {
            val engine = player ?: return showSeekHint("字幕：未就绪", hold = false)
            val exo = (engine as? ExoPlayerEngine)?.exoPlayer
            if (exo == null) {
                showSeekHint("字幕：当前内核不支持", hold = false)
                return
            }

            val rawTarget = action.lang.trim()
            val targetCode =
                if (rawTarget.equals(PlayerCustomShortcutAction.SUBTITLE_LANG_DEFAULT, ignoreCase = true) || rawTarget.isBlank()) {
                    defaultSubtitleLangCode()
                } else {
                    rawTarget
                }

            val currentCode = resolvedSubtitleLangCode()
            val nextCode =
                if (currentCode.equals(targetCode, ignoreCase = true)) {
                    if (memory.subtitleLangOverridePrevByKey.containsKey(keyCode)) {
                        memory.subtitleLangOverridePrevByKey[keyCode]
                    } else {
                        targetCode
                    }
                } else {
                    memory.subtitleLangOverridePrevByKey[keyCode] = currentCode
                    targetCode
                }
            applySubtitleLanguageSetting(nextCode ?: targetCode, exo)
            showSeekHint("字幕语言：${subtitleLangHintText(resolvedSubtitleLangCode())}", hold = false)
        }

        is PlayerCustomShortcutAction.SetSubtitleTextSize -> {
            val target = action.textSizeSp.takeIf { it.isFinite() }?.coerceIn(10f, 60f) ?: 26f
            val current = session.subtitleTextSizeSp
            val next =
                if (sameFloat(current, target)) {
                    memory.subtitleTextSizePrevByKey[keyCode] ?: target
                } else {
                    memory.subtitleTextSizePrevByKey[keyCode] = current
                    target
                }
            applySessionSettingValue(
                value = next.coerceIn(10f, 60f),
                updateSession = { copy(subtitleTextSizeSp = it) },
                syncToGlobal = { subtitleTextSizeSp = it },
                afterApplied = { applySubtitleTextSize() },
            )
            showSeekHint("字幕大小：${next.toInt()}", hold = false)
        }

        is PlayerCustomShortcutAction.SetDanmakuOpacity -> {
            val target = action.opacity.takeIf { it.isFinite() }?.coerceIn(0.05f, 1.0f) ?: 1.0f
            val current = session.danmaku.opacity
            val next =
                if (sameFloat(current, target)) {
                    memory.danmakuOpacityPrevByKey[keyCode] ?: target
                } else {
                    memory.danmakuOpacityPrevByKey[keyCode] = current
                    target
                }
            applyDanmakuSettingValue(
                value = next.coerceIn(0.05f, 1.0f),
                updateDanmaku = { copy(opacity = it) },
                syncToGlobal = { danmakuOpacity = it },
                afterApplied = { binding.danmakuView.invalidate() },
            )
            showSeekHint("弹幕透明度：${String.format(Locale.US, "%.2f", next)}", hold = false)
        }

        is PlayerCustomShortcutAction.SetDanmakuTextSize -> {
            val target = action.textSizeSp.takeIf { it.isFinite() }?.coerceIn(10f, 60f) ?: 18f
            val current = session.danmaku.textSizeSp
            val next =
                if (sameFloat(current, target)) {
                    memory.danmakuTextSizePrevByKey[keyCode] ?: target
                } else {
                    memory.danmakuTextSizePrevByKey[keyCode] = current
                    target
                }
            applyDanmakuSettingValue(
                value = next.coerceIn(10f, 60f),
                updateDanmaku = { copy(textSizeSp = it) },
                syncToGlobal = { danmakuTextSizeSp = it },
                afterApplied = { binding.danmakuView.invalidate() },
            )
            showSeekHint("弹幕大小：${next.toInt()}", hold = false)
        }

        is PlayerCustomShortcutAction.SetDanmakuSpeed -> {
            val target = action.speedLevel.coerceIn(1, 10)
            val current = session.danmaku.speedLevel
            val next =
                if (current == target) {
                    memory.danmakuSpeedLevelPrevByKey[keyCode] ?: target
                } else {
                    memory.danmakuSpeedLevelPrevByKey[keyCode] = current
                    target
                }
            applyDanmakuSettingValue(
                value = next.coerceIn(1, 10),
                updateDanmaku = { copy(speedLevel = it) },
                syncToGlobal = { danmakuSpeed = it },
                afterApplied = { binding.danmakuView.invalidate() },
            )
            showSeekHint("弹幕速度：$next", hold = false)
        }

        is PlayerCustomShortcutAction.SetDanmakuArea -> {
            val target = action.area.takeIf { it.isFinite() }?.let(AppPrefs::normalizeLegacyDanmakuAreaCompat) ?: AppPrefs.DANMAKU_AREA_DEFAULT
            val current = session.danmaku.area
            val next =
                if (sameFloat(current, target)) {
                    memory.danmakuAreaPrevByKey[keyCode] ?: target
                } else {
                    memory.danmakuAreaPrevByKey[keyCode] = current
                    target
                }
            applyDanmakuSettingValue(
                value = AppPrefs.normalizeDanmakuArea(next),
                updateDanmaku = { copy(area = it) },
                syncToGlobal = { danmakuArea = it },
                afterApplied = { binding.danmakuView.invalidate() },
            )
            showSeekHint("弹幕区域：${areaText(next)}", hold = false)
        }
    }
}

private fun subtitleLangHintText(code: String?): String {
    val v = code?.trim().orEmpty()
    if (v.isBlank() || v.equals(PlayerCustomShortcutAction.SUBTITLE_LANG_DEFAULT, ignoreCase = true)) return "跟随全局"
    return when (v) {
        "auto" -> "自动"
        "zh-Hans" -> "中文(简体)"
        "zh-Hant" -> "中文(繁体)"
        "en" -> "English"
        "ja" -> "日本語"
        "ko" -> "한국어"
        else -> v
    }
}
