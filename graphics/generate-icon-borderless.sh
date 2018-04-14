#!/bin/bash

set -euo pipefail

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

inkscape -z \
    -e $DIR/icon-borderless.png \
    -w 512 \
    -h 512 \
    $DIR/icon-borderless.svg
