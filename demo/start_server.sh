#!/bin/bash
#
# https://github.com/WASdev/ci.docker.websphere-traditional/blob/main/docker-build/9.0.5.x/scripts/start_server.sh
# Modified to fix a bug when running the linux/amd64 image on ARM
# Macs under Rosetta where it fails to detect the WebSphere JVM running
# and eventually shuts down.
#
#####################################################################################
#                                                                                   #
#  Script to start the server and wait.                                             #
#                                                                                   #
#  Usage : start_server                                                             #
#                                                                                   #
#####################################################################################

PROFILE_NAME=${PROFILE_NAME:-"AppSrv01"}
SERVER_NAME=${SERVER_NAME:-"server1"}

update_hostname()
{
  wsadmin.sh -lang jython -conntype NONE -f /work/updateHostName.py ${NODE_NAME:-"DefaultNode01"} $(hostname)
  touch /work/hostnameupdated
}

start_server()
{
  echo "Starting server ..................."
  /opt/IBM/WebSphere/AppServer/profiles/$PROFILE_NAME/bin/startServer.sh $SERVER_NAME
}

run_logviewer(){
  echo "Starting logViewer ................"
  mkdir -p /opt/IBM/WebSphere/AppServer/profiles/AppSrv01/logs/server1/logdata
  touch /opt/IBM/WebSphere/AppServer/profiles/AppSrv01/logs/server1/logViewer.pos
  /opt/IBM/WebSphere/AppServer/bin/logViewer.sh -monitor 1 -resumable -resume -format json | grep --line-buffered "^{" &
}

stop_server()
{
  echo "Stopping server ..................."
  kill -s INT $PID 
}

applyConfigs(){
  if [ ! -z "$(ls /etc/websphere)" ]; then
    echo "+ Found config-files under /etc/websphere. Executing..."
    find /etc/websphere -type f \( -name \*.props -o -name \*.conf \) -print0 | sort -z | xargs -0 -n 1 -r /work/applyConfig.sh
  fi
}

configure_logging(){
  echo "Configure logging mode"
  /work/configure_logging.sh
}

ENABLE_BASIC_LOGGING=${ENABLE_BASIC_LOGGING:-"false"}
if [ "$ENABLE_BASIC_LOGGING" = false ]; then
  configure_logging
fi

applyConfigs

if [ "$EXTRACT_PORT_FROM_HOST_HEADER" = "true" ]; then
  /work/applyConfig.sh /work/config-ibm/webContainer.props
fi

if ! cmp -s "/tmp/passwordupdated" "/tmp/PASSWORD"; then
  /work/set_password.sh
fi

if [ "$UPDATE_HOSTNAME" = "true" ] && [ ! -f "/work/hostnameupdated" ]; then
  update_hostname
fi

trap "stop_server" TERM INT

if [ "$ENABLE_BASIC_LOGGING" = false ]; then
  echo "HPEL is enabled"
  rm -f /opt/IBM/WebSphere/AppServer/profiles/$PROFILE_NAME/logs/$SERVER_NAME/SystemOut.log*
  rm -f /opt/IBM/WebSphere/AppServer/profiles/$PROFILE_NAME/logs/$SERVER_NAME/SystemErr.log*
  run_logviewer
fi

start_server || exit $?
PID=$(ps -C java -o pid=,cmd= | grep com.ibm.ws.runtime.WsServer | awk '{print $1}')

if [ "$ENABLE_BASIC_LOGGING" = true ]; then
  echo "Basic Logging is enabled"
  tail -F /opt/IBM/WebSphere/AppServer/profiles/$PROFILE_NAME/logs/$SERVER_NAME/SystemOut.log --pid $PID -n +0 &
  tail -F /opt/IBM/WebSphere/AppServer/profiles/$PROFILE_NAME/logs/$SERVER_NAME/SystemErr.log --pid $PID -n +0 >&2 &
fi

if [ "$ENABLE_BASIC_LOGGING" = true ]; then
  while [ -e "/proc/$PID" ]; do
    sleep 1
  done
else
  while [ -e "/proc/$PID" ]; do
    LOGVIEWER_PS=$(ps auxww | grep logViewer | grep -v grep)
    if [ -z "$LOGVIEWER_PS" ]; then
        run_logviewer
    fi
    sleep 1
  done

  LOGVIEWER_PID=$(ps -C logViewer.sh -o pid= | tr -d " ")
  if [ $? -eq 0 ]; then
    # give server time to flush logs and logViewer time to send them
    sleep 15
    echo "Stopping logViewer ................"
    kill -9 $LOGVIEWER_PID
    exit 0
  fi
fi
