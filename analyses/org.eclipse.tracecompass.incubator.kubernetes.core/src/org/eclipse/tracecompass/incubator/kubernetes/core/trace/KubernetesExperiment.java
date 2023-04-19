package org.eclipse.tracecompass.incubator.kubernetes.core.trace;

import java.util.Collections;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

public class KubernetesExperiment extends TmfExperiment {

    public KubernetesExperiment() {
        this(StringUtils.EMPTY, Collections.emptySet());
    }

    public KubernetesExperiment(String id, Set<ITmfTrace> traces) {
        super(ITmfEvent.class, id, traces.toArray(new ITmfTrace[traces.size()]), TmfExperiment.DEFAULT_INDEX_PAGE_SIZE, null);
    }
}
