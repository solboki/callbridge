#!/bin/sh
# Builds Callbridge.apk without Gradle or Android Studio.
# Requirements (Debian/Ubuntu):
#   apt install openjdk-17-jdk aapt dalvik-exchange zipalign apksigner android-sdk-platform-23
set -e
cd "$(dirname "$0")"

SDK=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
DX=${DX:-dalvik-exchange}
B=build
rm -rf "$B"
mkdir -p "$B/gen" "$B/obj"

python3 make_icons.py

# 1. Resources and R.java
aapt package -f -m -J "$B/gen" -M AndroidManifest.xml -S res -I "$SDK" \
    -F "$B/unsigned.apk"

# 2. Compile Java (Java 8 bytecode, no lambdas)
javac -source 8 -target 8 -bootclasspath "$SDK" -encoding UTF-8 \
    -Xlint:-options -d "$B/obj" "$B"/gen/org/callbridge/relay/R.java \
    src/org/callbridge/relay/*.java

# 3. Convert to Dalvik bytecode
$DX --dex --min-sdk-version=21 --output="$B/classes.dex" "$B/obj"

# 4. Add code to the APK, align, sign
(cd "$B" && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 "$B/unsigned.apk" "$B/aligned.apk"

KS=${KEYSTORE:-callbridge.keystore}
if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore "$KS" -storepass callbridge -keypass callbridge \
        -alias callbridge -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Callbridge, O=Callbridge, C=FR" >/dev/null 2>&1
fi
apksigner sign --ks "$KS" --ks-pass pass:callbridge --key-pass pass:callbridge \
    --out Callbridge.apk "$B/aligned.apk"
apksigner verify Callbridge.apk
echo "OK : $(pwd)/Callbridge.apk"
