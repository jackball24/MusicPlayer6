# MusicPlayer6

![GitHub](https://img.shields.io/github/license/AkaneTan/Gramophone?style=flat-square&logoColor=white&labelColor=black&color=white)

一个优雅、轻量级的本地音乐播放器，使用 Media3 和 Material Design 3 构建，严格遵循 Android 开发的标准。

大三下实践周 Android 应用开发，使用 Kotlin 语言，项目为--音乐播放器应用。



## 特色

- material 3 design，界面简洁美观

- 扫描本地音乐并加载到列表中，支持搜索功能

- 支持专辑封面，支持卡片式列表切换

- 支持音频文件内嵌歌词和外部 lrc 等歌词文件，同时可以同步歌词

- 支持多种不同的播放模式，如随机模式生成一个随机的歌曲播放队列

- media3-exoplayer 进行播放控制，响应快速

- 性能良好，占用系统资源极少

  

**应用功能实现：**

**1.音乐播放：**

- 支持播放本地存储的音乐文件，如 MP3、AAC 等。
- 提供基本的播放控制，如播放、暂停、停止、上一曲、下一曲等。
- 显示歌词信息

**2.音乐列表：**

- 显示所有音乐文件的列表，包括歌曲名称、艺术家和时长。
- 允许用户点击列表项选择音乐进行播放。 

**3.播放器控制：**

- 显示音乐播放进度条和当前播放时间。
- 支持调整音量和音频焦点管理。

**4.播放队列和循环模式：**

- 允许用户创建播放队列，并支持顺序播放、单曲循环和随机播放模式。 

**5.音乐文件扫描：**

- 自动扫描设备上的音乐文件，并将它们加入到应用的音乐库中。 

**6.通知栏控制：**

- 在通知栏中显示当前播放歌曲信息，并提供播放、暂停和关闭功能。 

**7.界面设计：**

- 设计一个直观和美观的用户界面，包括音乐列表、播放控制面板和播放器界面。 

**8.错误处理：**

- 处理音乐加载失败、文件损坏等异常情况。
- 提供适当的用户反馈和错误提示。



## 截图

| ![image-20240626182231336](https://cdn.jsdelivr.net/gh/jackball24/Myblog_pic@main/202406261822477.png) | ![image-20240626182358138](https://cdn.jsdelivr.net/gh/jackball24/Myblog_pic@main/202406261823264.png) | ![image-20240626194600368](https://cdn.jsdelivr.net/gh/jackball24/Myblog_pic@main/202406261946487.png) |
| ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| ![image-20240626182703759](https://cdn.jsdelivr.net/gh/jackball24/Myblog_pic@main/202406261827948.png) | ![image-20240626182800735](https://cdn.jsdelivr.net/gh/jackball24/Myblog_pic@main/202406261828813.png) | ![image-20240626183001958](https://cdn.jsdelivr.net/gh/jackball24/Myblog_pic@main/202406261830073.png) |
| ![image-20240626183143293](https://cdn.jsdelivr.net/gh/jackball24/Myblog_pic@main/202406261831355.png) | ![image-20240626183220955](https://cdn.jsdelivr.net/gh/jackball24/Myblog_pic@main/202406261832087.png) | ![image-20240626183318214](https://cdn.jsdelivr.net/gh/jackball24/Myblog_pic@main/202406261833275.png) |



## 安装

[MusicPlayer v1.0.0](https://github.com/jackball24/MusicPlayer6/releases/tag/v1.0.0)



## 构建

构建此应用，建议的环境为：

- JDK 17
- Android Studio Jellyfish-2023.3.1
- Gradle 8.7
- AGP 8.4.0



## 感谢

本项目基于[留声机应用](https://github.com/AkaneTan/Gramophone)，由于不是完全在原项目上开发，故此创建了自己的Repository，感谢AkaneTan以及其他Contributors。

我们在此项目基础上作了许多功能的修改和实践要求功能的增添，仍有许多不足之处，未来还有待改进。

感谢以下项目带来的灵感：

- [歌词下载](https://github.com/lambada10/songsync)
- [歌词视图组件](https://github.com/Moriafly/LyricViewX)
- [一个简陋小项目](https://github.com/RoseTame/MusicPlayer)
- [成熟且高级的播放器](https://github.com/rRemix/APlayer)
- [现代化大项目](https://github.com/RetroMusicPlayer/RetroMusicPlayer)
- [简单的MusicPlayer](https://github.com/SimpleMobileTools/Simple-Music-Player)

