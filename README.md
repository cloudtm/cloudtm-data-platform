Cloudtm Demos
=============

### Pre Requirements

#### To run runexample.sh scripts

* Maven (>3.0.0)
* java(c) (1.6, preferable from sun-jdk)

#### To run the compiled code

* java (1.6, preferable from sun-jdk)

## Scenario 1

Simple example with Fénix Framework to demonstrate how to use. This scenario allows you to try two different backends: 

 * Hibernate OGM: uses Hibernate OGM to store the Domain Objects in Infinispan
 * Infinispan: uses Infinispan directly to store the Domain Objects

Execute the script in scenario1/runexample.sh to try. Use one of the following options;

 * -ogm: uses OGM backend
 * -infinispan: (selected by default) uses Infinispan backend

You can use the option -help to show the help message.

### Pre-compiled version

1. unzip the zip file
  $ #note: this will create a folder named scenario1. The -ogm prefix is the code compiled with Hibernate OGM backend and -ispn with Infinispan backend
	$ unzip scenario1<-ogm or -ispn>.zip
	
2. run the script run.sh
	$ cd scenario1
	$ ./run.sh

## Scenario 2

Similar to scenario 1 but with Hibernate Search enabled. See previous point to see how to start it (replacing scenario1 to scenario2).

## Scenario 3

Clustered version of scenario 2. In this scenario you run two processes (each one simulating a node) and you can configure if the data is replicated (i.e. it exists in both processes) or if it is distributed (it exists in one of the processes).

Execute the script in scenario3/runexample.sh to try. Use one of the following options;

 * -ogm: uses OGM backend
 * -infinispan: (selected by default) uses Infinispan backend

and then, select one of the following options:

 * -repl: (selected by default) replicated data
 * -dist: distributed data

You can use the option -help to show the help message.

### Pre-compiled version

Note: this process is to be done in all the machines that you want to form a cluster

1. unzip the zip file
  $ #note: this will create a folder named scenario3. The -ogm prefix is the code compiled with Hibernate OGM backend and -ispn with Infinispan backend
	$ unzip scenario3<-ogm or -ispn>.zip

2. starts the gossip router (well known process where each process connects to form the cluster)
	$ cd scenario3
	$ ./gossipRouter.sh
	
2. configure JGroups (if you are going to try in the cluster environment. By default, it looks for the gossip router in localhost)
	$ vi jgroups.xml
	
	and replace <TCPGOSSIP initial_hosts="${jgroups.bind_addr}[12001]" ... with <TCPGOSSIP initial_hosts="<gossip router hostname>[12001]".
	Do the same for hs-jgroups.xml
	
	$ vi hs-jgroups.xml

3. choose if you want to run in replicated or distributed mode
	$ #the prefix repl uses replicated mode and the prefix dist uses distributed mode
	$ cp ispn-<repl|dist>.xml infinispan.xml

4. run the script run.sh
	$ ./run.sh

## Scenario 4

Similar to scenario 3 but it uses a persistent cache store. The data written by both processes are stored in the file system in /tmp/fs-store

See previous point to see how to start this scenario.

### Pre-compiled version

Before running the script, you need to configure the persistence. By default, it assumes that all processes have access to the same folder and only one process will update the files. 

However, for a clustered environment, each machine has it owns persistence. In this scenario, you need to set the persistence as non-shared in infinispan.xml. Set the shared=false in <loaders passivation="false" shared="true" preload="false">