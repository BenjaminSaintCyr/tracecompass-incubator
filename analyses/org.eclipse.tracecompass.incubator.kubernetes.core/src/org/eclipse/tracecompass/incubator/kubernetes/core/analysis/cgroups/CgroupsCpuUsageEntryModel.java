package org.eclipse.tracecompass.incubator.kubernetes.core.analysis.cgroups;

import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.model.ICoreElementResolver;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeDataModel;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;

public class CgroupsCpuUsageEntryModel extends TmfTreeDataModel implements ICoreElementResolver {
    private final int fTid;
    private final long fTime;
    private final Multimap<String, Object> fMetadata;

    public CgroupsCpuUsageEntryModel(long id, long parentId, String processName, int tid, long time) {
        this(id, parentId, ImmutableList.of(processName, String.valueOf(tid), String.valueOf(0L), String.valueOf(time)), tid, time);
    }

    public CgroupsCpuUsageEntryModel(long id, long parentId, List<@NonNull String> labels, int tid, long time) {
        super(id, parentId, labels);
        fTid = tid;
        fTime = time;
        Multimap<String, Object> map = HashMultimap.create();
        fMetadata = map;
    }

    /**
     * Get the process name thread represented by this entry
     *
     * @return The thread's TID
     */
    public int getTid() {
        return fTid;
    }

    /**
     * Get the total time spent on CPU in the time interval represented by this
     * entry.
     *
     * @return The total time spent on CPU
     */
    public long getTime() {
        return fTime;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!super.equals(obj)) {
            // reference equality, nullness, getName, ID and parent ID
            return false;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        CgroupsCpuUsageEntryModel other = (CgroupsCpuUsageEntryModel) obj;
        return fTid == other.fTid
                && fTime == other.fTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fTid, fTime);
    }

    @Override
    public Multimap<String, Object> getMetadata() {
        // TODO Auto-generated method stub
        return fMetadata;
    }

}
