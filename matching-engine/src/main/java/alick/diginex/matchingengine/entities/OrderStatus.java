package alick.diginex.matchingengine.entities;

/**
 * mimic FIX order state
 */
public enum OrderStatus {
	NEW,
	PARTIAL_FILLED,
	FILLED,
	CANCELLED,
	REPLACED,
	REJECTED
}
