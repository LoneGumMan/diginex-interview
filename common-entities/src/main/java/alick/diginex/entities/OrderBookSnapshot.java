package alick.diginex.entities;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * A snapshot of an order book, with bid / ask queues at different prices, and the full depth at each price.
 */
public class OrderBookSnapshot {
	/**
	 * A basic mapping of order ID to its open quantity on the order book
	 */
	public static final class OrderOpenQty {
		private final long orderId;
		private final double openQty;
		public OrderOpenQty(final long orderId, final double openQty) {
			this.orderId = orderId;
			this.openQty = openQty;
		}

		public long getOrderId() {
			return orderId;
		}

		public double getOpenQty() {
			return openQty;
		}

		@Override
		public boolean equals(final Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			final OrderOpenQty that = (OrderOpenQty) o;
			return getOrderId() == that.getOrderId();
		}

		@Override
		public int hashCode() {
			return Objects.hash(getOrderId());
		}

		@Override
		public String toString() {
			return "OrderOpenQty(" +
					"orderId=" + getOrderId() +
					", openQty=" + getOpenQty() +
					')';
		}
	}

	private final List<OrderOpenQty> bidMarketQueue;
	private final List<OrderOpenQty> askMarketQueue;
	private final LinkedHashMap<Double, List<OrderOpenQty>> bidLimitQueue;
	private final LinkedHashMap<Double, List<OrderOpenQty>> askLimitQueue;

	public OrderBookSnapshot(
			final List<OrderOpenQty> bidMarketQueue,
			final List<OrderOpenQty> askMarketQueue,
			final LinkedHashMap<Double, List<OrderOpenQty>> bidLimitQueue,
			final LinkedHashMap<Double, List<OrderOpenQty>> askLimitQueue) {
		this.bidMarketQueue = bidMarketQueue;
		this.askMarketQueue = askMarketQueue;
		this.bidLimitQueue = bidLimitQueue;
		this.askLimitQueue = askLimitQueue;
	}

	/**
	 * The list of bid market orders on the order book.
	 *
	 * The traversal order is highest priority order first
	 */
	public List<OrderOpenQty> getBidMarketQueue() {
		return bidMarketQueue;
	}

	/**
	 * The list of ask market orders on the order book
	 *
	 * The traversal order is highest priority order first
	 */
	public List<OrderOpenQty> getAskMarketQueue() {
		return askMarketQueue;
	}

	/**
	 * The bid queues with prices; traversal order is highest price to lowest.
	 * Within each queue, the traversal order is highest priority order first
	 */
	public LinkedHashMap<Double, List<OrderOpenQty>> getBidLimitQueue() {
		return bidLimitQueue;
	}

	/**
	 * The ask queues with prices; traversal order is lowest price to highest
	 * Within each queue, the traversal order is highest priority order first
	 */
	public LinkedHashMap<Double, List<OrderOpenQty>> getAskLimitQueue() {
		return askLimitQueue;
	}

	@Override
	public String toString() {
		return "OrderBookSnapshot{" +
				"bidMarketQueue=" + bidMarketQueue +
				", askMarketQueue=" + askMarketQueue +
				", bidLimitQueue=" + bidLimitQueue +
				", askLimitQueue=" + askLimitQueue +
				'}';
	}
}