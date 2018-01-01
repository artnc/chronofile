MAKEFLAGS += --silent

.PHONY: log
log:
	adb logcat -s -v time,color AndroidRuntime Chronofile \
	  | sed -E 's/ .\/[A-Za-z]+\([0-9]+\)://' \
	  | sed -E 's/[0-9]{2}-[0-9]{2} ([0-9]{2}:[0-9]{2}:[0-9]{2})/\1/'
