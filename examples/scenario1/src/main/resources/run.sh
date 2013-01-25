#!/bin/bash

WORKING_DIR=`cd $(dirname $0); pwd`;
MAIN_CLASS="test.MainApp"
JVM_OPTIONS="-Xmx1G"

#set the class path
CP=${WORKING_DIR};
for jar in `ls ${WORKING_DIR}/lib/*.jar`;
do
CP="${CP}:${jar}";
done

CMD="java ${JVM_OPTIONS} -cp ${CP} ${MAIN_CLASS}"

echo $CMD
eval $CMD
exit 0