#!/bin/bash
# this bash script wraps another script by sourcing it and echos expected env variables known as EXPECTED_OUTPUTS
# source the script passed as first arg, other args are also passed
. $1 $@
# EXPECTED_OUTPUTS should contains a semicolon separated list of output names
for i in $(echo $EXPECTED_OUTPUTS | tr ";" "\n")
do
  # we prefix the output name to avoid collisions
  echo EXPECTED_OUTPUT_$i=${!i}
done
# we need to sleep in order to ensure the output will be handled !
sleep 1