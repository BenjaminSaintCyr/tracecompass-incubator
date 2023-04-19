package org.eclipse.tracecompass.incubator.kubernetes.core.analysis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.callstack.core.base.QuarkEdgeStateValue;
import org.eclipse.tracecompass.incubator.internal.kubernetes.core.Activator;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class UnifiedGlobalStateProvider extends AbstractTmfStateProvider {

    private static final String ID = "org.eclipse.tracecompass.incubator.kubernetes.core.stateprovider.interval"; //$NON-NLS-1$
    private static final int VERSION = 1;

    private final Map<String, Integer> spanToQuark;
    private final BiMap<String, String> podNameUid;

    private final Map<String, String> DeploymentReplicaSet;

    public UnifiedGlobalStateProvider(@NonNull ITmfTrace trace) {
        super(trace, ID);
        spanToQuark = new HashMap<>();
        podNameUid = HashBiMap.create();
        DeploymentReplicaSet = new HashMap<>();
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public ITmfStateProvider getNewInstance() {
        return new UnifiedGlobalStateProvider(getTrace());
    }

    private int getOrDefaultQuark(ITmfStateSystemBuilder ssb, String processName) {
        int processQuark;
        if (spanToQuark.containsKey(processName)) {
            processQuark = spanToQuark.getOrDefault(processName, 0);
        } else {
            processQuark = ssb.getQuarkAbsoluteAndAdd(processName);
            spanToQuark.put(processName, processQuark);
        }
        return processQuark;
    }

    private static String getEventField(String field, String opCtx) {
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

    private void groupChildrens(ITmfStateSystemBuilder ssb, String processName, int processQuark, String reason, String message) {
        boolean isDeployment = reason.equals("ScalingReplicaSet");
        boolean isReplicaSet = reason.equals("SuccessfulCreate");
        if (isDeployment || isReplicaSet) {
            String childName = message.split(isDeployment ? "Scaled up replica set | to" : "Created pod: ")[1];
            // Condition can be faster than Event
            spanToQuark.computeIfAbsent(childName, name -> ssb.getQuarkRelativeAndAdd(processQuark, name));
            if (isDeployment) {
                DeploymentReplicaSet.put(processName, childName);
            }
        }
    }

    private static String getOwners(String operationContext) {
        String owners = getEventField("Owners", operationContext);
        Pattern pattern = Pattern.compile("\\[\\s*([^,\\]]*?)\\s*(,|\\])");
        Matcher matcher = pattern.matcher(owners);

        if (matcher.find()) {
            String firstElement = matcher.group(1);
            firstElement = firstElement.trim();
            return firstElement;
        }
        return "";
    }

    @Override
    protected void eventHandle(ITmfEvent event) {
        try {

            ITmfEventField content = event.getContent();
            if (content == null) {
                return;
            }

            ITmfStateSystemBuilder ssb = getStateSystemBuilder();
            if (ssb == null) {
                return;
            }

            if (!event.getName().equals(KubernetesStrings.KUBERNETES_EVENT)) {
                return;
            }

            long timestamp = event.getTimestamp().toNanos();

            String operationContext = content.getFieldValue(String.class, KubernetesStrings.OPERATION_CONTEXT);
            String opName = event.getContent().getFieldValue(String.class, KubernetesStrings.OPERATION_NAME);
            if (opName == null || operationContext == null) {
                return;
            }

            String name = getEventField("Name", operationContext);
            if (name.isEmpty()) {
                return;
            }
            if (opName.equals("Event")) {
                String uid = getEventField("UID", operationContext);
                podNameUid.put(name, uid);
                String reason = getEventField("Reason", operationContext);

                // String parentName =
                // DeploymentReplicaSet.keySet().stream().filter(name::startsWith).findFirst().orElse("");
                // if (!parentName.isEmpty()) {
                // spanToQuark.computeIfAbsent(childName, name ->
                // ssb.getQuarkRelativeAndAdd(processQuark, name));
                // }
                String owner = getOwners(operationContext);
                if (!owner.isEmpty()) {
                    // find owner
                    Integer ownerQuark = spanToQuark.get(owner);
                    // make a relative one
                    if (ownerQuark != null) {
                        spanToQuark.computeIfAbsent(name, x -> ssb.getQuarkRelativeAndAdd(ownerQuark, x));
                    }
                }
                int processQuark = getOrDefaultQuark(ssb, name);
                ssb.modifyAttribute(timestamp, reason, processQuark);

                String message = getEventField("Message", operationContext);
                groupChildrens(ssb, name, processQuark, reason, message);
            } else if (opName.equals("Terminated")) {

                int processQuark = getOrDefaultQuark(ssb, name);
                ssb.modifyAttribute(timestamp, null, processQuark);
            } else if (opName.equals("Condition")) {
                String type = getEventField("Type", operationContext);

                // Condition can be faster than Event
                int processQuark = getOrDefaultQuark(ssb, name);
                ssb.modifyAttribute(timestamp, type, processQuark);
            }
        } catch (Exception e) {
            Activator.getInstance().logError(e.getMessage());
        }
    }

    public static void addArrow(ITmfStateSystemBuilder ssb, Long startTime, Long endTime, int src, int dest) {
        int edgeQuark = getAvailableEdgeQuark(ssb, startTime);
        Object edgeStateValue = new QuarkEdgeStateValue(src, dest);
        ssb.modifyAttribute(startTime, edgeStateValue, edgeQuark);
        ssb.modifyAttribute(endTime, (Object) null, edgeQuark);
    }

    private static int getAvailableEdgeQuark(ITmfStateSystemBuilder ssb, long startTime) {
        int edgeRoot = ssb.getQuarkAbsoluteAndAdd(KubernetesStrings.EDGES);
        List<@NonNull Integer> subQuarks = ssb.getSubAttributes(edgeRoot, false);

        for (int quark : subQuarks) {
            long start = ssb.getOngoingStartTime(quark);
            Object value = ssb.queryOngoing(quark);
            if (value == null && start <= startTime) {
                return quark;
            }
        }
        return ssb.getQuarkRelativeAndAdd(edgeRoot, Integer.toString(subQuarks.size()));
    }
}
