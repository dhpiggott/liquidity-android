#!/bin/bash

set -e

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
inkscape -z -e $DIR/app/src/main/res/mipmap-mdpi/ic_launcher.png -w 48 -h 48 icon.svg
inkscape -z -e $DIR/app/src/main/res/mipmap-hdpi/ic_launcher.png -w 72 -h 72 icon.svg
inkscape -z -e $DIR/app/src/main/res/mipmap-xhdpi/ic_launcher.png -w 96 -h 96 icon.svg
inkscape -z -e $DIR/app/src/main/res/mipmap-xxhdpi/ic_launcher.png -w 144 -h 144 icon.svg
inkscape -z -e $DIR/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png -w 192 -h 192 icon.svg
inkscape -z -e $DIR/icon.png -w 512 -h 512 icon.svg
