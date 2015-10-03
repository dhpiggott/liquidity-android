#!/bin/bash

set -e

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
for file in $DIR/raw/activity/*; do composite -quality 100 -compose atop $DIR/clean-status-bar-activity.png $file $DIR/clean/activity/`basename $file`; done
for file in $DIR/raw/dialog/*; do composite -quality 100 -compose atop $DIR/clean-status-bar-dialog.png $file $DIR/clean/dialog/`basename $file`; done
