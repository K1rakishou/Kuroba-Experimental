
# Kuroba Experimental (Temp name)

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

- Lots of other tiny improvements.

Screenshots can be found [here](https://github.com/K1rakishou/Kuroba-Experimental/tree/develop/fastlane/metadata/android/en-US/images)

### New stuff and important annoncements:

#### - (10/7/20) Unfortunatelly, built-in updater for release versions was broken in one of the releases so people using Android M and below won't be able to use it to update to a newer version. You will have to install  the update manually (it is fixed in [v0.2.10](https://github.com/K1rakishou/Kuroba-Experimental/releases/tag/v0.2.10-release)). If you are using Android N and above you should be fine. Beta versions are fine too.

#### - New theme engine has been added which now allows you to create any theme you want, see [this wiki page](https://github.com/K1rakishou/Kuroba-Experimental/wiki/Dynamic-themes) for more info

[Latest beta version](https://kuroba.io:8443/latest_apk)

[All beta versions](https://kuroba.io:8443/apks/0) (or [here](https://github.com/K1rakishou/Kuroba-Experimental/releases) look for "Pre-release" versions)

### [Latest release version (v0.2.10)](https://github.com/K1rakishou/Kuroba-Experimental/releases/tag/v0.2.10-release)

[F-Droid status](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/7450)

##### Currently supported sites
- 4Chan
- Dvach
- 8Kun (thanks to @jirn073-76)
- 420Chan (thanks to @Lolzen)
- Lainchan
- Sushichan
- Wired-7 (thanks to @Wired-7)

##### Currently supported 4chan archives
- ArchivedMoe
- ArchiveOfSins
- B4k
- DesuArchive
- Fireden 
- 4Plebs 
- Nyafuu 
- RebeccaBlackTech
- TokyoChronos

## License
[Kuroba is GPLv3](https://github.com/K1rakishou/Kuroba-Experimental/blob/develop/COPYING.txt), [licenses of the used libraries.](https://github.com/K1rakishou/Kuroba-Experimental/blob/develop/Kuroba/app/src/main/assets/html/licenses.html)
