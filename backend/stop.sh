#!/bin/bash

# TianShu Platform Cleanup Script
# This script removes all TianShu Platform resources from Minikube

# Color codes for better readability
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print section headers
print_header() {
    echo -e "\n${BLUE}========== $1 ==========${NC}\n"
}

# Function to check if a command was successful
check_status() {
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ $1 successful${NC}"
    else
        echo -e "${RED}✗ $1 failed (continuing anyway)${NC}"
    fi
}

# Ask for confirmation before proceeding
read -p "This will delete all TianShu platform resources from your Minikube cluster. Are you sure? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]
then
    echo -e "${YELLOW}Cleanup cancelled.${NC}"
    exit 1
fi

print_header "Cleaning up Lab Environments"

# Delete any lab environment resources first (created by lab-orchestration-service)
echo -e "${YELLOW}Deleting all lab environment resources...${NC}"
kubectl delete deployment,service,ingress,pod -n default -l instanceType=lab-environment

check_status "Lab environment resources deletion"

print_header "Removing Platform Resources"

# Delete Ingress for the platform
echo -e "${YELLOW}Deleting platform Ingress...${NC}"
kubectl delete ingress tianshu-platform-ingress -n default
check_status "Platform Ingress deletion"

# Delete lab-orchestration-service
echo -e "${YELLOW}Deleting lab-orchestration-service...${NC}"
kubectl delete service lab-orchestration-service-svc -n default
kubectl delete deployment lab-orchestration-service-deployment -n default
check_status "lab-orchestration-service deletion"

# Delete RBAC for lab-orchestration-service
echo -e "${YELLOW}Deleting lab-orchestration-service RBAC...${NC}"
kubectl delete serviceaccount lab-orchestrator-sa -n default
kubectl delete role lab-orchestrator-role
kubectl delete rolebinding lab-orchestrator-rolebinding
check_status "lab-orchestration-service RBAC deletion"

# Delete vulnerability-definition-service
echo -e "${YELLOW}Deleting vulnerability-definition-service...${NC}"
kubectl delete service vuln-def-service-svc -n default
kubectl delete deployment vuln-def-service-deployment -n default
check_status "vulnerability-definition-service deletion"

print_header "Stopping minikube tunnel"

# Stop minikube tunnel if it's running
if pgrep -f "minikube tunnel" > /dev/null; then
    echo -e "${YELLOW}Stopping minikube tunnel...${NC}"
    sudo pkill -f "minikube tunnel"
    sleep 3

    if pgrep -f "minikube tunnel" > /dev/null; then
        echo -e "${RED}Failed to stop minikube tunnel, trying with more force...${NC}"
        sudo pkill -9 -f "minikube tunnel"
        sleep 2
    fi

    if pgrep -f "minikube tunnel" > /dev/null; then
        echo -e "${RED}Failed to stop minikube tunnel. You may need to stop it manually.${NC}"
    else
        echo -e "${GREEN}Minikube tunnel stopped successfully${NC}"
    fi
else
    echo -e "${GREEN}Minikube tunnel is not running${NC}"
fi

print_header "Cleanup Options"

# Ask if user wants to stop/delete minikube
echo -e "${YELLOW}Do you want to also stop Minikube? (choose one)${NC}"
echo "1. Just stop Minikube (can be started again)"
echo "2. Delete Minikube completely (removes all data)"
echo "3. Do nothing (leave Minikube running)"
read -p "Enter your choice [1-3]: " minikube_choice

case $minikube_choice in
    1)
        echo -e "${YELLOW}Stopping Minikube...${NC}"
        minikube stop
        check_status "Minikube stop"
        ;;
    2)
        echo -e "${RED}Deleting Minikube completely...${NC}"
        minikube delete
        check_status "Minikube deletion"
        ;;
    *)
        echo -e "${GREEN}Leaving Minikube running${NC}"
        ;;
esac

print_header "Cleanup Complete"

echo -e "${GREEN}TianShu Platform resources have been removed!${NC}"
echo -e "${YELLOW}If you want to redeploy the platform, run:${NC}"
echo -e "  ./startup_tianshu.sh"

exit 0