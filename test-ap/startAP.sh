#!/bin/bash
#
# Simple test script to launch a vulnerable access point
# see hostapd config files
# 
# Run as root
# Usage: startAP.sh [device] <stop>
#
# Requires systemd dhcpcd service, hostapd
#
device=$1
conf="hostapd.conf"
if [ -z $device ]; then
    echo -e "Run as root\nUsage: startAP.sh [device] <stop>"
    exit 1
elif [ $2 = "stop" ]; then
    ip addr flush $device
    ip link set $device down
    systemctl stop dhcpd4@$device.service
    pkill hostapd
    exit 1
else
    sed -i 's/interface=.*/interface='$device'/' wpa-hostapd.conf 
    hostapd $conf&
    ip link set $device up
    ip addr add 139.96.30.100/24 dev $device
    systemctl start dhcpd4@$device.service
fi
