package io.fabric8.assignmessagepolicycontroller.api.model.v1alpha1;

public class AssignMessagePolicyStatus {
    private int availableReplicas;

    public int getAvailableReplicas() {
        return availableReplicas;
    }

    public void setAvailableReplicas(int availableReplicas) {
        this.availableReplicas = availableReplicas;
    }

    @Override
    public String toString() {
        return "AssignMessagePolicy{ availableReplicas=" + availableReplicas + "}";
    }
}
