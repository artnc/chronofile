MAKEFLAGS += --silent

.PHONY: log
log:
	adb logcat -s -v color AndroidRuntime Chronofile
