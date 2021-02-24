package io.fabric8.assignmessagepolicycontroller;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.assignmessagepolicycontroller.controller.AssignMessagePolicyController;
import io.fabric8.assignmessagepolicycontroller.api.model.v1alpha1.AssignMessagePolicy;
import io.fabric8.assignmessagepolicycontroller.api.model.v1alpha1.AssignMessagePolicyList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssignMessagePolicyControllerMain {
    public static final Logger logger = LoggerFactory.getLogger(AssignMessagePolicyControllerMain.class.getName());

    public static void main(String[] args) {
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            String namespace = client.getNamespace();
            if (namespace == null) {
                logger.info("No namespace found via config, assuming default.");
                namespace = "default";
            }

            logger.info("Using namespace : {}", namespace);

            SharedInformerFactory informerFactory = client.informers();

            MixedOperation<AssignMessagePolicy, AssignMessagePolicyList, Resource<AssignMessagePolicy>> assignMessagePolicyClient = client.customResources(AssignMessagePolicy.class, AssignMessagePolicyList.class);
            SharedIndexInformer<Deployment> deploymentSharedIndexInformer = informerFactory.sharedIndexInformerFor(Deployment.class, 10 * 60 * 1000);
            SharedIndexInformer<AssignMessagePolicy> assignMessagePolicySharedIndexInformer = informerFactory.sharedIndexInformerForCustomResource(AssignMessagePolicy.class, AssignMessagePolicyList.class, 10 * 60 * 1000);
            AssignMessagePolicyController assignMessagePolicyController = new AssignMessagePolicyController(client, assignMessagePolicyClient, deploymentSharedIndexInformer, assignMessagePolicySharedIndexInformer, namespace);

            assignMessagePolicyController.create();
            informerFactory.startAllRegisteredInformers();
            informerFactory.addSharedInformerEventListener(exception -> logger.error("Exception occurred, but caught", exception));

            logger.info("Starting AssignMessagePolicy Controller");
            assignMessagePolicyController.run();
        } catch (KubernetesClientException exception) {
            logger.error("Kubernetes Client Exception : ", exception);
        }
    }
}
