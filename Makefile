MAKEFLAGS += --silent

.PHONY: log
log:
	adb logcat -s -v time,color AndroidRuntime Chronofile
