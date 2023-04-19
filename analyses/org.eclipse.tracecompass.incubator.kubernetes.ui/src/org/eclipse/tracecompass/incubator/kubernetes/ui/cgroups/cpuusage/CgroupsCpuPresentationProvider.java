package org.eclipse.tracecompass.incubator.kubernetes.ui.cgroups.cpuusage;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.internal.provisional.tmf.ui.viewers.xychart.BaseXYPresentationProvider;
import org.eclipse.tracecompass.tmf.core.model.OutputElementStyle;
import org.eclipse.tracecompass.tmf.core.model.StyleProperties;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

public class CgroupsCpuPresentationProvider extends BaseXYPresentationProvider {

    private static final int DEFAULT_SERIES_WIDTH = 1;
    private Set<Long> fTotalSeries = new TreeSet<>();

    private static Map<ITmfTrace, CgroupsCpuPresentationProvider> INSTANCES = new HashMap<>();

    /**
     * Get the presentation provider for a specific trace
     *
     * @param trace
     *            The trace to get the provider for
     * @return The presentation provider
     */
    public static CgroupsCpuPresentationProvider getForTrace(ITmfTrace trace) {
        return INSTANCES.computeIfAbsent(trace, t -> new CgroupsCpuPresentationProvider());
    }

    /**
     * Set a series ID as a total series, that should have a line style
     *
     * @param id
     *            The ID of the series that is a total series
     */
    public void addTotalSeries(long id) {
        fTotalSeries.add(id);
    }

    @Override
    public @NonNull OutputElementStyle getSeriesStyle(@NonNull Long seriesId) {
        if (fTotalSeries.contains(seriesId)) {
            return getSeriesStyle(seriesId, StyleProperties.SeriesType.LINE, DEFAULT_SERIES_WIDTH);
        }
        return getSeriesStyle(seriesId, StyleProperties.SeriesType.AREA, DEFAULT_SERIES_WIDTH);
    }

}
