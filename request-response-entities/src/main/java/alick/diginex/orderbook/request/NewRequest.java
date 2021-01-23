package alick.diginex.orderbook.request;

import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;

import java.util.Objects;

public class NewRequest extends Request {
	private final Side side;
	private final OrderType orderType;
	private final double quantity;
	private final double price;

	public NewRequest(final long orderId, final Side side, final OrderType orderType, final double quantity, final double price) {
		super(orderId);
		this.side = Objects.requireNonNull(side, "Order ID = '" + orderId + "': side");
		this.orderType = Objects.requireNonNull(orderType, "Order ID = '" + orderId + "': orderType");

		if (quantity <= 0)
			throw new IllegalArgumentException("Order ID = '" + orderId + "': Order quantity must be positive");
		this.quantity = quantity;

		if (0.0 == price && orderType != OrderType.MARKET)
			throw new IllegalArgumentException("Order ID = '" + orderId + "': Price is 0.0 but order type is " + orderType);
		// price, zero for MARKET order, and can be negative (oil future 2020); no need to validate
		this.price = price;
	}

	public Side getSide() {
		return side;
	}

	public OrderType getOrderType() {
		return orderType;
	}

	public double getQuantity() {
		return quantity;
	}

	public double getPrice() {
		return price;
	}
}