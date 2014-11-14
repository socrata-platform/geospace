#!/bin/bash

DEV_ROOT="$HOME/Developer/Socrata/"
ENVFILE="/tmp/geospace-env"     # This is the boot2docker vm path.

if ! [ "$(nc -z -G 1 localhost 2181)" ]; then
    echo "Starting ZooKeeper (using sudo)..."
    echo
    sudo zkServer start
    sleep 0.1
    echo
fi

if ! [ "$(nc -z -G 1 localhost 6020)" ]; then
    $DEV_ROOT/onramp/start_scripts/soda-fountain start
    sleep 0.1
    echo
fi

ping -c 2 jenkins.sea1.socrata.com >/dev/null 2>/dev/null \
    || { echo "VPN connection required to download artifact(s)." ; exit ; }

boot2docker init

if [ ! -e "$DEV_ROOT/armada/.git" ]; then
    cd "$DEV_ROOT"
    git clone --recursive git@git.socrata.com:armada.git
fi

VBoxManage sharedfolder add 'boot2docker-vm' \
    --name 'armada' \
    --hostpath "$DEV_ROOT/armada" \
    --automount 2>/dev/null

echo "Starting VM..."
$(boot2docker up 2>&1 | tail -n 4)

ADDRS=$(ifconfig | grep 'inet ' | awk '{print $2}' | grep -v 127.0.0.1)

ENSEMBLE=$(
echo "$ADDRS" | while read addr; do
    echo "$addr:2181"
done)

ENSEMBLE=$(echo $ENSEMBLE | sed 's/ /, /g')

boot2docker ssh <<EOF
    mkdir armada
    sudo mount -t vboxsf -o uid=1000,gid=50 armada armada
    cd armada/internal/geospace
    docker build --rm -t geospace .
    echo ZOOKEEPER_ENSEMBLE=[$ENSEMBLE] > $ENVFILE
    sed -i 's/\[/["/' $ENVFILE
    sed -i 's/, /", "/g' $ENVFILE
    sed -i 's/]/"]/' $ENVFILE
    echo starting geospace
    docker run --env-file=$ENVFILE -p 2020:2020 -d geospace
EOF

echo 'Forwaring localhost:2020 to boot2docker (Ctrl+C to exit)'
echo "boot2docker ssh -L 2020:localhost:2020 -N"
boot2docker ssh -L 2020:localhost:2020 -N
