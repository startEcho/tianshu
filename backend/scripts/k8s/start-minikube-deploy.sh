#!/bin/bash

# TianShu Platform startup script.
#
# Runtime call chain (high level):
# 1) Prepare Minikube + ingress controller + tunnel.
# 2) Deploy definition service (Deployment + Service).
# 3) Deploy orchestration service (RBAC + Deployment + Service).
# 4) Deploy platform ingress rules (prefix routing + rewrite).
# 5) Verify service reachability through ingress.

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
        echo -e "${RED}✗ $1 failed${NC}"
        exit 1
    fi
}

# Function to wait for pods to be ready
wait_for_pods() {
    local label=$1
    local namespace=$2
    local timeout=120
    local counter=0

    echo -e "${YELLOW}Waiting for pods with label $label in namespace $namespace to be ready...${NC}"

    while [ $counter -lt $timeout ]; do
        ready=$(kubectl get pods -l $label -n $namespace -o jsonpath='{.items[*].status.containerStatuses[0].ready}' 2>/dev/null | tr ' ' '\n' | grep -c "true")
        total=$(kubectl get pods -l $label -n $namespace --no-headers 2>/dev/null | wc -l)
        echo "ready='${ready}'"
        echo "total='${total}'"
        if [ "$ready" -eq "$total" ] && [ "$total" -gt "0" ]; then
            echo -e "${GREEN}All pods are ready!${NC}"
            return 0
        fi

        echo -n "."
        sleep 2
        ((counter+=2))
    done

    echo -e "\n${RED}Timeout waiting for pods to be ready${NC}"
    return 1
}

# Function to wait for a service to be ready
wait_for_service() {
    local service_name=$1
    local namespace=$2
    local timeout=60
    local counter=0

    echo -e "${YELLOW}Waiting for service $service_name in namespace $namespace to be available...${NC}"

    while [ $counter -lt $timeout ]; do
        if kubectl get service $service_name -n $namespace &>/dev/null; then
            echo -e "${GREEN}Service $service_name is available!${NC}"
            return 0
        fi

        echo -n "."
        sleep 2
        ((counter+=2))
    done

    echo -e "\n${RED}Timeout waiting for service $service_name${NC}"
    return 1
}

# Main script starts here
print_header "Checking Minikube Status"

# Check if Minikube is running
if minikube status | grep -q 'Running'; then
    echo -e "${GREEN}Minikube is already running${NC}"
else
    echo -e "${YELLOW}Starting Minikube...${NC}"
    minikube start
    check_status "Minikube start"
fi

# Check kubectl context
current_context=$(kubectl config current-context)
if [ "$current_context" != "minikube" ]; then
    echo -e "${YELLOW}Switching kubectl context to minikube...${NC}"
    kubectl config use-context minikube
    check_status "kubectl context switch"
fi

print_header "Enabling Nginx Ingress Controller"

# Enable ingress addon if not already enabled
if ! minikube addons list | grep -q "ingress: enabled"; then
    minikube addons enable ingress
    check_status "Enabling ingress addon"
else
    echo -e "${GREEN}Ingress addon is already enabled${NC}"
fi

# Wait for ingress controller pods to be ready
echo -e "${YELLOW}Waiting for ingress-nginx controller to be ready...${NC}"
wait_for_pods "app.kubernetes.io/component=controller" "ingress-nginx"
check_status "Ingress controller readiness check"

print_header "Starting minikube tunnel in background"

# Check if minikube tunnel is already running
if pgrep -f "minikube tunnel" > /dev/null; then
    echo -e "${GREEN}Minikube tunnel is already running${NC}"
else
    echo -e "${YELLOW}Starting minikube tunnel in the background...${NC}"
    # Start minikube tunnel in background
    sudo -b minikube tunnel > /tmp/minikube_tunnel.log 2>&1

    # Give it a moment to initialize
    sleep 5

    # Check if tunnel is running
    if pgrep -f "minikube tunnel" > /dev/null; then
        echo -e "${GREEN}Minikube tunnel started successfully${NC}"
    else
        echo -e "${RED}Failed to start minikube tunnel. Check /tmp/minikube_tunnel.log for details${NC}"
        exit 1
    fi
fi

print_header "Deploying Platform Secrets"

# Set the directory containing the YAML files
YAML_DIR="k8s-manifests/platform"

# Check if the directory exists
if [ ! -d "$YAML_DIR" ]; then
    echo -e "${YELLOW}YAML directory not found at $YAML_DIR${NC}"
    read -p "Please enter the full path to the kubernetes-manifests/platform directory: " YAML_DIR

    if [ ! -d "$YAML_DIR" ]; then
        echo -e "${RED}The directory $YAML_DIR does not exist${NC}"
        exit 1
    fi
fi

# Deploy secrets first (must exist before any Deployment references them)
kubectl apply -f "$YAML_DIR/tianshu-secrets.yaml"
check_status "tianshu-platform-secrets"

print_header "Deploying vulnerability-definition-service"

# Deploy vulnerability-definition-service
kubectl apply -f "$YAML_DIR/vulnerability-definition-service-deployment.yaml"
check_status "vulnerability-definition-service deployment"
echo "启动完毕1"
kubectl apply -f "$YAML_DIR/vulnerability-definition-service-svc.yaml"
check_status "vulnerability-definition-service service"
echo "启动完毕2"

