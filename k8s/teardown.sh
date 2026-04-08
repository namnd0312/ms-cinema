#!/usr/bin/env bash
set -euo pipefail
NAMESPACE="ms-cinema"
echo "Deleting namespace ${NAMESPACE} (all resources)..."
kubectl delete namespace "$NAMESPACE" --timeout=120s 2>/dev/null || echo "Namespace not found."
echo "Done. PVCs deleted with namespace. Run 'minikube stop' to stop Minikube."
