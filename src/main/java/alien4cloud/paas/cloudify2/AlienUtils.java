package alien4cloud.paas.cloudify2;

import alien4cloud.component.model.IndexedNodeType;

public class AlienUtils {

    private AlienUtils() {
    }

    /**
     * Verify that the given {@link IndexedNodeType} is from the given type.
     * 
     * @param indexedNodeType The {@link IndexedNodeType} to verify.
     * @param type The type to match
     * @return <code>true</code> if the {@link IndexedNodeType} is from the given type.
     */
    public static boolean isFromNodeType(IndexedNodeType indexedNodeType, String type) {
        return type.equals(indexedNodeType.getElementId())
                || (indexedNodeType.getDerivedFrom() != null && indexedNodeType.getDerivedFrom().contains(type));
    }
}
