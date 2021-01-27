package alick.diginex.orderbook;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;

import java.util.ArrayList;
import java.util.Iterator;

@Getter
@Log4j2
class OrderBucket {
	private final double priceOfBucket;
	private double quantityInQueue = 0.0d;
	private final FastList<OrderEntry> orderEntryList = new FastList<>(100);

	@lombok.Builder(builderClassName = "Builder")
	private OrderBucket(final double priceOfBucket) {
		this.priceOfBucket = priceOfBucket;
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
		log.debug("Bucket({}): Queueing order '{}', qty={}", this.priceOfBucket, orderEntry.getOrderId(), orderEntry.getRemainingQuantity());
		final boolean success = this.orderEntryList.add(orderEntry);
		quantityInQueue += orderEntry.getRemainingQuantity();
		return success;
	}

	/**
	 * Represents an order which was matched, and the quantity that was matched against this order
	 */
	@Getter
	@ToString(of = {"orderId", "quantity"})
	@EqualsAndHashCode(of = {"orderId", "quantity"})
	@lombok.Builder(builderClassName = "Builder", access = AccessLevel.PRIVATE)
	static class MatchedOrder {
		private final long orderId;
		private final double quantity;
	}

	/**
	 * cross the given order entry against this bucket's orders, taking as much quantity as possible.
	 *
	 * @param oppositeOrderEntry the order entry from opposite side
	 * @return The match result; contains a summary of how much was executed, and the executions, and
	 */
	MatchResult matchOrder(final OrderEntry oppositeOrderEntry) {
		log.debug("Bucket({}): Matching order '{}' in bucket : {} shares@{}", this.priceOfBucket, oppositeOrderEntry, this.quantityInQueue, this.priceOfBucket);
		final ArrayList<MatchedOrder> matchedOrders = new ArrayList<>();

		final double originalQtyToMatch = oppositeOrderEntry.getRemainingQuantity();
		double remainingQty = originalQtyToMatch;

		for (final Iterator<OrderEntry> it = orderEntryList.iterator(); remainingQty > 0 && it.hasNext(); ) {
			final OrderEntry curEntry = it.next();
			final double qtyTaken = curEntry.takeQuantity(remainingQty);
			if (qtyTaken > 0) {
				log.debug("Bucket({}): Matched '{}' against '{}', qty={}", this.priceOfBucket, oppositeOrderEntry.getOrderId(), curEntry.getOrderId(), qtyTaken);
				matchedOrders.add(MatchedOrder.builder().orderId(curEntry.getOrderId()).quantity(qtyTaken).build());
				remainingQty -= qtyTaken;
			}
		}

		final double totalExecutedQty = originalQtyToMatch - remainingQty;

		LongArrayList doneOrderIds = new LongArrayList();
		this.quantityInQueue -= totalExecutedQty;
		if (totalExecutedQty > 0) {
			final LongArrayList removedEntries = new LongArrayList(matchedOrders.size());
			final boolean removed = this.orderEntryList.removeIfWith((orderEntry, doneIdList) -> {
				if (orderEntry.isOrderDone()) {
					doneIdList.add(orderEntry.getOrderId());
					return true;
				}
				return false;
			}, doneOrderIds);
			if (removed) {
				log.debug("Bucket({}): Orders are done: {}", this.priceOfBucket, removedEntries);
				doneOrderIds = removedEntries;
			}
		}

		return MatchResult.builder()
				.totalMatchedQuantity(totalExecutedQty)
				.matchedOrders(matchedOrders)
				.doneOrderIds(doneOrderIds)
				.build();
	}

	/**
	 * Cancel the order with the given order ID.
	 *
	 * @param orderId the ID of the order to be cancelled
	 * @return {@code true} if the order entry was found and cancelled, {@code false} otherwise.
	 */
	boolean cancelOrder(final long orderId) {
		final Iterator<OrderEntry> it = this.orderEntryList.iterator();
		while (it.hasNext()) {
			final OrderEntry curEntry = it.next();
			if (curEntry.getOrderId() == orderId) {
				log.debug("Bucket({}): Cancel order '{}'", this.priceOfBucket, orderId);
				it.remove();
				// take out the quantity for consistency, in case the object is referenced somewhere else
				this.quantityInQueue -= curEntry.takeQuantity(curEntry.getRemainingQuantity());
				return true;
			}
		}
		log.debug("Bucket({}): Unable to cancel order '{}', order not found", this.priceOfBucket, orderId);
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
				log.debug("Bucket({}): In-place amend order '{}' quantity from {} to {}", this.priceOfBucket, orderId, origQty, newQuantity);
				curEntry.takeQuantity(delta);
				this.quantityInQueue -= delta;
				return true;
			}
			else {
				log.debug("Bucket({}): Removing order '{}' for re-queueing; quantity from {} to {}", this.priceOfBucket, orderId, origQty, newQuantity);
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
