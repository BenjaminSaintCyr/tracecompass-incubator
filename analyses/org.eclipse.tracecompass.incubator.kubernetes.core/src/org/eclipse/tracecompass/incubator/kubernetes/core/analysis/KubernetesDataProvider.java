package org.eclipse.tracecompass.incubator.kubernetes.core.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.callstack.core.base.QuarkEdgeStateValue;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.FetchParametersUtils;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderParameterUtils;
import org.eclipse.tracecompass.tmf.core.model.CommonStatusMessage;
import org.eclipse.tracecompass.tmf.core.model.IOutputStyleProvider;
import org.eclipse.tracecompass.tmf.core.model.OutputElementStyle;
import org.eclipse.tracecompass.tmf.core.model.OutputStyleModel;
import org.eclipse.tracecompass.tmf.core.model.StyleProperties;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.timegraph.AbstractTimeGraphDataProvider;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphArrow;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphArrow;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphEntryModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
import org.eclipse.tracecompass.tmf.core.response.ITmfResponse;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

public class KubernetesDataProvider extends AbstractTimeGraphDataProvider<@NonNull UnifiedGlobalAnalysis, @NonNull TimeGraphEntryModel> implements IOutputStyleProvider {

    /** Data provider suffix ID */
    public static final String ID = "org.eclipse.tracecompass.incubator.kubernetes.core.analysis.UnifiedGlobalAnalysis.dataprovider"; //$NON-NLS-1$
    // Map of styles with the parent
    private static final Map<String, OutputElementStyle> STYLE_MAP = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, KubernetesStyles> STATE_TO_STYLE;


    static {
        ImmutableMap.Builder<String, KubernetesStyles> builder = new ImmutableMap.Builder<>();
        builder.put("ScalingReplicaSet", KubernetesStyles.ORCHESTRATING);
        builder.put("PodScheduled", KubernetesStyles.ORCHESTRATING);
        builder.put("SuccessfulCreate", KubernetesStyles.ORCHESTRATING);
        builder.put("Pulling", KubernetesStyles.CREATING);
        builder.put("Pulled", KubernetesStyles.CREATING);
        builder.put("Created", KubernetesStyles.CREATING);
        builder.put("Started", KubernetesStyles.RUNNING);
        builder.put("Killing", KubernetesStyles.TERMINATING);
        builder.put("Evicted", KubernetesStyles.TERMINATING);
        builder.put("SuccessfulDelete", KubernetesStyles.TERMINATING);
        builder.put("BackOff", KubernetesStyles.ERROR);
        builder.put("Failed", KubernetesStyles.ERROR);
        builder.put("FailedDaemonPod", KubernetesStyles.ERROR);
        builder.put("FailedMount", KubernetesStyles.ERROR);
        builder.put("FreeDiskSpaceFailed", KubernetesStyles.ERROR);
        builder.put("EvictionThresholdMet", KubernetesStyles.ERROR);
        builder.put("ExceededGracePeriod", KubernetesStyles.ERROR);
        builder.put("NodeHasDiskPressure", KubernetesStyles.ERROR);
        builder.put("NodeHasNoDiskPressure", KubernetesStyles.ERROR);
        STATE_TO_STYLE = builder.build();
    }


    public KubernetesDataProvider(ITmfTrace trace, UnifiedGlobalAnalysis analysisModule) {
        super(trace, analysisModule);
    }

    @Override
    public TmfModelResponse<List<ITimeGraphArrow>> fetchArrows(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        UnifiedGlobalAnalysis module = getAnalysisModule();
        if (!module.waitForInitialization()) {
            return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, CommonStatusMessage.ANALYSIS_INITIALIZATION_FAILED);
        }

