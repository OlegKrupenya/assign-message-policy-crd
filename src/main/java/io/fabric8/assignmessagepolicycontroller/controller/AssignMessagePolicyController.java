package io.fabric8.assignmessagepolicycontroller.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import io.fabric8.assignmessagepolicycontroller.api.model.v1alpha1.AssignMessagePolicy;
import io.fabric8.assignmessagepolicycontroller.api.model.v1alpha1.AssignMessagePolicyList;
import io.fabric8.assignmessagepolicycontroller.api.model.v1alpha1.AssignMessagePolicySpec;
import io.fabric8.assignmessagepolicycontroller.api.model.v1alpha1.AssignMessagePolicyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static io.fabric8.assignmessagepolicycontroller.utils.IoUtils.copyApiProxyToTarget;

public class AssignMessagePolicyController {
    private final BlockingQueue<String> workqueue;
    private final SharedIndexInformer<AssignMessagePolicy> policyInformer;
    private final SharedIndexInformer<Deployment> deploymentInformer;
    private final Lister<AssignMessagePolicy> policyLister;
    private final KubernetesClient kubernetesClient;
    private final MixedOperation<AssignMessagePolicy, AssignMessagePolicyList, Resource<AssignMessagePolicy>> policyClient;
    public static final Logger logger = LoggerFactory.getLogger(AssignMessagePolicyController.class);

    public AssignMessagePolicyController(KubernetesClient kubernetesClient, MixedOperation<AssignMessagePolicy, AssignMessagePolicyList, Resource<AssignMessagePolicy>> policyClient, SharedIndexInformer<Deployment> deploymentInformer, SharedIndexInformer<AssignMessagePolicy> policyInformer, String namespace) {
        this.kubernetesClient = kubernetesClient;
        this.policyClient = policyClient;
        this.policyLister = new Lister<>(policyInformer.getIndexer(), namespace);
        this.policyInformer = policyInformer;
        this.deploymentInformer = deploymentInformer;
        this.workqueue = new ArrayBlockingQueue<>(1024);
    }

    public void create() {
        // Set up an event handler for when AssignMessagePolicy resources change
        policyInformer.addEventHandler(new ResourceEventHandler<AssignMessagePolicy>() {
            @Override
            public void onAdd(AssignMessagePolicy assignMessagePolicy) {
                enqueueAssignMessagePolicy(assignMessagePolicy);
            }

            @Override
            public void onUpdate(AssignMessagePolicy assignMessagePolicy, AssignMessagePolicy newAssignMessagePolicy) {
                enqueueAssignMessagePolicy(newAssignMessagePolicy);
            }

            @Override
            public void onDelete(AssignMessagePolicy assignMessagePolicy, boolean b) {
                // Do nothing
            }
        });

        // Set up an event handler for when Deployment resources change. This
        // handler will lookup the owner of the given Deployment, and if it is
        // owned by a AssignMessagePolicy resource will enqueue that AssignMessagePolicy resource for
        // processing. This way, we don't need to implement custom logic for
        // handling Deployment resources. More info on this pattern:
        // https://github.com/kubernetes/community/blob/8cafef897a22026d42f5e5bb3f104febe7e29830/contributors/devel/controllers.md
        deploymentInformer.addEventHandler(new ResourceEventHandler<Deployment>() {
            @Override
            public void onAdd(Deployment deployment) {
                handleObject(deployment);
            }

            @Override
            public void onUpdate(Deployment oldDeployment, Deployment newDeployment) {
                // Periodic resync will send update events for all known Deployments.
                // Two different versions of the same Deployment will always have different RVs.
                if (oldDeployment.getMetadata().getResourceVersion().equals(newDeployment.getMetadata().getResourceVersion())) {
                    return;
                }
                handleObject(newDeployment);
            }

            @Override
            public void onDelete(Deployment deployment, boolean b) {
                handleObject(deployment);
            }
        });
    }

    public void run() {
        logger.info("Starting {} controller", AssignMessagePolicy.class.getSimpleName());
        logger.info("Waiting for informer caches to sync");
        while (!deploymentInformer.hasSynced() || !policyInformer.hasSynced()) {
            // Wait till Informer syncs
        }

        while (!Thread.currentThread().isInterrupted()) {
            try {
                logger.info("trying to fetch item from workqueue...");
                if (workqueue.isEmpty()) {
                    logger.info("Work Queue is empty");
                }
                String key = workqueue.take();
                Objects.requireNonNull(key, "key can't be null");
                logger.info("Got {}", key);
                if (key.isEmpty() || (!key.contains("/"))) {
                    logger.warn("invalid resource key: {}", key);
                }

                // Get the AssignMessagePolicy resource's name from key which is in format namespace/name
                String name = key.split("/")[1];
                AssignMessagePolicy assignMessagePolicy = policyLister.get(key.split("/")[1]);
                if (assignMessagePolicy == null) {
                    logger.error("AssignMessagePolicy {} in workqueue no longer exists", name);
                    return;
                }
                reconcile(assignMessagePolicy);

            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                logger.error("controller interrupted..");
            }
        }
    }

