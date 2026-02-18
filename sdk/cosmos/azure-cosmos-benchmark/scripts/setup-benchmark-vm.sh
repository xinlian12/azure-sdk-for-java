#!/bin/bash
# setup-benchmark-vm.sh — Run once after VM creation to install dependencies
# See §6.3 of the test plan.

set -euo pipefail

echo "=== Setting up benchmark VM ==="

# JDK
sudo apt-get update && sudo apt-get install -y openjdk-21-jdk maven git

# File descriptor limits
echo "* soft nofile 1048576" | sudo tee -a /etc/security/limits.conf
echo "* hard nofile 1048576" | sudo tee -a /etc/security/limits.conf
echo "session required pam_limits.so" | sudo tee -a /etc/pam.d/common-session
sudo mkdir -p /etc/systemd/system/user@.service.d
echo -e "[Service]\nLimitNOFILE=1048576" | sudo tee /etc/systemd/system/user@.service.d/limits.conf

# Networking tools
sudo apt-get install -y net-tools iproute2 sysstat

# Async-profiler
wget -qO /tmp/async-profiler.tar.gz \
  https://github.com/async-profiler/async-profiler/releases/download/v3.0/async-profiler-3.0-linux-x64.tar.gz
sudo tar -xzf /tmp/async-profiler.tar.gz -C /opt/
echo 'export PATH=$PATH:/opt/async-profiler-3.0-linux-x64/bin' >> ~/.bashrc

# Kernel tuning for high connection count
echo "net.core.somaxconn = 65535" | sudo tee -a /etc/sysctl.conf
echo "net.ipv4.tcp_max_syn_backlog = 65535" | sudo tee -a /etc/sysctl.conf
echo "net.ipv4.ip_local_port_range = 1024 65535" | sudo tee -a /etc/sysctl.conf
echo "net.ipv4.tcp_tw_reuse = 1" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p

# Clone SDK
git clone https://github.com/Azure/azure-sdk-for-java.git ~/azure-sdk-for-java
cd ~/azure-sdk-for-java

# Build benchmark module
mvn install -pl sdk/cosmos/azure-cosmos -am -DskipTests
mvn package -pl sdk/cosmos/azure-cosmos-benchmark -DskipTests

echo "=== Setup complete ==="
echo "Next: Set APPLICATIONINSIGHTS_CONNECTION_STRING and create tenants.json"
