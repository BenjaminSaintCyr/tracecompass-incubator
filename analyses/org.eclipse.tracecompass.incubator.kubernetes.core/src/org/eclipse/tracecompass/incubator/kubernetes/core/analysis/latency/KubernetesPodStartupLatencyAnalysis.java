package org.eclipse.tracecompass.incubator.kubernetes.core.analysis.latency;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.timing.core.segmentstore.AbstractSegmentStoreAnalysisEventBasedModule;
import org.eclipse.tracecompass.datastore.core.interval.IHTIntervalReader;
import org.eclipse.tracecompass.incubator.kubernetes.core.analysis.KubernetesStrings;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.ISegmentStore;
import org.eclipse.tracecompass.segmentstore.core.SegmentComparators;
import org.eclipse.tracecompass.segmentstore.core.SegmentStoreFactory.SegmentStoreType;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.segment.ISegmentAspect;

import com.google.common.collect.ImmutableList;

public class KubernetesPodStartupLatencyAnalysis extends AbstractSegmentStoreAnalysisEventBasedModule {

    /**
     * The ID of this analysis
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.kubernetes.core.analysis.latency.KubernetesPodStartupLatencyAnalysis"; //$NON-NLS-1$
    private static final int VERSION = 1;

    private static final Collection<ISegmentAspect> BASE_ASPECTS = ImmutableList.of(PodNameAspect.INSTANCE, PodUIDAspect.INSTANCE);

    /**
     * Constructor
     */
    public KubernetesPodStartupLatencyAnalysis() {
        // do nothing
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Iterable<ISegmentAspect> getSegmentAspects() {
        return BASE_ASPECTS;
    }

    @Override
    protected int getVersion() {
        return VERSION;
    }

    @Override
    protected @NonNull SegmentStoreType getSegmentStoreType() {
        return SegmentStoreType.OnDisk;
    }

    @Override
    protected AbstractSegmentStoreAnalysisRequest createAnalysisRequest(ISegmentStore<@NonNull ISegment> swsSegment, IProgressMonitor monitor) {
        return new PodStartupLatencyAnalysisRequest(swsSegment, monitor);
    }

    @Override
    protected @NonNull IHTIntervalReader<@NonNull ISegment> getSegmentReader() {
        return PodStartup.READER;
    }

    private class PodStartupLatencyAnalysisRequest extends AbstractSegmentStoreAnalysisRequest {
        private final Map<String, PodStartup.InitialInfo> fOngoingPodStartup = new HashMap<>();
        private final IProgressMonitor fMonitor;

        public PodStartupLatencyAnalysisRequest(ISegmentStore<@NonNull ISegment> swsSegment, IProgressMonitor monitor) {
            super(swsSegment);
            fMonitor = monitor;
        }

        private String getEventField(String field, String opCtx) {
            String[] parts = opCtx.split(", ");
            String reason = "";
            String REASON_KEY = field + ": ";
            for (String part : parts) {
                if (part.startsWith(REASON_KEY)) {
                    reason = part.substring(REASON_KEY.length());
                    break;
                }
            }
            return reason;
        }

        @Override
        public void handleData(final ITmfEvent event) {
            super.handleData(event);

            ITmfEventField content = event.getContent();
            if (content == null) {
                return;
            }

            if (!event.getName().equals(KubernetesStrings.KUBERNETES_EVENT)) {
                return;
            }

            String operationContext = content.getFieldValue(String.class, KubernetesStrings.OPERATION_CONTEXT);
            String opName = event.getContent().getFieldValue(String.class, KubernetesStrings.OPERATION_NAME);
            if (opName == null || operationContext == null || !opName.equals("Event")) {
                return;
            }

            String reason = getEventField("Reason", operationContext);
            if (reason.equals("Pulling")) {
                /* This is a sched_wakeup event */
                String uid = getEventField("UID", operationContext);
                if (uid == null || uid.isEmpty()) {
                    return;
                }
                /* Record the event's data into the intial system call info */
                long startTime = event.getTimestamp().toNanos();
                String PodName = getEventField("Name", operationContext);


                PodStartup.InitialInfo newPodStartup = new PodStartup.InitialInfo(startTime, PodName, uid);
                fOngoingPodStartup.put(uid, newPodStartup);

            } else if (reason.equals("Started")) {
                /* This is a sched_switch event */
                String uid = getEventField("UID", operationContext);
                if (uid == null || uid.isEmpty()) {
                    return;
                }
                PodStartup.InitialInfo info = fOngoingPodStartup.remove(uid);
                if (info == null) {
                    /*
                     * We have not seen the sched_wakeup event corresponding to
                     * this thread (lost event, or before start of trace).
                     */
                    return;
                }
                long endTime = event.getTimestamp().toNanos();
                PodStartup pod = new PodStartup(info, endTime);
                getSegmentStore().add(pod);
            }
        }

        @Override
        public void handleCompleted() {
            fOngoingPodStartup.clear();
            super.handleCompleted();
        }

        @Override
        public void handleCancel() {
            fMonitor.setCanceled(true);
            super.handleCancel();
        }
    }

    private static final class PodUIDAspect implements ISegmentAspect {
        public static final ISegmentAspect INSTANCE = new PodUIDAspect();

        private PodUIDAspect() {
            // Do nothing
        }

        @Override
        public String getHelpText() {
            return "TODO UID";
        }

        @Override
        public String getName() {
            return "UID";
        }

        @Override
        public @Nullable Comparator<?> getComparator() {
            return (ISegment segment1, ISegment segment2) -> {
                if (segment1 == null) {
                    return 1;
                }
                if (segment2 == null) {
                    return -1;
                }
                if (segment1 instanceof PodStartup && segment2 instanceof PodStartup) {
                    int res = ((PodStartup) segment1).getUID().compareToIgnoreCase(((PodStartup) segment2).getUID());
                    return (res != 0 ? res : SegmentComparators.INTERVAL_START_COMPARATOR.thenComparing(SegmentComparators.INTERVAL_END_COMPARATOR).compare(segment1, segment2));
                }
                return 0;
            };
        }

        @Override
        public @Nullable String resolve(ISegment segment) {
            if (segment instanceof PodStartup) {
                return ((PodStartup) segment).getUID();
            }
            return null;
        }
    }

    private static final class PodNameAspect implements ISegmentAspect {
        public static final ISegmentAspect INSTANCE = new PodNameAspect();

        private PodNameAspect() {
            // Do nothing
        }

        @Override
        public String getHelpText() {
            return "TODO pod name";
        }

        @Override
        public String getName() {
            return "Pod Name";
        }

        @Override
        public @Nullable Comparator<?> getComparator() {
            return (ISegment segment1, ISegment segment2) -> {
                if (segment1 == null) {
                    return 1;
                }
                if (segment2 == null) {
                    return -1;
                }
                if (segment1 instanceof PodStartup && segment2 instanceof PodStartup) {
                    int res = ((PodStartup) segment1).getPodName().compareToIgnoreCase(((PodStartup) segment2).getPodName());
                    return (res != 0 ? res : SegmentComparators.INTERVAL_START_COMPARATOR.thenComparing(SegmentComparators.INTERVAL_END_COMPARATOR).compare(segment1, segment2));
                }
                return 1;
            };
        }

        @Override
        public @Nullable String resolve(ISegment segment) {
            if (segment instanceof PodStartup) {
                return ((PodStartup) segment).getPodName();
            }
            return null;
        }
    }
}
