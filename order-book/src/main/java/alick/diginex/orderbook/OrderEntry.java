package alick.diginex.orderbook;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(of = "orderId")
@ToString(of = {"orderId", "remainingQuantity"})
class OrderEntry {
	private final long orderId;
	private double remainingQuantity;

	/**
	 * initializes order entry with the order ID and initial order quantity
	 *
	 * @throws IllegalArgumentException if order quantity is zero / negative
	 */
	@lombok.Builder(builderClassName = "Builder", access = AccessLevel.PRIVATE)
	OrderEntry(final long orderId, final double orderQuantity) {
		if (orderQuantity <= 0.0d)
			throw new IllegalArgumentException("order " + orderId + ": quantity cannot be zero or negative: " + orderQuantity);
		this.remainingQuantity = orderQuantity;
		this.orderId = orderId;
	}

	/**
	 * This order entry is a match with another order; take some quantity from this order entry's remaining quantity.
	 *
	 * @param quantityToTake the quantity trying to take from this order
	 * @return the quantity <em>actually taken from this order</em>; this order entry's remainingQty can never be negative.
	 */
	double takeQuantity(final double quantityToTake) {
		final double actualQtyToTake = Math.min(remainingQuantity, quantityToTake);
		this.remainingQuantity -= actualQtyToTake;
		return actualQtyToTake;
	}

	boolean isOrderDone() {
		return 0 == this.remainingQuantity;
//		return this.remainingQuantity < 0.00000001d;
	}
}