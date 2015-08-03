#!/bin/bash

DEVICE=$1
STORAGE_PATH=$2
echo "whoami `whoami`"
echo "mounting $DEVICE to $STORAGE_PATH"
sudo mount $DEVICE $STORAGE_PATH