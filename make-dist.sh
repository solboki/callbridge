#!/bin/sh
# Construit l'APK, l'archive des sources et le RPM Sailfish dans dist/.
set -e
cd "$(dirname "$0")"
V=1.3.0
TOP=$(pwd)

sh android/build.sh

rm -rf dist rpmbuild
mkdir -p dist rpmbuild/SOURCES rpmbuild/SPECS

# Archive des sources (sans la clé de signature ni les fichiers construits)
tar --transform "s,^\.,callbridge-$V," \
    --exclude=./.git --exclude=./dist --exclude=./rpmbuild --exclude=./android/build \
    --exclude='*.keystore' --exclude='*.idsig' --exclude='__pycache__' \
    -czf "rpmbuild/SOURCES/callbridge-$V.tar.gz" .
cp "rpmbuild/SOURCES/callbridge-$V.tar.gz" dist/

rpmbuild -bb --define "_topdir $TOP/rpmbuild" rpm/callbridge.spec
cp rpmbuild/RPMS/noarch/callbridge-$V-1.noarch.rpm dist/
cp android/Callbridge.apk "dist/Callbridge-$V.apk"
rm -rf rpmbuild
ls -l dist
