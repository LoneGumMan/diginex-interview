package alick.diginex.orderbook.request;

import lombok.AccessLevel;
import lombok.Getter;

public abstract class Request {
	@Getter
	private final long orderId;

	protected Request(final long orderId) {
		this.orderId = orderId;
	}
}