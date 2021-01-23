package alick.diginex.orderbook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

class OrderBucket {
	private final double priceOfBucket;
	private double quantityInQueue = 0.0d;

	// would have used a ArrayList from eclipse collections for quicker traversal
	private final ArrayList<OrderEntry> orderEntryList = new ArrayList<>(100);

	OrderBucket(final double priceOfBucket) {
		this.priceOfBucket = priceOfBucket;
	}

	double getPriceOfBucket() {
		return priceOfBucket;
	}

	double getQuantityInQueue() {
		return quantityInQueue;
	}

	ArrayList<OrderEntry> getOrderEntryList() {
		return orderEntryList;
	}

	/**
	 * Indicates whether the queue is empty
	 *
	 * @return {@code true} if there is no order within this waiting to be matched/executed; {@code false} otherwise.
	 */
	boolean isEmpty() {
		return 0 == quantityInQueue;
	}

	/**
	 * Add the given order to the back of this bucket.
	 *
	 * @param orderEntry the order entry to add
	 */
	boolean enqueueOrder(final OrderEntry orderEntry) {
		System.out.printf("Bucket(%f): Queueing order '%d', qty=%f%n", this.priceOfBucket, orderEntry.getOrderId(), orderEntry.getRemainingQuantity());
		final boolean success = this.orderEntryList.add(orderEntry);
		quantityInQueue += orderEntry.getRemainingQuantity();
		return success;
	}

	/**
	 * Represents an order which was matched, and the quantity that was matched against this order
	 */

	static class MatchedOrder {
		private final long orderId;
		private final double quantity;

		MatchedOrder(final long orderId, final double quantity) {
			this.orderId = orderId;
			this.quantity = quantity;
		}

		public long getOrderId() {
			return orderId;
		}

		public double getQuantity() {
			return quantity;
		}

		@Override
		public String toString() {
			return "MatchedOrder(" +
					"orderId=" + orderId +
					", quantity=" + quantity +
					')';
		}

		@Override
		public boolean equals(final Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			final MatchedOrder that = (MatchedOrder) o;
			return getOrderId() == that.getOrderId()
					&& getQuantity() == that.getQuantity();
		}

		@Override
		public int hashCode() {
			return Objects.hash(getOrderId(), getQuantity());
		}
	}

	/**
	 * cross the given order entry against this bucket's orders, taking as much quantity as possible.
	 *
	 * @param oppositeOrderEntry the order entry from opposite side
	 * @return The match result; contains a summary of how much was executed, and the executions, and
	 */
	MatchResult matchOrder(final OrderEntry oppositeOrderEntry) {
		System.out.printf("Bucket(%f): Matching order '%s' in bucket : %f shares@%s%n", this.priceOfBucket, oppositeOrderEntry, this.quantityInQueue, this.priceOfBucket);
		final ArrayList<MatchedOrder> matchedOrders = new ArrayList<>();

		final double originalQtyToMatch = oppositeOrderEntry.getRemainingQuantity();
		double remainingQty = originalQtyToMatch;

		for (final Iterator<OrderEntry> it = orderEntryList.iterator(); remainingQty > 0 && it.hasNext(); ) {
			final OrderEntry curEntry = it.next();
			final double qtyTaken = curEntry.takeQuantity(remainingQty);
			if (qtyTaken > 0) {
				System.out.printf("Bucket(%f): Matched '%d' against '%d', qty=%f%n", this.priceOfBucket, oppositeOrderEntry.getOrderId(), curEntry.getOrderId(), qtyTaken);
				matchedOrders.add(new MatchedOrder(curEntry.getOrderId(), qtyTaken));
				remainingQty -= qtyTaken;
			}
		}

		final double totalExecutedQty = originalQtyToMatch - remainingQty;

		List<Long> doneOrderIds = Collections.emptyList();
		this.quantityInQueue -= totalExecutedQty;
		if (totalExecutedQty > 0) {
			final ArrayList<Long> removedEntries = new ArrayList<>(matchedOrders.size());
			final boolean removed = this.orderEntryList.removeIf(orderEntry -> {
				if (orderEntry.isOrderDone()) {
					removedEntries.add(orderEntry.getOrderId());
					return true;
				}
				return false;
			});
			if (removed) {
				System.out.printf("Bucket(%f): Orders are done: %s%n", this.priceOfBucket, removedEntries);
				doneOrderIds = removedEntries;
			}
		}

		return new MatchResult(totalExecutedQty, matchedOrders, doneOrderIds);
	}

	/**
	 * Cancel the order with the given order ID.
	 *
	 * @param orderId the ID of the order to be cancelled
	 * @return {@code true} if the ordder entry was found and cancelled, {@code false} otherwise.
	 */
	boolean cancelOrder(final long orderId) {
		final Iterator<OrderEntry> it = this.orderEntryList.iterator();
		while (it.hasNext()) {
			final OrderEntry curEntry = it.next();
			if (curEntry.getOrderId() == orderId) {
				System.out.printf("Bucket(%f): Cancel order '%d'%n", this.priceOfBucket, orderId);
				it.remove();
				// take out the quantity for consistency, in case the object is referenced somewhere else
				this.quantityInQueue -= curEntry.takeQuantity(curEntry.getRemainingQuantity());
				return true;
			}
		}
		System.out.printf("Bucket(%f): Unable to cancel order '%d', order not found%n", this.priceOfBucket, orderId);
		return false;
	}

	/**
	 * Change the order quantity of the given order ID.
	 * <ol>
	 *     <li>If amend quantity down, then order retains its current position in the queue; otherwise</li>
	 *     <li>If amend quantity up, then order is placed at end of the queue.</li>
	 * </ol>
	 *
	 * @param orderId     order ID of the order to change
	 * @param newQuantity new order quantity
	 * @return {@code true} if the order was amended successfully, false otherwise
	 */
	boolean resizeOrder(final long orderId, final double newQuantity) {
		boolean queueOrderAtEnd = false;
		for (final Iterator<OrderEntry> it = this.orderEntryList.iterator(); it.hasNext(); ) {
			final OrderEntry curEntry = it.next();
			if (orderId != curEntry.getOrderId())
				continue;
			final double origQty = curEntry.getRemainingQuantity();
			final double delta = origQty - newQuantity;
			if (delta > 0) {
				System.out.printf("Bucket(%f): In-place amend order '%d' quantity from %f to %f%n", this.priceOfBucket, orderId, origQty, newQuantity);
				curEntry.takeQuantity(delta);
				this.quantityInQueue -= delta;
				return true;
			}
			else {
				System.out.printf("Bucket(%f): Removing order '%d' for re-queueing; quantity from %f to %f%n", this.priceOfBucket, orderId, origQty, newQuantity);
				queueOrderAtEnd = true;
				this.quantityInQueue -= curEntry.getRemainingQuantity();
				it.remove();
				break;
			}
		}
		if (queueOrderAtEnd)
			enqueueOrder(new OrderEntry(orderId, newQuantity));

		return queueOrderAtEnd;
	}
}
