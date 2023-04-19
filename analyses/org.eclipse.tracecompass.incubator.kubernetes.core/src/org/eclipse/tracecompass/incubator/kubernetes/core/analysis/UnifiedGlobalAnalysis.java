package org.eclipse.tracecompass.incubator.kubernetes.core.analysis;

import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.analysis.requirements.TmfAbstractAnalysisRequirement;
import org.eclipse.tracecompass.tmf.core.analysis.requirements.TmfAbstractAnalysisRequirement.PriorityLevel;
import org.eclipse.tracecompass.tmf.core.analysis.requirements.TmfAnalysisEventRequirement;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;

import com.google.common.collect.ImmutableSet;

public class UnifiedGlobalAnalysis extends TmfStateSystemAnalysisModule {

    public static final String ID = "org.eclipse.tracecompass.incubator.kubernetes.core.analysis.UnifiedGlobalAnalysis"; //$NON-NLS-1$

    private @Nullable Set<TmfAbstractAnalysisRequirement> fAnalysisRequirements;

    @Override
    protected ITmfStateProvider createStateProvider() {
        return new UnifiedGlobalStateProvider(Objects.requireNonNull(getTrace()));
    }

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
            requirements = ImmutableSet.of(new TmfAnalysisEventRequirement(
                    ImmutableSet.of(KubernetesStrings.KUBERNETES_EVENT),
                    PriorityLevel.AT_LEAST_ONE));
            fAnalysisRequirements = requirements;
        }
        return requirements;
    }


}
