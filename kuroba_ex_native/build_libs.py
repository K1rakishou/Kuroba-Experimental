import subprocess
import sys,os
import shutil

def download_and_extract_ndk():
	ndkDir = os.path.realpath("../ndk")
	ndkName = "android-ndk-r22b-linux-x86_64"
	ndkUrl = 'https://dl.google.com/android/repository/{}.zip'.format(ndkName)

	if (os.path.exists(ndkDir)):
		print("Deleting existing NDK directory ({})...".format(ndkDir))
		shutil.rmtree(ndkDir)
		os.makedirs(ndkDir)
		print("Done")
	else:
		os.makedirs(ndkDir)

	ndkOutZipFile = os.path.join(ndkDir, ndkName + ".zip")
	ndkExtractedDir = os.path.join(ndkDir, ndkName)

	print("ndkOutZipFile={}".format(ndkOutZipFile))
	print("ndkExtractedDir={}".format(ndkExtractedDir))

	print("Downloading NDK ({}) into file ({})...".format(ndkUrl, ndkOutZipFile))
	print(subprocess.check_call(['wget', '-O', ndkOutZipFile, ndkUrl]))
	print("Done")

	print("Extracting NDK archive ({}) into directory ({})".format(ndkOutZipFile, ndkExtractedDir))
	print(subprocess.check_call(['unzip', ndkOutZipFile, '-d', ndkExtractedDir]))
	print("Done")

if __name__ == "__main__":
	buildType = "debug" #TODO: change to "release" when doing release
	libName = 'libkuroba_ex_native.so'
	jniLibsAndroidDir = os.path.realpath("../Kuroba/app/src/main/jniLibs")
	jniLibsBuildDir = os.path.realpath("target")

	if buildType != "debug":
		download_and_extract_ndk()

	if os.path.exists(jniLibsBuildDir):
		print("Deleting existing jni libs target directory...")
		shutil.rmtree(jniLibsBuildDir)
		print("Done")

	targets = []
	androidJniLibDirs = []

	if buildType == "debug":
		targets = ['i686-linux-android']
		androidJniLibDirs = ['x86']
	else:
		targets = ['aarch64-linux-android', 'armv7-linux-androideabi', 'i686-linux-android']
		androidJniLibDirs = ['arm64-v8a', 'armeabi-v7a', 'x86']

	for index, target in enumerate(targets, start=0):
		print("Building {} with build type {}...".format(target, buildType))

		if buildType == "debug":
			print(subprocess.check_call(['cargo', 'build', '--target', target]))
		else:
			print(subprocess.check_call(['cargo', 'build', '--target', target, "--release"]))

		print("Done")

		androidJniLibDir = os.path.join(jniLibsAndroidDir, androidJniLibDirs[index])

		if os.path.exists(androidJniLibDir):
			print("Removing old libraries in {}...".format(androidJniLibDir))
			shutil.rmtree(androidJniLibDir)
			os.makedirs(androidJniLibDir)
			print("Done")
		else:
			os.makedirs(androidJniLibDir)

		sourceFile = os.path.join(jniLibsBuildDir, target, buildType, libName)
		destinationFile = os.path.join(androidJniLibDir, libName)

		print("sourceFile={}".format(sourceFile))
		print("destinationFile={}".format(destinationFile))

		print("Copying {} to {}...".format(sourceFile, destinationFile))
		shutil.copy(sourceFile, destinationFile)
		print("Done")

		print("Building {} Done".format(target))

	print("All done, exiting")
