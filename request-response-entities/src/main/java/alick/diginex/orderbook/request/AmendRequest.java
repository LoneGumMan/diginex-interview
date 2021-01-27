package alick.diginex.orderbook.request;

import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import lombok.Getter;
import lombok.NonNull;

import java.util.Objects;

@Getter
public class AmendRequest extends Request {
	private final Side side;
	private final OrderType orderType;
	private final double newOrderQuantity;
	private final double newPrice;

	@lombok.Builder(builderClassName = "Builder")
	private AmendRequest(final long orderId, @NonNull final Side side, @NonNull final OrderType orderType, final double newOrderQuantity, final double newPrice) {
		super(orderId);
		this.side = Objects.requireNonNull(side, "Order ID = '" + orderId + "': side");
		this.orderType = Objects.requireNonNull(orderType, "Order ID = '" + orderId + "': orderType");
		if (newOrderQuantity <= 0)
			throw new IllegalArgumentException("Order ID = '" + orderId + "': Order quantity must be positive");
		this.newOrderQuantity = newOrderQuantity;

		if (0.0 == newPrice && orderType != OrderType.MARKET)
			throw new IllegalArgumentException("Order ID = '" + orderId + "': Price is 0.0 but order type is " + orderType);
		this.newPrice = newPrice;
	}
}
