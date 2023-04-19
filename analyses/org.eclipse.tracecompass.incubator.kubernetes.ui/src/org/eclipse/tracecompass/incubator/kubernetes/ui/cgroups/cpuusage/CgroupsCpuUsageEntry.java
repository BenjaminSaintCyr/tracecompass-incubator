package org.eclipse.tracecompass.incubator.kubernetes.ui.cgroups.cpuusage;

import java.util.Objects;

import org.eclipse.tracecompass.analysis.os.linux.core.cpuusage.CpuUsageEntryModel;
import org.eclipse.tracecompass.incubator.kubernetes.core.analysis.cgroups.CgroupsCpuUsageEntryModel;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfGenericTreeEntry;

public class CgroupsCpuUsageEntry extends TmfGenericTreeEntry<CgroupsCpuUsageEntryModel> {
    private final double fPercent;

    /**
     * Constructor
     *
     * @param model
     *            {@link CpuUsageEntryModel} from the data provider
     * @param percent
     *            The percentage CPU usage
     */
    public CgroupsCpuUsageEntry(CgroupsCpuUsageEntryModel model, double percent) {
        super(model);
        fPercent = percent;
    }

    /**
     * Get the percentage of time spent on CPU in the time interval represented
     * by this entry.
     *
     * @return The percentage of time spent on CPU
     */
    public double getPercent() {
        return fPercent;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            // reference equality, nullness, getName, children and model
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        CgroupsCpuUsageEntry other = (CgroupsCpuUsageEntry) obj;
        return Objects.equals(fPercent, other.fPercent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fPercent);
    }
}