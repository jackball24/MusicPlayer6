# MusicPlayer6

![GitHub](https://img.shields.io/github/license/AkaneTan/Gramophone?style=flat-square&logoColor=white&labelColor=black&color=white)

一个优雅、轻量级的本地音乐播放器，使用 media3 和 material 3 design构建，严格遵循 android 开发的标准。

大三下实践周Android应用开发，使用kotlin语言，项目为-音乐播放器应用。

本项目由旧项目（于2024.6.17发布任务便构建-已废弃）[jackball24/MusicPlayer: v0.0.1 (github.com)](https://github.com/jackball24/MusicPlayer)移植至此，故团队成员的协作开发历史有所欠缺。

本次有多个项目可供选择，其中联系人应用是本学期课程的大作业，已经较好地实现了，~~我们团队表示不屑一顾，不想随大流~~，故选择多媒体方向的应用，既是挑战，也是很有趣的探索~

## 特色

- material 3 design，界面简洁美观

- 扫描本地音乐并加载到列表中，支持搜索功能

- 支持专辑封面，支持卡片式列表切换

- 支持音频文件内嵌歌词和外部lrc等歌词文件，同时可以同步歌词

- 支持多种不同的播放模式，如随机模式生成一个随机的歌曲播放队列

- media3-exoplayer进行播放控制，响应快速

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

~~还没有（bushi）~~等验收改进完再提供。



## 安装

待发行



## 构建

构建此应用，建议的环境为：

- JDK 17
- Android Studio Jellyfish-2023.3.1
- Gradle 8.7
- AGP 8.4.0



## 感谢

感谢以下项目带来的灵感：

[歌词下载](https://github.com/lambada10/songsync)
[歌词视图组件](https://github.com/Moriafly/LyricViewX)
[一个简陋小项目](https://github.com/RoseTame/MusicPlayer)
[成熟且高级的播放器](https://github.com/rRemix/APlayer)
[现代化大项目](https://github.com/RetroMusicPlayer/RetroMusicPlayer)
[简单的MusicPlayer](https://github.com/SimpleMobileTools/Simple-Music-Player)

