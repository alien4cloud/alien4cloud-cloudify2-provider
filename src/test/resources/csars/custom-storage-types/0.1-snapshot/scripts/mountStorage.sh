#!/bin/bash

DEVICE=$1
STORAGE_PATH=$2
echo "whoami `whoami`"
sudo mount $DEVICE $STORAGE_PATH