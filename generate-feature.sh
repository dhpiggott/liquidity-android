#!/bin/bash

set -e

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
inkscape -z -e $DIR/feature.png -w 1024 -h 500 feature.svg
