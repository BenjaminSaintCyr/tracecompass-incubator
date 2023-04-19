package org.eclipse.tracecompass.incubator.kubernetes.ui.cgroups.cpuusage;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.incubator.kubernetes.core.analysis.cgroups.CgroupsCpuUsageEntryModel;
import org.eclipse.tracecompass.incubator.kubernetes.core.analysis.cgroups.CgroupsDataProvider;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.FetchParametersUtils;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.AbstractSelectTreeViewer2;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeColumnDataProvider;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeViewerEntry;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeColumnData;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeViewerEntry;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class CgroupsCpuUsageTreeViewer extends AbstractSelectTreeViewer2 {

    static private final int NB_COLUMNS = 4;

    /** Provides label for the CPU usage tree viewer cells */
    protected class CpuLabelProvider extends DataProviderTreeLabelProvider {

        @Override
        public Image getColumnImage(Object element, int columnIndex) {
            if (columnIndex == 4 && element instanceof CgroupsCpuUsageEntry) {
                CgroupsCpuUsageEntry cgroupsCpuUsageEntry = (CgroupsCpuUsageEntry) element;
                CgroupsCpuUsageEntryModel model = cgroupsCpuUsageEntry.getModel();
                if (isChecked(element)) {
                    return getLegendImage(model.getId());
                }
            }
            return null;
        }
    }

    private CgroupsCpuPresentationProvider fPresentationProvider;

    public CgroupsCpuUsageTreeViewer(Composite parent) {
        super(parent, NB_COLUMNS, CgroupsDataProvider.ID);
        setLabelProvider(new CpuLabelProvider());
    }

    @Override
    protected void updateContent(long start, long end, boolean isSelection) {
        super.updateContent(start, end, isSelection);
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    @Override
    protected @NonNull Map<String, Object> getParameters(long start, long end, boolean isSelection) {
        long newStart = Long.max(start, getStartTime());
        long newEnd = Long.min(end, getEndTime());

        if (isSelection || newEnd < newStart) {
            return Collections.emptyMap();
        }

        Map<@NonNull String, @NonNull Object> parameters = FetchParametersUtils.selectionTimeQueryToMap(new SelectionTimeQueryFilter(start, end, 2, Collections.emptyList()));
        parameters.put(CgroupsDataProvider.REQUESTED_CPUS_KEY, CgroupsCpuUsageView.getCpus(getTrace()));
        return parameters;
    }

    @Override
    protected ITmfTreeViewerEntry modelToTree(long start, long end, List<ITmfTreeDataModel> model) {
        double time = end - start;

        Map<Long, TmfTreeViewerEntry> map = new HashMap<>();
        TmfTreeViewerEntry root = new TmfTreeViewerEntry(""); //$NON-NLS-1$
        map.put(-1L, root);

        for (CgroupsCpuUsageEntryModel entryModel : Iterables.filter(model, CgroupsCpuUsageEntryModel.class)) {
            fPresentationProvider.addTotalSeries(entryModel.getId()); // TODO
                                                                      // check

            CgroupsCpuUsageEntry cpuUsageEntry = new CgroupsCpuUsageEntry(entryModel, entryModel.getTime() / time);
            map.put(entryModel.getId(), cpuUsageEntry);

            TmfTreeViewerEntry parent = map.get(entryModel.getParentId());
            if (parent != null) {
                parent.addChild(cpuUsageEntry);
            }
        }
        return root;
    }

    @Override
    protected ITmfTreeColumnDataProvider getColumnDataProvider() {
        return () -> {
            ImmutableList.Builder<TmfTreeColumnData> columns = ImmutableList.builder();

            columns.add(createColumn("Process", Comparator.comparing(CgroupsCpuUsageEntry::getName)));

            Comparator<CgroupsCpuUsageEntry> tidCompare = Comparator.comparingInt(c -> c.getModel().getTid());
            columns.add(createColumn("TID", tidCompare));

            TmfTreeColumnData percentColumn = createColumn("%", Comparator.comparingDouble(CgroupsCpuUsageEntry::getPercent));
            percentColumn.setPercentageProvider(data -> ((CgroupsCpuUsageEntry) data).getPercent());
            columns.add(percentColumn);

            Comparator<CgroupsCpuUsageEntry> timeCompare = Comparator.comparingLong(c -> c.getModel().getTime());
            columns.add(createColumn("Time", timeCompare));

            columns.add(new TmfTreeColumnData("Legend"));

            return columns.build();
        };
    }

    @Override
    @TmfSignalHandler
    public void traceSelected(TmfTraceSelectedSignal signal) {
        super.traceSelected(signal);
        fPresentationProvider = CgroupsCpuPresentationProvider.getForTrace(signal.getTrace());
    }

    @Override
    @TmfSignalHandler
    public void traceOpened(TmfTraceOpenedSignal signal) {
        super.traceOpened(signal);
        fPresentationProvider = CgroupsCpuPresentationProvider.getForTrace(signal.getTrace());
    }
}
