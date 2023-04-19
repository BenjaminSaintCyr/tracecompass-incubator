package org.eclipse.tracecompass.incubator.kubernetes.ui.podstartup;

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.density2.AbstractSegmentStoreDensityView;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.density2.AbstractSegmentStoreDensityViewer;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.table.AbstractSegmentStoreTableViewer;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.table.SegmentStoreTableViewer;
import org.eclipse.tracecompass.incubator.kubernetes.core.analysis.latency.KubernetesPodStartupLatencyAnalysis;

public class PodStartupDensityView extends AbstractSegmentStoreDensityView {

    /** The view's ID */
    public static final String ID = "org.eclipse.tracecompass.incubator.kubernetes.view.podstartupdensity"; //$NON-NLS-1$

    /**
     * Constructs a new density view.
     */
    public PodStartupDensityView() {
        super(ID);
    }

    @Override
    protected AbstractSegmentStoreTableViewer createSegmentStoreTableViewer(Composite parent) {
        return new SegmentStoreTableViewer(new TableViewer(parent, SWT.FULL_SELECTION | SWT.VIRTUAL), KubernetesPodStartupLatencyAnalysis.ID, false) {
            @Override
            protected void createProviderColumns() {
                super.createProviderColumns();
                Table t = (Table) getControl();
                t.setColumnOrder(new int[] { 0, 1, 2, 3, 4});
            }
        };
    }

    @Override
    protected AbstractSegmentStoreDensityViewer createSegmentStoreDensityViewer(Composite parent) {
        return new PodStartupDensityViewer(parent);
    }

}