# Building the player manually for local installation.

(The scripts are taken from https://github.com/mpv-android/mpv-android repository and slightly modified)

## Download dependencies

`download.sh` will take care of installing the Android SDK, NDK and downloading the sources.

If you're running on Debian/Ubuntu or RHEL/Fedora it will also install the necessary dependencies for you.

```
./download.sh
```

## Build

0. Make sure that player_version (located in jni/main.cpp) is the same as MPVLib.SUPPORTED_MPV_PLAYER_VERSION.
1. Figure out what CPU architecture (ABI) your phone supports.
2. Run one of the following commands to build the libraries for your ABI.

```
./buildall.sh --arch armv7l mpv
./buildall.sh --arch arm64 mpv
./buildall.sh --arch x86_64 mpv
./buildall.sh --arch x86 mpv
```

3. Run the following command to build the player library.
```
./buildall.sh
```

Go to app/src/main/libs and grab the libs for your ABI.
Upload them to your devices and then use "Install mpv libraries from local directory".
Alternatively you can copy them manually (via adb) into the following directory.
"/data/data/com.github.k1rakishou.chan.<flavor-name-goes-here>/files/mpv_native_libs/".
Don't forget to restart the application.

# Credits, notes, etc

Huge thanks to https://github.com/mpv-android/mpv-android devs for making these scripts.