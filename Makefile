MAKEFLAGS += --silent

.PHONY: apk
apk:
	# Gradle doesn't support Java 17 yet
	PATH="/usr/lib/jvm/java-11-openjdk/bin:${PATH}" ./gradlew assembleRelease
	find app -name '*.apk' | grep release

.PHONY: log
log:
	adb logcat -s -v time,color AndroidRuntime Chronofile \
	  | sed -uE 's/ .\/[A-Za-z]+\([0-9]+\)://' \
	  | sed -uE 's/[0-9]{2}-[0-9]{2} ([0-9]{2}:[0-9]{2}:[0-9]{2})/\1/'
