#!/bin/bash

set -e

APP_VERSION="7.1.0"

cd ..

mkdir tmp tmp/app
cp target/tetrahedron-desktop-$APP_VERSION-SNAPSHOT-jar-with-dependencies.jar tmp/app/Tetrahedron.jar

jlink --strip-debug --no-man-pages --no-header-files --compress zip-6 \
  --add-modules java.base,java.scripting,java.desktop,jdk.unsupported,jdk.unsupported.desktop --output tmp/jre

jpackage --input tmp/app --main-jar Tetrahedron.jar --main-class org.cmps.tetrahedron.Launcher --runtime-image tmp/jre \
  --name Tetrahedron --vendor "CMPS, KhPI" --app-version $APP_VERSION --icon src/main/resources/logo.icns \
  --mac-package-identifier cmps.tetrahedron --type app-image --dest dist

rm -rf tmp
