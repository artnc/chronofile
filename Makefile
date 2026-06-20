MAKEFLAGS += --silent

# https://www.naturalearthdata.com/downloads/50m-cultural-vectors/50m-admin-0-countries/
MAP_URL ?= https://naturalearth.s3.amazonaws.com/50m_cultural/ne_50m_admin_0_countries.zip

.PHONY: apk
apk:
	./gradlew assembleRelease
	find app -name '*.apk' | grep release

# Generate the offline basemap asset
.PHONY: basemap
basemap:
	mkdir -p app/src/main/assets
	rm -rf /tmp/basemap && mkdir -p /tmp/basemap
	curl -sL '$(MAP_URL)' -o /tmp/basemap.zip
	unzip -o /tmp/basemap.zip -d /tmp/basemap
	npx -y mapshaper /tmp/basemap/*.shp \
	  -filter 'NAME !== "Antarctica"' \
	  -simplify 20% -filter-fields -o format=geojson precision=0.01 /tmp/basemap.geojson
	jq -Sc . /tmp/basemap.geojson > app/src/main/assets/countries.geojson
	ls -la app/src/main/assets/countries.geojson

.PHONY: log
log:
	adb logcat -v time,color --pid=$$(adb shell pidof -s com.chaidarun.chronofile) \
	  | sed -uE 's/ .\/[A-Za-z]+\([0-9]+\)://' \
	  | sed -uE 's/[0-9]{2}-[0-9]{2} ([0-9]{2}:[0-9]{2}:[0-9]{2})/\1/'

.PHONY: release
release:
	echo '$(V)' | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$$' \
	  || { echo 'Usage: make release V=X.Y.Z'; exit 1; }
	perl -i -pe 's/(versionCode = )(\d+)/$$1.($$2+1)/e; \
	  s/(versionName = ").*(")/$${1}$(V)$$2/' app/build.gradle.kts
	git commit -am 'Release v$(V)'
	git tag $(V)

.PHONY: screenshots
screenshots:
	dest=fastlane/metadata/android/en-US/images/phoneScreenshots; \
	rm -f $$dest/*.png; \
	i=0; \
	for src in $$(ls /tmp/attachments/*.png | sort); do \
	  i=$$((i + 1)); \
	  magick "$$src" -gravity North -chop 0x174 -gravity South -chop 0x126 \
	    -strip "$$(printf '%s/%02d.png' "$$dest" "$$i")"; \
	done
