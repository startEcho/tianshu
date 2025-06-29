echo "The following lab deployments will be deleted:"
kubectl get deployment -n default -l instanceType=lab-environment
echo "The following lab services will be deleted:"
kubectl get service -n default -l instanceType=lab-environment
echo "The following lab ingresses will be deleted:"
kubectl get ingress -n default -l instanceType=lab-environment
# Pods 会随 Deployments 自动删除

read -p "Are you sure you want to delete ALL of the above lab resources? (yes/no) " confirmation
if [ "$confirmation" == "yes" ]; then
    echo "Deleting lab deployments (and their pods)..."
    kubectl delete deployment -n default -l instanceType=lab-environment
    echo "Deleting lab services..."
    kubectl delete service -n default -l instanceType=lab-environment
    echo "Deleting lab ingresses..."
    kubectl delete ingress -n default -l instanceType=lab-environment
    echo "Lab resources cleanup initiated."
else
    echo "Deletion cancelled."
fi