        ITmfStateSystem ss = module.getStateSystem();
        if (ss == null) {
            return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, CommonStatusMessage.STATE_SYSTEM_FAILED);
        }

        TimeQueryFilter filter = FetchParametersUtils.createTimeQuery(fetchParameters);
        if (filter == null) {
            return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }

        TreeMultimap<Integer, ITmfStateInterval> intervals = TreeMultimap.create(Comparator.naturalOrder(),
                Comparator.comparing(ITmfStateInterval::getStartTime));
        Collection<Long> times = getTimes(filter, ss.getStartTime(), ss.getCurrentEndTime());
        List<Integer> quarks;
        try {
            quarks = ss.getSubAttributes(ss.getQuarkAbsolute(KubernetesStrings.EDGES), false);
        } catch (AttributeNotFoundException e1) {
            return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }

        try {
            for (ITmfStateInterval interval : ss.query2D(quarks, times)) {
                if (monitor != null && monitor.isCanceled()) {
                    return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
                }
                intervals.put(interval.getAttribute(), interval);
            }
        } catch (IndexOutOfBoundsException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (TimeRangeException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (StateSystemDisposedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        List<ITimeGraphArrow> tgArrows = new ArrayList<>();
        for (ITmfStateInterval interval : intervals.values()) {
            long duration = interval.getEndTime() - interval.getStartTime() + 1;
            QuarkEdgeStateValue edge = (QuarkEdgeStateValue) interval.getValue();
            if (edge == null) {
                continue;
            }
            long src = getId(edge.getSource());
            long dst = getId(edge.getDestination());
            tgArrows.add(new TimeGraphArrow(src, dst, interval.getStartTime(), duration));
        }

        return new TmfModelResponse<>(tgArrows, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public TmfModelResponse<Map<String, String>> fetchTooltip(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public String getId() {
        return ID;
    }



    private static OutputElementStyle getStyleFor(String state) {
        // map state to style
        // compute style if absent
        return STYLE_MAP.computeIfAbsent(state, fstate -> {
            Map<String, Object> defaultStyle = STATE_TO_STYLE.getOrDefault(fstate, KubernetesStyles.UNKNOWN).toMap();
            ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
            Map<String, Object> style = builder.putAll(defaultStyle).put(StyleProperties.STYLE_NAME, fstate).build();
            return new OutputElementStyle(state, style);
        });
    }

    @Override
    protected @Nullable TimeGraphModel getRowModel(ITmfStateSystem ss, Map<String, Object> parameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        TreeMultimap<Integer, ITmfStateInterval> intervals = TreeMultimap.create(Comparator.naturalOrder(),
                Comparator.comparing(ITmfStateInterval::getStartTime));
        SelectionTimeQueryFilter filter = FetchParametersUtils.createSelectionTimeQuery(parameters);
        Map<@NonNull Long, @NonNull Integer> entries = getSelectedEntries(filter);
        Collection<Long> times = getTimes(filter, ss.getStartTime(), ss.getCurrentEndTime());
        /* Do the actual query */
        for (ITmfStateInterval interval : ss.query2D(entries.values(), times)) {
            if (monitor != null && monitor.isCanceled()) {
                return new TimeGraphModel(Collections.emptyList());
            }
            intervals.put(interval.getAttribute(), interval);
        }
        Map<@NonNull Integer, @NonNull Predicate<@NonNull Multimap<@NonNull String, @NonNull Object>>> predicates = new HashMap<>();
        Multimap<@NonNull Integer, @NonNull String> regexesMap = DataProviderParameterUtils.extractRegexFilter(parameters);
        if (regexesMap != null) {
            predicates.putAll(computeRegexPredicate(regexesMap));
        }
        List<@NonNull ITimeGraphRowModel> rows = new ArrayList<>();
        for (Map.Entry<@NonNull Long, @NonNull Integer> entry : entries.entrySet()) {
            if (monitor != null && monitor.isCanceled()) {
                return new TimeGraphModel(Collections.emptyList());
            }

            List<ITimeGraphState> eventList = new ArrayList<>();
            for (ITmfStateInterval interval : intervals.get(entry.getValue())) {
                long startTime = interval.getStartTime();
                long duration = interval.getEndTime() - startTime + 1;
                Object state = interval.getValue();
                TimeGraphState value;
                if (state == null) {
                    value = new TimeGraphState(startTime, duration, null, null);
                } else {
                    value = new TimeGraphState(startTime, duration, String.valueOf(state), getStyleFor(String.valueOf(state)));
                }
                applyFilterAndAddState(eventList, value, entry.getKey(), predicates, monitor);
            }
            rows.add(new TimeGraphRowModel(entry.getKey(), eventList));

        }
        return new TimeGraphModel(rows);
    }

    @Override
    protected boolean isCacheable() {
        return false;
    }

    @Override
    protected TmfTreeModel<TimeGraphEntryModel> getTree(ITmfStateSystem ss, Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        Builder<@NonNull TimeGraphEntryModel> builder = new Builder<>();
        long rootId = getId(ITmfStateSystem.ROOT_ATTRIBUTE);
        long start = ss.getStartTime();
        long end = ss.getCurrentEndTime();
        builder.add(new TimeGraphEntryModel(rootId, ITmfStateSystem.ROOT_ATTRIBUTE, Collections.singletonList(String.valueOf(getTrace().getName())), start, end));

        for (int traceQuark : ss.getSubAttributes(ITmfStateSystem.ROOT_ATTRIBUTE, false)) {
            addTrace(ss, builder, traceQuark, rootId);
        }
        // TODO from ss edge dump a collection of arrows

        return new TmfTreeModel<>(Collections.emptyList(), builder.build());
    }

    private void addTrace(ITmfStateSystem ss, Builder<@NonNull TimeGraphEntryModel> builder, int quark, long parentId) {
        long traceQuarkId = getId(quark);
        long start = ss.getStartTime();
        long end = ss.getCurrentEndTime();
        String traceName = ss.getAttributeName(quark);
        builder.add(new TimeGraphEntryModel(traceQuarkId, parentId, Collections.singletonList(traceName), start, end));
        addChildren(ss, builder, quark, traceQuarkId);
    }

    private void addChildren(ITmfStateSystem ss, Builder<@NonNull TimeGraphEntryModel> builder, int parentQuark, long parentId) {
        for (Integer child : ss.getSubAttributes(parentQuark, false)) {
            String childName = ss.getAttributeName(child);

            long childId = getId(child);
            builder.add(new TimeGraphEntryModel(childId, parentId, Collections.singletonList(childName), ss.getStartTime(), ss.getCurrentEndTime()));
            addChildren(ss, builder, child, childId);
        }
    }

    @Override
    public TmfModelResponse<@NonNull OutputStyleModel> fetchStyle(@NonNull Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(new OutputStyleModel(FlameDefaultPalette.getStyles()), ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

}
