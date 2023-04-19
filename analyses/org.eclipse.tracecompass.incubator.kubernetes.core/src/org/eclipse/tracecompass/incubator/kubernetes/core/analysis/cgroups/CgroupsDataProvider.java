package org.eclipse.tracecompass.incubator.kubernetes.core.analysis.cgroups;

import java.text.Format;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.cpuusage.KernelCpuUsageAnalysis;
import org.eclipse.tracecompass.common.core.format.SubSecondTimeWithUnitFormat;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.cpuusage.Messages;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.FetchParametersUtils;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderParameterUtils;
import org.eclipse.tracecompass.tmf.core.model.YModel;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectedCpuQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
import org.eclipse.tracecompass.tmf.core.model.xy.AbstractTreeCommonXDataProvider;
import org.eclipse.tracecompass.tmf.core.model.xy.IYModel;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Maps;

public class CgroupsDataProvider extends AbstractTreeCommonXDataProvider<@NonNull KernelCpuUsageAnalysis, @NonNull CgroupsCpuUsageEntryModel> {

    private static final Format TIME_FORMATTER = SubSecondTimeWithUnitFormat.getInstance();
    public static final String TOTAL = "total:"; //$NON-NLS-1$

    public static final String ID = "org.eclipse.tracecompass.incubator.kubernetes.core.analysis.cgroups.dataprovider"; //$NON-NLS-1$

    public static final String REQUESTED_CPUS_KEY = "requested_cpus"; //$NON-NLS-1$
    public static final String SUFFIX = ".dataprovider"; //$NON-NLS-1$

    private final CgroupsAnalysis cgroupsAnalysis;
    private final Map<Long, Long> idToCgroup = new HashMap<>();

    public CgroupsDataProvider(ITmfTrace trace, KernelCpuUsageAnalysis analysisModule, CgroupsAnalysis cgroups) {
        super(trace, analysisModule);
        cgroupsAnalysis = cgroups;
    }

    @Override
    public @NonNull String getId() {
        return ID;
    }

