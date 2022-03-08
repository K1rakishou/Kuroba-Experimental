
# Kuroba Experimental

<p align="left"><a href="https://f-droid.org/packages/com.github.k1rakishou.chan.fdroid/"><img src="https://f-droid.org/assets/fdroid-logo-text.svg" width="250"></a></p> 


KurobaEx is a fast Android app for browsing imageboards, such as 4chan and 8chan. It's a fork of Kuroba. This fork provides lots of new features:

- New technological stack (Kotlin, RxJava/Coroutines, Room etc).

- On demand content loading (includes prefetching, youtube videos titles and durations fetching, inlined files size fetching etc).

- Third-party archives support.

- New thread navigation (tabs).

- New in-app navigation (bottom nav bar).

- New bookmarks (they were fully rewritten from scratch, now use way less memory, don't use wakelocks, show separate notifications per thread (and notifications can be swiped away).

- Edge-to-edge theme support.

- New database.

- 4chan global search support.

- Fully dynamic themes with Android Q Day/Night mode support.

- Per-site proxies.

- Ability to attach multiple media files to reply, attach media files that was shared by external apps (even by some keyboards), attach remote media files by URL, etc.

- New image downloader. Allows downloading images while the app is in background, retrying failed to download images, resolving duplicates, etc. 

- New posting. Posting code was moved into a foreground service which now allows stuff like using automatic captcha solvers (2captcha API) seamlessly or queueing multiple replies in different threads (only one reply per thread).

- New Media Viewer. It was rewritten from scratch and now lives in a separate activity. It now also supports stuff like viewing links to media files shared into the app.

- Thread downloader with ability to export threads as HTML pages with all downloaded media.

- Composite catalogs (ability to combine multiple boards of any available sites (except archives) together into a single catalog).

- Mpv video player (downloadable).

- Bookmark groups with ability to setup regex matchers to automatically move newly created bookmarks into them.

- Lots of other tiny improvements.

### Screenshots:

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/2.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/4.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/5.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/6.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/7.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/8.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/9.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/9.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/10.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/11.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/11.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/12.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/12.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/13.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/13.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/14.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/14.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/15.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/15.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/16.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/16.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/17.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/17.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/18.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/18.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/19.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/19.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/20.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/20.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/21.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/21.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/22.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/22.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/23.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/23.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/24.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/24.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/25.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/25.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/26.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/26.png)

### New stuff and important annoncements:

#### - (2022-01-01) Deprecation of apk/report server (https://kuroba.io:8443/), the server will be shut down in ~1-2 weeks. The apks are already stored on Github, reports will soon be moved into a separate github repository (https://github.com/KurobaExReports/Reports/issues). 

#### - (2021-11-03) A new tool can now be used to create/modify KurobaEx themes - https://github.com/K1rakishou/KurobaEx-theme-builder

#### - (2021-09-26) Due to another mistake in v0.12.7-beta you will have to update it manually since the updater is broken. Release/FDroid versions are not affected.

#### - (2021-08-28) Due to a mistake in v0.12.0-beta (that was caused by disk cache directory migration) you will have to install v0.12.2-beta apk manually. Release/FDroid versions are not affected.

#### - (2021-07-04) Beta apks are now stored and distributed as updates from this repo: https://github.com/K1rakishou/Kuroba-Experimental-beta The migration process is automatic and you shouldn't notice any difference.

#### - (2021-05-30) Themes were moved into a separate repository: https://github.com/K1rakishou/KurobaEx-themes

#### - (2021-02-24) Beta apks are now built as release apks (not debug) to make them more performant. The downside here is that you won't be able to automatically install v0.6.x-beta and will have to do that manually. You will have to export all your settings, remove the previous beta version, install the new one and then import the settings back. This is pretty inconvenient but as a result you get an apk with the same performance as the release one. The stable release versions are not affected, only beta!

#### - (2021-01-27) F-Droid version is now available.

#### - (2020-07-10) Unfortunatelly, built-in updater for release versions was broken in one of the releases so people using Android M and below won't be able to use it to update to a newer version. You will have to install  the update manually (it is fixed in [v0.2.10](https://github.com/K1rakishou/Kuroba-Experimental/releases/tag/v0.2.10-release)). If you are using Android N and above you should be fine. Beta versions are fine too.

#### - New theme engine has been added which now allows you to create any theme you want, see [this wiki page](https://github.com/K1rakishou/KurobaEx-themes/wiki/Dynamic-themes) for more info. If you want to request a new theme or if you want to make a theme by yourself, but have no idea what to make, then this issue may help you https://github.com/K1rakishou/KurobaEx-themes/issues/6

[Latest beta version](https://github.com/K1rakishou/Kuroba-Experimental-beta/releases/latest)

[All beta versions](https://github.com/K1rakishou/Kuroba-Experimental-beta/releases)

### [Latest release version (v1.3.9)](https://github.com/K1rakishou/Kuroba-Experimental/releases/tag/v1.3.9-release)

##### Currently supported sites
- 4Chan
- Dvach
- 8Kun (thanks to @jirn073-76)
- 420Chan (thanks to @Lolzen)
- Lainchan
- Sushichan
- Wired-7 (thanks to @Wired-7)
- 370chan.info (thanks to @alcharkov)
- Endchan
- Kohlchan

##### Currently supported 4chan archives
- ArchivedMoe
- ArchiveOfSins
- B4k
- DesuArchive
- Fireden 
- 4Plebs 
- Nyafuu 
- TokyoChronos
- Warosu
- Wakarimasen.moe
- RozenArcana

## License
[Kuroba is GPLv3](https://github.com/K1rakishou/Kuroba-Experimental/blob/develop/COPYING.txt), [licenses of the used libraries.](https://github.com/K1rakishou/Kuroba-Experimental/blob/develop/Kuroba/app/src/main/assets/html/licenses.html)
