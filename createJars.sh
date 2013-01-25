#!/bin/bash

WORKING_DIR=`cd $(dirname $0); pwd`
BIN=${WORKING_DIR}/bin
SRC=${WORKING_DIR}/src

#should be by this order
PROJECTS="JGroups Infinispan Hibernate-Search Hibernate-OGM Fenix-Framework"

copy_jars() {
mkdir -p ${BIN}/$1
for jar in `find . -iname *.jar | grep target`;
do
cp $jar ${BIN}/$1;
done;
}

copy_dependencies() {
mkdir -p ${BIN}/$1
mvn dependency:copy-dependencies -DoutputDirectory=${BIN}/$1
}

for project in ${PROJECTS}; do
cd ${SRC}/${project};
rm -r ${BIN}/${project} 2>/dev/null
mvn clean install -DskipTests && copy_jars $project && copy_dependencies $project
cd -;
done;

exit 0