    /**
     * Compares the actual state with the desired, and attempts to
     * converge the two. It then updates the Status block of the AssignMessagePolicy resource
     * with the current status of the resource.
     *
     * @param assignMessagePolicy specified resource
     */
    protected void reconcile(AssignMessagePolicy assignMessagePolicy) {
        String deploymentName = assignMessagePolicy.getSpec().getDeploymentName();
        if (deploymentName == null || deploymentName.isEmpty()) {
            // We choose to absorb the error here as the worker would requeue the
            // resource otherwise. Instead, the next time the resource is updated
            // the resource will be queued again.
            logger.warn("No Deployment name specified for AssignMessagePolicy {}/{}", assignMessagePolicy.getMetadata().getNamespace(), assignMessagePolicy.getMetadata().getName());
            return;
        }

        // Get the deployment with the name specified in AssignMessagePolicy.spec
        Deployment deployment = kubernetesClient.apps().deployments().inNamespace(assignMessagePolicy.getMetadata().getNamespace()).withName(deploymentName).get();
        // If the resource doesn't exist, we'll create it
        if (deployment == null) {
            createDeployments(assignMessagePolicy);
            createApiProxy(assignMessagePolicy.getSpec());
            deployProxyToApigee();
            return;
        }

        // If the Deployment is not controlled by this AssignMessagePolicy resource, we should log
        // a warning to the event recorder and return error msg.
        if (!isControlledBy(deployment, assignMessagePolicy)) {
            logger.warn("Deployment {} is not controlled by AssignMessagePolicy {}", deployment.getMetadata().getName(), assignMessagePolicy.getMetadata().getName());
            return;
        }

        // If this number of the replicas on the AssignMessagePolicy resource is specified, and the
        // number does not equal the current desired replicas on the Deployment, we
        // should update the Deployment resource.
        if (assignMessagePolicy.getSpec().getReplicas() != deployment.getSpec().getReplicas()) {
            logger.info("AssignMessagePolicy {} replicas: {}, Deployment {} replicas: {}", assignMessagePolicy.getMetadata().getName(), assignMessagePolicy.getSpec().getReplicas(),
                    deployment.getMetadata().getName(), deployment.getSpec().getReplicas());
            deployment.getSpec().setReplicas(assignMessagePolicy.getSpec().getReplicas());
            kubernetesClient.apps().deployments()
                    .inNamespace(assignMessagePolicy.getMetadata().getNamespace())
                    .withName(deployment.getMetadata().getNamespace())
                    .replace(deployment);
        }

        // Finally, we update the status block of the AssignMessagePolicy resource to reflect the
        // current state of the world
        updateAvailableReplicasInAssignMessagePolicyStatus(assignMessagePolicy, assignMessagePolicy.getSpec().getReplicas());
    }

    private void deployProxyToApigee() {

    }

    private void createApiProxy(AssignMessagePolicySpec spec) {
        copyApiProxyToTarget(spec);
    }

    private void createDeployments(AssignMessagePolicy assignMessagePolicy) {
        Deployment deployment = createNewDeployment(assignMessagePolicy);
        kubernetesClient.apps().deployments().inNamespace(assignMessagePolicy.getMetadata().getNamespace()).create(deployment);
    }

    private void enqueueAssignMessagePolicy(AssignMessagePolicy assignMessagePolicy) {
        logger.info("enqueueAssignMessagePolicy({})", assignMessagePolicy.getMetadata().getName());
        String key = Cache.metaNamespaceKeyFunc(assignMessagePolicy);
        logger.info("Going to enqueue key {}", key);
        if (key != null && !key.isEmpty()) {
            logger.info("Adding item to workqueue");
            workqueue.add(key);
        }
    }

    private void handleObject(HasMetadata obj) {
        logger.info("handleDeploymentObject({})", obj.getMetadata().getName());
        OwnerReference ownerReference = getControllerOf(obj);
        Objects.requireNonNull(ownerReference);
        if (!ownerReference.getKind().equalsIgnoreCase(AssignMessagePolicy.class.getSimpleName())) {
            return;
        }
        AssignMessagePolicy assignMessagePolicy = policyLister.get(ownerReference.getName());
        if (assignMessagePolicy == null) {
            logger.info("ignoring orphaned object '{}' of AssignMessagePolicy '{}'", obj.getMetadata().getSelfLink(), ownerReference.getName());
            return;
        }
        enqueueAssignMessagePolicy(assignMessagePolicy);
    }

