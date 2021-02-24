package io.fabric8.assignmessagepolicycontroller.api.model.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("assignmessagepolicycontroller.k8s.io")
@Plural("assignmessagepolicies")
public class AssignMessagePolicy extends CustomResource<AssignMessagePolicySpec, AssignMessagePolicyStatus> implements Namespaced { }
