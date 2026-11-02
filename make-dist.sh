#!/bin/sh
# Builds the APK, the source archive and the Sailfish RPM into dist/.
set -e
cd "$(dirname "$0")"
V=1.4.1
TOP=$(pwd)

sh android/build.sh

rm -rf dist rpmbuild
mkdir -p dist rpmbuild/SOURCES rpmbuild/SPECS

# Source archive (without the signing key or built files)
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
