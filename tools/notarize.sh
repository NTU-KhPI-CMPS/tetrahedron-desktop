#!/bin/bash

set -e

CERT_NAME=""
APP_VERSION="7.1.0"

cd ../dist

(mkdir Tetrahedron.app/Contents/app/Tetrahedron \
  && cd Tetrahedron.app/Contents/app/Tetrahedron \
  && jar xf ../Tetrahedron.jar > /dev/null)

find Tetrahedron.app/Contents/app/Tetrahedron -name "*.dylib" -exec \
  codesign -s "$CERT_NAME" --options runtime --timestamp --force {} \;

rm Tetrahedron.app/Contents/app/Tetrahedron.jar
jar cf Tetrahedron.app/Contents/app/Tetrahedron.jar -C Tetrahedron.app/Contents/app/Tetrahedron . > /dev/null
rm -r Tetrahedron.app/Contents/app/Tetrahedron

find Tetrahedron.app -type f -exec sh -c '
  if file "$0" | grep -q "Mach-O"; then
    codesign -s "$CERT_NAME" --options runtime --timestamp --force "$0"
  fi
' {} \;
codesign -s "$CERT_NAME" --entitlements ../tools/Tetrahedron.entitlements --options runtime --timestamp --force \
   Tetrahedron.app

create-dmg \
  --volname "Tetrahedron" \
  --background ../tools/dmg-background.png \
  --window-size 660 420 \
  --icon-size 160 \
  --icon "Tetrahedron" 180 170 \
  --app-drop-link 480 170 \
  "Tetrahedron-$APP_VERSION.dmg" \
  "./"

xcrun notarytool submit Tetrahedron-$APP_VERSION.dmg --keychain-profile "notarytool-password" --wait
xcrun stapler staple Tetrahedron-$APP_VERSION.dmg