# Wait for pods to be ready
wait_for_pods "app=vulnerability-definition-service" "default"
check_status "vulnerability-definition-service readiness check"

# Wait for service to be available
wait_for_service "vuln-def-service-svc" "default"
check_status "vulnerability-definition-service service availability"

print_header "Deploying lab-orchestration-service"

# Apply RBAC
kubectl apply -f "$YAML_DIR/lab-orchestration-service-rbac.yaml"
check_status "lab-orchestration-service RBAC"

# Deploy lab-orchestration-service
kubectl apply -f "$YAML_DIR/lab-orchestration-service-deployment.yaml"
check_status "lab-orchestration-service deployment"

kubectl apply -f "$YAML_DIR/lab-orchestration-service-svc.yaml"
check_status "lab-orchestration-service service"

# Wait for pods to be ready
wait_for_pods "app=lab-orchestration-service" "default"
check_status "lab-orchestration-service readiness check"

# Wait for service to be available
wait_for_service "lab-orchestration-service-svc" "default"
check_status "lab-orchestration-service service availability"

print_header "Checking lab-orchestration-service logs"

# Get the pod name
LAB_ORCH_POD_NAME=$(kubectl get pods -l app=lab-orchestration-service -n default -o jsonpath='{.items[0].metadata.name}')

# Check logs for connection to vulnerability-definition-service
echo -e "${YELLOW}Checking logs for successful connection to vulnerability-definition-service...${NC}"
kubectl logs $LAB_ORCH_POD_NAME -n default | grep -i "vuln-def-service-svc.default.svc.cluster.local:8081" | tail -5

# Note: We don't make this a failure point since it might be hard to programmatically determine success

print_header "Deploying auth-service"

kubectl apply -f "$YAML_DIR/auth-service-deployment.yaml"
check_status "auth-service deployment"

kubectl apply -f "$YAML_DIR/auth-service-svc.yaml"
check_status "auth-service service"

wait_for_pods "app=auth-service" "default"
check_status "auth-service readiness check"

wait_for_service "auth-service-svc" "default"
check_status "auth-service service availability"

print_header "Deploying gateway-service"

kubectl apply -f "$YAML_DIR/gateway-service-deployment.yaml"
check_status "gateway-service deployment"

kubectl apply -f "$YAML_DIR/gateway-service-svc.yaml"
check_status "gateway-service service"

wait_for_pods "app=gateway-service" "default"
check_status "gateway-service readiness check"

wait_for_service "gateway-service-svc" "default"
check_status "gateway-service service availability"

print_header "Deploying platform Ingress rules"

# Deploy Ingress
kubectl apply -f "$YAML_DIR/platform-ingress.yaml"
check_status "Platform Ingress deployment"

# Wait a moment for Ingress to be processed
sleep 5

# Check Ingress status
echo -e "${YELLOW}Ingress details:${NC}"
kubectl get ingress tianshu-platform-ingress -n default
kubectl describe ingress tianshu-platform-ingress -n default | grep -A5 "Backend"

print_header "Testing Services via Ingress"

# Wait a bit for all components to stabilize
sleep 10

# Test gateway/auth entrypoint
echo -e "${YELLOW}Testing auth entrypoint via Ingress...${NC}"
curl -s -o /dev/null -w "%{http_code}" http://localhost/api/v1/auth/login

if [ $? -eq 0 ]; then
    echo -e "${GREEN}auth-service is reachable through gateway + Ingress${NC}"
else
    echo -e "${RED}Failed to reach auth-service through gateway + Ingress${NC}"
fi

# Test definition-service via gateway
echo -e "${YELLOW}Testing vulnerability-definition-service via Gateway + Ingress...${NC}"
curl -s -o /dev/null -w "%{http_code}" http://localhost/api/v1/definitions

if [ $? -eq 0 ]; then
    echo -e "${GREEN}vulnerability-definition-service is reachable through gateway + Ingress${NC}"
else
    echo -e "${RED}Failed to reach vulnerability-definition-service through gateway + Ingress${NC}"
fi

print_header "Deployment Summary"

echo -e "${GREEN}TianShu Platform deployment completed successfully!${NC}"
echo -e "${YELLOW}Services should be accessible at:${NC}"
echo -e "  - Gateway: http://localhost/api/v1/"
echo -e "  - Auth Login: http://localhost/api/v1/auth/login"
echo -e "  - Vulnerability Definition Service: http://localhost/api/v1/definitions"
echo -e "  - Lab Orchestration Service: http://localhost/api/v1/labs"
echo ""
echo -e "${YELLOW}Demo accounts:${NC}"
echo -e "  - admin / Admin123456"
echo -e "  - trainer / Trainer123456"
echo -e "  - student / Student123456"
echo ""
echo -e "${YELLOW}To check the status of all components:${NC}"
echo -e "  kubectl get deployment,service,ingress,pod -n default"
echo ""
echo -e "${YELLOW}To clean up all resources:${NC}"
echo -e "  ./cleanup_tianshu.sh"
echo ""
echo -e "${RED}IMPORTANT: The minikube tunnel is running in the background.${NC}"
echo -e "${RED}If you want to stop it, run: sudo pkill -f \"minikube tunnel\"${NC}"

exit 0
