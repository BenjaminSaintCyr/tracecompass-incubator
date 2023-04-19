package org.eclipse.tracecompass.incubator.kubernetes.ui.cgroups.cpuusage;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swtchart.ITitle;
import org.eclipse.tracecompass.incubator.kubernetes.core.analysis.cgroups.CgroupsDataProvider;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.cpuusage.CpuUsageXYViewer;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.cpuusage.Messages;
import org.eclipse.tracecompass.internal.provisional.tmf.ui.viewers.xychart.BaseXYPresentationProvider;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.FetchParametersUtils;
import org.eclipse.tracecompass.tmf.core.model.OutputElementStyle;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceContext;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ui.viewers.xychart.linechart.TmfFilteredXYChartViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.xychart.linechart.TmfXYChartSettings;

import com.google.common.base.Joiner;

public class CgroupsCpuUsageXYViewer extends TmfFilteredXYChartViewer {

    /**
     * Constructor
     *
     * @param parent
     *            parent composite
     * @param settings
     *            See {@link TmfXYChartSettings} to know what it contains
     */
    public CgroupsCpuUsageXYViewer(Composite parent, TmfXYChartSettings settings) {
        super(parent, settings, CgroupsDataProvider.ID);
        getSwtChart().getTitle().setVisible(true);
        getSwtChart().getLegend().setVisible(false);
    }

    protected static @NonNull Set<@NonNull Integer> getCpus(@NonNull ITmfTrace trace) {
        TmfTraceContext ctx = TmfTraceManager.getInstance().getTraceContext(trace);
        Set<@NonNull Integer> data = (Set<@NonNull Integer>) ctx.getData(CgroupsCpuUsageView.CPU_USAGE_FOLLOW_CPU);
        return data != null ? data : Collections.emptySet();
    }

    @Override
    protected @NonNull Map<String, Object> createQueryParameters(long start, long end, int nb) {
        Map<@NonNull String, @NonNull Object> parameters = FetchParametersUtils.selectionTimeQueryToMap(new SelectionTimeQueryFilter(start, end, nb, getSelected()));
        parameters.put(CgroupsDataProvider.REQUESTED_CPUS_KEY, getCpus(getTrace()));
        return parameters;
    }

    @Override
    public OutputElementStyle getSeriesStyle(@NonNull Long seriesId) {
        return getPresentationProvider().getSeriesStyle(seriesId);
    }

    /**
     * Update the {@link CpuUsageXYViewer} title to append the current cpu
     * numbers
     */
    protected void setTitle() {
        ITitle title = getSwtChart().getTitle();
        Set<Integer> cpus = getCpus(getTrace());
        if (cpus.isEmpty()) {
            title.setText(Messages.CpuUsageView_Title);
        } else {
            title.setText(Messages.CpuUsageView_Title + ' ' + Joiner.on(", ").join(cpus)); //$NON-NLS-1$
        }
    }

    @Override
    protected BaseXYPresentationProvider createPresentationProvider(ITmfTrace trace) {
        CgroupsCpuPresentationProvider presProvider = CgroupsCpuPresentationProvider.getForTrace(trace);
        return presProvider;
    }

}
