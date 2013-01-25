#!/bin/bash

if [ $# -eq 0 ]; then
echo "Expected -start or -stop";
exit 1;
fi

WORKING_DIR=`cd $(dirname $0); pwd`;
MAIN_CLASS="org.jgroups.stack.GossipRouter"
D_VARS="-Djava.net.preferIPv4Stack=true"

#set the class path
CP=`ls ${WORKING_DIR}/lib/*.jar | grep jgroups`;

case "$1" in
  -start) CMD="java ${D_VARS} -cp ${CP} ${MAIN_CLASS}"; shift 1;;
  -stop) CMD="kill "`ps -ef | grep ${MAIN_CLASS} | grep -v grep | awk '{print $2}'`; shift 1;;
  *) echo "Unknown '$1'"; shift 1;;
esac

if [ "$CMD" == "" ]; then
echo "Expected -start or -stop";
exit 1;
fi

echo $CMD
eval $CMD &
exit 0
