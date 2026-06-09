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
