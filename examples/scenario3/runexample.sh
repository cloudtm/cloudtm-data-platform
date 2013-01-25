#!/bin/bash

WORKING_DIR=`cd $(dirname $0); pwd`

BACKEND=pt.ist.fenixframework.backend.infinispan.InfinispanCodeGenerator
ISPN_CONFIG=${WORKING_DIR}/src/main/resources/ispn-repl.xml
HOSTNAME=`hostname -s`

help_and_exit() {
  echo "usage: $0 [-infinispan] [-ogm] [-repl] [-dist] [-h|-help] [maven opts]"
  echo "  -infinispan: uses infinispan backend (default)"
  echo "  -ogm:        uses Hibernate OGM backed"
  echo "  maven opts:  options passed to maven"
  echo "  -h|-help:    shows this message"
  echo "  -repl:       uses replicated cache (default)"
  echo "  -dist:       uses distributed cache"
  exit 0;
}

while [ -n "$1" ]; do
  case $1 in
    -infinispan) BACKEND=pt.ist.fenixframework.backend.infinispan.InfinispanCodeGenerator; shift 1;;
    -ogm) BACKEND=pt.ist.fenixframework.backend.ogm.OgmCodeGenerator; shift 1;;
    -repl|-dist) ISPN_CONFIG=${WORKING_DIR}/target/classes/ispn${1}.xml; shift 1;;
    -h|-help) help_and_exit;;
    *) ARGS="${ARGS} $1" ; shift 1;;
  esac
done

cd ${WORKING_DIR}
#Compile
mvn clean package -DskipTests -Dfenixframework.code.generator=$BACKEND

cp ${ISPN_CONFIG} ${WORKING_DIR}/target/classes/infinispan.xml

#start gossip router
mvn exec:java -DskipTests -Dexec.mainClass="org.jgroups.stack.GossipRouter" &
GOSSIP_ROUTER_PID=$!
sleep 10

#start node A
MAVEN_OPTS="-Xmx1G -Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=${HOSTNAME}" mvn exec:java -Dfenixframework.code.generator=$BACKEND -DskipTests -Dexec.mainClass="test.MainApp" -Dexec.args="node_a 2" $ARGS &
PID=$!

#start node B
MAVEN_OPTS="-Xmx1G -Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=${HOSTNAME}" mvn exec:java -Dfenixframework.code.generator=$BACKEND -DskipTests -Dexec.mainClass="test.MainApp" -Dexec.args="node_b 2" $ARGS
wait $PID
#kill Gossip Router
kill ${GOSSIP_ROUTER_PID}
exit 0
