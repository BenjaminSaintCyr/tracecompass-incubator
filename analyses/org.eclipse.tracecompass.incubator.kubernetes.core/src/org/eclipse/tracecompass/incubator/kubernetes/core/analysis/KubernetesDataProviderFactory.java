package org.eclipse.tracecompass.incubator.kubernetes.core.analysis;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor.ProviderType;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderFactory;
import org.eclipse.tracecompass.tmf.core.model.DataProviderDescriptor;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

public class KubernetesDataProviderFactory implements IDataProviderFactory {

    private static final IDataProviderDescriptor DESCRIPTOR = new DataProviderDescriptor.Builder()
            .setId(KubernetesDataProvider.ID)
            .setName(Objects.requireNonNull("k8s startup analysis"))
            .setDescription(Objects.requireNonNull("analyse the startup time of each containers"))
            .setProviderType(ProviderType.TIME_GRAPH)
            .build();

    @Override
    public @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> createProvider(ITmfTrace trace) {
        UnifiedGlobalAnalysis module = TmfTraceUtils.getAnalysisModuleOfClass(trace, UnifiedGlobalAnalysis.class, UnifiedGlobalAnalysis.getFullAnalysisId());
        if (module == null) {
            return null;
        }
        module.schedule();
        return new KubernetesDataProvider(trace, module);
    }

    @Override
    public Collection<IDataProviderDescriptor> getDescriptors(ITmfTrace trace) {
        return Collections.singletonList(DESCRIPTOR);
    }
}
