package org.eclipse.tracecompass.incubator.kubernetes.core.analysis.latency;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.datastore.core.interval.IHTIntervalReader;
import org.eclipse.tracecompass.datastore.core.serialization.ISafeByteBufferWriter;
import org.eclipse.tracecompass.datastore.core.serialization.SafeByteBufferFactory;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.segment.interfaces.INamedSegment;
import org.eclipse.tracecompass.tmf.core.model.ICoreElementResolver;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class PodStartup implements INamedSegment, ICoreElementResolver {

    private static final long serialVersionUID = 1L;

    public static final IHTIntervalReader<@NonNull ISegment> READER = buffer -> new PodStartup(buffer.getLong(), buffer.getLong(), buffer.getString(), buffer.getString());

    /**
     * The subset of information that is available from the sched wakeup/switch
     * entry event.
     */
    public static class InitialInfo {

        private long fStartTime;
        private String fName;
        private String fUID;

        public InitialInfo(
                long startTime,
                String name,
                String UID) {
            fStartTime = startTime;
            fName = name.intern();
            fUID = UID;
        }
    }

    private final long fStartTime;
    private final long fEndTime;
    private final String fName;
    private final String fUID;

    public PodStartup(
            InitialInfo info,
            long endTime) {
        fStartTime = info.fStartTime;
        fName = info.fName;
        fEndTime = endTime;
        fUID = info.fUID;
    }

    private PodStartup(long StartTime, long EndTime, String Name, String UID) {
        fStartTime = StartTime;
        fEndTime = EndTime;
        fName = Name;
        fUID = UID;
    }

    @Override
    public long getStart() {
        return fStartTime;
    }

    @Override
    public long getEnd() {
        return fEndTime;
    }

    public String getPodName() {
        return fName;
    }

    @Override
    public @NonNull String getName() {
        return fName;
    }

    public String getUID() {
        return fUID;
    }

    @Override
    public int getSizeOnDisk() {
        return 2 * Long.BYTES + SafeByteBufferFactory.getStringSizeInBuffer(fName) + SafeByteBufferFactory.getStringSizeInBuffer(fUID);
    }

    @Override
    public void writeSegment(@NonNull ISafeByteBufferWriter buffer) {
        buffer.putLong(fStartTime);
        buffer.putLong(fEndTime);
        buffer.putString(fName);
        buffer.putString(fUID);
    }

    @Override
    public int compareTo(@NonNull ISegment o) {
        int ret = INamedSegment.super.compareTo(o);
        if (ret != 0) {
            return ret;
        }
        return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return "Start Time = " + getStart() + //$NON-NLS-1$
                "; End Time = " + getEnd() + //$NON-NLS-1$
                "; Duration = " + getLength() + //$NON-NLS-1$
                "; Name = " + getName() + //$NON-NLS-1$
                "; UID = " + getUID(); //$NON-NLS-1$
    }

    @Override
    public @NonNull Multimap<@NonNull String, @NonNull Object> getMetadata() {
        Multimap<String, Object> map = HashMultimap.create();
        map.put("UID", fUID);
        return map;
    }

}