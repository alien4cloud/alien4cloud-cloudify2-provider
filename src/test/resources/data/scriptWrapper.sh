#!/bin/bash
# source the script passed as first arg, other args are also passed
. $1 $@
# EXPECTED_OUTPUTS should contains a semi comma separated list of output names
for i in $(echo $EXPECTED_OUTPUTS | tr ";" "\n")
do
  # we prefix the output name to avoid collisions
  echo EXPECTED_OUTPUT_$i=${!i}
done
# we need to sleep in order to ensure the output will be handled !
sleep 1