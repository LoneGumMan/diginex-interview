package alick.diginex.orderbook.response;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class Response {
	/**
	 * Denotes the order ID for which this response was generated
	 */
	private final Long orderId;

	/**
	 * Bid side order book summary; the {@link Level2Summary#getDepths()} traversal order is best price to worst
	 */
	private final Level2Summary bidSummary;

	/**
	 * Ask side order book summary; the {@link Level2Summary#getDepths()}  traversal order is best price to worst
	 */
	private final Level2Summary askSummary;

	private final List<Execution> executions;

	protected Response(final Long orderId, final Level2Summary bidSummary, final Level2Summary askSummary,  final List<Execution> executions) {
		this.orderId = Objects.requireNonNull(orderId, "order ID");
		this.bidSummary = Objects.requireNonNull(bidSummary, "bid summary");
		this.askSummary = Objects.requireNonNull(askSummary, "ask summary");
		this.executions = null != executions ? executions : Collections.emptyList();
	}

	public Long getOrderId() {
		return orderId;
	}

	public Level2Summary getBidSummary() {
		return bidSummary;
	}

	public Level2Summary getAskSummary() {
		return askSummary;
	}

	public List<Execution> getExecutions() {
		return executions;
	}
}