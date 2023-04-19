package org.eclipse.tracecompass.incubator.callstack.core.base;

import java.util.Comparator;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.datastore.core.serialization.ISafeByteBufferWriter;
import org.eclipse.tracecompass.internal.provisional.statesystem.core.statevalue.CustomStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;

public class QuarkEdgeStateValue extends CustomStateValue {

    public static final byte CUSTOM_TYPE_ID = 23;

    private final int src;
    private final int dst;

    private static final Comparator<QuarkEdgeStateValue> COMPARATOR = Comparator
            .comparingInt(QuarkEdgeStateValue::getSource)
            .thenComparingInt(QuarkEdgeStateValue::getDestination);

    public QuarkEdgeStateValue(int srcQuark, int dstQuark) {
        src = srcQuark;
        dst = dstQuark;
    }

    @Override
    public int compareTo(@NonNull ITmfStateValue o) {
        if (o instanceof QuarkEdgeStateValue) {
            return COMPARATOR.compare(this, (QuarkEdgeStateValue) o);
        }

        return 1;
    }

    /**
     * Getter for the source thread
     *
     * @return the source {@link HostThread} object
     */
    public int getSource() {
        return src;
    }

    /**
     * Getter for the destination thread
     *
     * @return the destination {@link HostThread} object.
     */
    public int getDestination() {
        return dst;
    }

    @Override
    protected Byte getCustomTypeId() {
        return CUSTOM_TYPE_ID;
    }

    @Override
    protected void serializeValue(ISafeByteBufferWriter buffer) {
        buffer.putInt(src);
        buffer.putInt(dst);

    }

    @Override
    protected int getSerializedValueSize() {
        return Integer.BYTES * 2;
    }

}
