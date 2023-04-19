package org.eclipse.tracecompass.incubator.kubernetes.core.analysis.cgroups;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAbstractAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.requirements.TmfAbstractAnalysisRequirement;
import org.eclipse.tracecompass.tmf.core.analysis.requirements.TmfAbstractAnalysisRequirement.PriorityLevel;
import org.eclipse.tracecompass.tmf.core.analysis.requirements.TmfAnalysisEventFieldRequirement;
import org.eclipse.tracecompass.tmf.core.component.ITmfEventProvider;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;
import org.eclipse.tracecompass.tmf.core.trace.location.ITmfLocation;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;

public class CgroupsAnalysis extends TmfAbstractAnalysisModule {

    public static final String ID = "org.eclipse.tracecompass.incubator.kubernetes.core.analysis.cgroups"; //$NON-NLS-1$

    private @Nullable Set<TmfAbstractAnalysisRequirement> fAnalysisRequirements;

    private @Nullable IProgressMonitor fMonitor;

    public final HashMultimap<Long, Integer> CgroupToTids = HashMultimap.create();
    public final Map<Integer, Long> TidToCgroup = new HashMap<>();
    public final Map<Long, String> CgroupToUID = new HashMap<>();
    public final Map<Integer, String> TidToProcname = new HashMap<>();

    private static final Pattern POD_UID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * @return Unified Global Analysis ID
     */
    public static String getFullAnalysisId() {
        return ID;
    }

    @Override
    public Iterable<@NonNull TmfAbstractAnalysisRequirement> getAnalysisRequirements() {
        Set<TmfAbstractAnalysisRequirement> requirements = fAnalysisRequirements;
        if (requirements == null) {
            requirements = ImmutableSet.of(new TmfAnalysisEventFieldRequirement(
                    StringUtils.EMPTY,
                    ImmutableSet.of("context.cgroups_ns"),
                    PriorityLevel.AT_LEAST_ONE));
            fAnalysisRequirements = requirements;
        }
        return requirements;
    }

    private void associateCgroups(Long cgroup, Integer tid) {
        if (cgroup != 0L) {
            CgroupToTids.put(cgroup, tid);
            TidToCgroup.put(tid, cgroup);
        }
    }

    private void associateProcname(Integer tid, String procname) {
        TidToProcname.put(tid, procname);
    }

    private static Integer parseTid(ITmfEventField content) {
        ITmfEventField rawTid = content.getField("context._tid");
        if (rawTid == null) {
            return null;
        }
        return Integer.parseInt(rawTid.getFormattedValue());
    }

    private static String parseProcname(ITmfEventField content) {
        ITmfEventField procname = content.getField("context._procname");
        if (procname == null) {
            return null;
        }
        return procname.getFormattedValue();
    }

    private static Long parseCgroup(ITmfEventField content) {
        ITmfEventField rawCgroup = content.getField("context._cgroup_ns");
        if (rawCgroup == null) {
            return null;
        }
        Long cgroup = Long.parseLong(rawCgroup.getFormattedValue());
        return cgroup;
    }

    @Override
    protected boolean executeAnalysis(@NonNull IProgressMonitor monitor) throws TmfAnalysisException {
        fMonitor = monitor;
        ITmfTrace trace = getTrace();
        if (!(trace instanceof TmfExperiment)) {
            throw new IllegalStateException();
        }
        for (ITmfEventProvider subTrace : trace.getChildren()) {
            if (subTrace instanceof IKernelTrace) {
                IKernelTrace kernelTrace = ((IKernelTrace) subTrace);
                ITmfLocation location = kernelTrace.getCurrentLocation();
                analyseKernelTrace(kernelTrace, location, monitor);
            }
        }
        return !monitor.isCanceled();
    }

    private void analyseKernelTrace(IKernelTrace kernelTrace, ITmfLocation location, @NonNull IProgressMonitor monitor) {
        ITmfContext context = kernelTrace.seekEvent(location);
        ITmfEvent event = kernelTrace.getNext(context);

        int totalTasks = (int) kernelTrace.getNbEvents();
        long previousCheckTime = System.currentTimeMillis();
        monitor.beginTask("Associate tids to cgroups", totalTasks);
        int completedTasks = 0;
        int samplingRate = 10000;
        monitor.subTask("Events left " + String.valueOf(totalTasks - completedTasks));

        IKernelAnalysisEventLayout kernelLayout = kernelTrace.getKernelEventLayout();
        while (event != null && !monitor.isCanceled()) {
            String name = event.getName();
            if (kernelLayout.eventSchedSwitch().equals(name)) {
                // cgroups -> tid
                // tid -> procname
                ITmfEventField content = event.getContent();
                Integer tid = parseTid(content);
                Long cgroup = parseCgroup(content);
                String procname = parseProcname(content);
                boolean anyNull = tid == null || procname == null || cgroup == null;
                if (!anyNull) {
                    associateCgroups(cgroup, tid);
                    associateProcname(tid, procname);
                }
            } else if (name.equals("syscall_entry_mount")) {
                // cgroup to uid
                ITmfEventField content = event.getContent();
                String procname = parseProcname(content);
                if (procname != null && procname.equals("runc:[2:INIT]")) {

                    Long cgroup = parseCgroup(content);

                    String filename = content.getField("dev_name").getFormattedValue();
                    if (cgroup != null && filename != null && !filename.isEmpty()) {
                        Matcher matcher = POD_UID_PATTERN.matcher(filename);
                        if (matcher.find()) {
                            String uid = matcher.group(0);
                            CgroupToUID.put(cgroup, uid);
                        }
                    }
                }
            }
            monitor.worked(completedTasks++);
            if (completedTasks % samplingRate == 0) {
                long currentTime = System.currentTimeMillis();
                long elapsedTimeSinceLastCheck = currentTime - previousCheckTime;

                double averageTaskDuration = (double) elapsedTimeSinceLastCheck / samplingRate;
                int remainingTasks = totalTasks - completedTasks;
                long estimatedTimeForRemainingTasks = (long) (averageTaskDuration * remainingTasks);

                int minutesLeft = (int) (estimatedTimeForRemainingTasks / 60000);
                int secondsLeft = (int) (estimatedTimeForRemainingTasks / 1000) % 60;

                String etaMessage = String.format("Estimated time left: %d minutes, %d seconds", minutesLeft, secondsLeft);
                monitor.subTask(etaMessage);

                previousCheckTime = currentTime;  // Update the time of the last check
            }
            event = kernelTrace.getNext(context);
        }
        monitor.done();
    }

    @Override
    protected void canceling() {
        IProgressMonitor mon = fMonitor;
        if ((mon != null) && (!mon.isCanceled())) {
            mon.setCanceled(true);
        }
    }

}
