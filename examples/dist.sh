#!/bin/bash

WORKING_DIR=`cd $(dirname $0); pwd`;

cd $WORKING_DIR
mkdir bin 2>/dev/null

for i in scenario1 scenario2 scenario3 scenario4;
do
cd $i;
echo "Processing $i"
mvn clean package && cp target/${i}*.zip ../bin/${i}-ispn.zip;
mvn clean package -Dcode.generator=pt.ist.fenixframework.backend.ogm.OgmCodeGenerator && cp target/${i}*.zip ../bin/${i}-ogm.zip;
cd -;
done;
