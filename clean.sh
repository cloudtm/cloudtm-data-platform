#!/bin/bash

WORKING_DIR=`cd $(dirname $0); pwd`
SRC=${WORKING_DIR}/src
EXAMPLES=${WORKING_DIR}/examples
PROJECTS="JGroups Infinispan Fenix-Framework Hibernate-Search Hibernate-OGM"
EX="scenario1 scenario2 scenario3 scenario4"

for project in ${PROJECTS}; do
cd ${SRC}/${project};
mvn clean
cd -;
done;

rm -r ${WORKING_DIR}/bin/* 2>/dev/null

for project in ${EX}; do
cd ${EXAMPLES}/${project}
mvn clean
cd -
done

rm -r ${EXAMPLES}/bin/* 2>/dev/null

exit 0
