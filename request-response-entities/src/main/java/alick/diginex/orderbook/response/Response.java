package alick.diginex.orderbook.response;

import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Getter
public abstract class Response {
	/**
	 * Denotes the order ID for which this response was generated
	 */
	private final long orderId;

	/**
	 * Bid side order book summary; the {@link Level2Summary#getDepths()} traversal order is best price to worst
	 */
	private final Level2Summary bidSummary;

	/**
	 * Ask side order book summary; the {@link Level2Summary#getDepths()}  traversal order is best price to worst
	 */
	private final Level2Summary askSummary;

	private final List<Execution> executions;

	protected Response(final long orderId, final Level2Summary bidSummary, final Level2Summary askSummary, final List<Execution> executions) {
		this.orderId = orderId;
		this.bidSummary = Objects.requireNonNull(bidSummary, "bid summary");
		this.askSummary = Objects.requireNonNull(askSummary, "ask summary");
		this.executions = null != executions ? executions : Collections.emptyList();
	}
}