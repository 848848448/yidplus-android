#!/bin/bash
# qa/inplace.sh OLD.apk NEW.apk : install the public old APK, leave a marker in its private data, update in place, check.
# Needs a rootable emulator image (target default, userdebug) so the marker can be written and read in /data/data.
OLD=$1; NEW=$2; PKG=com.yidplus.app; OUT=qa/out; mkdir -p $OUT; R=$OUT/RESULT; L=$OUT/inplace.log
log() { echo "[$(date +%T)] $*" | tee -a $L; }
fail() { log "FAIL: $*"; echo "FAIL $*" > $R; exit 1; }
adb wait-for-device; until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
[ "$(adb shell id -u | tr -d '\r')" = 0 ] || fail "adb root not available on this image"
adb install -r "$OLD" >>$L 2>&1 || fail "could not install the old (public) APK"
OV=$(adb shell dumpsys package $PKG | grep -m1 versionCode | tr -d '\r'); log "old installed: $OV"
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 15
adb shell am force-stop $PKG; sleep 1
adb shell "mkdir -p /data/data/$PKG/files && echo qa-marker-keep > /data/data/$PKG/files/qa-inplace-marker && ls -la /data/data/$PKG/files/qa-inplace-marker" >>$L 2>&1
adb shell "ls /data/data/$PKG" | tr '\n' ' ' | tee -a $L; echo >> $L
log "data dir listing before update: $(adb shell ls /data/data/$PKG | tr '\n' ' ')"
adb install -r "$NEW" >$OUT/install-new.txt 2>&1; cat $OUT/install-new.txt >>$L
grep -q Success $OUT/install-new.txt || fail "in-place install of the candidate was refused: $(tr '\n' ' ' < $OUT/install-new.txt | cut -c1-200)"
NV=$(adb shell dumpsys package $PKG | grep -m1 versionCode | tr -d '\r'); log "new installed: $NV"
[ "$(adb shell cat /data/data/$PKG/files/qa-inplace-marker 2>/dev/null | tr -d '\r')" = qa-marker-keep ] || fail "private data marker did not survive the in-place update"
log "marker survived the update; data dir after update: $(adb shell ls /data/data/$PKG | tr '\n' ' ')"
adb logcat -c; adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 15
adb shell pidof $PKG >/dev/null 2>&1 || { adb logcat -d -t 200 | grep -E "FATAL|AndroidRuntime" | head -20 >>$L; fail "candidate is not running after the update"; }
adb exec-out screencap -p > $OUT/INPLACE-after-update.png
adb logcat -d | grep -E "FATAL EXCEPTION" | head -5 >>$L
grep -q "FATAL EXCEPTION" $L && fail "crash after the update (see inplace.log)"
echo "PASS old $OV -> new $NV; private data marker survived; candidate running" > $R; log "$(cat $R)"
