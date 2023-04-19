package org.eclipse.tracecompass.incubator.kubernetes.core.analysis;

import java.util.Map;

import org.eclipse.tracecompass.tmf.core.dataprovider.X11ColorUtils;
import org.eclipse.tracecompass.tmf.core.model.StyleProperties;

import com.google.common.collect.ImmutableMap;

public enum KubernetesStyles {
    UNKNOWN(100, 100, 100),
    ORCHESTRATING(33, 150, 243),
    CREATING(0, 150, 136),
    RUNNING(76, 175, 80),
    TERMINATING(63, 81, 181),
    ERROR(244, 67, 54);

    private final Map<String, Object> fMap;

    private KubernetesStyles(int red, int green, int blue) {
        this(red, green, blue, 255, 1.00f);
    }

    private KubernetesStyles(int red, int green, int blue, int alpha, float heightFactor) {
        if (red > 255 || red < 0) {
            throw new IllegalArgumentException("Red needs to be between 0 and 255"); //$NON-NLS-1$
        }
        if (green > 255 || green < 0) {
            throw new IllegalArgumentException("Green needs to be between 0 and 255"); //$NON-NLS-1$
        }
        if (blue > 255 || blue < 0) {
            throw new IllegalArgumentException("Blue needs to be between 0 and 255"); //$NON-NLS-1$
        }
        if (alpha > 255 || alpha < 0) {
            throw new IllegalArgumentException("alpha needs to be between 0 and 255"); //$NON-NLS-1$
        }
        if (heightFactor > 1.0 || heightFactor < 0) {
            throw new IllegalArgumentException("Height factor needs to be between 0 and 1.0, given hint : " + heightFactor); //$NON-NLS-1$
        }
        fMap = ImmutableMap.of(
                StyleProperties.BACKGROUND_COLOR, X11ColorUtils.toHexColor(red, green, blue),
                StyleProperties.HEIGHT, heightFactor,
                StyleProperties.OPACITY, (float) alpha / 255,
                StyleProperties.STYLE_GROUP, "K8S");
    }

    public Map<String, Object> toMap() {
        return fMap;
    }
}
