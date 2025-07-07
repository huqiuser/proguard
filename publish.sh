#!/bin/bash
./gradlew clean --no-daemon
./gradlew publishToMavenLocal
cp -r /home/xc/.m2/repository/com/guardsquare /media/xc/T5/code/cmgit/huqix/android-res-guard/maven/com