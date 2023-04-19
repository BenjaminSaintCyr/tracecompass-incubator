package org.eclipse.tracecompass.incubator.kubernetes.core.analysis.cgroups;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.cpuusage.KernelCpuUsageAnalysis;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor.ProviderType;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderFactory;
import org.eclipse.tracecompass.tmf.core.model.DataProviderDescriptor;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

public class CgroupsDataProviderFactory implements IDataProviderFactory {

    private static final IDataProviderDescriptor DESCRIPTOR = new DataProviderDescriptor.Builder()
            .setId(CgroupsDataProvider.ID)
            .setName(Objects.requireNonNull("k8s cgroups CPU usage analysis"))
            .setDescription(Objects.requireNonNull("Analyse the CPU usage of each cgroups"))
            .setProviderType(ProviderType.TIME_GRAPH)
            .build();

    @Override
    public @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> createProvider(ITmfTrace trace) {
        CgroupsAnalysis cgroupsAnalysis = TmfTraceUtils.getAnalysisModuleOfClass(trace, CgroupsAnalysis.class, CgroupsAnalysis.getFullAnalysisId());
        KernelCpuUsageAnalysis kernelAnalysis = TmfTraceUtils.getAnalysisModuleOfClass(trace, KernelCpuUsageAnalysis.class, KernelCpuUsageAnalysis.ID);
        if (cgroupsAnalysis == null || kernelAnalysis == null) {
            return null;
        }
        cgroupsAnalysis.schedule();
        kernelAnalysis.schedule();
        return new CgroupsDataProvider(trace, kernelAnalysis, cgroupsAnalysis);
    }

    @Override
    public Collection<IDataProviderDescriptor> getDescriptors(ITmfTrace trace) {
        return Collections.singletonList(DESCRIPTOR);
    }
}
