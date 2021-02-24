package io.fabric8.assignmessagepolicycontroller.api.model.v1alpha1;

public class AssignMessagePolicySpec {
    private String deploymentName;
    private String orderId;
    private String description;
    private int replicas;

    public int getReplicas() {
        return replicas;
    }

    @Override
    public String toString() {
        return "AssignMessagePolicySpec{replicas=" + replicas + "}";
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    public String getDeploymentName() { return deploymentName; }

    public void setDeploymentName(String deploymentName) { this.deploymentName = deploymentName; }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
