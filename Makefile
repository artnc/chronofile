MAKEFLAGS += --silent

.PHONY: log
log:
	adb logcat -s -v color "Chronofile"
