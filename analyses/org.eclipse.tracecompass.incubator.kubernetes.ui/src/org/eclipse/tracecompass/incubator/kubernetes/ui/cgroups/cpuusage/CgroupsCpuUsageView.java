package org.eclipse.tracecompass.incubator.kubernetes.ui.cgroups.cpuusage;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.cpuusage.CpuUsageView;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceContext;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ui.viewers.TmfViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.xychart.TmfXYChartViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.xychart.linechart.TmfXYChartSettings;
import org.eclipse.tracecompass.tmf.ui.views.xychart.TmfChartView;

public class CgroupsCpuUsageView extends TmfChartView {

    public static final @NonNull String ID = "org.eclipse.tracecompass.incubator.kubernetes.view.cgroups.cpuusage"; //$NON-NLS-1$

    /** ID of the followed CPU in the map data in {@link TmfTraceContext} */
    public static final @NonNull String CPU_USAGE_FOLLOW_CPU = ID + ".FOLLOW_CPU"; //$NON-NLS-1$

    public CgroupsCpuUsageView() {
        super("CPU Usage");
    }

    @Override
    protected TmfXYChartViewer createChartViewer(Composite parent) {
        TmfXYChartSettings settings = new TmfXYChartSettings("CPU Usage", "Time", "% CPU", 0.4);
        return new CgroupsCpuUsageXYViewer(parent, settings);
    }

    @Override
    public TmfViewer createLeftChildViewer(Composite parent) {
        return new CgroupsCpuUsageTreeViewer(parent);
    }

    private static Object getData(@NonNull ITmfTrace trace, @NonNull String key) {
        TmfTraceContext ctx = TmfTraceManager.getInstance().getTraceContext(trace);
        return ctx.getData(key);
    }

    /**
     * Get CPUs from a trace
     *
     * @param trace
     *            the trace
     * @return the CPUs set
     */
    protected static @NonNull Set<@NonNull Integer> getCpus(@NonNull ITmfTrace trace) {
        Set<@NonNull Integer> data = (Set<@NonNull Integer>) getData(trace, CpuUsageView.CPU_USAGE_FOLLOW_CPU);
        return data != null ? data : Collections.emptySet();
    }

}