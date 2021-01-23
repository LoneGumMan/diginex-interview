package alick.diginex.orderbook.request;

public abstract class Request {
	private final long orderId;

	protected Request(final long orderId) {
		this.orderId = orderId;
	}

	public long getOrderId() {
		return orderId;
	}
}