    private void updateAvailableReplicasInAssignMessagePolicyStatus(AssignMessagePolicy assignMessagePolicy, int replicas) {
        AssignMessagePolicyStatus assignMessagePolicyStatus = new AssignMessagePolicyStatus();
        assignMessagePolicyStatus.setAvailableReplicas(replicas);
        // NEVER modify objects from the store. It's a read-only, local cache.
        // You can create a copy manually and modify it
        AssignMessagePolicy assignMessagePolicyClone = getAssignMessagePolicyClone(assignMessagePolicy);
        assignMessagePolicyClone.setStatus(assignMessagePolicyStatus);
        // If the CustomResourceSubresources feature gate is not enabled,
        // we must use Update instead of UpdateStatus to update the Status block of the AssignMessagePolicy resource.
        // UpdateStatus will not allow changes to the Spec of the resource,
        // which is ideal for ensuring nothing other than resource status has been updated.
        policyClient.inNamespace(assignMessagePolicy.getMetadata().getNamespace()).withName(assignMessagePolicy.getMetadata().getName()).updateStatus(assignMessagePolicy);
    }

    /**
     * createNewDeployment creates a new Deployment for a AssignMessagePolicy resource. It also sets
     * the appropriate OwnerReferences on the resource so handleObject can discover
     * the AssignMessagePolicy resource that 'owns' it.
     * @param assignMessagePolicy {@link AssignMessagePolicy} resource which will be owner of this Deployment
     * @return Deployment object based on this AssignMessagePolicy resource
     */
    private Deployment createNewDeployment(AssignMessagePolicy assignMessagePolicy) {
        return new DeploymentBuilder()
                .withNewMetadata()
                  .withName(assignMessagePolicy.getSpec().getDeploymentName())
                  .withNamespace(assignMessagePolicy.getMetadata().getNamespace())
                  .withLabels(getDeploymentLabels(assignMessagePolicy))
                  .addNewOwnerReference().withController(true).withKind(assignMessagePolicy.getKind()).withApiVersion(assignMessagePolicy.getApiVersion()).withName(assignMessagePolicy.getMetadata().getName()).withNewUid(assignMessagePolicy.getMetadata().getUid()).endOwnerReference()
                .endMetadata()
                .withNewSpec()
                  .withReplicas(assignMessagePolicy.getSpec().getReplicas())
                  .withNewSelector()
                  .withMatchLabels(getDeploymentLabels(assignMessagePolicy))
                  .endSelector()
                  .withNewTemplate()
                     .withNewMetadata().withLabels(getDeploymentLabels(assignMessagePolicy)).endMetadata()
                     .withNewSpec()
                         .addNewContainer()
                         .withName("nginx")
                         .withImage("nginx:latest")
                         .endContainer()
                     .endSpec()
                  .endTemplate()
                .endSpec()
                .build();
    }

    private Map<String, String> getDeploymentLabels(AssignMessagePolicy assignMessagePolicy) {
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "nginx");
        labels.put("controller", assignMessagePolicy.getMetadata().getName());
        return labels;
    }

    private OwnerReference getControllerOf(HasMetadata obj) {
        List<OwnerReference> ownerReferences = obj.getMetadata().getOwnerReferences();
        for (OwnerReference ownerReference : ownerReferences) {
            if (ownerReference.getController().equals(Boolean.TRUE)) {
                return ownerReference;
            }
        }
        return null;
    }

    private boolean isControlledBy(HasMetadata obj, AssignMessagePolicy assignMessagePolicy) {
        OwnerReference ownerReference = getControllerOf(obj);
        if (ownerReference != null) {
            return ownerReference.getKind().equals(assignMessagePolicy.getKind()) && ownerReference.getName().equals(assignMessagePolicy.getMetadata().getName());
        }
        return false;
    }

    private AssignMessagePolicy getAssignMessagePolicyClone(AssignMessagePolicy assignMessagePolicy) {
        AssignMessagePolicy cloneAssignMessagePolicy = new AssignMessagePolicy();
        AssignMessagePolicySpec cloneAssignMessagePolicySpec = new AssignMessagePolicySpec();
        cloneAssignMessagePolicySpec.setDeploymentName(assignMessagePolicy.getSpec().getDeploymentName());
        cloneAssignMessagePolicySpec.setReplicas(assignMessagePolicy.getSpec().getReplicas());

        cloneAssignMessagePolicy.setSpec(cloneAssignMessagePolicySpec);
        cloneAssignMessagePolicy.setMetadata(assignMessagePolicy.getMetadata());

        return cloneAssignMessagePolicy;
    }
}
