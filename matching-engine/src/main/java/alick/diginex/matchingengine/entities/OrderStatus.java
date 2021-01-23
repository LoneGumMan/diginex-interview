package alick.diginex.matchingengine.entities;

/**
 * mimic FIX order state
 */
public enum OrderStatus {
	NEW,
	PARTIAL_FILLLED,
	FILLED,
	CANCELLED,
	REPLACED,
	REJECTED
}
