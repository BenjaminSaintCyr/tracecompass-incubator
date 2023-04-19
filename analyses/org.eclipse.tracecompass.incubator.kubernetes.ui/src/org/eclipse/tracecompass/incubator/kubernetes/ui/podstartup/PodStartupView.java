package org.eclipse.tracecompass.incubator.kubernetes.ui.podstartup;

import org.eclipse.tracecompass.incubator.kubernetes.core.analysis.KubernetesDataProvider;
import org.eclipse.tracecompass.internal.provisional.tmf.ui.widgets.timegraph.BaseDataProviderTimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.views.timegraph.BaseDataProviderTimeGraphView;

public class PodStartupView extends BaseDataProviderTimeGraphView {

    private static final String ID = "org.eclipse.tracecompass.incubator.kubernetes.view.podstartup";

    public PodStartupView() {
        super(ID, new BaseDataProviderTimeGraphPresentationProvider(), KubernetesDataProvider.ID);
    }
}
