#!/bin/sh -e

REBUILD=true
BOSH_URI=http://jitsi-meet.george.jitsi/http-bind
ROOM_NAME=room1
USERS_COUNT=3
SC_HOME_DIR_NAME=.jitsi-hammer
SC_HOME_DIR=~/$SC_HOME_DIR_NAME
SRC_HOME=~/jitsi-hammer
LOG_HOME=$SC_HOME_DIR/log
VIDEO_RTPDUMP=$SRC_HOME/simulcast.rtpdump

if $REBUILD ; then
  mvn clean compile
fi

exec mvn exec:java -Dexec.args="-BOSHuri $BOSH_URI -room $ROOM_NAME -users $USERS_COUNT -videortpdump $VIDEO_RTPDUMP" -Djava.net.preferIPv4Stack=true -Djava.library.path=$SRC_HOME/lib/native/linux-64 -Djava.util.logging.config.file=$SRC_HOME/lib/logging.properties -Dnet.java.sip.communicator.SC_HOME_DIR_NAME="$SC_HOME_DIR_NAME" 2>&1 | tee $LOG_HOME/output.log