    private static Set<Integer> extractCpuSet(Map<String, Object> parameters) {
        Object cpus = parameters.get(REQUESTED_CPUS_KEY);
        if (cpus instanceof Collection<?>) {
            return ((Collection<?>) cpus).stream().filter(cpu -> cpu instanceof Integer)
                    .map(cpu -> (Integer) cpu)
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private static @Nullable SelectedCpuQueryFilter createCpuQuery(Map<String, Object> parameters) {
        List<Long> timeRequested = DataProviderParameterUtils.extractTimeRequested(parameters);
        List<Long> selectedItems = DataProviderParameterUtils.extractSelectedItems(parameters);
        Set<Integer> cpus = extractCpuSet(parameters);

        if (timeRequested == null || selectedItems == null) {
            return null;
        }

        return new SelectedCpuQueryFilter(timeRequested, selectedItems, cpus);
    }

    private static long getInitialPrevTime(SelectionTimeQueryFilter filter) {
        /*
         * Subtract from start time the same interval as the interval from start
         * time to next time, ignoring duplicates in the times requested.
         */
        long startTime = filter.getStart();
        for (long time : filter.getTimesRequested()) {
            if (time > startTime) {
                return startTime - (time - startTime);
            }
        }
        return startTime;
    }

    private static @Nullable String extractThreadName(String key) {
        String[] strings = key.split(KernelCpuUsageAnalysis.SPLIT_STRING, 2);
        if ((strings.length > 1) && !(strings[1].equals(KernelCpuUsageAnalysis.TID_ZERO))) {
            return strings[1];
        }
        return null;
    }

    private static double normalize(long prevTime, long time, long value) {
        return (double) value / (time - prevTime) * 100;
    }

    @Override
    protected @Nullable Collection<@NonNull IYModel> getYSeriesModels(@NonNull ITmfStateSystem ss, @NonNull Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        Set<Integer> cpus = Collections.emptySet();

        SelectionTimeQueryFilter filter = createCpuQuery(fetchParameters);
        if (filter == null) {
            filter = FetchParametersUtils.createSelectionTimeQuery(fetchParameters);
            if (filter == null) {
                return null;
            }
        }

        if (filter instanceof SelectedCpuQueryFilter) {
            cpus = ((SelectedCpuQueryFilter) filter).getSelectedCpus();
        }

        long[] xValues = filter.getTimesRequested();

        /* CPU usage values for total and selected thread */
        double[] totalValues = new double[xValues.length];
        Map<String, IYModel> selectedThreadValues = new HashMap<>();
        Map<Long, double[]> cgroupsValues = new HashMap<>();
        for (Entry<Long, Integer> entry : getSelectedEntries(filter).entrySet()) {
            String name = Integer.toString(entry.getValue());
            selectedThreadValues.put(name, new YModel(entry.getKey(), getTrace().getName() + ':' + name, new double[xValues.length]));
            // check if a Cgroup is selected
            if (idToCgroup.containsKey(entry.getKey())) {
                cgroupsValues.put(entry.getKey(), new double[xValues.length]);
            }
        }

        long prevTime = Math.max(getInitialPrevTime(filter), ss.getStartTime());
        long currentEnd = ss.getCurrentEndTime();

        for (int i = 0; i < xValues.length; i++) {
            long time = xValues[i];
            if (time < ss.getStartTime() || time > currentEnd) {
                /* Leave empty if time xValue is out of bounds */
                prevTime = time;
                continue;
            }
            if (prevTime < time) {
                Map<String, Long> cpuUsageMap = Maps.filterKeys(getAnalysisModule().getCpuUsageInRange(cpus, prevTime, time),
                        key -> key.startsWith(KernelCpuUsageAnalysis.TOTAL));

                /*
                 * Calculate the sum of all total entries, and add a data point
                 * to the selected one
                 */
                long totalCpu = 0;
                for (Entry<String, Long> entry : cpuUsageMap.entrySet()) {
                    String threadName = extractThreadName(entry.getKey());
                    if (threadName != null) {
                        long cpuTime = entry.getValue();
                        totalCpu += cpuTime;
                        IYModel values = selectedThreadValues.get(threadName);
                        if (values != null) {
                            values.getData()[i] = normalize(prevTime, time, cpuTime);
                        }
                        // FIXME inefficient
                        // sum to cgroup
                        for (Long cgroupId : cgroupsValues.keySet()) {
                            Set<Integer> tids = cgroupsAnalysis.CgroupToTids.get(idToCgroup.get(cgroupId));
                            Integer tid = Integer.parseInt(threadName);
                            boolean isThreadInSelectedCgroup = tids.contains(tid);
                            if (isThreadInSelectedCgroup) {
                                double[] cgroupValues = cgroupsValues.get(cgroupId);
                                if (cgroupValues != null) {
                                    cgroupValues[i] += normalize(prevTime, time, cpuTime);
                                }
                            }
                        }
                    }
                }
                totalValues[i] = normalize(prevTime, time, totalCpu);
            } else if (i > 0) {
                /* In case of duplicate time xValue copy previous yValues */
                for (IYModel values : selectedThreadValues.values()) {
                    values.getData()[i] = values.getData()[i - 1];
                }
                totalValues[i] = totalValues[i - 1];
            }
            prevTime = time;
            if (monitor != null && monitor.isCanceled()) {
                return null;
            }
        }

        ImmutableList.Builder<IYModel> ySeries = ImmutableList.builder();
        String key = TOTAL + getTrace().getName();
        ySeries.add(new YModel(getId(ITmfStateSystem.ROOT_ATTRIBUTE), key, totalValues));
        for (Long cgroupId : cgroupsValues.keySet()) {
            double[] cgroupValues = cgroupsValues.get(cgroupId);
            Long cgroup = idToCgroup.get(cgroupId);
            if (cgroupValues != null && cgroup != null) {
                ySeries.add(new YModel(cgroupId, cgroup.toString() + ':' + getTrace().getName(), cgroupValues));
            }
        }
        for (IYModel entry : selectedThreadValues.values()) {
            ySeries.add(entry);
        }

        return ySeries.build();
    }

    @Override
    protected @NonNull String getTitle() {
        return "Cgroups CPU Usage";
    }

    @Override
    protected boolean isCacheable() {
        return false;
    }

    @Override
    protected TmfTreeModel<@NonNull CgroupsCpuUsageEntryModel> getTree(@NonNull ITmfStateSystem ss, @NonNull Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        cgroupsAnalysis.waitForCompletion();

        TimeQueryFilter filter = FetchParametersUtils.createTimeQuery(fetchParameters);
        if (filter == null) {
            return new TmfTreeModel<>(Collections.emptyList(), Collections.emptyList());
        }
        long end = filter.getEnd();

        Builder<@NonNull CgroupsCpuUsageEntryModel> builder = new Builder<>();
        Set<Integer> cpus = extractCpuSet(fetchParameters);
        Map<String, Long> cpuUsageMap = getAnalysisModule().getCpuUsageInRange(cpus, filter.getStart(), end);
        double timeRange = end - filter.getStart();

        long totalTime = cpuUsageMap.getOrDefault(KernelCpuUsageAnalysis.TOTAL, 0l);
        long rootId = getId(ITmfStateSystem.ROOT_ATTRIBUTE);
        builder.add(new CgroupsCpuUsageEntryModel(rootId, -1, ImmutableList.of(getTrace().getName(), "total", String.format(Messages.CpuUsageDataProvider_TextPercent, timeRange > 0 ? 100 * totalTime / timeRange : (float) 0), TIME_FORMATTER.format(totalTime)), -1, totalTime));

        // Generate ids for cgroups
        for (Long cgroup : cgroupsAnalysis.CgroupToTids.keySet()) {
            Long cgroupId = getId(Math.abs(cgroup.hashCode())); // FIXME not
                                                                // unique
            idToCgroup.put(cgroupId, cgroup);
        }

        // Check tids total + cgroups
        Map<Long, Long> cgroupCpuUsageTotal = new HashMap<>();
        ArrayList<@NonNull CgroupsCpuUsageEntryModel> tidBuilder = new ArrayList<>();
        for (Entry<String, Long> entry : cpuUsageMap.entrySet()) {
            /*
             * Process only entries representing the total of all CPUs and that
             * have time on CPU
             */
            String key = entry.getKey();
            if (entry.getValue() == 0 || !key.startsWith(KernelCpuUsageAnalysis.TOTAL)) {
                continue;
            }
            String[] strings = key.split(KernelCpuUsageAnalysis.SPLIT_STRING, 2);

            if (strings.length > 1) {
                int tid = Integer.parseInt(strings[1]);
                Long time = entry.getValue();
                Long cgroup = cgroupsAnalysis.TidToCgroup.get(tid);
                if (tid != 0 && cgroup != null) {
                    Long cgroupId = getId(Math.abs(cgroup.hashCode()));
                    String procname = cgroupsAnalysis.TidToProcname.get(tid);
                    cgroupCpuUsageTotal.put(cgroupId, cgroupCpuUsageTotal.getOrDefault(cgroupId, 0L) + time);
                    tidBuilder.add(new CgroupsCpuUsageEntryModel(getId(tid), cgroupId, ImmutableList.of(procname, String.valueOf(tid), String.format(Messages.CpuUsageDataProvider_TextPercent, timeRange > 0 ? 100 * time / timeRange : (float) 0), TIME_FORMATTER.format(time)), tid, time));
                }
            }
        }
        // Add cgroups (display even if not used)
        for (Long cgroup : cgroupsAnalysis.CgroupToTids.keySet()) {
            Long cgroupId = getId(Math.abs(cgroup.hashCode()));
            Long totalCgroupTime = cgroupCpuUsageTotal.getOrDefault(cgroupId, 0L);
            String cgroupName = cgroupsAnalysis.CgroupToUID.getOrDefault(cgroup, cgroup.toString());
            builder.add(new CgroupsCpuUsageEntryModel(cgroupId, rootId, ImmutableList.of(cgroupName, "cgroup", String.format(Messages.CpuUsageDataProvider_TextPercent, timeRange > 0 ? 100 * totalCgroupTime / timeRange : (float) 0), TIME_FORMATTER.format(totalCgroupTime)), -1, totalCgroupTime));
        }
        // Add tids
        builder.addAll(tidBuilder);

        return new TmfTreeModel<>(ImmutableList.of("Process", "%", "Time"), builder.build());
    }
}
