#!/bin/bash

WORKING_DIR=`cd $(dirname $0); pwd`

BACKEND=pt.ist.fenixframework.backend.infinispan.InfinispanCodeGenerator

help_and_exit() {
  echo "usage: $0 [-infinispan] [-ogm] [-h|-help] [maven opts]"
  echo "  -infinispan: uses infinispan backend (default if no option is given)"
  echo "  -ogm:        uses Hibernate OGM backed"
  echo "  maven opts:  options passed to maven"
  echo "  -h|-help:    shows this message"
  exit 0;
}

while [ -n "$1" ]; do
  case $1 in  
    -infinispan) BACKEND=pt.ist.fenixframework.backend.infinispan.InfinispanCodeGenerator; shift 1;;
    -ogm) BACKEND=pt.ist.fenixframework.backend.ogm.OgmCodeGenerator; shift 1;;
    -h|-help) help_and_exit;;
    *) ARGS="${ARGS} $1" ; shift 1;;
  esac
done

cd ${WORKING_DIR}
MAVEN_OPTS="-Xmx1G" mvn clean package exec:java -Dfenixframework.code.generator=$BACKEND -DskipTests -Dexec.mainClass="test.MainApp" $ARGS
exit 0
