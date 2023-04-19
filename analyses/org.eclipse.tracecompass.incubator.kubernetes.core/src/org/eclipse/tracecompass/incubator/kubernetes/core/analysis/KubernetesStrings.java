package org.eclipse.tracecompass.incubator.kubernetes.core.analysis;

@SuppressWarnings({ "javadoc", "nls" })
public interface KubernetesStrings {
    String TRACE_PROVIDER = "k8s_ust";
    String CONTEXT_PROCESS_NAME = "context._procname";
    String CONTEXT_VTID = "context._vtid";

    String KUBERNETES_EVENT = "k8s_ust:event";

    String OPERATION_NAME = "op_name";
    String OPERATION_CONTEXT = "op_ctx";

    String EDGES = "Edges";
}
