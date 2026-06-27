# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

- Build debug: `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`)
- Build release: `./gradlew assembleRelease`
- Compile only: `./gradlew compileDebugJavaWithJavac`
- Check theme tokens (runs automatically on preBuild): `./gradlew app:checkThemeTokens`
- Versioned build: `./gradlew assembleRelease -PversionName=X.Y.Z -PversionCode=N`

## Project Overview

这是一个第三方哔哩哔哩安卓 App，针对平板、TV、车机设备优化，支持触摸和遥控器操作。

## Architecture

**包结构：**
- `blbl.cat3399.core` — 核心模块（账号、API、网络、数据模型、偏好设置、UI工具、主题等）
- `blbl.cat3399.feature` — 功能页面（首页、推荐、分类、播放、搜索、登录等）
- `blbl.cat3399.ui` — 界面组件（视频卡片、弹幕、播放器等）

**关键架构：**
- 单 Android App 模块，无多模块拆分
- 基于 ViewBinding 的视图绑定
- Kotlin 协程用于异步操作
- Bilibili API 通过 OkHttp 调用，支持 WBI 签名
- 视频流支持两种实现：App gRPC API 和 Web API

**主题系统：**
- 所有颜色使用 `?attr/` 属性引用，禁止在布局中直接引用硬编码颜色
- 预构建任务 `checkThemeTokens` 会自动检查违规
- 添加新主题预设只需要修改 `ThemePresets.kt`

**播放器：**
- 默认使用 Media3 (ExoPlayer)
- 可选支持 Ijkplayer (native so 作为依赖 aar 引入)
- 支持弹幕、字幕、多分辨率切换、倍速播放

## Technology Stack

- Kotlin + AndroidX + ViewBinding
- Media3 (ExoPlayer) / Ijkplayer
- OkHttp + Protobuf-lite + gRPC
- Material Design 3 + RecyclerView + ViewPager2
- JDK 17, compileSdk 36, minSdk 21
