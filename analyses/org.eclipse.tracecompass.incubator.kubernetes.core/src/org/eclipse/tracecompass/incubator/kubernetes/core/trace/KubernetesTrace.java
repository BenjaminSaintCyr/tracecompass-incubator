package org.eclipse.tracecompass.incubator.kubernetes.core.trace;

import java.util.Collection;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.kubernetes.core.Activator;
import org.eclipse.tracecompass.tmf.core.trace.TraceValidationStatus;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTraceValidationStatus;

public class KubernetesTrace extends CtfTmfTrace {

    private static final String KUBERNETES_EVENT = "k8s_ust:"; //$NON-NLS-1$
    private static final int CONFIDENCE = 101;


    @Override
    public IStatus validate(final @Nullable IProject project, final @Nullable String path) {
        IStatus status = super.validate(project, path);
        if (status instanceof CtfTraceValidationStatus) {
            Map<String, String> environment = ((CtfTraceValidationStatus) status).getEnvironment();
            /* Make sure the domain is "ust" in the trace's env vars */
            String domain = environment.get("domain"); //$NON-NLS-1$
            if (domain == null || !domain.equals("\"ust\"")) { //$NON-NLS-1$
                return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Not a UST trace"); //$NON-NLS-1$
            }

            // Checks if one of the declared event types has k8s events
            Collection<String> eventNames = ((CtfTraceValidationStatus) status).getEventNames();
            for (String eventName: eventNames) {
                if (eventName.startsWith(KUBERNETES_EVENT)) {
                    return new TraceValidationStatus(CONFIDENCE, Activator.PLUGIN_ID);
                }
            }
            return new TraceValidationStatus(0, Activator.PLUGIN_ID);
        }
        return status;
    }

}
