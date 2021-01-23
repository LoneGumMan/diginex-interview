package alick.diginex.orderbook;

import java.util.List;

/**
 * The result of matching execution.
 * </p>
 * Contains the total matched quantity, and the list of orders matched against with the respective matched quantities
 */
class MatchResult {
	private final double totalMatchedQuantity;
	private final List<OrderBucket.MatchedOrder> matchedOrders;
	private final List<Long> doneOrderIds;

	MatchResult(final double totalMatchedQuantity, final List<OrderBucket.MatchedOrder> matchedOrders, final List<Long> doneOrderIds) {
		this.totalMatchedQuantity = totalMatchedQuantity;
		this.matchedOrders = matchedOrders;
		this.doneOrderIds = doneOrderIds;
	}

	double getTotalMatchedQuantity() {
		return totalMatchedQuantity;
	}

	List<OrderBucket.MatchedOrder> getMatchedOrders() {
		return matchedOrders;
	}

	List<Long> getDoneOrderIds() {
		return doneOrderIds;
	}

	@Override
	public String toString() {
		return "MatchResult(" +
				"totalMatchedQuantity=" + totalMatchedQuantity +
				", matchedOrders=" + matchedOrders +
				", doneOrderIds=" + doneOrderIds +
				')';
	}
}
